# Modrinth release metadata

## Title

CipherChannels

## Summary

Client-side encrypted public chat for Fabric and NeoForge, with fixed markerless messages, session-only keys, replay blocking, and a guided channel UI.

## Categories and compatibility

- Categories: Utility, Social
- Environment: Client only
- Minecraft versions: 1.21.1, 1.21.11, 26.1, 26.1.2, 26.2
- Loaders: Fabric, NeoForge
- Java: 21 for 1.21.x; 25 for 26.x
- Fabric requires Fabric Loader 0.19.3+ and the matching Fabric API
- NeoForge requires 21.1.248+ for 1.21.1, 21.11.45+ for 1.21.11, 26.1.0.19-beta+ for 26.1, 26.1.2.94+ for 26.1.2, or 26.2.0.53-beta+ for 26.2
- Mod Menu is optional on Fabric
- License: All Rights Reserved

## Files

- `cipherchannels-1.0.0+fabric.1.21.1.jar`
- `cipherchannels-1.0.0+neoforge.1.21.1.jar`
- `cipherchannels-1.0.0+fabric.1.21.11.jar`
- `cipherchannels-1.0.0+neoforge.1.21.11.jar`
- `cipherchannels-1.0.0+fabric.26.1.jar`
- `cipherchannels-1.0.0+neoforge.26.1.jar`
- `cipherchannels-1.0.0+fabric.26.1.2.jar`
- `cipherchannels-1.0.0+neoforge.26.1.2.jar`
- `cipherchannels-1.0.0+fabric.26.2.jar`
- `cipherchannels-1.0.0+neoforge.26.2.jar`
- Source release: `CipherChannels-1.0.0-source.zip`
- Icon: `modrinth/icon.png`
- Changelog: `CHANGELOG.md`

Create one Modrinth version entry per JAR so each file declares only its exact Minecraft version and loader. The 26.1 and 26.2 NeoForge entries should be marked beta because their NeoForge dependencies are beta builds. The other eight can be marked release.

## Description

CipherChannels encrypts an entire public-chat message into one fixed 256-character ciphertext that a vanilla server can relay without a plugin. Friends import the same secret invite; Minecraft signs ciphertext, and authenticated recipients see plaintext with a `[CC]` badge and hoverable exact wire data.

The same wire protocol is used by all supported Fabric and NeoForge builds, so invites and encrypted messages remain compatible across those client variants.

The default Base32768 transport guarantees 443 raw UTF-8 bytes. Helpful compression can carry up to 4,096 restored bytes in the same fixed message. A manual per-server ASCII compatibility mode is available for servers that reject Unicode and guarantees 155 raw bytes.

The guided manager has first-use onboarding plus Overview, Channels, and Share & Security tabs. It clearly shows encryption state, active channel, session-key readiness, binding, endpoint, transport, action results, and blocked-send reasons.

Keys are session-only. The persistent configuration contains non-secret metadata and per-server transport choices, never keys or chat contents. Sending fails closed if the active key is missing, the strict server binding mismatches, Unicode is malformed, or the draft does not fit. Slash commands remain plaintext and trigger a warning.

Servers and unmodded clients see fixed random-looking text. Servers can still observe traffic metadata and interfere with delivery. Anyone holding an invite can read and create channel messages; `[CC]` is shared-key authentication, not individual authorship. Read `README.md` and `SECURITY.md` before use.
