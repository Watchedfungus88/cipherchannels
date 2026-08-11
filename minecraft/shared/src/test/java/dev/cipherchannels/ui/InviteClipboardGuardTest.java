package dev.cipherchannels.ui;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteClipboardGuardTest {
    @Test
    void channelRemovalClearsOnlyTheMatchingInvite() {
        InviteClipboardGuard.Lease lease = new InviteClipboardGuard.Lease();
        UUID channel = UUID.randomUUID();
        lease.copy(channel, "CC2.secret");
        assertFalse(lease.clearMatching(UUID.randomUUID(), "CC2.secret"));
        assertTrue(lease.clearMatching(channel, "CC2.secret"));
    }

    @Test
    void repeatedCopiesAndChannelRemovalCannotEraseUnrelatedClipboardData() {
        InviteClipboardGuard.Lease lease = new InviteClipboardGuard.Lease();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        lease.copy(first, "CC2.first");
        lease.copy(second, "CC2.second");
        assertFalse(lease.clearMatching(first, "CC2.second"));
        assertFalse(lease.clearMatching(second, "unrelated"));

        lease.copy(second, "CC2.second");
        assertTrue(lease.clearMatching(null, "CC2.second"));
    }
}
