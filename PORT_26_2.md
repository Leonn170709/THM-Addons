<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Minecraft 26.2 port

Goal: preserve the released 1.21.11 modules, commands, HUDs, settings, and behavior on Minecraft 26.2. Use Blaze3D for OpenGL and Vulkan. Branch `1.21.11` and tag `release0.2.9` remain the behavior reference.

**Status:** Code port complete; automated checks pass. In-game verification remains open. Do not publish this snapshot as a verified release yet.

## To do

- [x] Preserve the 1.21.11 release and work on branch `26.2`.
- [x] Update Minecraft, Fabric, Meteor, Loom, and Java; use Mojang names.
- [x] Port inventory clicks to `ContainerInput` and `handleContainerInput`.
- [x] Port shared Minecraft, Fabric, and Meteor APIs; make main and test sources compile.
- [x] Audit required and optional mixin targets against dependency bytecode.
- [x] Make `test` and `build` pass with Java 25.
- [x] Compare registration and saved setting names against `1.21.11`; migrate legacy packet selections.
- [x] Port world, entity, cape, HUD, GUI, and shader paths through Blaze3D; replace raw GL ghost and Chams depth state.
- [x] Compile and link complete background and inline shader programs for OpenGL and Vulkan; run them as build tests.
- [x] Update snapshot metadata and Gradle CI. GitHub Actions execution is still pending a push.
- [ ] Verify rendering in-game with **actual OpenGL** and **actual Vulkan**, checking F3 before recording each result.
- [ ] Verify highway automation, restock, travel, PvP, packet utilities, reconnect, and UI in-game on 26.2.
- [ ] Complete release review after the in-game checks pass.

## Current work

Implementation is finished. Next: use the snapshot jar for the checks below and record logs, active backend, and results here. Fix any confirmed runtime regression before releasing.

| Check | Result on 2026-10-03 | Evidence |
| --- | --- | --- |
| `compileJava`, `compileTestJava` | Passed | Java 25, Minecraft 26.2, Meteor 26.2-SNAPSHOT. |
| `test` | Passed, 271 tests | Existing behavior tests, migration/pipeline/mixin checks, and 74 shader tests. |
| `build` | Passed | `build/libs/THM-Addons-0.3.0-SNAPSHOT.jar`. |
| `checkShaders` | Passed, 74 tests | 35 complete programs (30 backgrounds, 5 blur/trip passes) across both backends; 4 negative syntax/linker checks. Uses actual Minecraft vertex source and compiled inline constants. |
| Registration and setting names | Unchanged | 41 registered modules, 6 commands, 15 HUDs, 8 themes; 824 setting-name occurrences compared with `1.21.11`. |
| Optional mixin targets | Passed | Sodium 0.9.2, Xaero Minimap 26.5.1, Xaero World Map 1.46.1, all for 26.2. These are test dependencies only. |
| OpenGL in-game | Pending | Check the active backend in F3. |
| Vulkan in-game | Pending | Check the active backend in F3. |
| Gameplay and settings import | Pending | Static comparisons and unit tests do not establish runtime parity. |

The mixin check validates classes, shadow/accessor/invoker members, injection selectors, callback arguments, and referenced bytecode instructions. It does not apply the combined Minecraft/Meteor mixin transformations or start a client.

## In-game checklist

Record each rendering result separately for OpenGL and Vulkan. Use Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Meteor 26.2-SNAPSHOT, and Baritone for 26.2.

- [ ] Startup without a mixin error; dynamic build branch/commit metadata and addon registration.
- [ ] Load a copy of 1.21.11 settings: modules, HUD positions, profiles, themes, packet logger filters, limiter bypass/block selections.
- [ ] HighwayBuilder: normal/packet mining and placing, shulker restock, autosetup, bow draw, freelook, monitor recovery/reconnect, profiles.
- [ ] HighwayTraveler, ElytraRoute, HighwayTools, TunnelMiner, StashMover, loadouts, and all six commands.
- [ ] CrystalAura: regular/predicted attacks, pause/rotation behavior, damage text, and CrystalMetrics attack counting. Check Surround, AntiMine, and other PvP utilities.
- [ ] PacketLogger: both directions, complete wire payloads, filters and file output. Check limiter/choke traffic and Packet HUD counting.
- [ ] World overlays and projected text: HighwayBuilder labels, Nuker, SignRender, Nametags, tracers and block outlines.
- [ ] LogoutSpots/PopChams skins, limb pose, alpha, cape toggle, and through-wall switch. Test occlusion behind solid blocks, glass, and portals.
- [ ] Chams through-wall entities and player colors, with THM ghosts enabled and disabled.
- [ ] Capes: smooth/blocky styles, crouching, swimming, flight, and THM textures.
- [ ] Main-menu shaders: all backgrounds, especially Gas; None fallback; preview, blur, GUI scaling, resize, and windows partly offscreen.
- [ ] THM themes, HighwayBuilder screen replacement, tooltips, tabs, player list, inventory preview, death chat, screenshot clipboard, and I'm High effect.
- [ ] Server reconnect/authentication, Kitbot integration, API-backed capes/member HUDs, webhooks, and Discord RPC.
- [ ] Edge chunks with vanilla rendering and the tested Sodium/Xaero versions.

## Progress log

- 2026-10-03: Established the 26.2 dependency and Mojang-name baseline. Compilation failed; raw OpenGL ghost rendering required replacement.
- 2026-10-03: Ported seven inventory click call sites. Compilation still reported 389 other errors.
- 2026-10-03: Ported remaining Minecraft/Fabric/Meteor APIs, GUI extraction, entity submission, GPU pipelines, packet codecs, text rendering, and cape submission. Corrected stale mixin targets using a bytecode regression check, including optional Sodium/Xaero integrations.
- 2026-10-03: Preserved all registrations and setting names. Added migration for 227 legacy packet names, including split entity interaction/attack selections. Replaced ghost and Meteor Chams depth handling with Blaze3D pipelines; fixed the Gas shader for Vulkan.
- 2026-10-03: Build and 197 tests pass. All 37 shader sources compile for both backends. Updated Gradle CI and snapshot metadata. In-game rendering and gameplay checks remain pending; no client was launched or release published.
- 2026-10-03: Added `ShaderCompatibilityTest` and `checkShaders`: 70 complete-program OpenGL/Vulkan compilation/linking checks plus 4 negative cases. Integrated into `test`/`build`, replaced the old stage-only script with a Gradle wrapper, and moved compiler installation before the CI build. Full build and 271 tests pass. Actual GPU and in-game checks remain pending.
