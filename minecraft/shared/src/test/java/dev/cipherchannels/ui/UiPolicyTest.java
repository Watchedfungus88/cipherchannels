package dev.cipherchannels.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPolicyTest {
    @Test
    void validatesLocalChannelNames() {
        assertFalse(UiPolicy.validChannelName(null));
        assertFalse(UiPolicy.validChannelName("  "));
        assertFalse(UiPolicy.validChannelName("x".repeat(49)));
        assertFalse(UiPolicy.validChannelName("Friends\nWork"));
        assertTrue(UiPolicy.validChannelName(" Friends "));
        assertTrue(UiPolicy.validChannelName("家族"));
    }

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
    void chatStatusIsCenteredOverTheButtonInsteadOfRightAligned() {
        for (int screenWidth : new int[] {320, 427, 640, 960}) {
            int buttonX = screenWidth - 152;
            for (int textWidth = 0; textWidth <= 148; textWidth++) {
                int x = UiPolicy.chatStatusX(screenWidth, buttonX, 148, textWidth);
                assertTrue(Math.abs((2 * x + textWidth) - (2 * buttonX + 148)) <= 1);
                assertTrue(x >= 4);
                assertTrue(x + textWidth <= screenWidth - 4);
            }
        }
        assertEquals(178, UiPolicy.chatStatusX(320, 168, 148, 128));
        assertEquals(204, UiPolicy.chatStatusX(320, 168, 148, 76));
        assertEquals(75, UiPolicy.chatStatusX(640, 40, 148, 78));
    }

    @Test
    void longerChatWarningsStayInsideTheScreenWhenTheyFit() {
        assertEquals(116, UiPolicy.chatStatusX(320, 168, 148, 200));
        assertEquals(4, UiPolicy.chatStatusX(320, 4, 148, 200));
        assertEquals(4, UiPolicy.chatStatusX(320, 168, 148, 312));
        assertEquals(4, UiPolicy.chatStatusX(320, 168, 148, 500));
    }

    @Test
    void vanillaManagerTextIsCompleteAndDashboardTextIsGone() throws Exception {
        JsonObject translations = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(
            getClass().getResourceAsStream("/assets/cipherchannels/lang/en_us.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        for (String key : List.of(
            "cipherchannels.manager.encryption.on", "cipherchannels.manager.encryption.off",
            "cipherchannels.manager.encryption.on_tooltip",
            "cipherchannels.manager.state.key_needed", "cipherchannels.manager.state.binding",
            "cipherchannels.manager.state.logging_blocked", "cipherchannels.manager.empty",
            "cipherchannels.manager.use", "cipherchannels.manager.settings",
            "cipherchannels.manager.create", "cipherchannels.manager.import",
            "cipherchannels.manager.row.active", "cipherchannels.manager.row.key_loaded",
            "cipherchannels.manager.row.key_needed", "cipherchannels.manager.row.narration",
            "cipherchannels.manager.row.active_key_needed",
            "cipherchannels.form.import.title", "cipherchannels.form.load.title",
            "cipherchannels.form.invite.show", "cipherchannels.form.invite.hide",
            "cipherchannels.form.validation.legacy_invite", "cipherchannels.form.validation.wrong_channel",
            "cipherchannels.form.validation.key_limit",
            "cipherchannels.settings.bind_tooltip", "cipherchannels.settings.unbind_tooltip",
            "cipherchannels.settings.remember.on", "cipherchannels.settings.remember.off",
            "cipherchannels.settings.remember.tooltip", "cipherchannels.toast.remember_failed",
            "cipherchannels.settings.copy_invite", "cipherchannels.settings.invite_warning",
            "cipherchannels.settings.transport", "cipherchannels.settings.rotate",
            "cipherchannels.toast.invite_copied", "cipherchannels.toast.switch_failed",
            "cipherchannels.disabled.no_channel", "cipherchannels.disabled.key_needed",
            "cipherchannels.disabled.binding", "cipherchannels.disabled.select_channel",
            "cipherchannels.disabled.multiplayer_only", "cipherchannels.disabled.chat_logging",
            "cipherchannels.chat.status.encrypted", "cipherchannels.chat.status.off",
            "cipherchannels.chat.status.command_plaintext", "cipherchannels.block.source_too_large",
            "cipherchannels.block.does_not_fit", "cipherchannels.block.config_locked",
            "key.category.cipherchannels.general")) {
            assertTrue(translations.has(key), key);
            assertFalse(translations.get(key).getAsString().isBlank(), key);
        }
        for (String removed : List.of(
            "cipherchannels.header.summary", "cipherchannels.tab.overview",
            "cipherchannels.feedback.created", "cipherchannels.onboarding.explanation",
            "cipherchannels.overview.active", "cipherchannels.channels.row.ready",
            "cipherchannels.channel_tab.active", "cipherchannels.form.join.title",
            "cipherchannels.form.invite.reveal", "cipherchannels.disabled.not_ready",
            "cipherchannels.chat.status.ready_raw", "cipherchannels.chat.status.ready_compressed",
            "cipherchannels.settings.key_loaded", "cipherchannels.settings.key_missing",
            "cipherchannels.settings.access", "cipherchannels.settings.share",
            "cipherchannels.settings.server", "cipherchannels.settings.local",
            "cipherchannels.settings.fingerprint", "cipherchannels.settings.endpoint")) {
            assertFalse(translations.has(removed), removed);
        }
    }

}
