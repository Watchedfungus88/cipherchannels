package dev.cipherchannels.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cipherchannels.channels.ChannelConfig;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.channels.TransportOverride;
import dev.cipherchannels.protocol.TransportMode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConfigStore {
    private static final String FILE_NAME = "cipherchannels.json";
    private final Path configFile;

    public ConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public LoadedConfig load() {
        if (!Files.exists(configFile)) {
            Path backup = configFile.resolveSibling(FILE_NAME + ".bak");
            if (Files.isRegularFile(backup)) {
                try {
                    ChannelConfig recovered = decode(Files.readString(backup, StandardCharsets.UTF_8));
                    save(recovered);
                    return new LoadedConfig(recovered, true,
                        "CipherChannels settings were recovered from the backup file.");
                } catch (NewerVersionException exception) {
                    return new LoadedConfig(ChannelConfig.empty(), false,
                        "CipherChannels backup configuration is from a newer version and was left untouched.");
                } catch (IOException | RuntimeException ignored) {
                    return new LoadedConfig(ChannelConfig.empty(), true, "");
                }
            }
            return new LoadedConfig(ChannelConfig.empty(), true, "");
        }
        try {
            String source = Files.readString(configFile, StandardCharsets.UTF_8);
            ChannelConfig config = decode(source);
            return new LoadedConfig(config, true, "");
        } catch (NewerVersionException exception) {
            return new LoadedConfig(ChannelConfig.empty(), false,
                "CipherChannels configuration is from a newer version and was left untouched.");
        } catch (IOException | RuntimeException exception) {
            boolean preserved = preserveCorruptFile();
            String notice = preserved
                ? "CipherChannels configuration was invalid and was preserved as a .corrupt file."
                : "CipherChannels configuration could not be read safely; settings are read-only for this session.";
            return new LoadedConfig(ChannelConfig.empty(), preserved, notice);
        }
    }

    public void save(ChannelConfig config) {
        String encoded = encode(config);
        byte[] content = encoded.getBytes(StandardCharsets.UTF_8);
        Path directory = configFile.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".cipherchannels-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(content));
                channel.force(true);
            }
            if (Files.exists(configFile)) {
                Files.copy(configFile, directory.resolve(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("CipherChannels could not safely save settings", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    temporary.toFile().deleteOnExit();
                }
            }
            java.util.Arrays.fill(content, (byte) 0);
        }
    }

    public Path configFile() {
        return configFile;
    }

    private boolean preserveCorruptFile() {
        Path destination = configFile.resolveSibling(FILE_NAME + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(configFile, destination, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(configFile, destination);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        } catch (IOException exception) {
            return false;
        }
    }

    private static String encode(ChannelConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("version", config.version());
        root.addProperty("encryptionEnabled", config.encryptionEnabled());
        if (config.activeChannelId() == null) {
            root.add("activeChannelId", com.google.gson.JsonNull.INSTANCE);
        } else {
            root.addProperty("activeChannelId", config.activeChannelId().toString());
        }
        JsonArray channels = new JsonArray();
        for (ChannelRecord channel : config.channels()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", channel.id().toString());
            entry.addProperty("name", channel.name());
            entry.addProperty("fingerprint", channel.fingerprint());
            if (channel.binding() == null) {
                entry.add("binding", com.google.gson.JsonNull.INSTANCE);
            } else {
                JsonObject binding = new JsonObject();
                binding.addProperty("host", channel.binding().host());
                binding.addProperty("port", channel.binding().port());
                entry.add("binding", binding);
            }
            channels.add(entry);
        }
        root.add("channels", channels);
        JsonArray transportOverrides = new JsonArray();
        for (TransportOverride override : config.transportOverrides()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("host", override.endpoint().host());
            entry.addProperty("port", override.endpoint().port());
            entry.addProperty("mode", override.mode().name());
            transportOverrides.add(entry);
        }
        root.add("transportOverrides", transportOverrides);
        return root.toString();
    }

    private static ChannelConfig decode(String source) {
        JsonElement parsed = JsonParser.parseString(source);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Settings root is not an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        int version = required(root, "version").getAsInt();
        if (version > ChannelConfig.CURRENT_VERSION) {
            throw new NewerVersionException();
        }
        if (version < 1) {
            throw new IllegalArgumentException("Unsupported settings version");
        }
        boolean enabled = required(root, "encryptionEnabled").getAsBoolean();
        UUID active = nullableUuid(required(root, "activeChannelId"));
        JsonElement rawChannels = required(root, "channels");
        if (!rawChannels.isJsonArray()) {
            throw new IllegalArgumentException("channels must be an array");
        }
        List<ChannelRecord> channels = new ArrayList<>();
        for (JsonElement element : rawChannels.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Invalid channel entry");
            }
            JsonObject entry = element.getAsJsonObject();
            UUID id = UUID.fromString(required(entry, "id").getAsString());
            String name = required(entry, "name").getAsString();
            String fingerprint = required(entry, "fingerprint").getAsString();
            ServerBinding binding = nullableBinding(required(entry, "binding"));
            channels.add(new ChannelRecord(id, name, fingerprint, binding));
        }
        List<TransportOverride> overrides = new ArrayList<>();
        if (version >= 2) {
            JsonElement rawOverrides = required(root, "transportOverrides");
            if (!rawOverrides.isJsonArray()) {
                throw new IllegalArgumentException("transportOverrides must be an array");
            }
            for (JsonElement element : rawOverrides.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Invalid transport override");
                }
                JsonObject entry = element.getAsJsonObject();
                ServerBinding endpoint = ServerBinding.of(required(entry, "host").getAsString(),
                    required(entry, "port").getAsInt());
                TransportMode mode = TransportMode.valueOf(required(entry, "mode").getAsString());
                overrides.add(new TransportOverride(endpoint, mode));
            }
        }
        return new ChannelConfig(ChannelConfig.CURRENT_VERSION, enabled, active, channels, overrides);
    }

    private static JsonElement required(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing settings field");
        }
        return value;
    }

    private static UUID nullableUuid(JsonElement value) {
        return value.isJsonNull() ? null : UUID.fromString(value.getAsString());
    }

    private static ServerBinding nullableBinding(JsonElement value) {
        if (value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("Invalid binding");
        }
        JsonObject binding = value.getAsJsonObject();
        return ServerBinding.of(required(binding, "host").getAsString(), required(binding, "port").getAsInt());
    }

    private static final class NewerVersionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
