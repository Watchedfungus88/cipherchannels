# Interoperability test vectors

These deterministic examples let other implementations check that they produce and read the same CipherChannels 2 messages.

`protocol-vectors-v2.json` uses a fixed public master key and nonces. It records the derived keys, invite, fingerprint, recognition hints, complete binary frames, wire hashes, and replay digests for raw and compressed records in both chat formats.

`ProtocolVectorTest` checks these values. They are test fixtures, not an independent security audit. The keys and nonces are public test data and must not be used for real channels.
