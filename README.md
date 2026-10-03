<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# THM Addons for Meteor Client

THM Addons is a Meteor Client addon focused on highway automation, travel utilities, PvP tooling, and quality-of-life HUD widgets. The `26.2` branch is being ported to Minecraft 26.2; the latest released 1.21.11 code remains on the `1.21.11` branch.

## Highlights
- Highway automation and monitoring with dedicated HUD support.
- Utility modules for inventory management, rendering, AFK safety, and performance control.
- PvP-focused modules grouped under a dedicated THM PVP category.
- Optional integrations for Discord webhooks and Rich Presence.
- [More Features](FEATURES.md)

## Requirements
- Minecraft `26.2`
- Fabric Loader `0.19.5`
- Meteor Client `26.2-SNAPSHOT`
- Java `25`

## Installation
1. Build the addon (see below) or obtain a prebuilt jar.
2. Place the jar in your Minecraft `mods` folder alongside Meteor Client, Fabric API, and Baritone.
3. Launch the game with Fabric.

## Building
1. Clone the repository.
2. In the repository root, run:
   ```bash
   ./gradlew build
   ```
3. The jar is created in `build/libs`.

The 26.2 code port builds and passes automated tests. This snapshot still needs in-game checks for gameplay, OpenGL, and Vulkan before release. Progress and test steps: [PORT_26_2.md](PORT_26_2.md).

## Features
A full module-by-module overview is available in `FEATURES.md`.

## Documentation
- `docs/highway-settings.md`
- `docs/highwaybuilder-stats-screenshot-simulation.md`
- `docs/hwymonitor-reconnect-simulation.md`

## Contributing
Issues and pull requests are welcome.

## License
Licensed under the GNU General Public License v3.0. See `LICENSE` for details.

## Credits
Thanks to Stainless and BepHax.
