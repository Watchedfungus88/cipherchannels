# Security model

CipherChannels 1.0.0 provides shared-key confidentiality and authentication for complete Minecraft 1.21.1, 1.21.11, and 26.2 public-chat messages on Fabric and NeoForge. It is a client-only transport; it is not a private-message service, identity system, or anonymous network.

## Cryptographic construction

New channels use 32 bytes from Java `SecureRandom` as a master key. HKDF-SHA-256 uses salt `CipherChannels key schedule v1\0` and distinct expansion-info domains for the AES-256-GCM key and HMAC-SHA-256 recognition key.

Each message uses a fresh random 96-bit nonce and a 128-bit GCM authentication tag. A rotating 64-bit hint is the first eight bytes of HMAC over a recognition domain, transport ID, and nonce. The hint is authenticated as AAD and is compared in constant time before decryption. It is not a stable channel ID.

The fixed encrypted record is `content || control || zero padding`. Authenticated control `0x10` selects raw strict UTF-8 and `0x11` selects raw DEFLATE plus strict UTF-8. Other controls, missing controls, invalid UTF-8, malformed/truncated/trailing compressed streams, and expansion beyond 4,096 bytes are rejected.

High-capacity frames contain 480 binary bytes encoded into exactly 256 Base32768 BMP characters. They guarantee 443 raw plaintext bytes. Compatibility frames contain 192 binary bytes encoded into exactly 256 unpadded Base64url characters and guarantee 155 raw bytes. Outgoing source text over 4,096 UTF-8 bytes is rejected even if it would compress.

## Detection and authentication

A candidate must be one complete, maximal 256-character run using exactly one supported alphabet. The run may be surrounded by delimiter-separated text in the same literal component so timestamps, channel labels, player names, and other chat formatting do not hide an intact frame. Scans are capped at 4,096 characters, and a literal containing more than one candidate is left untouched. No public `CC1` marker or delimiter is present in the frame itself. At most 16 live keys are tried, active first. A hint miss is only `Encrypted-looking — no matching key`; it is not proof that the text came from CipherChannels. A hint match followed by GCM failure is reported as damaged or altered. No unauthenticated bytes are ever interpreted as plaintext.

Because detection is markerless, arbitrary delimiter-bounded 256-character Base64url or Base32768 runs can receive the cautious unknown-key badge. Conversely, replacing, splitting, or editing the blob so it no longer matches the exact candidate rules makes it ordinary visible text. Fixed length and unusual alphabets also reveal that traffic is encrypted-looking even though channels have no stable public identifier.

## Storage and recovery

Version-2 settings contain only enabled intent, active channel UUID, local names, fingerprints, strict bindings, and per-endpoint ASCII overrides. They never contain raw keys, invites, plaintext, ciphertext, or compression state. Version-1 development settings migrate with high capacity as the default.

Writes use a temporary file in the same directory, forced file contents, atomic replacement when supported, and a `.bak` copy. If the primary is missing, a valid backup can be recovered. A corrupt primary is moved to a timestamped `.corrupt-*` copy and a disabled empty state starts. A newer unsupported configuration is left untouched and opened read-only.

Keys are session-only and best-effort wiped when replaced, forgotten, or the service closes. There can be 64 metadata records, 16 live keys, and 64 ASCII endpoint overrides. Forgetting removes metadata, its live key, and matching replay entries.

## Replay, signing, and logging

Only successfully authenticated frames enter the replay cache. Its key is the local channel fingerprint plus SHA-256 of transport ID and the complete binary record. It retains 4,096 entries for six hours. A duplicate withholds plaintext. This cache is process-local and resets on restart.

Outgoing replacement happens before `SignedMessageBody` is constructed, and the identical prepared ciphertext is used in `ServerboundChatPacket`; Minecraft signs ciphertext, never plaintext. Incoming transformation happens only when adding the already-filtered and trust-evaluated message to the chat display. Existing styles, siblings, click events, hover events, signatures, and message tags remain attached.

Minecraft's ordinary text logger is suppressed for transformed display text so it does not write decrypted plaintext. Minecraft's reporting data retains the signed original ciphertext. CipherChannels code does not log keys, invites, drafts, plaintext, decrypted output, or raw frames.

## Server binding and fail-closed behavior

Binding compares the normalized saved multiplayer host and port. If enabled intent is active but the key is missing or the binding does not match, public-chat sending is blocked. There is no plaintext fallback and no hidden channel switch. Integrated singleplayer still encrypts; it simply cannot be bound.

Slash commands are outside the encrypted transport and always remain plaintext. CipherChannels warns for every command while encrypted intent is enabled.

## Threat model and limitations

Everyone holding an invite has the same master key and can both read and create valid channel messages. `[CC]` means shared-key authentication, not proof of an individual author.

The server learns normal player/account/network metadata, timing, ordering, fixed ciphertext length, and transport alphabet. It can block, delay, reorder, delete, replace, replay after restart, or otherwise interfere with delivery. It cannot read or forge authenticated CipherChannels plaintext without a channel key.

There is no anonymity, forward secrecy, post-compromise security, malicious-client resistance, compromised-device resistance, traffic-flow hiding, server-plugin compatibility guarantee, or slash-command encryption. Unicode-clean relays are required for high-capacity mode; server plugins that normalize, split, decorate, truncate, or reject chat may damage detection. Compatibility mode handles Unicode rejection but has lower raw capacity.

## Key-compromise response

Create a new channel, distribute the new invite through a separate trusted route, compare the new fingerprint, and forget the old channel. Never send the replacement invite through a channel whose key may already be compromised.

Do not publish keys, invites, plaintext, or raw frames in a bug report. This source release does not define a private security mailbox; establish a trusted private contact with the distributor before sending sensitive reproduction data.
