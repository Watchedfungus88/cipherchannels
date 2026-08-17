# Changelog

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
