# CipherChannels protocol 2

This document specifies the interoperable CipherChannels 2 wire protocol used unchanged by CipherChannels 2.0 through 2.2. Integers and byte lengths are exact. Text domains are UTF-8 and include the final NUL byte shown as `\0`.

## Master key and identities

A channel master key is 32 uniformly random bytes.

```text
HKDF salt:            "CipherChannels key schedule v2\0"
encryption-key info:  "CipherChannels encryption key v2\0"
recognition-key info: "CipherChannels recognition key v2\0"
```

HKDF uses HMAC-SHA-256. Both output keys are 32 bytes.

An invite is:

```text
CC2.<43-character canonical unpadded Base64url key>.<8-character Crockford Base32 checksum>
```

The checksum is the first 40 bits of:

```text
SHA-256(UTF-8("CipherChannels invite checksum v2\0") || master-key)
```

The 80-bit fingerprint is the first 80 bits of:

```text
SHA-256(UTF-8("CipherChannels fingerprint v2\0") || master-key)
```

It is Crockford Base32 encoded as `XXXX-XXXX-XXXX-XXXX`. Parsing requires exact syntax, the canonical Base64url representation, and a constant-time checksum comparison. `CC1` is rejected explicitly.

## Binary frame

Both transports encode:

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

AES-256-GCM uses a 128-bit tag. Its AAD is:

```text
UTF-8("CipherChannels frame v2\0") || transport-id || nonce || recognition-hint
```

The encrypted plaintext record is:

```text
content || control || zero-padding
```

`0x20` selects raw strict UTF-8. `0x21` selects raw DEFLATE with `nowrap=true`, followed after bounded expansion by strict UTF-8. The control is the final nonzero byte. Empty content, another control, nonzero trailing data, malformed UTF-8, incomplete/trailing DEFLATE data, and restored data over 4,096 bytes are invalid.

Raw UTF-8 is always used when it fits. Otherwise the sender tries Java default-compression raw DEFLATE. Compression is accepted only when it fits and the original strict UTF-8 is no more than 4,096 bytes. Messages are never truncated, split, or sent as plaintext fallback.

## Transports

| Transport | ID | Binary frame | Encrypted record | Wire | Raw content |
|---|---:|---:|---:|---:|---:|
| High Capacity | `0x01` | 480 bytes | 444 bytes | 256 Base32768 UTF-16 characters | 443 bytes |
| Compatibility | `0x02` | 192 bytes | 156 bytes | 256 unpadded Base64url characters | 155 bytes |

Base32768 uses the repertoire attributed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Decoding must be canonical and normalization-stable. Compatibility uses RFC 4648 URL-safe Base64 without padding.

## Candidate detection and authentication

A candidate is one intact maximal run of exactly 256 characters from one supported alphabet. It may span adjacent literal component nodes. Prefixes and suffixes are allowed outside the run. A changed, normalized, truncated, or guessed reconstruction is never attempted.

Receivers try at most 16 live keys, active first. For each key they derive the expected hint and compare it using a constant-time equality function. No hint match leaves the frame visible as encrypted-looking with no matching key. A hint match followed by GCM failure is damaged or altered. Only authenticated records can produce plaintext.

## Replay identity

After authentication, the replay identifier is:

```text
fingerprint || SHA-256(transport-id || complete-binary-frame)
```

Implementations retain up to 4,096 verified identifiers for six hours or until the client closes, whichever comes first. A duplicate withholds plaintext. CipherChannels keeps replay history only in memory.

## Vectors and compatibility

Deterministic subkey, invite, fingerprint, hint, raw-frame, compressed-frame, and transport fixtures are in [vectors/protocol-vectors-v2.json](vectors/protocol-vectors-v2.json). CipherChannels accepts no v1 domain, invite, control byte, frame, or configuration as v2 material.
