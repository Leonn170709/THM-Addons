<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# 26.2 port gotchas

- The 2026-10-04 live diagnostics confirmed `TitleBridgeScreen` from ForceCloseLoadingScreen, outer THM setup, 30 discovered backgrounds, and successful scaled shader drawing on Vulkan. An ImageButton sprite includes its vanilla frame; shrinking the whole sprite retains that frame, so crop its outer pixels when adding THM chrome.

- Animated backgrounds reuse one offscreen texture between updates, independently of GUI FPS. Defaults are 25% resolution and 30 background FPS; 50% restores the old resolution and 0 FPS redraws every frame. Shader changes and texture resizes force an immediate update. Compiler checks and cadence tests do not measure live FPS.

- Main-menu setup must run after the outer `Screen.init`, resize, or widget rebuild. Another client can cancel the inner `TitleScreen.init`, skipping tail hooks that add THM settings or select shaders. Setup must avoid duplicate buttons when these hooks nest. Place Boze first in the upper-left sidebar and stack other wide addon buttons below it. Keep small addon buttons with Friends/Language/Accessibility in the central icon row; keep THM Menu inside the panel to avoid third-party footer text.

- `[THM/Init]` logs startup stages. `[THM/Menu]` logs requested/active screen classes, title init entry/return, outer setup, styled widget counts, render hooks, GPU backend, shader selection/compilation, and fallback reasons. Render/button messages log once or when their state changes.

- 26.2 adds a Friends button beside Language and Accessibility. Lay out small title-screen buttons after initialization, preserve their sprite and notification rendering, and anchor the separate Realms notification overlay inside the relocated Realms button's right edge. Its vanilla badge coordinates do not follow the Realms button. Pack visible badges without reserving empty notification slots.

- TPS safety walls must use Netherrack independently of paving material. Track only placed safety targets and remove them on all four sides after recovery, before Forward moves. Normal highway mining alone leaves the lateral walls behind.

- `Manage-hotbar` controls whether HighwayBuilder enables HotbarManager; an independently active manager still reserves its slots. Restocking uses configured slots directly and pauses the manager's sorting to keep offhand swap slots stable. Offhand EChest placement checks the actual hand, handles exhausted supply, and pauses its watchdog while a safety totem is required.

- Keep the held mining tool on equal AutoTool scores. Choosing the first inventory match every tick can swap two equal pickaxes back and forth through the same managed slot, resetting container mining. Restock states own main-hand selection; generic offhand/packet-build loadouts must not replace their cleanup tool.

- Boze and THM both initialized in the 2026-10-04 Prism 26.2 log, with no reported TitleScreen mixin failure. A replacement menu is a possible cause when vanilla TitleScreen hooks disappear, but this log does not identify the active screen. The published [Boze event API](https://docs.boze.dev/dev/boze/api/event/package-summary.html) has no dedicated title-screen event.

- Vanilla handles primary STOP immediately at progress >= 0.7, but the delayed secondary completes in `ServerPlayerGameMode.tick()` with the tool held then. Secondary STOP after a new START may target a different `destroyPos` and be ignored. Normal mining therefore retains its tool through confirmation in every swap mode; fast swap timing applies to instant/primed breaks. Prioritize the delayed secondary tool, but send the primary STOP at its own threshold. Track both pending confirmations; waiting for the secondary before finishing the primary delays double-break.

- Speedmine's Keep mode checks after tick placements and holds through the next client tick. A replacement keeps the tool even while TPS throttling delays its STOP. Same Tick wraps selection/mining/restoration in one call; End of Tick restores in `TickEvent.Post`; Tool Hold preserves the confirmation wait. Meteor enum values serialize their `toString()` labels, so legacy `tool-hold` migration must write those labels.

- TPS sync must not throttle starts at 20 TPS. Below 20, preserve fractional start credit with a two-block cap; a one-block cap discards overflow and repeatedly skips ticks when the estimate is just under 20 or client ticks vary.

- Speedmine now exposes only `client-prediction` for local removal. Legacy `instant-client-remove=true` or `validate-break=false` migrates to prediction on; `remove-slow-blocks` is retired. Mining packets use the best hotbar tool. Keep/end-of-tick reuse a retained selection only when the shared server-slot tracker still matches. Prediction cannot make a ghost placement exist on the server.

- Silent miners must compare against `InventoryManager.getServerSlot()`, because other modules and vanilla can change it. Tool Hold waits for server confirmation; client-side air does not confirm a break. A rebreak STOP uses the position retained by the last server START, so starting another position invalidates that primed rebreak.
- Client block prediction must call `ClientLevel.setBlock` inside the same `startPrediction` callback as its mining packet. Setting air after that scope closes bypasses vanilla acknowledgement/correction tracking. TPS sync scales elapsed progress and packet cadence; START already contributes one server tick of mining progress.

- Offhand supplies belong to the whole restock sequence, including recovery states. Refilling obsidian outside `MineEnderChests` can displace its chest stack. `PICKUP` moves require an empty cursor, and chest menus expose no offhand slot; use `ContainerInput.SWAP` with button `SlotUtils.OFFHAND` for different items, and merge matching stacks only in the player inventory with an empty cursor.
- `migrateMappings` changed known Yarn names to Mojang names. It did not port changed Minecraft, Fabric, or Meteor APIs. Wildcard imports and mixin targets need manual review. Run it only in an isolated checkout: a failed run previously removed its input directory.
- Minecraft 26.2 uses Java 25 and the non-remapping Fabric Loom plugin. There is no Yarn dependency or `mappings(...)` entry in this branch. The Gradle daemon JVM criteria requests Java 25.
- The compiler reports only its first 100 errors by default. Treat that output as the next work queue, not a count of all remaining failures.
- Minecraft 26.2 replaced `ClickType` with `ContainerInput` and `MultiPlayerGameMode.handleInventoryMouseClick` with `handleContainerInput`. The `PICKUP`, `QUICK_MOVE`, and `SWAP` input values still exist. The seven addon call sites now use the new API; inventory behavior still needs an in-game check.
- Raw OpenGL is unsupported with the optional Vulkan backend. Ghost rendering and Chams model depth now use Blaze3D pipelines; the addon disables Meteor's old `Chams.shouldRender` GL-state path and applies its entity selection when submitting model geometry. Keep these hooks together. See [Fabric rendering concepts](https://docs.fabricmc.net/develop/rendering/basic-concepts).
- Selecting Vulkan or OpenGL in settings can fall back to the other backend. Check the active backend in F3 before recording a test result, as described in the [Minecraft 26.2 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2).
- A successful compile or unit test does not prove rendering or server behavior. Do not mark feature parity or either graphics backend complete without an in-game check.
- `getCommit()` and `getRepo()` read the SHA and branch embedded at build time. Keep both dynamic; a jar built from uncommitted changes still identifies its last committed SHA.

- GUI extraction and drawing are separate in 26.2. Screen callbacks receive `GuiGraphicsExtractor`; shader GPU passes belong in `GuiRenderer.render`. Apply title-window blur directly after drawing the shader or vanilla panorama in that callback. An earlier GUI renderer reaches `prepare()` without a panorama and would consume a queued blur before the background overwrites it. Meteor widget rendering runs in its own later GUI pass.
- Screen ownership moved from `Minecraft` to `Minecraft.gui`; HighwayBuilder's screen replacement now targets `Gui#setScreen`. Camera rotation alignment moved into `Camera.alignWithEntity`.
- Blaze3D uses `GpuFormat`, `BindGroupLayout`, optional clear colors, and four-argument draw calls. Postprocessing pipelines include `Globals`; bind default uniforms before drawing. New scissor validation rejects offscreen rectangles, so clamp blur bounds.
- Vulkan's surface presentation flips Y. Its framebuffer/scissor convention still matches the existing bottom-origin blur coordinates; do not add a second backend-specific flip.
- `ServerboundAttackPacket` now carries attacks separately from `ServerboundInteractPacket`. CrystalAura prediction and CrystalMetrics use the new record; do not mutate an interaction packet's entity ID.
- Packet codecs are named `STREAM_CODEC`. Meteor packet settings store `PacketType` IDs instead of classes. Its upstream legacy aliases use Mojang names, while the released addon saved Yarn names. `legacy-packets.properties` and `PacketSettingMigration` preserve those 227 names, including one-to-many splits.
- Minecraft input movement is `ClientInput.moveVector`. The old THM `InputAccessor` had no registered implementation; it is now a real mixin accessor under `mixin/accessor`.
- Gas previously declared loose scalar uniforms, which Vulkan rejects. Those values were never supplied by the old renderer; constants retain the old zero defaults.
- `ShaderCompatibilityTest` links complete vertex/fragment programs for OpenGL and Vulkan 1.2, including the actual vanilla screenquad source. `test`/`build` require `glslangValidator` on PATH. Compiler/linker success does not validate GPU drivers or the Java render passes.
- Optional target checks use [Sodium](https://modrinth.com/mod/sodium), [Xaero Minimap](https://modrinth.com/mod/xaeros-minimap), and [Xaero World Map](https://modrinth.com/mod/xaeros-world-map) 26.2 jars as test-only dependencies. Passing a target-bytecode check does not prove combined mixin application or in-game behavior.
- Accessor interfaces targeting classes must contain only `@Accessor`/`@Invoker` methods. Default helpers make Mixin classify them as ordinary interface mixins and reject class targets. Keep helper logic in consumers.
- `AbstractButton.extractContents` is abstract in 26.2. Inject button styling into `extractWidgetRenderState` and preserve `handleCursor`; injecting into an abstract method fails before startup.
- Vulkan retains the unused `liquid` sampler that OpenGL removes. Background shaders have no texture bindings; remove unused sampler declarations. Shader tests now reject texture resources in background SPIR-V.
- Baritone's cache workers run indefinitely on non-daemon executor threads. Minecraft 26.2's shutdown watchdog crashes after world saving if they keep the JVM alive. `BaritoneWorkerMixin` makes workers daemon threads when the executor is created; shadow alias `a` covers the obfuscated Meteor jar.
- Flat-all-dimensions test worlds can show an experimental backup prompt when reopened. Autonomous smoke runs create fresh worlds instead of assuming an old test world opens without a prompt.
