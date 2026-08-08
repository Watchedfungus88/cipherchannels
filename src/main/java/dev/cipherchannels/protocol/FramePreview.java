package dev.cipherchannels.protocol;

public record FramePreview(Status status, TransportMode transport, int sourceBytes, int payloadBytes) {
    public enum Status {
        READY_RAW,
        READY_COMPRESSED,
        EMPTY,
        MALFORMED_UNICODE,
        SOURCE_TOO_LARGE,
        DOES_NOT_FIT
    }

    public boolean ready() {
        return status == Status.READY_RAW || status == Status.READY_COMPRESSED;
    }

    public boolean compressed() {
        return status == Status.READY_COMPRESSED;
    }

    public int capacity() {
        return transport.rawCapacity();
    }
}
