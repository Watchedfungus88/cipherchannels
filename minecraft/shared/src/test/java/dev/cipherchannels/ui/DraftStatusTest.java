package dev.cipherchannels.ui;

import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ChannelService;
import dev.cipherchannels.channels.MessagePreflight;
import dev.cipherchannels.channels.OutboundBlockReason;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.protocol.TransportMode;
import dev.cipherchannels.storage.ConfigStore;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftStatusTest {
    @TempDir
    Path directory;

    @Test
    void idleRawAndCompressedDraftsShowOnlyTheChannelAndKeepTheirPreflight() {
        try (ChannelService channels = new ChannelService(new ConfigStore(directory))) {
            channels.create("Friends");
            channels.setEnabled(true, null);
            for (TransportMode mode : TransportMode.values()) {
                ServerBinding server = ServerBinding.of("example.org", 25565);
                channels.setTransport(server, mode);
                for (String draft : new String[] {"", "hello", "hello ".repeat(100)}) {
                    MessagePreflight preflight = channels.preflightOutgoing(draft, server);
                    if (!draft.isEmpty()) {
                        assertTrue(preflight.ready());
                        assertEquals(draft.length() > mode.rawCapacity(), preflight.frame().compressed());
                    }
                    DraftStatus status = ClientContext.statusForPreflight(preflight, draft.length());
                    TranslatableContents text = (TranslatableContents) status.message().getContents();
                    assertEquals("cipherchannels.chat.status.encrypted", text.getKey());
                    assertArrayEquals(new Object[] {"Friends"}, text.getArgs());
                    assertEquals(0xFF55FF55, status.color());
                    assertSame(preflight, status.preflight());
                }
            }
        }
    }

    @Test
    void overlongAndNonFittingDraftsRemainBlockedWithoutByteReadouts() {
        try (ChannelService channels = new ChannelService(new ConfigStore(directory))) {
            channels.create("Friends");
            channels.setEnabled(true, null);
            assertBlocked(channels.preflightOutgoing("a".repeat(4097), null), 4097,
                OutboundBlockReason.SOURCE_TOO_LARGE, "cipherchannels.block.source_too_large");
            Random random = new Random(9328);
            StringBuilder draft = new StringBuilder();
            for (int index = 0; index < 2000; index++) draft.append((char) ('!' + random.nextInt(90)));
            assertBlocked(channels.preflightOutgoing(draft.toString(), null), draft.length(),
                OutboundBlockReason.DOES_NOT_FIT, "cipherchannels.block.does_not_fit");
        }
    }

    @Test
    void missingInvitesAndWrongServersNeverShowAnEncryptedReadyStatus() {
        ChannelRecord channel;
        ServerBinding server = ServerBinding.of("example.org", 25565);
        try (ChannelService channels = new ChannelService(new ConfigStore(directory))) {
            channel = channels.create("Friends");
            channels.bind(channel.id(), server);
            channels.setEnabled(true, server);
            assertBlocked(channels.preflightOutgoing("hello", null), 5,
                OutboundBlockReason.BINDING_MISMATCH, "cipherchannels.block.binding");
        }
        try (ChannelService channels = new ChannelService(new ConfigStore(directory))) {
            assertFalse(channels.hasSessionKey(channel.id()));
            assertBlocked(channels.preflightOutgoing("hello", server), 5,
                OutboundBlockReason.NO_CHANNEL, "cipherchannels.block.no_key");
        }
    }

    @Test
    void plaintextStatusAndItsLengthLimitRemainExplicit() {
        try (ChannelService channels = new ChannelService(new ConfigStore(directory))) {
            MessagePreflight preflight = channels.preflightOutgoing("hello", null);
            assertStatus(preflight, 5, "cipherchannels.chat.status.off", 0xFFAAAAAA);
            assertStatus(preflight, 257, "cipherchannels.chat.status.public_too_long", 0xFFFF5555);
        }
    }

    private static void assertBlocked(MessagePreflight preflight, int length,
                                      OutboundBlockReason reason, String key) {
        assertFalse(preflight.ready());
        assertEquals(reason, preflight.blockReason());
        assertStatus(preflight, length, key, 0xFFFF5555);
    }

    private static void assertStatus(MessagePreflight preflight, int length, String key, int color) {
        DraftStatus status = ClientContext.statusForPreflight(preflight, length);
        assertEquals(key, ((TranslatableContents) status.message().getContents()).getKey());
        assertEquals(color, status.color());
        assertSame(preflight, status.preflight());
    }
}
