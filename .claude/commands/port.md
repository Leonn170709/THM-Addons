---
description: Continue the THM Addons Minecraft 26.2 port and update its progress
argument-hint: [area]
---

<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

Continue the Minecraft 26.2 port. `$ARGUMENTS` may name an area to focus on; otherwise take the next unchecked task in `PORT_26_2.md`.

1. Read `AGENTS.md`, `PORT_26_2.md`, and `gotcha.md`. Check the branch, working tree, and current dependency versions. Work on branch `26.2`; preserve existing changes and the `1.21.11` reference branch.
2. Work through error groups and feature areas until the requested port scope is complete. Repeat implementation and validation after each group. Fix its root cause using the 26.2 Minecraft, Fabric, and Meteor APIs. Preserve the 1.21.11 behavior and saved settings. Compare with the reference branch when behavior is unclear. Do not remove a feature or add a dummy implementation to make compilation pass.
3. For rendering, use the game's Blaze3D abstractions so the same feature can work with OpenGL and Vulkan. Do not add raw OpenGL calls. Check world, entity, cape, HUD, GUI, and shader paths affected by the change.
4. Run the relevant compile, test, or build check. For shader changes, run `tools/scripts/check-shaders.sh` (requires `glslangValidator`). Autonomous `runClient` tests and local test-world creation/joining are allowed. Use a separate run directory and preserve existing worlds/configs. Verify the active OpenGL/Vulkan backend in F3 or runtime device information, record results separately, and shut down test clients cleanly. List any runtime or visual checks still needed.
5. Before ending, update `PORT_26_2.md`: checked items, current work, last results, next step, and one dated progress-log entry. Add confirmed traps or constraints to `gotcha.md`. Run `tools/scripts/add-credits.sh` on every changed supported file and `git diff --check`.
6. Report the changed behavior, validation result, remaining blockers, and next task. Do not push or publish unless asked.
