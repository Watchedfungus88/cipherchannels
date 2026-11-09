# CipherChannels protocol 2

This is the wire protocol used by CipherChannels 2.0–2.3. The protocol has not changed between those releases.

All integer values and byte lengths below are exact. Text domains are encoded as UTF-8 and include the final NUL byte shown as `\0`.

## Keys and invites

A channel master key is 32 uniformly random bytes. HKDF with HMAC-SHA-256 derives two 32-byte keys:

```text
HKDF salt:            "CipherChannels key schedule v2\0"
encryption-key info:  "CipherChannels encryption key v2\0"
recognition-key info: "CipherChannels recognition key v2\0"
```

An invite has this format:

```text
CC2.<43-character canonical unpadded Base64url key>.<8-character Crockford Base32 checksum>
```

Its checksum is the first 40 bits of:

```text
SHA-256(UTF-8("CipherChannels invite checksum v2\0") || master-key)
```

The channel fingerprint is the first 80 bits of:

```text
SHA-256(UTF-8("CipherChannels fingerprint v2\0") || master-key)
```

The fingerprint is encoded with Crockford Base32 as `XXXX-XXXX-XXXX-XXXX`. Invite parsing requires exact syntax, canonical Base64url encoding, and a constant-time checksum comparison. `CC1` invites are explicitly rejected.

## Binary frame

Both transports encode the same layout:

```text
nonce(12) || recognition-hint(8) || AES-GCM(ciphertext-and-tag)
```

The nonce is 12 fresh random bytes. The recognition hint is the first eight bytes of:

```text
HMAC-SHA-256(
  recognition-key,
  UTF-8("CipherChannels recognition v2\0") || transport-id || nonce
)
```

AES-256-GCM uses a 128-bit tag and this additional authenticated data:

```text
UTF-8("CipherChannels frame v2\0") || transport-id || nonce || recognition-hint
```

The plaintext record inside the encryption is:

```text
content || control || zero-padding
```

Control `0x20` selects strict UTF-8. Control `0x21` selects raw DEFLATE with `nowrap=true`, followed by bounded decompression and strict UTF-8 decoding. The control is the final nonzero byte.

Receivers reject empty content, unsupported control bytes, nonzero trailing data, malformed UTF-8, incomplete or trailing DEFLATE data, and decompressed text over 4,096 bytes.

Senders use raw UTF-8 whenever it fits. Otherwise, they try raw DEFLATE with Java's default compression level. Compressed content is accepted only if it fits the record and the original strict UTF-8 text is at most 4,096 bytes. Messages are never truncated, split, or sent unencrypted as a fallback.

## Transport formats

| Transport | ID | Binary frame | Encrypted record | Wire | Raw content |
|---|---:|---:|---:|---:|---:|
| High Capacity | `0x01` | 480 bytes | 444 bytes | 256 Base32768 UTF-16 characters | 443 bytes |
| Compatibility | `0x02` | 192 bytes | 156 bytes | 256 unpadded Base64url characters | 155 bytes |

Base32768 uses the repertoire credited in [Third-party notices](THIRD_PARTY_NOTICES.md). Its encoding must be canonical and normalization-stable. Compatibility uses RFC 4648 URL-safe Base64 without padding.

## Finding and authenticating a frame

A candidate is one intact, maximal run of exactly 256 characters from a supported alphabet. It may span adjacent literal text-component nodes, with prefixes or suffixes outside the run. Receivers do not attempt to repair changed, normalized, or truncated text, or guess missing content.

Receivers try at most 16 loaded keys, starting with the active channel. For each key, they derive the expected recognition hint and compare it in constant time. If no key matches, the text remains visible as a possible encrypted message with no matching key.

A matching hint followed by a failed GCM check means the frame is damaged or altered. Only authenticated records can produce plaintext.

## Replay detection

After authentication, the replay identifier is:

```text
fingerprint || SHA-256(transport-id || complete-binary-frame)
```

Implementations retain up to 4,096 authenticated identifiers for six hours or until the client closes, whichever comes first. A duplicate is not displayed as plaintext. CipherChannels stores this history only in memory.

## Test vectors and compatibility

The [protocol vectors](vectors/protocol-vectors-v2.json) include deterministic subkeys, invites, fingerprints, hints, raw and compressed frames, transport data, and replay digests.

Protocol v1 domains, invites, control bytes, frames, and settings are never treated as valid v2 material.
