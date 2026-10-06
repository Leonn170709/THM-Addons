<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# THMcrystal aura

**THM PVP → THMcrystal aura** replaces `crystal-aura-thm` on Minecraft 26.2. The internal name is `thmcrystal-aura`, preserving Meteor's own aura and its class lookups. The old THM implementation is removed; its settings are not transferred into this different algorithm.

## Source and license

Ported from [Lambda's 1.21.11 Crystal Aura](https://github.com/lambda-client/lambda/tree/e8eb261c9d605cc2312ea05063d0cb74d005ce46/src/main/kotlin/com/lambda/module/modules/combat/crystalaura), commit `e8eb261c9d605cc2312ea05063d0cb74d005ce46`. Lambda's copyright and GPL-3.0-or-later notice remain in the ported source. The four Kotlin files are combined into one Java Meteor module; Lambda's automation framework is replaced with existing THM/Meteor APIs.

Support placement is adapted from [Meteor CrystalAura](https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/CrystalAura.java), using the installed 26.2-SNAPSHOT source. It keeps Lambda's single-target scoring and THM's inventory handling.

## Behavior

- Select a target by distance, health, or field of view, using configurable entity types and named/tamed/owned filters.
- Score existing crystals and obsidian/bedrock placement bases by target damage or target-minus-self damage.
- Enforce minimum target damage, maximum self damage, and remaining-health limits. Prefer usable opportunities and clear crystals blocking a placement.
- Use millisecond placement/attack delays, normal or legacy two-air-block placement checks, configurable hand selection, hotbar/inventory retrieval, and silent slot restoration.
- Optional support follows Meteor: prefer existing bases, otherwise score replaceable positions with simulated obsidian and place the best safe base. Support is off by default and uses hotbar/offhand obsidian. `support-delay` defaults to one tick; zero attempts the crystal immediately after successful client prediction. Failed support attempts retry after 500 ms.
- Prediction modes: **None**, **Packet**, **Deferred**, **Tick**, and **Mixed**. None is the default. Packet modes use client-thread spawn events; deferred modes guess IDs immediately after placement while recent spawn information is valid.
- Ticked evaluation is the default. Optional Async mode schedules bounded client-thread work with update-delay and per-frame limits; worker threads never read or modify the world.
- Draw the recent placement base with THM's purple outline and translucent fill through THM's rendering helper. Debug reports observed crystal removals over three seconds.

## Port adaptations

The port enforces placement and attack ranges at execution, rechecks damage after queued rotations, blocks guesses that already identify a non-crystal entity, honors THM's eating/inventory priorities, and restores the actual prior server slot. Meteor rotations and line rendering replace Lambda's automation and line-width configuration. Packet spawn handling attacks the received safe crystal when requested; it does not reproduce Lambda's misleading received-crystal description while only attacking incremented IDs. Known upstream placeholders and hard-coded developer target exclusions are omitted.

Friends and ignored THM members are excluded as targets. This does not guarantee that nearby friends receive no collateral explosion damage.

## In-game validation needed

Check placement and breaking with each hand, inventory refill, rotation competition, support placement and rollback, zero/one-tick support delay, missing obsidian, blocked bases, range limits, self-damage prevention, each prediction mode, ticked/async timing, eating pauses, and disconnect/reconnect. Check the gradient and tooltip display on both OpenGL and Vulkan. Builds and unit tests do not establish anticheat compatibility or server acceptance.
