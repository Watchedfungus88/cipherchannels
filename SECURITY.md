# Security

CipherChannels provides shared-secret confidentiality and authentication for complete public-chat messages. It is not an identity system, anonymous network, or full secure messenger.

## Construction

Each channel begins with 32 random bytes from Java `SecureRandom`. HKDF-SHA-256 derives separate AES-256-GCM encryption and HMAC-SHA-256 recognition keys under v2-only domains. Every frame has a fresh random 96-bit nonce, a rotating authenticated 64-bit recognition hint, and a 128-bit GCM tag.

The fixed encrypted record is content, an authenticated v2 control byte, then zero padding. Raw strict UTF-8 uses `0x20`; raw DEFLATE followed by strict UTF-8 uses `0x21`. Invalid controls, malformed UTF-8, truncated or trailing compressed data, and expansion beyond 4,096 bytes are rejected.

Receivers try no more than 16 live keys, active first. Recognition hints are compared in constant time before AES-GCM. There is no stable public channel identifier. Markerless fixed-size traffic is still recognizable as encrypted-looking traffic.

The complete construction is in [PROTOCOL.md](PROTOCOL.md). Public deterministic fixtures are in [vectors/protocol-vectors-v2.json](vectors/protocol-vectors-v2.json). They support interoperability testing and do not imply an independent audit.

## Fail-closed behavior

Enabled encryption never falls back to plaintext. Missing keys, strict-binding mismatches, locked configuration, known-unprotected chat logging, malformed drafts, capacity failures, and encryption errors block the send and preserve the draft.

Configuration schema v4 stores no secrets. Version 3 migrates automatically by dropping only its old verification label. Channel UUIDs, names, fingerprints, active selection, bindings, transport overrides, and enabled intent are preserved. Version 1 or 2 settings are preserved as timestamped `.pre-2.0` files and replaced with an empty disabled v4 configuration.

A corrupt primary tries a valid backup first. Unrecoverable or unsupported newer settings lock CipherChannels and block public chat until the user restores valid settings or explicitly preserves and resets them. Newer files are left untouched. Reset is explicit because it permits plaintext mode again.

## Replay protection

Only authenticated frames enter the replay cache. Its identifier combines the channel fingerprint with a SHA-256 digest of the complete transport frame. It holds at most 4,096 entries and expires each after six hours.

Replay history is deliberately session-only and is never written to disk. Restarting Minecraft clears it, so recorded ciphertext can be replayed after restart. Forgetting a channel or rotating its key removes matching entries immediately.

## Keys, invites, and authorship

The `CC2` invite contains the channel master key. Raw keys are session-only and are best-effort wiped when forgotten, rotated, or Minecraft closes. Persistent configuration contains no keys, invites, plaintext, ciphertext, or replay history.

Everyone with an invite can read and create valid messages. `[CC]` authenticates shared-key possession, not a person. A matching fingerprint confirms that two people have the same shared key, but CipherChannels does not record trust or identity.

There is no forward secrecy: someone who records frames and later obtains the invite may decrypt those recorded messages. Removing a person from a group is not supported. If compromise is suspected, rotate the channel key, distribute the new invite out of band, and stop using the old one. CipherChannels never distributes a replacement through the old channel.

## Clipboard and logs

Invite input is masked by default. After copying, CipherChannels retains only an in-memory digest. A matching invite is cleared when its channel is forgotten or rotated, or on normal shutdown. Unrelated clipboard contents are never erased.

Vanilla logging of transformed chat text is suppressed. With a compatible Chat Patches logger enabled, sent and incoming encrypted history is replaced with:

```text
[CipherChannels encrypted message intentionally not stored]
```

Neither plaintext nor the raw frame is intentionally persisted. Compatibility hooks are checked at startup. A detected, enabled Chat Patches logger that cannot be protected blocks encrypted sending and pauses incoming decryption. Old `logs/chatlog*.json` files may contain plaintext from earlier releases; CipherChannels never scans or deletes them.

Unknown or malicious client mods can inspect Minecraft memory before CipherChannels can sanitize it. The operating system, screen recorders, accessibility tools, servers, other players, and compromised devices remain outside this boundary.

## Metadata and transport limits

The server sees the account, network metadata, timing, ordering, destination, fixed ciphertext size, and transport alphabet. It can block, delay, reorder, delete, replace, normalize, truncate, or replay delivery. Without the invite it cannot read or forge accepted plaintext.

High Capacity requires an unchanged Unicode relay. Compatibility handles Unicode rejection but has lower capacity. Slash commands are plaintext. CipherChannels does not provide traffic-flow hiding, anonymity, forward secrecy, post-compromise security, individual authorship, malicious-client resistance, or guaranteed compatibility with chat-rewriting plugins.

## Reporting vulnerabilities

Never paste an invite, plaintext, or raw frame in a public issue. Use [GitHub private vulnerability reporting](https://github.com/watchedfungus88/CipherChannels/security/advisories/new) for sensitive reports. Replace all real secrets in logs and reproduction steps.
