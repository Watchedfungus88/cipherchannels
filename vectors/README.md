# Interoperability vectors

This directory contains deterministic CipherChannels 2 protocol vectors for compatible implementations.

`protocol-vectors-v2.json` fixes a public master key and nonces, then records the derived subkeys, invite, fingerprint, recognition hints, complete binary frames, wire hashes, and replay digests for raw and compressed records in both transports.

The vectors are exercised by `ProtocolVectorTest`. They are interoperability fixtures, not evidence or a claim of an independent security audit.
