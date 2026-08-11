package dev.cipherchannels.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cipherchannels.channels.ChannelConfig;
import dev.cipherchannels.channels.ChannelRecord;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPolicyTest {
    @Test
    void draftLimitsAndBackgroundOpacityAreBounded() {
        assertEquals(256, UiPolicy.editorLimit(false, 200));
        assertEquals(4096, UiPolicy.editorLimit(true, 0));
        assertEquals(4096, UiPolicy.editorLimit(false, 300));
        assertEquals(0x00000000, UiPolicy.textBackgroundColor(0.0));
        assertEquals(0x80000000, UiPolicy.textBackgroundColor(0.5));
        assertEquals(0xFF000000, UiPolicy.textBackgroundColor(1.0));
        assertEquals(0x00000000, UiPolicy.textBackgroundColor(-1.0));
        assertEquals(0xFF000000, UiPolicy.textBackgroundColor(2.0));
    }

    @Test
    void switchingIsImmediateOnlyWhenEncryptionIsOffAndNeverHidden() {
        ChannelRecord first = record("One");
        ChannelRecord second = record("Two");
        ChannelConfig off = new ChannelConfig(ChannelConfig.CURRENT_VERSION, false, first.id(),
            List.of(first, second), List.of());
        assertEquals(UiPolicy.SwitchFlow.ALREADY_ACTIVE, UiPolicy.switchFlow(off, first.id()));
        assertEquals(UiPolicy.SwitchFlow.IMMEDIATE, UiPolicy.switchFlow(off, second.id()));
        assertEquals(UiPolicy.SwitchFlow.CONFIRM, UiPolicy.switchFlow(off.withEnabled(true), second.id()));
    }

    @Test
    void everyPersistentActionBannerAndDisabledReasonHasEnglishText() throws Exception {
        JsonObject translations = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(
            getClass().getResourceAsStream("/assets/cipherchannels/lang/en_us.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        for (String key : List.of(
            "cipherchannels.feedback.created", "cipherchannels.feedback.joined",
            "cipherchannels.feedback.renamed", "cipherchannels.feedback.encryption_on",
            "cipherchannels.feedback.encryption_off", "cipherchannels.feedback.switched_on",
            "cipherchannels.feedback.switched_off", "cipherchannels.feedback.invite_copied",
            "cipherchannels.feedback.forgotten", "cipherchannels.feedback.bound",
            "cipherchannels.feedback.unbound", "cipherchannels.feedback.transport",
            "cipherchannels.feedback.failed", "cipherchannels.feedback.replaced",
            "cipherchannels.feedback.verified", "cipherchannels.feedback.verification_reset",
            "cipherchannels.disabled.no_channel", "cipherchannels.disabled.not_ready",
            "cipherchannels.disabled.multiplayer_only", "cipherchannels.disabled.chat_logging",
            "cipherchannels.disabled.unverified_switch",
            "cipherchannels.chat.status.ready_raw", "cipherchannels.chat.status.ready_compressed",
            "cipherchannels.chat.status.warning", "cipherchannels.chat.status.unverified",
            "cipherchannels.chat.status.replay_session_only",
            "cipherchannels.chat.status.command_plaintext", "cipherchannels.block.source_too_large",
            "cipherchannels.block.does_not_fit", "cipherchannels.block.config_locked",
            "key.category.cipherchannels.general")) {
            assertTrue(translations.has(key), key);
            assertFalse(translations.get(key).getAsString().isBlank(), key);
        }
    }

    private static ChannelRecord record(String name) {
        return new ChannelRecord(UUID.randomUUID(), name, "0123-4567-89AB-CDEF", null);
    }
}
