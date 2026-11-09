# Compatibility

Everyone who wants to read a channel's messages needs CipherChannels and the same `CC2` invite. The server needs no mod or plugin, but it must relay the 256-character encrypted message without changing its text.

## Supported versions

| Minecraft | Fabric | NeoForge | Java |
|---|---|---|---:|
| 1.21.1 | Supported | Supported | 21 |
| 1.21.11 | Supported | Supported | 21 |
| 26.1 | Supported | Supported | 25 |
| 26.1.2 | Supported | Supported | 25 |
| 26.2 | Supported | Supported | 25 |

All ten builds compile against their target Minecraft APIs and run the shared protocol vectors and security tests. Fabric builds require Fabric API. NeoForge builds need no additional mod.

The versions below are minimum requirements. Install only the JAR that matches your Minecraft version and loader.

| Minecraft | Loader | Minimum runtime | JAR |
|---|---|---|---|
| 1.21.1 | Fabric | Fabric Loader 0.19.3; Fabric API 0.116.15+1.21.1 | `cipherchannels-2.3.0+fabric.1.21.1.jar` |
| 1.21.1 | NeoForge | NeoForge 21.1.248 | `cipherchannels-2.3.0+neoforge.1.21.1.jar` |
| 1.21.11 | Fabric | Fabric Loader 0.19.3; Fabric API 0.141.6+1.21.11 | `cipherchannels-2.3.0+fabric.1.21.11.jar` |
| 1.21.11 | NeoForge | NeoForge 21.11.45 | `cipherchannels-2.3.0+neoforge.1.21.11.jar` |
| 26.1 | Fabric | Fabric Loader 0.19.3; Fabric API 0.145.1+26.1 | `cipherchannels-2.3.0+fabric.26.1.jar` |
| 26.1 | NeoForge | NeoForge 26.1.0.19-beta | `cipherchannels-2.3.0+neoforge.26.1.jar` |
| 26.1.2 | Fabric | Fabric Loader 0.19.3; Fabric API 0.155.2+26.1.2 | `cipherchannels-2.3.0+fabric.26.1.2.jar` |
| 26.1.2 | NeoForge | NeoForge 26.1.2.94 | `cipherchannels-2.3.0+neoforge.26.1.2.jar` |
| 26.2 | Fabric | Fabric Loader 0.19.3; Fabric API 0.156.0+26.2 | `cipherchannels-2.3.0+fabric.26.2.jar` |
| 26.2 | NeoForge | NeoForge 26.2.0.53-beta | `cipherchannels-2.3.0+neoforge.26.2.jar` |

The manager uses Minecraft's standard screens and controls, adapted to the 1.21.1, 1.21.11/26.1/26.1.2, and 26.2 GUI APIs. Each version has the same screens, keyboard controls, narration, and toast notifications.

## Saved invites

Remembered invites use macOS Keychain, Windows Credential Manager, or Linux Secret Service. Linux needs libsecret and an available desktop keyring. An unavailable or locked credential store never causes a key to be saved in a plaintext file.

The keyring tests include live macOS Keychain checks. Windows and Linux backends are covered by mocked native interfaces, not live OS tests.

## Chat and other mods

| Integration | Behavior |
|---|---|
| Vanilla public chat | Supported in integrated singleplayer and vanilla multiplayer |
| Timestamps, ranks, server prefixes, channel labels, and adjacent formatting nodes | An unchanged encrypted message can be read across adjacent literal text nodes; surrounding styles and actions are kept |
| High Capacity | Default format; the server must relay Unicode text without normalizing or changing it |
| Compatibility | ASCII format selected manually per server if Unicode messages are rejected |
| Slash commands | Sent unencrypted, with a warning |
| Chat Patches 8.0 alpha 11 on Fabric 26.2 | Logging protection tested with C2ME 0.4.2 alpha 0.35 |
| Other Chat Patches versions | Support depends on the version; if enabled logging cannot be protected, encrypted sending and decryption pause |
| No Chat Reports | CipherChannels encrypts text before Minecraft constructs and sends the signed message body, so signing and reporting code sees ciphertext |
| Other mods that rewrite chat | Not guaranteed; changed ciphertext is left visible rather than guessed at or repaired |

Chat Patches support protects the `ChatLog.addHistory(String)` and `ChatLog.addMessage(Component)` logging entry points. The installed logger is checked at startup. If logging is enabled but those methods do not match the protection hooks, encrypted sending and incoming decryption pause.

No Chat Reports 2.20.1 was also tested on Fabric 26.2. These results apply to the listed versions, not every release of those mods.

No client mod can stop other code in the same process from reading plaintext or keys from memory. When reporting compatibility results, do not include real invites, message contents, or raw encrypted frames.

## Checks before a release

Test integrated singleplayer and a vanilla multiplayer server with:

- Both chat formats, server locks, slash commands, and missing keys.
- Changed messages, replayed messages, prefixes, and chat formatting.
- Chat Patches logging both on and off, and No Chat Reports.
- A small window, high GUI scale, keyboard navigation, and narration.
- Hidden invite input and every confirmation dialog.

Record the exact versions tested. A passing build or a similar mod version is not proof that an integration works.
