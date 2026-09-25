<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Highway Settings Guide

This public guide explains the user-facing settings in `THM-HighwayBuilder` (`HighwayBuilderTHM`), `THM Highway Monitor` (`THMHwyMonitor`), and the Highway Profiles section of the THM Addon tab.

## Highway Profiles

Highway Profiles let you keep separate complete HighwayBuilder configurations for `None`, `HighwayBuilding`, and `HighwayDigging`.

1. Open the **THM Addon** tab.
2. Select a value under **Highway Profiles**.
3. Press **Apply Profile**.

Applying a different profile saves the complete current HighwayBuilder configuration under the profile you are leaving before loading the selected profile. Changes made while using a profile are restored the next time you return to it. Profile snapshots persist with the THM Addon configuration.

For users upgrading from an earlier version, the existing HighwayBuilder configuration becomes the `None` profile baseline. Selecting or applying `None` does not force preset values over those settings.

The first time `HighwayBuilding` or `HighwayDigging` is applied, it starts from the current settings and applies these public seed values:

| Profile | Initial public seed values |
| --- | --- |
| `None` | No forced changes; preserves the current HighwayBuilder configuration. |
| `HighwayBuilding` | Width `5`, height `3`, `Replace` floor, railings and mine-above-railings enabled, Obsidian placement, Highway Monitor management when Baritone is installed, plus the proven paving setup: THM speed `4.98`, `blocks-per-tick` `6.96`, `save-pickaxes` `0`, `place-range` `5.4`, `placements-per-tick` `1.0`, `break-speed-multiplier` `1.465`, egap food restock at `14`, ender-chest search on with `11` saved, stats webhook+API on, KitBot restock with `6` ender chests (KitBotThenEnderChest). |
| `HighwayDigging` | Width `5`, height `4`, `Replace` floor, railings and mine-above-railings enabled, Netherrack/Basalt/Blackstone/Soul Soil placement, and Highway Monitor management enabled when Baritone is installed. |

After that first seed, each profile loads its own saved values rather than reapplying defaults. The `toggle-modules` setting defaults to `true`; when enabled, **Apply Profile** also activates HighwayBuilder if it is currently off.

The tabbed HighwayBuilder screen can also save the current settings as a named custom preset. Each preset is stored as `<Name>.json` under `meteor-client/thm/highway-builder-presets`, loaded when HighwayBuilder is registered during Minecraft startup, and applied from the **Custom preset** selector with its **Apply** button. Imported files are size- and depth-limited and may contain only known HighwayBuilder groups and settings.

Restock and KitBot enclosures use Netherrack. With `offhand-build`, it is temporarily moved into the offhand and the previous item is restored afterwards; otherwise it is moved to the hotbar and placed with the normal main-hand swap. Enclosure blocks are placed normally, one at a time, even when Packet Build is enabled.

## Quick Cheat Sheet

### Basic Highway Shape

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `width` | `5`, range `1-7` | Highway floor width. |
| `height` | `3`, range `2-5` | Vertical clearance to mine. |
| `floor` | `Replace`; options `Replace`, `PlaceMissing` | Use `Replace` for normal rebuilding; use `PlaceMissing` when you only want missing floor blocks filled. |
| `railings` | `true` | Builds side rails. |
| `corner-support-block` | `false`; shown when `railings` is on | Adds blocks under railings to avoid air placement. |
| `mine-above-railings` | `true` | Clears blocks above the rail lines. |

### Movement, Speed, And Safety

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `center-mode` | `Teleport`; options `Teleport`, `Walk` | Chooses how the module recenters before continuing. |
| `use-thm-speed` | `false` | Lets HighwayBuilder own horizontal movement speed while active. |
| `highway-speed` | `5.0`, range `1.0-6.0`; shown when `use-thm-speed` is on | Forward/backward speed used by THM speed control. |
| `pause-on-lag` | `false` | Throttles actions from TPS and pauses below 10 TPS. |
| `tps-safety-enclosure` | `true` | Builds a small safety enclosure during confirmed low/unknown TPS pauses. |
| `destroy-crystal-traps` | `true` | Uses a bow to safely defuse crystal traps from range. |
| `manage-thm-highway-monitor` | `true`; shown only when Baritone is installed | Lets HighwayBuilder toggle/manage THM Highway Monitor. |
| `fall-save-air-place` | `false`; shown when `manage-thm-highway-monitor` is on | Places safety blocks while descending at least 0.25 below the managed paving or digging level with support missing. |
| `fall-save-distance` | `3`, range `3-5`; shown when fall-save is on | How far below the hitbox fall-save can place. |
| `autosetup-modules` | `true` | Configures Meteor Speed Mine, Reach, Velocity, and HighwayBuilder place range for highway work. |
| `toggle-perspective` | `true` | Switches to third person while active, then restores the old camera. |
| `toggle-hud` | `true` | Toggles the highway HUD integration. |

### Digging

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `double-mine` | `true` | Uses normal mine and packet mine together when applicable. |
| `fast-break` | `true`; shown when `double-mine` is on | Finishes double-mined blocks faster. |
| `blocks-per-tick` | `7`, range `1-30`, slider max `20` | Maximum instant-break mining throughput, including fractional values. |
| `adaptive-mining` | `false` | Drops blocks-per-tick by 3 when a broken block comes back (the server refused the break; min `1`), raises it 1 per 10 stable seconds up to `29`, staying one step below the last rate that failed (retried after 5 calm minutes). Starts from `blocks-per-tick`. The rate slider shows the current value live, and moving it by hand restarts adaptive from there. |
| `break-delay` | `0`, minimum `0` | Delay between normal break actions. |
| `dont-break-tools` | `false` | Stops using tools before they break. |
| `save-pickaxes` | `1`, range `0-36`; shown when `dont-break-tools` is off | Pickaxe reserve that triggers restock or shutdown. |
| `ignore-signs` | `false` | Preserves signs instead of mining them. |
| `break-advertisement-signs` | `true`; shown when `ignore-signs` is off | Only breaks signs that look like ads/invites. |
| `packet-borer` | `false` | Sends extra instant-break packets across the highway shape. |

### Paving

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `blocks-to-place` | `Obsidian`; full-cube blocks only | Blocks the builder may place. |
| `placements-per-tick` | `1.5`, range `0.1-100`, slider `0.1-10`, one decimal place | Maximum averaged place throughput; `1.5` bursts 1-2-1-2 blocks per tick for 30 blocks/s, `0.1` performs about one placement every 10 ticks. |
| `adaptive-placements` | `false` | Drops the place rate by 0.5 on a rubberband or a server-reverted placement (min `0.5`), raises it 0.1 per 10 stable seconds up to `3`, staying one step below the last rate that failed (retried after 5 calm minutes). Starts from `placements-per-tick`; ignored with `packet-build`. The rate slider shows the current value live, and moving it by hand restarts adaptive from there. |
| `place-range` | `4.5`, slider max `5.5` | Maximum block placement reach. |
| `place-delay` | `0`, minimum `0` | Delay between place actions. |
| `packet-build` | `false` | Places with raw packets only: the client never sets a block itself, so what you see is always the server's state (no ghost blocks). |
| `air-place-mode` | `Never`; options `Never`, `Smart`, `Always`; shown when `packet-build` is on | Controls packet-build air placement. |
| `packet-build-lookahead` | `true`; shown when `packet-build` is on | Also places upcoming rows in the same tick. |
| `silent-forward-place-swap` | `true`; hidden in legacy mode | Restores your selected slot after scheduler placement. |
| `silent-forward-tool-swap` | `true`; hidden in legacy mode | Restores your selected slot after scheduler mining. |

### Experimental

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `enable-experimental` | `false` | Master switch: nothing else in this group does anything while it is off. |
| `packet-budget` | `false` | Caps mining and placing per tick so the tick stays under the server's packet limit; Packet Build gives unused mining capacity to lookahead placement. |
| `packets-per-tick` | `23`, range `4-200`; shown when `packet-budget` is on | Packets one tick may send. |
| `packet-build-once` | `false`; shown when `packet-build` is on | Experimental: one packet per block instead of one per block per tick. |
| `packet-build-resend` | `20`, range `2-200`; shown when `packet-build-once` is on | Ticks before asking the server what is at a block it never answered for. |
| `predictive-echest-rebreak` | `false`; shown for offhand OnPlace rebreak | Sends three STOP packets per tick to try to break each EChest sooner. |
| `ghost-block-check` | `false`; shown when `check-behind` is on | Has the server confirm the row behind before moving on. |

### Inventory And Restock

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `protected-items` | Ender chest, obsidian, netherite tools, elytra, totem, egaps, XP bottles | Items trash cleanup must never throw out. |
| `food-restock` | `false` | Restocks one configured food stack when food reaches the saved amount. |
| `food-management` | `None`; options `None`, `Auto Eat`, `Auto Gap` | Keeps the selected Meteor food module enabled while building. |
| `food-types` | Empty list; shown when food restock or food management is active | Food items counted for restock/food management. Multiple allowed — selection order is priority, top item preferred (its max stack size is used for restock math). Its own picker has Up/Down buttons to reorder. |
| `save-food` | `16`, range `1-32`; shown when `food-restock` is on | Food count threshold that queues restock. |
| `minimum-empty-slots` | `1`, minimum `0`, slider `0-9` | Empty inventory slots to preserve after mining obsidian. |

### Ender Chests

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `mine-ender-chests` | `true` | Mines ender chests to convert them into obsidian. |
| `save-ender-chests` | `4`, range `4-64` | Loose ender chest reserve to keep in inventory. |
| `break-mode` | `Instant rebreak on place`; shown when `mine-ender-chests` and `offhand-build` are on | How a placed chest is broken again: `Speedmine rebreak`, `Instant rebreak`, `Instant rebreak on place` or `Normal breaking`. |
| `break-mode-no-offhand` | `Speedmine rebreak`; shown when `mine-ender-chests` is on and `offhand-build` is off | Same choice without the on-place mode, which needs the chest in the offhand. |
| `rebreak-delay` | `0`, slider max `20`; shown for `Instant rebreak` | Ticks between rebreak packets. |
| `silent-rebreak-swap` | `true`; shown unless the mode is `Normal breaking` | Restores your selected slot after a rebreak or chest placement. |
| `predictive-echest-replace` | `true`; shown for offhand OnPlace rebreak | Places the next offhand EChest immediately after the rebreak packet. |
| `use-break-speed-multiplier` | `true`; shown when `mine-ender-chests` is on | Temporarily boosts Timer while mining ender chests. |
| `break-speed-multiplier` | `1.5`, range `1-3`; shown when the boost is on | How much Timer is boosted. |

### KitBot Updates

| Setting | Default / Options | What to change it for |
| --- | --- | --- |
| `kitbot-update-on-finish` | `true` | Sends `$update` to KitBot1 with the current direction when the module finishes, then disconnects. |
| `kitbot-periodic-update` | `true` | Sends `$update` every 60 minutes while building, deferred during restock. |

### Highway Monitor Recovery

| Setting | Default / Range | What to change it for |
| --- | --- | --- |
| `auto-recover` | `true` | Enables automatic monitor corrections while HighwayBuilder is active. |
| `true-center-mode` | `true` | Uses 0.5-centered highway math. |
| `check-interval` | `2`, range `1-20` | How often alignment is checked. |
| `max-correction-distance` | `10.0`, range `0.5-32.0` | Largest automatic horizontal correction allowed. |
| `repair-misalignments` | `false` | Steps back 2 blocks first so the builder can repair possible bad paving/digging. |
| `recover-forward-stalls` | `true`; shown when `auto-recover` is on | Escapes Forward/Center stalls with a forced backstep. |
| `recover-rubberband-ghostblocks` | `true`; shown when `auto-recover` is on | Uses disconnect/reconnect recovery for long rubberband or ghostblock stalls. |



## Full Manual

### THM-HighwayBuilder: General

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `width` | `5`, range `1-7` | Always | Sets the highway floor width. |
| `height` | `3`, range `2-5` | Always | Sets the vertical tunnel clearance. |
| `floor` | `Replace`; options `Replace`, `PlaceMissing` | Always | `Replace` rebuilds floor blocks; `PlaceMissing` only fills gaps. |
| `railings` | `true` | Always | Builds railings alongside the highway. |
| `corner-support-block` | `false` | `railings` is on | Places support blocks under railings to avoid air placement requirements. |
| `mine-above-railings` | `true` | Always | Mines blocks above railings so the side lanes stay clear. |
| `rotation` | `None`; options `None`, `Mine`, `Place`, `Both` | Always | Rotates for mining, placing, both, or neither. |
| `center-mode` | `Teleport`; options `Teleport`, `Walk` | Always | Chooses how HighwayBuilder recenters when it needs to line up before continuing. |
| `use-thm-speed` | `false` | Always | Gives HighwayBuilder ownership of horizontal speed while it is running. |
| `highway-speed` | `5.0`, range `1.0-6.0` | `use-thm-speed` is on | Sets THM-controlled forward and backward speed. |
| `legacy-mode` | `false` | Always | Uses the older forward path instead of the rolling row scheduler. |
| `disconnect-on-toggle` | `true` | Always | Disconnects automatically when the module turns itself off, such as when blocks run out. |
| `pause-on-lag` | `false` | Always | Throttles mining/placing from TPS and pauses below 10 TPS. |
| `destroy-crystal-traps` | `true` | Always | Uses a bow to defuse crystal traps safely from distance. |
| `manage-thm-highway-monitor` | `true` when Baritone is installed | Baritone is installed | Lets HighwayBuilder enable and manage THM Highway Monitor while active. |
| `fall-save-air-place` | `false` | `manage-thm-highway-monitor` is on | Places one safety block per tick while the player remains below the managed operating level, is still descending by position and velocity, and has no direct support. Turning monitor management off also turns fall-save off. |
| `fall-save-distance` | `3`, range `3-5` | Monitor management and `fall-save-air-place` are on | Vertical distance below the hitbox used by fall-save placement. |
| `autosetup-modules` | `true` | Always | Automatically configures Meteor Speed Mine, Reach, Velocity, and HighwayBuilder place range for highway work. |
| `packet-mode` | `false` | Always | Enables Packet Build and Packet Borer, leaving already-enabled pieces alone. |
| `check-behind` | `true` | Always | Repairs missing floor or railings behind the player, every row within `place-range` (Forward scheduler; legacy mode checks one row). |
| `ghost-block-check` | `false` | `enable-experimental` and `check-behind` are on | Before moving into the next row, has the server confirm the floor/railings of the row behind (one use-on-block packet per two blocks, needs an empty hand, pickaxe or totem). A ghost block turns into a hole client-side and is re-placed first. Forward scheduler only. |
| `advertise` | `false` | Always | Sends THM advertisement messages in chat. |
| `advertise-interval` | `5`, range `1-60` minutes | `advertise` is on | Delay between advertisement messages. |
| `toggle-perspective` | `true` | Always | Switches to third person while active and restores the previous perspective afterward. |
| `toggle-hud` | `true` | Always | Toggles the highway HUD support for the module. |

### THM-HighwayBuilder: Digging

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `instamine-bypass` | `false` | Always | Uses old-style breaking for basalt/blackstone override blocks so double mine and fast break only bypass when truly instamineable. |
| `double-mine` | `true` | Always | Mines with normal mine and packet mine together when applicable. |
| `fast-break` | `true` | `double-mine` is on | Speeds up finishing blocks while double mining. |
| `dont-break-tools` | `false` | Always | Stops using tools before they break. |
| `durability-percentage` | `2`, range `1-100` | `dont-break-tools` is on | Tool durability percentage where the module stops using that tool. |
| `save-pickaxes` | `1`, range `0-36` | `dont-break-tools` is off | Pickaxe reserve that triggers restock or module shutdown when reached. |
| `restock-pickaxes-amount` | `1`, range `1-36`, slider `1-9` | `dont-break-tools` is off | How many pickaxes to pull during each pickaxe restock task. |
| `break-delay` | `0`, minimum `0` | Always | Delay in ticks between break actions. |
| `blocks-per-tick` | `7`, range `1-30`, slider max `20` | Always | Maximum instant-break mining actions per tick; fractional values are averaged over time. |
| `adaptive-mining` | `false` | Always | Auto-tunes blocks-per-tick between `1` and `29` from broken blocks the server puts back. |
| `ignore-signs` | `false` | Always | Preserves signs by not mining them. |
| `break-advertisement-signs` | `true` | `ignore-signs` is off | Only breaks signs that look like advertisements or invites. |
| `packet-borer` | `false` | Always | Sends instant-break packets around the full highway shape every tick, similar to Packet Build for placing. |
| `mine-lookahead` | `true` | Always | Spends unused mine actions on reachable blocks in upcoming rows. |

### THM-HighwayBuilder: Paving

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `blocks-to-place` | `Obsidian`; full-cube blocks only | Always | Blocks HighwayBuilder is allowed to place. |
| `place-range` | `4.5`, slider max `5.5` | Always | Maximum distance for block placement. |
| `place-delay` | `0`, minimum `0` | Always | Delay in ticks between place actions. |
| `tps-safety-enclosure` | `true` | Always | Builds a small enclosure during confirmed low or unknown TPS pauses after TPS settling. |
| `packet-build` | `false` | Always | Ordinary highway placements use raw place packets and appear only after the server's block update. Restock/KitBot enclosures and block-entity containers use normal placement. It removes the per-tick placement cap for highway paving and automatically enables Packet Limiter on activation. |
| `air-place-mode` | `Smart`; options `Never`, `Smart`, `Always` | `packet-build` is on | `Never` skips no-face placements, `Smart` packet-air-places only when needed, and `Always` allows air placement. |
| `packet-build-lookahead` | `true` | `packet-build` is on | Lets Packet Build place blocks from upcoming rows in the same tick. |
| `packet-build-once` | `false` | `enable-experimental` and `packet-build` are on | Experimental: sends one packet per block. Without it ordinary face placements may be retried every tick; air placements wait for a server reply to avoid stacking blocks. |
| `packet-build-resend` | `20`, range `2-200` | `packet-build-once` is on | How long to wait before asking the server for that block's real state (a silent use-on-block with an empty hand, pickaxe or totem). The place itself is only repeated once the server says the spot is still air, so air-place can never stack a second block on top. |
| `silent-forward-place-swap` | `true` | `legacy-mode` is off | Silently swaps to placement blocks for scheduler work, then restores your selected slot. |
| `silent-forward-tool-swap` | `true` | `legacy-mode` is off | Silently swaps to scheduler mining tools, then restores your selected slot. |
| `placements-per-tick` | `1.5`, range `0.1-100`, slider `0.1-10`, one decimal place | Always | Maximum averaged placement rate; `1.5` bursts 1-2-1-2 blocks per tick for 30 blocks/s, `0.1` performs about one placement every 10 ticks. |
| `adaptive-placements` | `false` | Always | Auto-tunes the place rate between `0.5` and `3` from rubberbands and reverted placements. |

### THM-HighwayBuilder: Inventory

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `protected-items` | Ender chest, obsidian, netherite pickaxe/sword/shovel/axe, elytra, totem, enchanted golden apple, XP bottle | Always | Items trash cleanup must never throw out; everything else can be treated as trash. |
| `food-restock` | `false` | Always | Restocks one configured food stack when valid food count drops to the saved amount. |
| `food-management` | `None`; options `None`, `Auto Eat`, `Auto Gap` | Always | Keeps the selected Meteor food module enabled while HighwayBuilder is running. |
| `food-types` | Empty list, any number of items | `food-restock` is on or `food-management` is not `None` | Food items counted for restock and food module setup. Multiple can be selected; list order is priority (top = highest), used for the restock max-stack-size math. Picker screen has Up/Down/remove buttons per row instead of a plain checklist. |
| `save-food` | `16`, range `1-32` | `food-restock` is on | Restock threshold for the configured food count. |
| `keep-trash-block-stacks` | `1`, range `1-10` | Always | Number of trash block stacks to keep before dropping the rest. |
| `inventory-delay` | `3`, minimum `0` | Always | Delay in ticks between inventory interactions. |
| `eject-useless-shulkers` | `true` | Always | Drops shulkers that do not contain protected items, place blocks, pickaxes, or food. |
| `search-ender-chest` | `false` | Always | Searches your ender chest for usable items. |
| `search-shulkers` | `true` | Always | Searches shulker contents for usable items. |
| `Manage-hotbar` | `true` | Always | Automatically sorts the hotbar. |
| `Anti-drop` | `false` | Always | Prevents dropping items the module considers needed. |
| `offhand-build` | `true` | Always | Mines with the pickaxe in hand and places your block from the offhand. AutoTotem is paused and Meteor Offhand cannot swap the slot while building; the builder puts a totem there at low health or with an enemy near. While ender chests are being mined the offhand holds the chests instead, so a chest can be placed and mined in the same tick without swapping; the offhand returns to your placement block afterwards. |
| `Anti-hunger` | `true` | Always | Turns Meteor's AntiHunger on while building and off again when it stops. If you already had it on, it is left alone. |
| `minimum-empty-slots` | `1`, minimum `0`, slider `0-9` | Always | Empty inventory slots to preserve after obsidian mining. |

### THM-HighwayBuilder: Ender Chests

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `mine-ender-chests` | `true` | Always | Mines ender chests to create obsidian. |
| `save-ender-chests` | `4`, range `4-64` | Always | Loose ender chests to reserve; falling one below this queues restock, and failure to replenish can hard-fail the module. |
| `break-mode` | `Instant rebreak on place`; options below | `mine-ender-chests` and `offhand-build` are on | Picks one breaking method for the mining cycle. **Speedmine rebreak**: breaks with THM Speedmine, turning its `auto-rebreak` on for the cycle and putting your own setting back afterwards. **Instant rebreak**: after placing a chest, sends a sequenced `STOP_DESTROY_BLOCK` plus a swing (the same shape Meteor's InstantRebreak uses); the server breaks it at once when its stored mining progress for that spot is far enough along, otherwise it records the attempt and finishes the block itself a few ticks later. **Instant rebreak on place**: sends the same packet immediately after a successful normal placement, then retries it while the chest remains visible instead of falling back to a normal break on the next tick. With `offhand-build`, the pickaxe stays in the main hand. **Normal breaking**: plain mining, no packet tricks. |
| `break-mode-no-offhand` | `Speedmine rebreak` | `mine-ender-chests` is on, `offhand-build` is off | Same modes as `break-mode` minus **Instant rebreak on place**, which needs the chest in the offhand. It's a separate setting because a select can't hide a single option. |
| `rebreak-delay` | `0`, slider max `20` | Mode is `Instant rebreak` | Delay in ticks between rebreak packets. |
| `silent-rebreak-swap` | `true` | Mode is not `Normal breaking` | Restores your previously selected slot after a rebreak packet or a chest placement. |
| `predictive-echest-replace` | `true` | Offhand OnPlace mode | Places the next offhand EChest immediately after the rebreak packet. |
| `use-break-speed-multiplier` | `true` | `mine-ender-chests` is on | Temporarily boosts Timer while mining ender chests, then restores the previous Timer state. |
| `break-speed-multiplier` | `1.5`, range `1-3` | `mine-ender-chests` and `use-break-speed-multiplier` are on | Timer multiplier used during ender chest mining. |

### THM-HighwayBuilder: KitBot Updates

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `kitbot-update-on-finish` | `true` | Always | Sends `$update` to KitBot1 with the current highway direction when the module finishes, waits for KitBot to teleport, then disconnects. |
| `kitbot-periodic-update` | `true` | Always | Sends `$update` to KitBot1 every 60 minutes while building without stopping; delayed until restock completes. |

### THM-HighwayBuilder: Experimental

| Setting | Default / Range | Shown when | What it does |
| --- | --- | --- | --- |
| `enable-experimental` | `false` | Always | Master switch for this group: every setting below is ignored (and hidden) while it is off. |
| `predictive-echest-rebreak` | `false` | Experimental enabled, offhand OnPlace mode | Sends three rebreak STOP packets per tick. |
| `packet-budget` | `false` | `enable-experimental` is on | Caps mine and place actions each tick so their packet cost stays under `packets-per-tick`. Mining is served first; Packet Build gives unused mining capacity to lookahead placement. One mine action always goes through so the builder cannot stall. Counts start+stop per mined block, one use-on-block per placed block, movement, slot swaps, and swing packets unless PaketLimiter filters them. |
| `packets-per-tick` | `23`, range `4-200` | `packet-budget` is on | The tick's packet allowance. Set it below the server's own limit. |

### THM-HighwayBuilder: Debugging

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `debug` | `false` | Always | Writes state transitions and movement input to `logs/thm/<start-time>/highwaybuilder-debug.log`. |
| `forward-scheduler-debug` | `false` | `legacy-mode` is off | Logs active row, queue, boundary, and actionability details for the forward scheduler. |
| `statistics-debug` | `false` | Always | Logs detailed stats validation decisions for mine/place work. |
| `restock-debug-log` | `false` | Always | Writes blockade/restock diagnostics to `logs/thm/<start-time>/highwaybuilder-restock-debug.log` and EChest place/rebreak timing to `logs/thm/<start-time>/highwaybuilder-echest-debug.log`. |
| `session-summary` | `false` | Always | When the builder turns off, prints duration, distance, blocks placed (with average/s), broken, restocks, e-chest refills, rubberbands, adaptive drops and ghost blocks. |

### THM-HighwayBuilder: Render

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `render-reach` | `false` | Always | Outlines every non-air block within `place-range` through walls (white), with scheduler work in blue (ahead) and orange (behind). |
| `render-blocks-to-mine` | `true` | Always | Renders blocks selected for mining. |
| `blocks-to-mine-shape-mode` | `Both`; Meteor `ShapeMode` | Always | Controls whether mine targets render sides, lines, or both. |
| `blocks-to-mine-side-color` | RGBA `225,25,25,25` | Always | Fill color for mine target rendering. |
| `blocks-to-mine-line-color` | RGBA `225,25,25,255` | Always | Outline color for mine target rendering. |
| `render-blocks-to-place` | `true` | Always | Renders blocks selected for placement. |
| `blocks-to-place-shape-mode` | `Both`; Meteor `ShapeMode` | Always | Controls whether place targets render sides, lines, or both. |
| `blocks-to-place-side-color` | RGBA `25,25,225,25` | Always | Fill color for place target rendering. |
| `blocks-to-place-line-color` | RGBA `25,25,225,255` | Always | Outline color for place target rendering. |

### THM-HighwayBuilder: Logging

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `print-statistics` | `true` | Always | Prints HighwayBuilder statistics in chat when the module disables. |
| `auto-screenshot-statistics` | `false` | `print-statistics` is on | Captures a proof screenshot shortly after statistics print. |
| `Send-Status` | `true` | Always | Sends a status update every 5 minutes with digging/paving, axis, name, and API token. |
| `sends-statistics(Webhook)` | `false` | `print-statistics` is on | Sends HighwayBuilder statistics to a webhook when the module disables. |
| `webhook` | `MyWebhookInHere` | `print-statistics` and `sends-statistics(Webhook)` are on | Webhook URL used for statistics delivery. |
| `sends-statistics(API)` | `false` | `print-statistics` is on | Sends statistics to the API when the module disables. |

Every THM debug category also writes to `logs/thm/<start-time>/thm-debug-all.log`, with its source filename on each line. The start-time folder uses local `yyyy-MM-dd_HH.mm.ss` and is created when the first log line is written. Existing logs stay in `logs/thm/`. Logs rotate at 100 MB.

### THM-HighwayBuilder: Notifies

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `desktop-notifies` | `true` | Always | Enables desktop notifications while HighwayBuilder is running. |
| `disconnect` | `true` | `desktop-notifies` is on | Notifies when HighwayBuilder disconnects you. |
| `restock-issues` | `true` | `desktop-notifies` is on | Notifies when restocking fails because of materials, slots, or container issues. |
| `out-of-blocks` | `true` | `desktop-notifies` is on | Notifies when no placeable blocks are left. |
| `pickaxe-shortage` | `true` | `desktop-notifies` is on | Notifies when there are not enough pickaxes to continue. |

### THM Highway Monitor: General

| Setting | Default / Range / Options | Visible when | Behavior |
| --- | --- | --- | --- |
| `auto-recover` | `true` | Always | Auto-corrects misalignment while THM HighwayBuilder is active. |
| `true-center-mode` | `true` | Always | Uses 0.5-centered highway math for alignment and recovery. |
| `check-interval` | `2`, range `1-20`, slider `1-10` | Always | Tick interval between alignment checks while HighwayBuilder is active. |
| `max-correction-distance` | `10.0`, range `0.5-32.0`, slider `0.5-16.0` | Always | Maximum horizontal distance the monitor may correct automatically. |
| `repair-misalignments` | `false` | Always | During normal recovery, steps backward 2 blocks first to let HighwayBuilder repair possible misaligned paving or digging. |
| `recover-forward-stalls` | `true` | `auto-recover` is on | Runs monitor recovery if HighwayBuilder remains stuck in Forward or Center, including a forced 2-block backstep. |
| `forward-stall-timeout-seconds` | `20`, range `10-900`, slider `10-300` | `auto-recover` and `recover-forward-stalls` are on | Seconds without meaningful Forward progress or Center transition before forced stall escape begins. |
| `recover-rubberband-ghostblocks` | `true` | `auto-recover` is on | Disconnects and uses AutoReconnect when Forward appears rubberbanded or ghostblocked for too long. |
| `recovery-cooldown` | `10`, range `1-100`, slider `1-40` | Always | Ticks to wait before checking again after a recovery attempt. |
