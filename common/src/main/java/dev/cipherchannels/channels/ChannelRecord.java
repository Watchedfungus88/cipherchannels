package dev.cipherchannels.channels;

import java.util.Objects;
import java.util.UUID;

public record ChannelRecord(UUID id, String name, String fingerprint, ServerBinding binding) {
    public ChannelRecord {
        Objects.requireNonNull(id, "id");
        name = validateName(name);
        if (fingerprint == null || !fingerprint.matches("[0-9A-HJKMNP-TV-Z]{4}(?:-[0-9A-HJKMNP-TV-Z]{4}){3}")) {
            throw new IllegalArgumentException("Invalid channel fingerprint");
        }
    }

    public ChannelRecord renamed(String newName) {
        return new ChannelRecord(id, newName, fingerprint, binding);
    }

    public ChannelRecord withBinding(ServerBinding newBinding) {
        return new ChannelRecord(id, name, fingerprint, newBinding);
    }

    private static String validateName(String value) {
        Objects.requireNonNull(value, "name");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 48) {
            throw new IllegalArgumentException("Channel names must contain 1 to 48 characters");
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (Character.isISOControl(trimmed.charAt(index))) {
                throw new IllegalArgumentException("Channel names cannot contain control characters");
            }
        }
        return trimmed;
    }
}
