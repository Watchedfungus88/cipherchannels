# Audit package

This directory contains deterministic CipherChannels 2.0 protocol vectors intended for independent review and interoperable implementations.

`protocol-vectors-v2.json` fixes a public master key and nonces, then records the derived subkeys, invite, fingerprint, recognition hints, complete binary frames, wire hashes, and replay digests for raw and compressed records in both transports.

The vectors are exercised by `ProtocolVectorTest`. The source, protocol specification, dependency-verification metadata, reproducible build script, and release checksums complete the review package.

No independent audit is claimed unless a named third party publishes one.
