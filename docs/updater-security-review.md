<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Updater security review

Reviewed on 2026-10-06 against the current 26.2 client and mounted API/publisher source. No unsigned remote-code installation path was found. Updates still trust the publisher and signing key; a valid signature does not prove a build is harmless.

## Findings and changes

| Severity | Finding | Result |
| --- | --- | --- |
| High, publisher trust | The publisher executes fetched repository Gradle code as a subprocess of the process that signs feeds. No build sandbox or separate identity is specified in that code. A compromised repository build can access signing material unless deployment independently prevents it. | Open: isolate builds from the signer, give them no private-key access, and sign only verified outputs. No live service configuration was changed. |
| Medium | HTTP redirects compared only hostnames. A same-host redirect to another port could receive the API Bearer token. | Fixed: redirects must retain scheme, hostname, and effective port. Explicit HTTPS port 443 remains valid. |
| Low | Repeated GUI actions could queue unlimited updater work and retain obsolete install requests. | Fixed: allow one active updater operation and discard duplicate submissions. |
| Low | Failed refreshes left the previous verified history available for installation. | Fixed: clear the history when a refresh begins. |
| Low | JAR origins were resolved through symbolic links before checking the mods directory. | Hardened: require a regular, direct JAR and reject symbolic-link origins. |
| Low | Feed parsing accepted uppercase SHA-256 but installation compared against lowercase text. | Fixed: compare decoded digest bytes. |

Regression tests cover redirected origins, digest mismatch, incompatible JAR metadata, successful atomic replacement and backup, escaped/symlink paths, backup failure, temporary-file cleanup, and duplicate-operation handling.

## Existing protections verified in source

- Auto-install is off by default. API access requires the personal token.
- HTTPS, certificate validation, public-address checks, connection/read timeouts, response limits, and bounded redirects protect downloads.
- The feed's exact payload is authenticated with a build-pinned Ed25519 key before entries are used.
- JAR URLs must use the feed's HTTPS origin. Entries must match the installed Minecraft version and branch.
- The JAR's SHA-256 and mod ID, version, commit, branch, and Minecraft metadata are checked before backup or replacement.
- The current JAR is replaced atomically. Failed verification discards the download; failed backup does not replace the installation.
- The previous JAR is retained as a non-loadable `.bak`. Replacement requires restarting Minecraft.

## Server source review

The API's updater middleware validates the existing UUID Bearer credential and reloads membership for each request, failing closed if membership data cannot be read. The protected file handler allowlists feed/JAR routes, checks real paths against its root, opens the final component with `O_NOFOLLOW`, and limits file sizes. The publisher checks JAR metadata/digests, validates release ancestry, publishes immutable JARs, and verifies its generated signatures. These are source observations, not fresh live authorization tests.

The publisher's build subprocess inherits its process identity. Restricting its environment variables does not restrict filesystem access to the signing key. A separate build user/container with no signer files or credentials is the recommended boundary. Existing write access to the GitHub branch is already authority to publish code through development updates.

## Remaining trust and privacy limits

A Git commit SHA identifies repository content, not a compiled JAR. A malicious JAR can copy legitimate `github:sha` metadata, so checking that the commit exists does not prove what code the JAR contains. This client currently checks against the signed feed, not an independent GitHub build identity.

Independent verification should bind the **downloaded JAR's SHA-256** to an artifact published through the trusted GitHub repository. [GitHub release assets expose a digest](https://docs.github.com/en/rest/releases/assets); the client must fetch it directly from the fixed repository over HTTPS without sending the THM API token, and reject missing or mismatched digests. This prevents an updater server from substituting a different JAR, but GitHub uploads alone do not prove the artifact was built from source.

For build provenance, use [GitHub artifact attestations](https://docs.github.com/en/actions/concepts/security/artifact-attestations), verifying the exact artifact digest, repository, trusted workflow, source commit, and hosted runner identity. Build once in isolated CI and mirror those exact bytes; independently rebuilding the same commit is insufficient because generated endpoint encryption uses fresh randomness. Development updates currently come from the publisher's local Gradle build and have no independently published GitHub artifact to verify. This verification protocol is proposed, not implemented. A secure deployment must reject unverifiable builds rather than silently fall back to server-supplied hashes.

1. **Signed-feed replay:** format 1 has no signed expiry or monotonic generation. A compromised distribution host can replay a valid older feed and its matching JAR. Complete downgrade protection needs a coordinated publisher/client protocol change. Manual history installs are intentional downgrades.
2. **Signing-key compromise:** the trusted signer can authorize arbitrary addon code. Keep the private key outside public repositories and restrict publisher access. Endpoint encryption in the client is obfuscation, not an authentication boundary.
3. **DNS and proxy behavior:** the normal HTTP path validates DNS before connecting, but does not pin the checked address. Its DNS-over-HTTPS fallback pins a public address and validates the original TLS hostname, but bypasses Java's configured proxy and sends DNS queries to Cloudflare. OS-level VPN routing still applies.
4. **Filesystem races:** other processes with write access to the mods folder can modify paths concurrently. This updater is not a boundary against code already running with the user's filesystem permissions.
5. **Scope:** live server authorization, actual publisher filesystem isolation, Windows replacement of an open JAR, and the in-game install/restart flow were not exercised in this review. Client tests do not prove those deployment properties.
