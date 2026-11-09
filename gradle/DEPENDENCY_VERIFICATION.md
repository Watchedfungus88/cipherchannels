# Dependency verification

`verification-metadata.xml` keeps strict SHA-256 verification enabled. Do not
regenerate checksums blindly or disable verification to make a build pass.

## Fabric API 0.116.15+1.21.1 archive variants

On 2026-11-09, 47 module JARs served by Fabric Maven differed from the original
archive hashes. The accepted alternatives are pinned per artifact, not trusted
through a group-wide exception.

The reference umbrella JAR was checked against both its existing SHA-256 pin and
the independently published [Modrinth version](https://modrinth.com/mod/fabric-api/version/Nlt8gI9z):

- SHA-256: `a61a10f730ab8aa45ff42486ee65699e6e51c28a0c168deb161e7d4029473aa3`
- Modrinth SHA-512: `d5dcf28a05676b1436f7ccce76e2f36495ab058f7c3b6f87a5b5cf5abaaf943ef7f37cd4ce72ff1080965a3d11899cd0bf11121e49c4a651daca20f6338043f7`

For 46 modules, every uncompressed ZIP entry, including class files, manifests,
and signatures, was byte-identical to the corresponding originally pinned
module nested inside that umbrella. ZIP entry names were also compared, with
duplicate entries rejected. Only archive packaging differed.

`fabric-gametest-api-v1:2.0.5+6fc22b9919` is not included in the umbrella.
Its payload signatures were verified with JDK `jarsigner`, with no unsigned
payload entries. Its signer certificate was checked byte-for-byte against the
Fabric certificate from the already-pinned `fabric-api-base` module. That
certificate is self-signed; the trust anchor here is the pinned reference JAR,
not the platform certificate store.

All 47 cached artifacts were compared with fresh downloads from
`https://maven.fabricmc.net/`. One fresh download had an additional archive
representation, also with identical entries. Only the exact verified archive
hashes were added as `also-trust` values. Original pins were retained.

Some Maven checksum sidecars did not match the served archives, so those
sidecars alone were not treated as sufficient evidence. Future mismatches must
be investigated again rather than accepted automatically.
