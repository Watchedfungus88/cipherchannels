# Dependency verification

`verification-metadata.xml` enables strict SHA-256 verification. Investigate a mismatch before accepting new bytes. Do not regenerate checksums blindly or turn verification off to get a passing build.

## Fabric API 0.116.15+1.21.1 archive variants

On 2026-11-09, 47 module JARs from Fabric Maven had different archive hashes from the original pins. The accepted alternatives are pinned to individual artifacts, not allowed through a group-wide exception.

The reference umbrella JAR was checked against its existing SHA-256 pin and the hash published for the corresponding [Modrinth version](https://modrinth.com/mod/fabric-api/version/Nlt8gI9z):

- SHA-256: `a61a10f730ab8aa45ff42486ee65699e6e51c28a0c168deb161e7d4029473aa3`
- Modrinth SHA-512: `d5dcf28a05676b1436f7ccce76e2f36495ab058f7c3b6f87a5b5cf5abaaf943ef7f37cd4ce72ff1080965a3d11899cd0bf11121e49c4a651daca20f6338043f7`

For 46 modules, every uncompressed ZIP entry matched the corresponding originally pinned module inside the umbrella JAR, byte for byte. This included classes, manifests, and signatures. Entry names were compared and duplicates rejected. Only the archive packaging differed.

The remaining module, `fabric-gametest-api-v1:2.0.5+6fc22b9919`, is not in the umbrella JAR. JDK `jarsigner` verified its payload signatures, with no unsigned payload entries. Its signer certificate also matched the Fabric certificate from the already-pinned `fabric-api-base` module, byte for byte. The certificate is self-signed: trust comes from the pinned reference JAR, not the system certificate store.

All 47 cached artifacts were compared with fresh downloads from `https://maven.fabricmc.net/`. One fresh download had another archive representation, also with identical entries. Only those exact verified hashes were added as `also-trust` values. Every original pin was kept.

Some Maven checksum sidecars did not match the archives being served. Those sidecars were not accepted as sufficient evidence on their own. Any future mismatch needs a new investigation.
