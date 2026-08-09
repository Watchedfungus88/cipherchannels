package dev.cipherchannels.protocol;

import java.util.Arrays;

public final class ParsedFrame implements AutoCloseable {
    private final TransportMode transport;
    private final byte[] nonce;
    private final byte[] recognitionHint;
    private final byte[] ciphertextAndTag;
    private final byte[] binary;
    private final String wire;

    ParsedFrame(TransportMode transport, byte[] nonce, byte[] recognitionHint,
                byte[] ciphertextAndTag, byte[] binary, String wire) {
        this.transport = transport;
        this.nonce = nonce;
        this.recognitionHint = recognitionHint;
        this.ciphertextAndTag = ciphertextAndTag;
        this.binary = binary;
        this.wire = wire;
    }

    public TransportMode transport() {
        return transport;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] recognitionHint() {
        return recognitionHint.clone();
    }

    public byte[] ciphertextAndTag() {
        return ciphertextAndTag.clone();
    }

    public byte[] binary() {
        return binary.clone();
    }

    public String wire() {
        return wire;
    }

    @Override
    public void close() {
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(recognitionHint, (byte) 0);
        Arrays.fill(ciphertextAndTag, (byte) 0);
        Arrays.fill(binary, (byte) 0);
    }
}
