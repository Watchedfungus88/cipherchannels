# CipherChannels

![CipherChannels icon](assets/cipherchannels-icon.png)

CipherChannels is a client-only Fabric and NeoForge mod that encrypts complete Minecraft public-chat messages for people who share the same secret invite. The server needs no plugin. It relays and signs a fixed 256-character ciphertext instead of seeing the plaintext.

CipherChannels 2.0 is deliberately incompatible with every 1.x channel and invite. Create new channels and distribute new `CC2` invites.

## Quick start

1. Open CipherChannels from chat or its configurable gameplay key, then create a channel.
2. Share its secret invite privately and compare the complete fingerprint through another trusted route.
3. Turn on encrypted chat from Overview. Friends import the invite, verify the fingerprint, and explicitly approve an unverified channel before first use.

The manager always shows encryption, active channel, session key, verification, binding, transport, replay persistence, and logging protection. Failed sends keep the complete draft and explain the exact reason.

## Downloads

Install exactly one JAR matching the Minecraft version and loader.

| Minecraft | Loader | Java | Required runtime | JAR |
|---|---|---:|---|---|
| 1.21.1 | Fabric | 21 | Fabric Loader 0.19.3+, Fabric API 0.116.15+1.21.1+ | `cipherchannels-2.0.1+fabric.1.21.1.jar` |
| 1.21.1 | NeoForge | 21 | NeoForge 21.1.248+ | `cipherchannels-2.0.1+neoforge.1.21.1.jar` |
| 1.21.11 | Fabric | 21 | Fabric Loader 0.19.3+, Fabric API 0.141.6+1.21.11+ | `cipherchannels-2.0.1+fabric.1.21.11.jar` |
| 1.21.11 | NeoForge | 21 | NeoForge 21.11.45+ | `cipherchannels-2.0.1+neoforge.1.21.11.jar` |
| 26.1 | Fabric | 25 | Fabric Loader 0.19.3+, Fabric API 0.145.1+26.1+ | `cipherchannels-2.0.1+fabric.26.1.jar` |
| 26.1 | NeoForge | 25 | NeoForge 26.1.0.19-beta+ | `cipherchannels-2.0.1+neoforge.26.1.jar` |
| 26.1.2 | Fabric | 25 | Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+ | `cipherchannels-2.0.1+fabric.26.1.2.jar` |
| 26.1.2 | NeoForge | 25 | NeoForge 26.1.2.94+ | `cipherchannels-2.0.1+neoforge.26.1.2.jar` |
| 26.2 | Fabric | 25 | Fabric Loader 0.19.3+, Fabric API 0.156.0+26.2+ | `cipherchannels-2.0.1+fabric.26.2.jar` |
| 26.2 | NeoForge | 25 | NeoForge 26.2.0.53-beta+ | `cipherchannels-2.0.1+neoforge.26.2.jar` |

Fabric API is required on Fabric. NeoForge builds have no extra mod dependency. Mod Menu is optional.

## What the server sees

The server sees your account, timing, destination, ordering, selected transport alphabet, and one fixed-size 256-character ciphertext. It can block, delay, reorder, delete, replace, or replay delivery. It cannot read authenticated plaintext or create a frame accepted by channel holders without the invite key. Minecraft signs the ciphertext, not the plaintext.

High Capacity is the default. It guarantees 443 raw UTF-8 bytes and can carry compressible text up to the 4,096-byte restored-text limit. Compatibility mode is a manually selected per-server ASCII fallback with 155 raw bytes. CipherChannels never truncates, splits, or falls back to plaintext.

Slash commands remain plaintext because the server must interpret them. The UI warns before sending one.

## Keys, verification, and recovery

An invite is the channel's 256-bit shared secret. Anyone holding it can read and create valid channel messages. `[CC]` proves possession of that shared secret, not personal authorship.

Raw keys live only in memory. Restarting Minecraft clears them, so imported invites must be supplied again. Persistent settings contain only local metadata. Replay digests persist for six hours so restarting normally does not reopen that window.

Imported channels start Unverified. Compare the complete fingerprint through another trusted route before marking one Verified. If an invite may be exposed, use **Replace compromised channel**, distribute the new invite out of band, and stop using the old channel.

Unsafe or unreadable settings fail closed. CipherChannels blocks public chat until recovery or an explicit reset permits plaintext mode.

## Privacy boundaries

CipherChannels does not provide anonymity, forward secrecy, individual identity, membership revocation, protection after key theft, protection from a compromised device, or command encryption. Unknown or malicious client mods can read Minecraft process memory.

Vanilla transformed-message logging is suppressed. Supported Chat Patches logging receives a fixed placeholder instead of plaintext or ciphertext. If an installed Chat Patches version cannot be protected, encrypted sending and incoming decryption pause. Existing old logs are never deleted automatically.

## Build and verify

Use Java 25 to run the checked-in Gradle wrapper; Java 21 targets use a toolchain automatically.

```sh
./gradlew clean test releaseBuild --no-daemon
./scripts/reproducible-build.sh
```

Release files appear in `build/release`. The build validates all ten JARs, creates the source ZIP, and writes `SHA256SUMS`. Dependency verification and the wrapper distribution checksum are enabled. Tagged GitHub builds generate provenance attestations.

See [Protocol](PROTOCOL.md), [Security](SECURITY.md), [Compatibility](COMPATIBILITY.md), [Changelog](CHANGELOG.md), and [Third-party notices](THIRD_PARTY_NOTICES.md). CipherChannels is [All Rights Reserved](LICENSE).
