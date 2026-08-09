# CipherChannels

![CipherChannels icon](modrinth/icon.png)

CipherChannels is a client-only Fabric and NeoForge mod for Minecraft **1.21.1**, **1.21.11**, and **26.2**. It encrypts an entire normal public-chat message for friends who share the same secret invite. The server needs no plugin: it relays one fixed 256-character ciphertext message, and Minecraft signs that ciphertext rather than the plaintext.

## Install

Choose exactly one JAR matching both the Minecraft version and mod loader:

| Minecraft | Loader | Required loader/API | Java | JAR |
|---|---|---|---:|---|
| 1.21.1 | Fabric | Fabric Loader 0.19.3+, Fabric API 0.116.15+1.21.1+ | 21 | `cipherchannels-1.0.0+fabric.1.21.1.jar` |
| 1.21.1 | NeoForge | NeoForge 21.1.248+ | 21 | `cipherchannels-1.0.0+neoforge.1.21.1.jar` |
| 1.21.11 | Fabric | Fabric Loader 0.19.3+, Fabric API 0.141.6+1.21.11+ | 21 | `cipherchannels-1.0.0+fabric.1.21.11.jar` |
| 1.21.11 | NeoForge | NeoForge 21.11.45+ | 21 | `cipherchannels-1.0.0+neoforge.1.21.11.jar` |
| 26.2 | Fabric | Fabric Loader 0.19.3+, Fabric API 0.156.0+26.2+ | 25 | `cipherchannels-1.0.0+fabric.26.2.jar` |
| 26.2 | NeoForge | NeoForge 26.2.0.53-beta+ | 25 | `cipherchannels-1.0.0+neoforge.26.2.jar` |

NeoForge for Minecraft 26.2 is currently a beta loader line. Put only the matching CipherChannels JAR in the instance's `mods` folder. Mod Menu is optional on Fabric; NeoForge exposes the manager from its Mods screen. Every variant also provides the chat button and configurable gameplay-only `O` key. The shortcut does not fire while chat or another screen is open.

All six variants use the same protocol, so a channel invite and encrypted message can be shared across supported Minecraft versions and loaders when the server relays the frame unchanged.

## Start using it

For the person creating the channel:

1. Open CipherChannels and choose **Create a channel**.
2. Give it a local name. Creation selects it but deliberately leaves encrypted chat off.
3. Open **Share & Security**, choose **Copy secret invite…**, read the warning, and send the invite to your friend through a private route.
4. Compare the shown fingerprint with your friend through a separate trusted route.
5. In **Overview**, choose **Turn on encrypted chat**.

For the friend joining:

1. Choose **Join with an invite**, enter any local name, and paste the exact invite.
2. Confirm that the fingerprint matches the creator's.
3. Turn on encrypted chat in **Overview**.

The three manager tabs always show what is happening:

- **Overview** shows on/off/blocked state, active channel, session-key readiness, binding, current endpoint, transport, and the next action.
- **Channels** is a scrollable list. The active channel has a checkmark; every row says `Ready` or `Key needed`. Clicking a different row switches immediately while encryption is off. While it is on, a three-choice confirmation appears.
- **Share & Security** contains the fingerprint, guarded invite copying, strict server binding, and the current server's transport selection.

Every action leaves a colored banner. The chat entry button itself says `On`, `Off`, or `Blocked`, and the line above the editor explains readiness, capacity, compression, commands, or the exact reason sending is blocked.

## Message capacity and compatibility

High-capacity mode is the default. Its markerless Base32768 wire is always exactly **256 UTF-16 characters** and guarantees **443 UTF-8 plaintext bytes** without compression. If a larger draft compresses enough, CipherChannels transparently sends it in the same fixed wire; restored plaintext is strictly limited to **4,096 UTF-8 bytes**.

If a multiplayer server rejects the Unicode blob, open **Share & Security** while connected and select **Compatibility**. This manual choice is remembered only for that normalized host and port. Compatibility mode is also exactly 256 characters, uses unpadded Base64url, and guarantees 155 raw UTF-8 bytes; useful compressible drafts can still be longer.

When encrypted intent is on, the editor accepts up to 4,096 Java characters and continually preflights the normalized draft. If it is malformed, over 4,096 UTF-8 bytes, or still too large after compression, pressing Enter keeps the chat open, preserves the entire draft, shows a red inline reason, and adds one deduplicated local notice. CipherChannels never truncates, splits, sends a partial result, or falls back to plaintext.

Encryption continues to work in integrated singleplayer. Only server binding and per-server compatibility selection are unavailable there.

## Chat behavior

- Only entire public-chat messages are encrypted. Embedded encrypted fragments are intentionally not a version-1 feature.
- Slash commands are never encrypted. `/msg`, `/tell`, `/w`, `/r`, and every other command retain vanilla's 256-character limit and produce a plaintext warning while encrypted intent is on.
- A successfully authenticated message displays as plaintext with a separate `[CC]` badge. Hover the plaintext to inspect the exact 256-character wire blob. Hover `[CC]` to see the local channel name and the shared-key authorship warning.
- Timestamp, channel, rank, and player-name prefixes or suffixes from servers and chat-formatting mods are tolerated when the intact ciphertext remains one delimiter-bounded run. Editing or splitting characters inside the ciphertext still makes it undecryptable.
- A 256-character supported-alphabet blob with no matching session key stays visible with `Encrypted-looking — no matching key`. A recognized but altered blob stays visible with a stronger damage warning. Replayed plaintext is withheld.
- Text such as the former `~CC1:…~` public frame is ordinary chat in 1.0.0. There is no legacy receive compatibility because this release was not previously published.

## Session-only keys and strict binding

The invitation contains the channel's shared master key. Anyone holding it can read and create authenticated messages in that channel. Local channel names and bindings are not shared.

Raw keys exist only in memory. Quitting Minecraft clears every key and the replay cache. The version-2 JSON configuration stores only enabled intent, active UUID, local names, fingerprints, bindings, and ASCII transport overrides. If enabled intent survives a restart, the UI says `Key needed`/`Blocked` and public chat remains blocked until the invite is re-imported or encryption is explicitly turned off.

A bound channel can encrypt only on its exact normalized saved multiplayer host and port. Elsewhere it is suspended and public sending fails closed. CipherChannels never silently switches channels; if another channel is bound to the current endpoint, Overview offers a visible switch action.

## Wire protocol

Each channel master key is 32 random bytes. HKDF-SHA-256 derives independent AES-256-GCM and recognition keys using fixed CipherChannels version-1 domains. Every message uses a fresh random 12-byte nonce.

Both transports encode:

```text
nonce(12) || recognition-hint(8) || AES-GCM(encrypted-record || tag(16))

recognition-hint = first 8 bytes of
  HMAC-SHA-256(recognition-key,
    UTF-8("CipherChannels recognition v1\0") || transport-id || nonce)

AAD = UTF-8("CipherChannels frame v1\0") || transport-id || nonce || recognition-hint
encrypted-record = content || control-byte || zero-padding
```

Control `0x10` means strict UTF-8. Control `0x11` means raw DEFLATE followed by strict UTF-8 after bounded expansion. The high-capacity binary record is 480 bytes encoded as 256 Base32768 BMP characters. Compatibility is 192 bytes encoded as 256 unpadded Base64url characters.

Frames have no stable public channel identifier. Receivers try at most 16 in-memory keys, active first, and compare the rotating 64-bit recognition hint in constant time before attempting AES-GCM. This avoids exposing one unchanging label for a channel, although the fixed size and alphabets still make traffic recognizable as encrypted-looking.

The invite and local fingerprint formats remain:

```text
CC1.<43-character-base64url-key>.<8-character-Crockford-Base32-checksum>
checksum = first 40 bits of SHA-256("CipherChannels invite checksum v1\0" || key)

fingerprint = first 80 bits of SHA-256("CipherChannels fingerprint v1\0" || key)
              shown as XXXX-XXXX-XXXX-XXXX
```

Invite parsing requires the one canonical unpadded Base64url representation, in addition to exact syntax and checksum validation.

## Replay protection and limits

After authentication, CipherChannels caches `channel fingerprint + SHA-256(transport-id || binary record)` for six hours, up to 4,096 entries. An intact duplicate is recognized and its plaintext is withheld. The cache resets after restart, so a server can replay an old ciphertext after the recipient restarts.

A completely replaced message may no longer match either supported alphabet and then looks like ordinary text. Without a public marker, a recipient who lacks the right key cannot prove that a random-looking 256-character blob was produced by CipherChannels; the UI deliberately says only `Encrypted-looking`.

The server still sees account identity, timing, ordering, destination, a fixed-size ciphertext, and which transport alphabet is used. It can block, delay, reorder, delete, replace, or replay delivery. Without a channel key it cannot read authenticated plaintext or forge a message that a key holder will accept.

CipherChannels does not provide anonymity, forward secrecy, individual authorship proof, protection after key theft, resistance to malicious client mods or devices, or command encryption. If a key may be compromised, create a new channel, distribute the new invite out of band, verify its fingerprint, and forget the old channel.

## Build from source

Use Java 25 to run Gradle; the build automatically obtains its Java 21 toolchain for Minecraft 1.21.x. From a clean extracted source archive:

```sh
sh ./gradlew clean test build --no-daemon
```

The build compiles all six targets against their exact Minecraft mappings, runs protocol/storage/chat/UI tests and 50,000-input fuzz suites, and produces the loader-specific JARs plus a clean source ZIP.

See [SECURITY.md](SECURITY.md), [CHANGELOG.md](CHANGELOG.md), [MODRINTH.md](MODRINTH.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). CipherChannels itself is [All Rights Reserved](LICENSE).
