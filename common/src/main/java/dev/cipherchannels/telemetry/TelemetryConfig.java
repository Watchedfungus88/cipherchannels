package dev.cipherchannels.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Local, non-secret identity and opt-out state for the diagnostics channel. */
final class TelemetryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_BYTES = 8192;

    String installId = "";
    boolean enabled = true;
    int payloadVersion;

    static TelemetryConfig load(Path file) {
        TelemetryConfig config = new TelemetryConfig();
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES) {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonElement id = json.get("installId");
                if (id != null && id.getAsString().matches("[a-f0-9]{32}")) config.installId = id.getAsString();
                JsonElement enabled = json.get("enabled");
                if (enabled != null) config.enabled = enabled.getAsBoolean();
                JsonElement version = json.get("payloadVersion");
                if (version != null) config.payloadVersion = version.getAsInt();
            }
        } catch (IOException | RuntimeException ignored) {
            // A broken file just means a fresh identity; never block the game.
        }
        if (config.installId.isEmpty()) {
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            config.installId = HexFormat.of().formatHex(random);
        }
        config.save(file);
        return config;
    }

    void save(Path file) {
        try {
            Path directory = file.getParent();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".telemetry-", ".tmp");
            Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Not worth surfacing.
        }
    }
}
