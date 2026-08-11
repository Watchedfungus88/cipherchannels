package dev.cipherchannels.crypto;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityAndInviteTest {
    @Test
    void inviteRoundTripsAndRejectsEveryChecksumMutation() {
        try (KeyMaterial original = ChannelKeys.generate()) {
            String invite = InviteCode.create(original);
            assertTrue(invite.matches("CC2\\.[A-Za-z0-9_-]{43}\\.[0-9A-HJKMNP-TV-Z]{8}"));
            try (KeyMaterial parsed = InviteCode.parse(invite)) {
                assertTrue(Arrays.equals(original.copyBytes(), parsed.copyBytes()));
            }
            for (int index = invite.length() - 8; index < invite.length(); index++) {
                String mutation = mutate(invite, index);
                assertThrows(IllegalArgumentException.class, () -> InviteCode.parse(mutation));
            }
            for (int index = 4; index < 47; index++) {
                String mutation = mutate(invite, index);
                assertThrows(IllegalArgumentException.class, () -> InviteCode.parse(mutation));
            }
            assertThrows(IllegalArgumentException.class, () -> InviteCode.parse(invite + "="));
        }
    }

    @Test
    void fingerprintIsStableAndDoesNotUseAmbiguousCharacters() {
        byte[] raw = new byte[KeyMaterial.LENGTH];
        Arrays.fill(raw, (byte) 42);
        try (KeyMaterial first = KeyMaterial.fromBytes(raw); KeyMaterial second = KeyMaterial.fromBytes(raw);
             KeyMaterial different = ChannelKeys.generate()) {
            String fingerprint = ChannelIdentity.fingerprint(first);
            assertEquals(fingerprint, ChannelIdentity.fingerprint(second));
            assertNotEquals(fingerprint, ChannelIdentity.fingerprint(different));
            assertTrue(fingerprint.matches("[0-9A-HJKMNP-TV-Z]{4}(?:-[0-9A-HJKMNP-TV-Z]{4}){3}"));
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    @Test
    void inviteRejectsNonCanonicalBase64urlPaddingBits() {
        byte[] zeros = new byte[KeyMaterial.LENGTH];
        try (KeyMaterial key = KeyMaterial.fromBytes(zeros)) {
            String invite = InviteCode.create(key);
            int finalKeyCharacter = 4 + 42;
            assertEquals('A', invite.charAt(finalKeyCharacter));
            String alias = invite.substring(0, finalKeyCharacter) + 'B'
                + invite.substring(finalKeyCharacter + 1);
            assertThrows(IllegalArgumentException.class, () -> InviteCode.parse(alias));
        } finally {
            Arrays.fill(zeros, (byte) 0);
        }
    }

    @Test
    void rejectsVersionOneInvitesWithAnActionableMessage() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> InviteCode.parse("CC1." + "A".repeat(43) + ".00000000"));
        assertTrue(failure.getMessage().contains("incompatible"));
        assertTrue(failure.getMessage().contains("2.0"));

        byte[] raw = new byte[KeyMaterial.LENGTH];
        byte[] digest = ChannelIdentity.sha256(
            "CipherChannels invite checksum v1\0".getBytes(StandardCharsets.UTF_8), raw);
        byte[] checksumBytes = Arrays.copyOf(digest, 5);
        String disguised = "CC2." + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
            + '.' + CrockfordBase32.encode(checksumBytes);
        assertThrows(IllegalArgumentException.class, () -> InviteCode.parse(disguised));
        Arrays.fill(raw, (byte) 0);
        Arrays.fill(digest, (byte) 0);
        Arrays.fill(checksumBytes, (byte) 0);
    }

    private static String mutate(String source, int index) {
        char replacement = source.charAt(index) == '0' ? '1' : '0';
        return source.substring(0, index) + replacement + source.substring(index + 1);
    }
}
