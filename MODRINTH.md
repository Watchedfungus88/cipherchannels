# Modrinth release metadata

## Title

CipherChannels

## Summary

Client-side encrypted public chat for Minecraft 26.2 Fabric, with fixed markerless messages, session-only keys, replay blocking, and a guided channel UI.

## Categories and compatibility

- Categories: Utility, Social
- Environment: Client only
- Minecraft version: 26.2
- Loader: Fabric
- Java: 25
- Required: Fabric Loader 0.19.3+, Fabric API 0.156.0+26.2+
- Optional: Mod Menu 20.0.1+
- License: All Rights Reserved

## Files

- Primary upload: `cipherchannels-1.0.0+mc26.2.jar`
- Source release: `CipherChannels-1.0.0+mc26.2-source.zip`
- Icon: `modrinth/icon.png`
- Changelog: `CHANGELOG.md`

## Description

CipherChannels encrypts an entire public-chat message into one fixed 256-character ciphertext that a vanilla server can relay without a plugin. Friends import the same secret invite; Minecraft signs ciphertext, and authenticated recipients see plaintext with a `[CC]` badge and hoverable exact wire data.

The default Base32768 transport guarantees 443 raw UTF-8 bytes. Helpful compression can carry up to 4,096 restored bytes in the same fixed message. A manual per-server ASCII compatibility mode is available for servers that reject Unicode and guarantees 155 raw bytes.

The guided manager has first-use onboarding plus Overview, Channels, and Share & Security tabs. It clearly shows encryption state, active channel, session-key readiness, binding, endpoint, transport, action results, and blocked-send reasons.

Keys are session-only. The persistent configuration contains non-secret metadata and per-server transport choices, never keys or chat contents. Sending fails closed if the active key is missing, the strict server binding mismatches, Unicode is malformed, or the draft does not fit. Slash commands remain plaintext and trigger a warning.

Servers and unmodded clients see fixed random-looking text. Servers can still observe traffic metadata and interfere with delivery. Anyone holding an invite can read and create channel messages; `[CC]` is shared-key authentication, not individual authorship. Read `README.md` and `SECURITY.md` before use.
