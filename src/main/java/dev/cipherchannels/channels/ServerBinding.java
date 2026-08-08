package dev.cipherchannels.channels;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

public record ServerBinding(String host, int port) {
    public ServerBinding {
        Objects.requireNonNull(host, "host");
        host = normalizeHost(host);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535");
        }
    }

    public static ServerBinding of(String host, int port) {
        return new ServerBinding(host, port);
    }

    public String displayName() {
        return host.indexOf(':') >= 0 ? '[' + host + "]:" + port : host + ':' + port;
    }

    private static String normalizeHost(String source) {
        String value = source.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty() || value.length() > 253 || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid server host");
        }
        if (value.indexOf(':') >= 0) {
            return value.toLowerCase(Locale.ROOT);
        }
        String ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        return ascii.endsWith(".") ? ascii.substring(0, ascii.length() - 1) : ascii;
    }
}
