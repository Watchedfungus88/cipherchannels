# Changelog

## 1.0.0 — 2026-08-09

- Added Fabric and NeoForge builds for Minecraft 1.21.1, 1.21.11, 26.1, 26.1.2, and 26.2 from one shared protocol/security core.
- Added native loader integration for key mappings and configuration screens; Mod Menu remains an optional Fabric integration.
- Replaced the unreleased `~CC1:…~` public-chat format with markerless, fixed 256-character frames; no legacy receive path is shipped.
- Added a 480-byte Base32768 high-capacity transport with a guaranteed 443 raw UTF-8 bytes and a manual per-server 192-byte Base64url compatibility transport with 155 raw bytes.
- Added bounded raw-DEFLATE compression for useful drafts up to 4,096 restored UTF-8 bytes.
- Added HKDF-SHA-256 domain-separated AES and recognition subkeys plus rotating authenticated 64-bit recognition hints.
- Preserved fresh AES-256-GCM nonces, 128-bit tags, ciphertext signing, session-only keys, strict binding, replay blocking, fail-closed sending, and hover-to-view exact ciphertext.
- Expanded encrypted chat drafts, added cached preflight status, and now preserve the complete editor contents with exact inline and local feedback whenever Enter is blocked.
- Continued encryption in integrated singleplayer while keeping binding and endpoint transport overrides multiplayer-only.
- Replaced the ambiguous flat manager with first-use onboarding and clear Overview, Channels, and Share & Security tabs.
- Added persistent action banners, active-channel checkmarks, key readiness, explicit channel-switch confirmation, guarded invite copying, real forget confirmation, visible bound-channel switching, disabled-action tooltips, and stateful chat entry labels.
- Polished the Channels tab with a framed list, padded separated channel cards, fitted text, and distinct active, hover, and keyboard-focus states.
- Fixed incoming decryption when server or client chat formatters surround an intact encrypted message with timestamps, channel labels such as `[L]`, player names, ranks, or suffixes.
- Fixed invisible channel-list rows and chat-input status text under Minecraft 26.2's alpha-aware GUI renderer.
- Moved the live chat status to a right-aligned backed panel so it no longer stacks over ChatPatches or vanilla chat lines.
- Made the status panel follow Minecraft's Text Background Opacity setting, including fully transparent at 0%.
- Made the manager shortcut gameplay-only so typing `O` in chat does not open the manager, and fixed its controls-category translation.
- Tightened invite parsing to reject non-canonical Base64url aliases caused by non-zero unused bits.
- Migrated non-secret development configuration to schema version 2 with at most 64 per-endpoint ASCII overrides and high capacity as the default.
- Expanded tests for Base32768, HKDF separation, raw/compressed boundaries, malformed authenticated content, decompression limits, both transports, cautious markerless diagnostics, old-frame non-detection, replay, concurrency, storage migration/recovery, UI policy, mixin targets, and 50,000-input fuzz suites.
- Added Base32768 attribution and rewrote all release documentation for the final protocol and UI.
