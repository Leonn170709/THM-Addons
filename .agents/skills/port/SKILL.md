---
name: port
description: Continue the THM Addons Minecraft 26.2 port, preserving 1.21.11 behavior and tracking progress and graphics backend checks.
---

<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Port THM Addons to 26.2

Use this skill when the user asks to continue the 26.2 port or invokes `$port` in Codex. Read the repository's `AGENTS.md`, `PORT_26_2.md`, and `gotcha.md`, then follow the workflow in `.claude/commands/port.md`. Treat any user-specified area as the focus; otherwise work through the remaining port plan until the code port and checks are complete.

Keep `PORT_26_2.md` current after each porting session. Preserve the released 1.21.11 behavior. Autonomous client tests are allowed; use separate run directories and local test worlds. Verify OpenGL and Vulkan separately through F3 or runtime device information, and distinguish smoke coverage from complete feature verification.
