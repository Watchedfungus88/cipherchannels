# Changelog

## 2.3.0 (2026-11-09)

- Added **Remember Invite**, an optional per-channel setting that saves invites in macOS Keychain, Windows Credential Manager, or Linux Secret Service.
- Kept session-only keys as the default. There is no separate mod password or new setup screen.
- Added background loading for remembered keys, with fingerprint checks, server locks, and blocked-send protection. Forgetting a channel deletes its saved invite; rotating its key replaces it.
- Updated settings to schema v5, leaving **Remember Invite** off for existing channels.
- Simplified the vanilla UI with compact channel rows, fewer repeated labels, quieter forms, and short chat status text without byte or compression readouts.
- Moved fingerprints and server details into tooltips while keeping invite controls, blocked-send warnings, and confirmations for destructive actions.
- Centered chat status above the CipherChannels button and sized its background to fit the text.
- Fixed Fabric 1.21.1 dependency verification by pinning verified archive variants. Strict verification stays enabled, original pins are retained, and no dependencies were upgraded.
- Fixed isolated platform builds by configuring the common source sets before platform archives use them.

## 2.2.1 (2026-08-23)

- Replaced the narrow channel frame with a full-width vanilla list and compact, centered rows.
- Centered the empty-channel message and kept it clear of the list separators.
- Kept long names and the active label inside each selectable row.
- Fixed text positioning on Minecraft 26.2 after a change to its `StringWidget` constructor.

## 2.2.0 (2026-08-23)

CipherChannels 2.2 remains compatible with 2.0 and 2.1 `CC2` channels, invites, fingerprints, settings, and encrypted messages.

- Replaced the dashboard and tabs with a compact vanilla manager, native channel list, standard buttons, tooltips, confirmation dialogs, and system toasts.
- Added screens for creating, importing, loading invites, renaming, channel settings, and recovering locked settings.
- Made channel switching explicit. Selecting a row does not switch channels; **Use Channel**, double-click, and Enter use the same safety checks.
- Made importing an already-saved invite reload only its session key, without renaming or duplicating the channel, selecting it, or changing the encryption setting.
- Added hidden invite input with paste and show/hide controls, inline validation, keyboard submission, and a clear error for incompatible CC1 invites.
- Kept security and chat-format controls in channel settings while removing decorative panels and persistent banners.
- Left the CC2 protocol, schema v4 settings, session-only keys and replay cache, server locks, Chat Patches protection, and fixed-size transports unchanged.

## 2.1.0 (2026-08-17)

CipherChannels 2.1 remains compatible with 2.0 `CC2` channels, invites, fingerprints, and encrypted messages.

- Made replay protection session-only again: up to 4,096 authenticated frames for six hours, cleared on restart and never saved to disk.
- Removed verification labels, unverified-channel approvals, stored trust decisions, and the security checklist.
- Made ready channels switch immediately. Missing keys and server-lock mismatches block a switch without changing the current state.
- Replaced the compromised-channel workflow with direct key rotation, keeping the local name and server lock.
- Simplified the manager to Overview, Channels, and Channel tabs, with one-click invite copying and a visible secret-invite warning.
- Updated schema v3 settings to v4 without changing channel details or the saved encryption setting.
- Kept Chat Patches log protection, formatted-message reconstruction across text nodes, server locks, blocked-send protection, conditional clipboard clearing, and reproducible releases.
- Renamed the public protocol fixtures to interoperability vectors to avoid implying an independent audit.

## 2.0.1 (2026-08-17)

- Fixed a startup crash when Chat Patches was installed with mods that trigger early Mixin processing, including C2ME.
- Kept compatibility with 2.0 channels, invites, encrypted messages, and settings.

## 2.0.0 (2026-08-11)

CipherChannels 2.0 is not compatible with 1.x. Old channels must be recreated and shared with new `CC2` invites.

- Separated all key, recognition, frame, invite, and fingerprint domains under protocol v2.
- Added explicit verification for imported channels and session-only approval for unverified ones.
- Added one-step replacement for a compromised channel, without sharing the new secret through the old channel.
- Blocked public chat when settings could not be read safely, with backup recovery and a dedicated recovery screen.
- Saved the six-hour replay history across normal restarts without storing secrets or message contents.
- Added protection for optional Chat Patches logging, startup compatibility checks, and blocked sends when known logging could not be protected.
- Added hidden invite input and conditional clipboard clearing when a channel was removed or replaced, or Minecraft closed.
- Added reconstruction of intact encrypted messages split across text nodes by compatible chat formatters.
- Turned Overview into a security checklist and split Share & Security into verification, invite, server, and compromise sections.
- Added scrolling, keyboard access, narration, status labels that do not rely on color, persistent feedback, and specific blocked-send explanations.
- Added deterministic v2 protocol vectors, corruption, restart, and fuzz tests, reproducible archives, dependency verification, pinned CI actions, artifact validation, checksums, and tagged-release provenance.
- Added Fabric and NeoForge builds for Minecraft 1.21.1, 1.21.11, 26.1, 26.1.2, and 26.2.
