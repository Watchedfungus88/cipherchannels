package dev.cipherchannels.protocol;

import java.util.ArrayList;
import java.util.List;

public final class FrameScanner {
    public static final int MAX_LITERAL_SCAN = 4096;
    public static final int MAX_CANDIDATES = 1;

    private FrameScanner() {}

    public static List<FrameCandidate> scan(String text) {
        if (text == null || text.length() < FrameCodec.WIRE_LENGTH || text.length() > MAX_LITERAL_SCAN) {
            return List.of();
        }

        List<FrameCandidate> candidates = new ArrayList<>(2);
        if (!scanRuns(text, TransportMode.HIGH_CAPACITY, candidates)
            || !scanRuns(text, TransportMode.ASCII_COMPATIBILITY, candidates)) {
            return List.of();
        }
        return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
    }

    private static boolean scanRuns(String text, TransportMode transport,
                                    List<FrameCandidate> candidates) {
        int runStart = -1;
        for (int index = 0; index <= text.length(); index++) {
            boolean inAlphabet = index < text.length()
                && transport.accepts(text.charAt(index));
            if (inAlphabet) {
                if (runStart < 0) {
                    runStart = index;
                }
                continue;
            }
            if (runStart < 0) {
                continue;
            }

            if (index - runStart == FrameCodec.WIRE_LENGTH) {
                candidates.add(new FrameCandidate(runStart, index,
                    text.substring(runStart, index), transport));
                if (candidates.size() > MAX_CANDIDATES) {
                    return false;
                }
            }
            runStart = -1;
        }
        return true;
    }
}
