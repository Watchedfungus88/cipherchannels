# Compatibility

CipherChannels is client-only. Every participant who wants plaintext must install a matching build and import the same `CC2` invite. The server requires no mod or plugin, but it must relay the 256-character frame unchanged.

## Supported targets

| Minecraft | Fabric | NeoForge | Java |
|---|---|---|---:|
| 1.21.1 | Supported | Supported | 21 |
| 1.21.11 | Supported | Supported | 21 |
| 26.1 | Supported | Supported | 25 |
| 26.1.2 | Supported | Supported | 25 |
| 26.2 | Supported | Supported | 25 |

All ten artifacts compile and run the same protocol vectors and common security tests against their exact Minecraft APIs. Fabric requires Fabric API. NeoForge has no extra mod dependency.

The vanilla manager is implemented against the 1.21.1, 1.21.11/26.1/26.1.2, and 26.2 GUI APIs. All families provide the same screens, controls, keyboard behavior, narration, and toast feedback.

## Chat behavior

| Integration | Behavior |
|---|---|
| Vanilla public chat | Supported in integrated singleplayer and vanilla multiplayer transport |
| Timestamps, ranks, server-added prefixes, channel labels, adjacent formatting nodes | One unchanged frame is reconstructed across adjacent literal nodes while styles and actions are preserved |
| High Capacity | Default; requires a normalization-clean Unicode relay |
| Compatibility | Manual per-server ASCII fallback for Unicode rejection |
| Slash commands | Deliberately plaintext with an explicit warning |
| Chat Patches 8.0 alpha 11 on Fabric 26.2 | Protected logging hook validated with C2ME 0.4.2 alpha 0.35 |
| Other Chat Patches versions | Optional version-sensitive hook; incompatible enabled logging fails closed |
| No Chat Reports | CipherChannels replaces text before Minecraft constructs and sends the signed message body; ciphertext is the message seen by signing/reporting code |
| Unknown chat rewriting mods | Not guaranteed; modified ciphertext remains visible and is never guessed or repaired |

Chat Patches protection targets its `ChatLog.addHistory(String)` and `ChatLog.addMessage(Component)` logging entry points and is validated at startup. Compatibility is deliberately version-sensitive: if the installed logger is enabled but its protected methods do not match, encrypted sending and decryption pause. No Chat Reports 2.20.1 was also tested on Fabric 26.2.

No client mod can prevent arbitrary other code in the same process from reading plaintext or keys from memory. Report compatibility results without posting real invites, plaintext, or raw frames.

## Validation checklist

Before publishing an artifact, validate integrated singleplayer and a vanilla multiplayer relay with both transports, bindings, commands, unknown keys, tampering, replay, prefixes, Chat Patches logging enabled and disabled, and No Chat Reports. Also test a small window, high GUI scale, keyboard navigation, narration, masked invite input, and every confirmation path. Record only versions that were actually tested; do not infer compatibility from a similar release.
