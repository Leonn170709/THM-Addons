---
name: thm-release
description: Build and publish a THM Addons GitHub release with its jar, release tag, and player-facing notes. Also use for THM release-notes-only requests.
---

<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# THM release

Use this skill only in the THM-Addons repository. A notes-only request ends after drafting the notes; it does not build, tag, push, or publish.

1. Inspect `git status --short`, the current branch and remote, local/remote tags, the latest published GitHub release, and `mod-version` in `gradle/libs.versions.toml`. Default to the next patch version after the latest `releaseX.Y.Z` GitHub release unless the user specifies a version. Use `release<version>` as the tag. For publication, stop for a dirty tree, a missing/diverged remote branch, an existing tag/release, or a version that is not newer. For notes-only, exclude and disclose uncommitted work instead.
2. Read `.claude/commands/thm-release.md` and draft notes from the code changes since the previous release tag, not just commit messages. Keep its player-facing format and compare link. For notes-only, print the draft and stop.
3. If `mod-version` differs from the chosen version, show the proposed version change and get approval before editing and committing only that file with a Conventional Commit. Build only after that commit so the jar's embedded Git SHA matches the release commit.
4. Require a real `secrets.properties` for a public jar; never print its values. Run `./gradlew build --rerun-tasks`, never `runClient`. Verify `build/libs/THM-Addons-<version>.jar` exists, contains the expected `fabric.mod.json` version, and is the release jar rather than a sources/dev jar. Record its SHA-256. Stop on any failed check.
5. Show the tag, commit, branch, jar name and hash, and complete notes. Get explicit approval before pushing or publishing. Then push the release commit if needed and the tag without force, and create a GitHub release named `Release <version>` with that exact jar and the drafted notes. Verify the published release's tag, body, and asset. If a later step fails, report what already exists; do not recreate or overwrite it blindly.

Never include unrelated uncommitted work, silently change the Minecraft target, or publish a jar built from a different commit than the tag.
