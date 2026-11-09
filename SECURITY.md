# Security

CipherChannels encrypts normal Minecraft chat messages using a shared channel key. It protects message contents and checks that a sender has the key. It does not identify individual senders, hide network traffic, or replace a full secure messenger.

## Encryption

Each channel starts with 32 random bytes from Java `SecureRandom`. HKDF-SHA-256 derives separate AES-256-GCM encryption and HMAC-SHA-256 recognition keys using protocol-v2 domains. Every frame has a fresh random 96-bit nonce, an authenticated 64-bit recognition hint that changes with the nonce, and a 128-bit GCM tag.

The encrypted record contains the message, an authenticated v2 control byte, and zero padding. Control `0x20` selects strict UTF-8. Control `0x21` selects raw DEFLATE followed by strict UTF-8 decoding. Invalid controls, malformed UTF-8, incomplete or trailing compressed data, and decompressed text over 4,096 bytes are rejected.

Receivers try at most 16 loaded keys, starting with the active channel. Recognition hints are compared in constant time before AES-GCM decryption. There is no stable public channel identifier, but fixed-size encrypted traffic can still be recognized as such.

The full construction is in [Protocol](PROTOCOL.md). The [test vectors](vectors/protocol-vectors-v2.json) provide public, deterministic examples for compatible implementations. They are not an independent security audit.

## Blocked sends

When encryption is on, CipherChannels never falls back to unencrypted public chat. A missing key, server-lock mismatch, locked settings, an unprotected known chat logger, invalid text, a message that does not fit, or an encryption error blocks the send and leaves the draft in place.

Slash commands are an explicit exception: they are sent unencrypted because the server must read them. The UI warns before sending one.

## Settings and recovery

Settings schema v5 contains no secrets. Schema v3 and v4 settings update automatically, with **Remember Invite** off for every existing channel. Migration preserves channel UUIDs, names, fingerprints, the active channel, server locks, chat formats, and the saved encryption setting. Migration from v3 also removes the old verification labels.

Schema v1 and v2 settings are incompatible. They are kept as timestamped `.pre-2.0` files and replaced with empty v5 settings with encryption off.

If the main settings file is damaged, CipherChannels tries a valid backup. If neither file can be used, or the files require a newer version of the mod, CipherChannels locks its settings and blocks public chat. Files from newer versions are left untouched.

You must restore valid settings or explicitly keep copies of the old files and reset them. Resetting requires confirmation because it turns encryption off and allows unencrypted chat again.

## Replay protection

Only authenticated frames enter the replay cache. Each entry combines the channel fingerprint with a SHA-256 digest of the complete transport frame. The cache holds at most 4,096 entries, each for up to six hours.

Replay history is kept in memory and never written to disk. Restarting Minecraft clears it, so a recorded message may be accepted again after a restart. Forgetting a channel or rotating its key immediately removes that channel's entries.

## Invites and shared-key identity

A `CC2` invite contains the channel's master key. By default, keys are kept only for the current session. CipherChannels makes a best-effort attempt to wipe its in-memory copies when a key is forgotten or rotated, or when Minecraft closes. Settings files never contain keys, invites, plaintext, ciphertext, or replay history.

Anyone with an invite can read and create valid channel messages. The `[CC]` mark proves possession of the shared key, not a person's identity. Matching full fingerprints confirms that two people have the same key. CipherChannels does not store identity checks or trust decisions.

There is no forward secrecy. Someone who records encrypted messages and later obtains the invite may be able to decrypt them. There is also no way to remove one person's access while keeping the same key.

If you suspect an invite has been exposed, rotate the channel key, share the replacement privately outside the old channel, and stop using the old key. CipherChannels never sends a replacement invite through the old channel.

## Remembered invites

**Remember Invite** is an opt-in setting for each channel. It uses:

- macOS Keychain.
- Windows Credential Manager, with local-machine persistence.
- The default persistent Linux Secret Service collection, through libsecret.

There is no separate mod password and no plaintext-file fallback. The OS may show its own permission or unlock prompt.

Credential names use a hash of the absolute configuration-directory path and the local channel UUID. This separates Minecraft profiles without putting channel names or invites in credential names.

Remembered keys load off the game thread and are checked against the saved fingerprint. Loading does not change the active channel, server locks, or the saved encryption setting. If a credential is missing, access is denied, or the key is invalid, encrypted sending stays blocked. Manually loading the correct invite repairs its saved copy.

Turning **Remember Invite** off deletes the saved copy but keeps the current session key. Forgetting a channel deletes its credential. Rotating a key replaces the credential and keeps remembering enabled if it was already on.

A storage failure rejects the operation. If saving settings fails after a credential change, CipherChannels attempts to restore the previous credential. A crash or an unavailable store during rollback can leave an orphaned OS entry. Resetting unreadable settings cannot identify entries whose metadata has been lost; remove those through your OS credential manager.

OS storage protects keys at rest according to your OS account's security policy. It does not protect against a compromised OS, malicious software with sufficient access as your user, or other client mods. It does not add forward secrecy.

## Clipboard

Invite input is hidden by default. After copying an invite, CipherChannels keeps only an in-memory digest of the most recent invite it copied. It clears a matching clipboard value when that invite's channel is forgotten or rotated, or when Minecraft shuts down normally. It does not erase unrelated clipboard contents.

There is no timed clipboard expiry. A crash may leave the invite on the clipboard, and clipboard history or synced copies are not cleared.

## Chat logs

CipherChannels suppresses vanilla logging of chat text it transforms. When a compatible Chat Patches logger is enabled, sent and received encrypted-message history is replaced with:

```text
[CipherChannels encrypted message intentionally not stored]
```

Neither the plaintext nor the raw encrypted frame is intentionally saved. Compatibility hooks are checked at startup. If an enabled Chat Patches logger is detected but cannot be protected, encrypted sending and incoming decryption pause.

Old `logs/chatlog*.json` files may contain plaintext from earlier releases. CipherChannels does not scan or delete them.

Other client mods can read Minecraft's memory before CipherChannels can sanitize it. A compromised device, the OS, screen recorders, accessibility tools, servers, and other players are outside this protection.

## Metadata and server behavior

The server sees your account, network metadata, timing, destination, message order, transport alphabet, and the fixed ciphertext size. It can block, delay, reorder, delete, replace, normalize, truncate, or replay messages. Without the invite, it cannot read authenticated plaintext or forge a message accepted by channel members.

High Capacity needs the server to relay Unicode text unchanged. Compatibility avoids Unicode-specific rejection by using ASCII, but carries less text. Neither format can guarantee compatibility with plugins that rewrite chat.

CipherChannels does not hide traffic patterns, provide anonymity, verify individual authors, resist malicious code in the client, or provide post-compromise security. Commands remain unencrypted.

## Report a vulnerability

Do not post real invites, message contents, or raw encrypted frames in a public issue. Use [GitHub's private vulnerability reporting](https://github.com/Watchedfungus88/cipherchannels/security/advisories/new) for sensitive reports. Replace real secrets with placeholders in logs, screenshots, and reproduction steps.
