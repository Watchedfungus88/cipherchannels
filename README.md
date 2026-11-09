# CipherChannels

![CipherChannels icon](assets/cipherchannels-icon.png)

Encrypted chat in Minecraft, without a server plugin.

Create a channel, share its secret invite with friends, and use the normal chat box. When encrypted chat is on, CipherChannels encrypts your messages before Minecraft signs and sends them. The server receives encrypted text instead of the original message.

Everyone in a channel needs the mod and the same invite. CipherChannels runs on your client and is available for Fabric and NeoForge.

## Install

[Download the latest release](https://github.com/Watchedfungus88/cipherchannels/releases/latest) and put **one JAR** in your instance's `mods` folder. Choose the file that matches both your Minecraft version and loader.

Supported Minecraft versions: **1.21.1, 1.21.11, 26.1, 26.1.2, and 26.2**.

- Minecraft 1.21.x needs Java 21; 26.x needs Java 25.
- Fabric builds also need Fabric API and Fabric Loader 0.19.3 or newer.
- NeoForge builds need no additional mod.
- Mod Menu is optional.

See [Compatibility](COMPATIBILITY.md#supported-versions) for exact filenames and minimum dependency versions. The server must pass encrypted messages through without changing their text.

## Get started

1. Open CipherChannels from the chat screen or its configurable key binding. Choose **Create…** to make a channel, or **Import Invite…** to join one.
2. To share a channel, open **Channel Settings…** and choose **Copy Invite**. Send it privately. Anyone with the invite can read and send messages in that channel.
3. Select the channel, choose **Use Channel**, and turn **Encrypted Chat** on.

Keys stay loaded only until you close Minecraft, unless you turn on **Remember Invite** in the channel's settings. Keep a private copy of the invite so you can load it again.

## Chat formats

**High Capacity** is the default. It carries 443 bytes of uncompressed UTF-8 text. Compressible messages can be longer, up to 4,096 UTF-8 bytes after decompression.

If a server rejects High Capacity messages, choose **Compatibility** in the channel's settings. It uses ASCII and carries 155 uncompressed bytes. Your choice is saved per server.

CipherChannels never cuts a message short, splits it into several messages, or sends it unencrypted as a fallback. If a message cannot be sent, it stays in the chat box with a warning.

**Slash commands are not encrypted.** The server needs to read them, and CipherChannels warns you before sending one.

## Saved invites

Keys are session-only by default. **Remember Invite** saves a channel's key in macOS Keychain, Windows Credential Manager, or Linux Secret Service, then loads it when Minecraft starts. Linux needs libsecret and an available desktop keyring. Your OS may ask you to allow access or unlock the store.

The key is never saved in Minecraft's settings files. If OS storage is unavailable, there is no plaintext-file fallback.

Turning **Remember Invite** off deletes the saved copy but keeps the key loaded for the current session. Forgetting a channel deletes its saved invite. Rotating a key replaces the saved invite if remembering is on.

## Before you share an invite

An invite contains the channel's 256-bit secret key. Treat it like a password. Anyone with it can read and send messages. The `[CC]` mark confirms the shared key, not a person's identity. Matching full fingerprints means you have the same key.

If an invite has been exposed, rotate the key and share the replacement privately outside the old channel. You cannot revoke one person's access while keeping the same key.

The server still knows who is connected, when messages are sent, and where they go. CipherChannels does not make you anonymous or protect against malicious client mods or a compromised device. Someone who gets an invite later may also be able to decrypt older recorded messages.

CipherChannels keeps transformed chat out of vanilla logs and replaces messages with a placeholder in supported Chat Patches logs. If an enabled Chat Patches logger cannot be protected, encrypted chat pauses. It does not delete old logs or control what other mods record. See [Security](SECURITY.md) for the full security model, replay limits, logging behavior, and settings recovery.

## Version compatibility

CipherChannels 2.3 uses the same `CC2` protocol, channels, and invites as 2.0–2.2. Existing settings update automatically. CipherChannels 1.x is not compatible.

See [Compatibility](COMPATIBILITY.md) for tested mod integrations.

## Build from source

Use Java 25 to run the included Gradle wrapper. Gradle selects a Java 21 toolchain for targets that need it.

```sh
./gradlew clean test releaseBuild --no-daemon
./scripts/reproducible-build.sh
```

The build checks all ten JARs and writes them to `build/release`, along with a source ZIP and `SHA256SUMS`. The second command builds the source archive twice in separate directories and compares their checksums.

Dependency verification and the Gradle distribution checksum stay enabled. See the [dependency verification notes](gradle/DEPENDENCY_VERIFICATION.md) for the accepted Fabric archive variants. Tagged GitHub builds also produce provenance attestations.

## More information

- [Protocol specification](PROTOCOL.md)
- [Security](SECURITY.md)
- [Compatibility](COMPATIBILITY.md)
- [Changelog](CHANGELOG.md)
- [Interoperability test vectors](vectors/README.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

CipherChannels is [All Rights Reserved](LICENSE).
