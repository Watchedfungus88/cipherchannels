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
    private static final long MAX_FILE_BYTES = 1_048_576;
    private final Path configFile;

    public ConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public LoadedConfig load() {
        removeLegacyReplayFiles();
        if (!Files.isRegularFile(configFile)) {
            return loadBackupOrFirstRun();
        }
        try {
            Decoded decoded = read(configFile);
            if (decoded.migrated()) save(decoded.config());
            return new LoadedConfig(decoded.config(), true, ConfigLoadState.NORMAL,
                decoded.migrated() ? "cipherchannels.notice.config.migrated_v4" : "");
        } catch (ObsoleteVersionException exception) {
            return resetObsolete(configFile);
        } catch (NewerVersionException exception) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_NEWER,
                "cipherchannels.notice.config.newer");
        } catch (IOException | RuntimeException exception) {
            return recoverAfterFailure();
        }
    }

    public LoadedConfig resetUnsafeConfiguration() {
        Path backup = backupFile();
        if (!preserve(configFile, "reset") || !preserve(backup, "reset")) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.reset_failed");
        }
        ChannelConfig empty = ChannelConfig.empty();
        save(empty);
        return new LoadedConfig(empty, true, ConfigLoadState.NORMAL,
            "cipherchannels.notice.config.reset");
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
            forceDirectory(directory);
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

    public Path configDirectory() {
        return configFile.getParent();
    }

    private LoadedConfig loadBackupOrFirstRun() {
        Path backup = backupFile();
        if (!Files.isRegularFile(backup)) {
            return new LoadedConfig(ChannelConfig.empty(), true, ConfigLoadState.NORMAL, "");
        }
        try {
            Decoded recovered = read(backup);
            save(recovered.config());
            return new LoadedConfig(recovered.config(), true, ConfigLoadState.NORMAL,
                "cipherchannels.notice.config.recovered_backup");
        } catch (ObsoleteVersionException exception) {
            return resetObsolete(backup);
        } catch (NewerVersionException exception) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_NEWER,
                "cipherchannels.notice.config.backup_newer");
        } catch (IOException | RuntimeException exception) {
            preserve(backup, "corrupt");
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.backup_corrupt");
        }
    }

    private LoadedConfig recoverAfterFailure() {
        if (!preserve(configFile, "corrupt")) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.preserve_failed");
        }
        Path backup = backupFile();
        if (!Files.isRegularFile(backup)) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.corrupt");
        }
        try {
            Decoded recovered = read(backup);
            save(recovered.config());
            return new LoadedConfig(recovered.config(), true, ConfigLoadState.NORMAL,
                "cipherchannels.notice.config.recovered_primary");
        } catch (ObsoleteVersionException exception) {
            return resetObsolete(backup);
        } catch (NewerVersionException exception) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_NEWER,
                "cipherchannels.notice.config.recovered_newer");
        } catch (IOException | RuntimeException exception) {
            preserve(backup, "corrupt");
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.both_corrupt");
        }
    }

    private LoadedConfig resetObsolete(Path source) {
        if (!preserve(source, "pre-2.0")) {
            return new LoadedConfig(ChannelConfig.empty(), false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.pre20_preserve_failed");
        }
        if (!source.equals(backupFile())) {
            preserve(backupFile(), "pre-2.0");
        }
        ChannelConfig empty = ChannelConfig.empty();
        try {
            save(empty);
            return new LoadedConfig(empty, true, ConfigLoadState.NORMAL,
                "cipherchannels.notice.config.pre20_reset");
        } catch (RuntimeException exception) {
            return new LoadedConfig(empty, false, ConfigLoadState.LOCKED_CORRUPT,
                "cipherchannels.notice.config.pre20_save_failed");
        }
    }

    private Decoded read(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Settings file is too large");
        return decode(Files.readString(path, StandardCharsets.UTF_8));
    }

    private void removeLegacyReplayFiles() {
        for (String name : List.of("cipherchannels-replay.json", "cipherchannels-replay.json.bak")) {
            try {
                Files.deleteIfExists(configFile.resolveSibling(name));
            } catch (IOException ignored) {
            }
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, java.nio.file.StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    private Path backupFile() {
        return configFile.resolveSibling(FILE_NAME + ".bak");
    }

    private static boolean preserve(Path source, String label) {
        if (!Files.exists(source)) {
            return true;
        }
        Path destination = source.resolveSibling(source.getFileName() + "." + label + '-' + Instant.now().toEpochMilli());
        try {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, destination);
            }
            return true;
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

    private static Decoded decode(String source) {
        JsonElement parsed = JsonParser.parseString(source);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Settings root is not an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        int version = required(root, "version").getAsInt();
        if (version > ChannelConfig.CURRENT_VERSION) {
            throw new NewerVersionException();
        }
        if (version < 3) {
            throw new ObsoleteVersionException();
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
            if (version == 3 && !required(entry, "verification").getAsString()
                .matches("LOCAL_CREATED|UNVERIFIED|VERIFIED")) {
                throw new IllegalArgumentException("Invalid verification value");
            }
            channels.add(new ChannelRecord(id, name, fingerprint, binding));
        }
        List<TransportOverride> overrides = new ArrayList<>();
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
        return new Decoded(new ChannelConfig(ChannelConfig.CURRENT_VERSION, enabled, active, channels, overrides),
            version == 3);
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

    private static final class ObsoleteVersionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record Decoded(ChannelConfig config, boolean migrated) {}
}
