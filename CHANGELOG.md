# Changelog

## 2.2.1 — 2026-08-23

- Replaced the narrow channel frame with a full-width vanilla selection area and compact centered rows.
- Centered the empty-channel message correctly and kept it clear of the list separators.
- Hardened row text sizing so long names and the active label remain inside the selectable row.
- Fixed text positioning on Minecraft 26.2 screens after its `StringWidget` constructor changed meaning.

## 2.2.0 — 2026-08-23

CipherChannels 2.2 keeps complete compatibility with 2.0 and 2.1 `CC2` channels, invites, fingerprints, settings, and encrypted frames.

- Replaced the dashboard and tabs with a narrow vanilla Minecraft manager, native channel list, standard buttons, tooltips, confirmations, and system toasts.
- Added dedicated create, import, load-invite, rename, channel-settings, and locked-settings recovery screens.
- Made channel activation explicit: selection never switches channels, while `Use Channel`, double-click, and Enter share the same safety checks.
- Made importing a saved invite reload only its session key without renaming, duplicating, activating, or changing encryption intent.
- Added masked invite entry with paste and show/hide controls, inline validation, keyboard submission, and direct CC1 incompatibility feedback.
- Kept every security and transport capability available through the selected channel's settings while removing decorative panels and permanent banners.
- Preserved the CC2 protocol, schema v4 configuration, session-only keys and replay cache, strict binding, Chat Patches protection, and fixed transports unchanged.

## 2.1.0 — 2026-08-17

CipherChannels 2.1 keeps complete compatibility with 2.0 `CC2` channels, invites, fingerprints, and encrypted frames.

- Made replay protection session-only again: 4,096 authenticated frames for up to six hours, cleared on restart and never persisted.
- Removed verification labels, unverified-channel approvals, stored trust state, and security-checklist ceremony.
- Simplified channel switching: ready channels switch immediately; missing keys and binding mismatches are rejected without changing state.
- Replaced compromised-channel presentation with direct channel-key rotation while preserving the local name and binding.
- Reduced the manager to Overview, Channels, and Channel tabs with one-click invite copying and an always-visible secret warning.
- Migrated schema v3 settings to v4 without changing operational channel metadata or enabled intent.
- Kept Chat Patches log sanitization, multi-node formatted-message reconstruction, strict binding, fail-closed sending, guarded clipboard clearing, and reproducible releases.
- Renamed the public protocol material to interoperability vectors without implying an independent audit.

## 2.0.1 — 2026-08-17

- Fixed a startup crash when Chat Patches was installed alongside mods that trigger early Mixin processing, including C2ME.
- Kept full compatibility with 2.0 channels, invites, frames, and configuration.

## 2.0.0 — 2026-08-11

CipherChannels 2.0 is intentionally incompatible with 1.x. Every old channel must be recreated and shared with a new `CC2` invite.

- Separated every key, recognition, frame, invite, and fingerprint domain under protocol v2.
- Added explicit imported-channel verification and session-only unverified approval.
- Added one-step compromised-channel replacement without distributing a new secret through the old channel.
- Made unsafe configuration fail closed with backup recovery and a dedicated recovery screen.
- Persisted the six-hour replay window across normal restarts without storing secrets or message contents.
- Added protected optional Chat Patches logging, startup compatibility checks, and fail-closed behavior for known-unprotected logging.
- Added masked invite entry and conditional clipboard clearing on channel removal, replacement, and shutdown.
- Added multi-node frame reconstruction for intact ciphertext surrounded or split by compatible chat formatters.
- Refined Overview into a security checklist and divided Share & Security into clear verification, invite, server, and compromise sections.
- Added scrolling, keyboard-accessible controls, narration, non-color-only status labels, persistent feedback, and exact blocked-send explanations.
- Added deterministic v2 protocol vectors, expanded corruption/restart/fuzz coverage, reproducible archives, dependency verification, pinned CI, artifact validation, checksums, and tagged-release provenance.
- Added all ten Fabric and NeoForge artifacts for Minecraft 1.21.1, 1.21.11, 26.1, 26.1.2, and 26.2.
