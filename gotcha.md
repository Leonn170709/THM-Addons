<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# 26.2 port gotchas

- `migrateMappings` changed known Yarn names to Mojang names. It did not port changed Minecraft, Fabric, or Meteor APIs. Wildcard imports and mixin targets need manual review. Run it only in an isolated checkout: a failed run previously removed its input directory.
- Minecraft 26.2 uses Java 25 and the non-remapping Fabric Loom plugin. There is no Yarn dependency or `mappings(...)` entry in this branch. The Gradle daemon JVM criteria requests Java 25.
- The compiler reports only its first 100 errors by default. Treat that output as the next work queue, not a count of all remaining failures.
- Minecraft 26.2 replaced `ClickType` with `ContainerInput` and `MultiPlayerGameMode.handleInventoryMouseClick` with `handleContainerInput`. The `PICKUP`, `QUICK_MOVE`, and `SWAP` input values still exist. The seven addon call sites now use the new API; inventory behavior still needs an in-game check.
- Raw OpenGL is unsupported with the optional Vulkan backend. Ghost rendering and Chams model depth now use Blaze3D pipelines; the addon disables Meteor's old `Chams.shouldRender` GL-state path and applies its entity selection when submitting model geometry. Keep these hooks together. See [Fabric rendering concepts](https://docs.fabricmc.net/develop/rendering/basic-concepts).
- Selecting Vulkan or OpenGL in settings can fall back to the other backend. Check the active backend in F3 before recording a test result, as described in the [Minecraft 26.2 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2).
- A successful compile or unit test does not prove rendering or server behavior. Do not mark feature parity or either graphics backend complete without an in-game check.
- `getCommit()` and `getRepo()` read the SHA and branch embedded at build time. Keep both dynamic; a jar built from uncommitted changes still identifies its last committed SHA.

- GUI extraction and drawing are separate in 26.2. Screen callbacks receive `GuiGraphicsExtractor`; shader GPU passes belong in `GuiRenderer.render`. Title-window blur is requested during extraction and drawn after the panorama. Meteor widget rendering runs in its own later GUI pass.
- Screen ownership moved from `Minecraft` to `Minecraft.gui`; HighwayBuilder's screen replacement now targets `Gui#setScreen`. Camera rotation alignment moved into `Camera.alignWithEntity`.
- Blaze3D uses `GpuFormat`, `BindGroupLayout`, optional clear colors, and four-argument draw calls. Postprocessing pipelines include `Globals`; bind default uniforms before drawing. New scissor validation rejects offscreen rectangles, so clamp blur bounds.
- Vulkan's surface presentation flips Y. Its framebuffer/scissor convention still matches the existing bottom-origin blur coordinates; do not add a second backend-specific flip.
- `ServerboundAttackPacket` now carries attacks separately from `ServerboundInteractPacket`. CrystalAura prediction and CrystalMetrics use the new record; do not mutate an interaction packet's entity ID.
- Packet codecs are named `STREAM_CODEC`. Meteor packet settings store `PacketType` IDs instead of classes. Its upstream legacy aliases use Mojang names, while the released addon saved Yarn names. `legacy-packets.properties` and `PacketSettingMigration` preserve those 227 names, including one-to-many splits.
- Minecraft input movement is `ClientInput.moveVector`. The old THM `InputAccessor` had no registered implementation; it is now a real mixin accessor under `mixin/accessor`.
- Gas previously declared loose scalar uniforms, which Vulkan rejects. Those values were never supplied by the old renderer; constants retain the old zero defaults.
- `ShaderCompatibilityTest` links complete vertex/fragment programs for OpenGL and Vulkan 1.2, including the actual vanilla screenquad source. `test`/`build` require `glslangValidator` on PATH. Compiler/linker success does not validate GPU drivers or the Java render passes.
- Optional target checks use [Sodium](https://modrinth.com/mod/sodium), [Xaero Minimap](https://modrinth.com/mod/xaeros-minimap), and [Xaero World Map](https://modrinth.com/mod/xaeros-world-map) 26.2 jars as test-only dependencies. Passing a target-bytecode check does not prove combined mixin application or in-game behavior.
