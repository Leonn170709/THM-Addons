<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Minecraft 26.2 port

Goal: preserve the released 1.21.11 modules, commands, HUDs, settings, and behavior on Minecraft 26.2. Use Blaze3D for OpenGL and Vulkan. Branch `1.21.11` and tag `release0.2.9` remain the behavior reference.

**Status:** Code port and automated checks pass. Real OpenGL and Vulkan client smoke tests pass for startup, 30 menu shaders, local world join, and clean shutdown. Full gameplay and rendering verification remains open.

## To do

- [x] Preserve the 1.21.11 release and work on branch `26.2`.
- [x] Port the 0.2.9 offhand EChest restock hotfix.
- [x] Update Minecraft, Fabric, Meteor, Loom, and Java; use Mojang names.
- [x] Port inventory clicks to `ContainerInput` and `handleContainerInput`.
- [x] Port shared Minecraft, Fabric, and Meteor APIs; make main and test sources compile.
- [x] Audit required and optional mixin targets against dependency bytecode.
- [x] Make `test` and `build` pass with Java 25.
- [x] Compare registration and saved setting names against `1.21.11`; migrate legacy packet selections.
- [x] Port world, entity, cape, HUD, GUI, and shader paths through Blaze3D; replace raw GL ghost and Chams depth state.
- [x] Compile and link complete background and inline shader programs for OpenGL and Vulkan; run them as build tests.
- [x] Update snapshot metadata and Gradle CI. GitHub Actions execution is still pending a push.
- [x] Run autonomous OpenGL and Vulkan smoke tests: startup, all 30 menu shaders, local world join, and clean shutdown. Confirm the active backend through runtime device information.
- [ ] Complete the rendering checklist on actual OpenGL and Vulkan, including Vulkan with Sodium.
- [ ] Verify highway automation, restock, travel, PvP, packet utilities, reconnect, and UI in-game on 26.2.
- [ ] Complete release review after the in-game checks pass.

## Current work

The 0.2.9 offhand EChest restock hotfix is ported. Next: verify repeated restocks with
`minimum-empty-slots=0`, a full inventory, and the default on-place break mode; then continue the
remaining gameplay and rendering checks below.

| Check | Result on 2026-10-03 | Evidence |
| --- | --- | --- |
| `compileJava`, `compileTestJava` | Passed | Java 25, Minecraft 26.2, Meteor 26.2-SNAPSHOT. |
| `test` | Passed, 272 tests | Existing behavior tests, migration/pipeline/mixin checks, and 75 shader tests. |
| `build` | Passed | `build/libs/THM-Addons-0.3.0.jar`. |
| Shader tests | Passed, 75 tests | 35 complete programs across both backends; 4 negative syntax/linker cases and an unused-sampler regression. Background SPIR-V must contain no unbound texture resources. |
| Registration and setting names | Unchanged | 41 registered modules, 6 commands, 15 HUDs, 8 themes; 824 setting-name occurrences compared with `1.21.11`. |
| Optional mixin targets | Passed | Sodium 0.9.2, Xaero Minimap 26.5.1, Xaero World Map 1.46.1, all for 26.2. These are test dependencies only. |
| OpenGL client smoke test | Passed | Radeon RX 9060 XT, Mesa 26.2.4; 30 shader screenshots, joined a fresh local world, 200 world ticks, clean exit. |
| Vulkan client smoke test | Passed | Radeon RX 9060 XT, RADV/Mesa 26.2.4; 30 shader screenshots, joined a fresh local world, 200 world ticks, clean exit. |
| Vulkan with Sodium | Pending | User is installing Sodium and testing this combination. No Sodium was loaded in the autonomous runs. |
| Gameplay and settings import | Pending | Static comparisons and unit tests do not establish runtime parity. |

The mixin check validates classes, shadow/accessor/invoker members, injection selectors, callback arguments, and referenced bytecode instructions. It does not apply the combined Minecraft/Meteor mixin transformations or start a client.

## Client smoke test evidence

The local harness in `run/client-smoke/` creates isolated creative test worlds through vanilla
world creation APIs, cycles all menu shaders, saves screenshots, and closes the client. It is
local test tooling, not included in the addon jar. Final logs are saved alongside its screenshots.

- `run/client-smoke/opengl/final-run.log`: `THM-SMOKE PASS world-ticks=200 backend=OpenGL`.
- `run/client-smoke/vulkan/final-run.log`: `THM-SMOKE PASS world-ticks=200 backend=Vulkan`.
- Both `screenshots/` directories contain 30 shader images and `world.png`. Contact sheets:
  `run/client-smoke/opengl/shader-overview.jpg`, `run/client-smoke/vulkan/shader-overview.jpg`.
- Device information confirms both backends on the RX 9060 XT. The contact sheets show the
  intended backgrounds on both; sampled world screenshots show successful world rendering.
- These runs cover the title screen and a quiet creative world. Module behavior, complex world
  overlays, capes, resize/scaling, blur bounds, and the trip effect still need targeted checks.
- Non-blocking environment messages: missing narrator `flite` and unauthenticated dev-account
  profile/Realms requests. Initial driver GLSL warnings did not prevent any background drawing.

Local commands (harness files are git-ignored):

```bash
./gradlew --init-script run/client-smoke/smoke.init.gradle -PsmokeBackend=OPENGL runClient
./gradlew --init-script run/client-smoke/smoke.init.gradle -PsmokeBackend=VULKAN runClient
```

## In-game checklist

Record each rendering result separately for OpenGL and Vulkan; confirm the active backend in F3 or runtime device information. Use Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Meteor 26.2-SNAPSHOT, and Baritone for 26.2.

- [ ] Startup without a mixin error; dynamic build branch/commit metadata and addon registration.
- [ ] Load a copy of 1.21.11 settings: modules, HUD positions, profiles, themes, packet logger filters, limiter bypass/block selections.
- [ ] HighwayBuilder: normal/packet mining and placing, shulker restock, autosetup, bow draw, freelook, monitor recovery/reconnect, profiles.
- [ ] Offhand EChest restock: full inventory, `minimum-empty-slots=0`, default on-place mode, recovery, and totem safety; echests stay in the offhand until restock finishes.
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

- 2026-10-03: Inlined the offhand ownership decisions in HighwayBuilder and removed the policy class and its three tests. The restock guard and atomic swaps remain. Clean builds pass 193 tests on 1.21.11 and 272 on 26.2; gameplay verification remains open.
- 2026-10-03: Ported the 0.2.9 offhand EChest hotfix. Restock recovery preserves offhand supplies; chest/totem/tool moves use cursor-free swaps, HotbarManager preserves occupied cursors, and echest mining rejects unsuitable tools. Build and 275 tests pass. The 1.21.11 release build passes 196 tests. The reported gameplay scenario still needs an in-game check; graphics paths are unchanged.
- 2026-10-03: Established the 26.2 dependency and Mojang-name baseline. Compilation failed; raw OpenGL ghost rendering required replacement.
- 2026-10-03: Ported seven inventory click call sites. Compilation still reported 389 other errors.
- 2026-10-03: Ported remaining Minecraft/Fabric/Meteor APIs, GUI extraction, entity submission, GPU pipelines, packet codecs, text rendering, and cape submission. Corrected stale mixin targets using a bytecode regression check, including optional Sodium/Xaero integrations.
- 2026-10-03: Preserved all registrations and setting names. Added migration for 227 legacy packet names, including split entity interaction/attack selections. Replaced ghost and Meteor Chams depth handling with Blaze3D pipelines; fixed the Gas shader for Vulkan.
- 2026-10-03: Build and 197 tests pass. All 37 shader sources compile for both backends. Updated Gradle CI and snapshot metadata. In-game rendering and gameplay checks remain pending; no client was launched or release published.
- 2026-10-03: Added `ShaderCompatibilityTest` and `checkShaders`: 70 complete-program OpenGL/Vulkan compilation/linking checks plus 4 negative cases. Integrated into `test`/`build`, replaced the old stage-only script with a Gradle wrapper, and moved compiler installation before the CI build. Full build and 271 tests pass. Actual GPU and in-game checks remain pending.
- 2026-10-03: Authorized autonomous client/world tests in `AGENTS.md` and the port workflow. Added Baritone to the dev runtime. Fixed accessor-interface helper methods and a button injection into an abstract method; extended the bytecode regression check. Real Vulkan caught an unused sampler in `liquid`; removed it and added a SPIR-V resource regression test. Made Baritone cache workers daemon threads to avoid the 26.2 shutdown watchdog. Final OpenGL/Vulkan runs both drew all 30 backgrounds, joined local worlds, and exited cleanly. Build and 272 tests pass. Vulkan with Sodium and full feature checks remain open.
