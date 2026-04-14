# THM Addons for Meteor Client

THM Addons is a Meteor Client addon focused on highway automation, travel utilities, PvP tooling, and quality-of-life HUD widgets for Minecraft 1.21.11.

## Highlights
- Highway automation and monitoring with dedicated HUD support.
- Utility modules for inventory management, rendering, AFK safety, and performance control.
- PvP-focused modules grouped under a dedicated THM PVP category.
- Optional integrations for Discord webhooks and Rich Presence.
- [More Features](FEATURES.md)

## Requirements
- Minecraft `1.21.11`
- Fabric Loader `0.18.2`
- Meteor Client `1.21.11-SNAPSHOT`
- Java `21`

## Installation
1. Build the addon (see below) or obtain a prebuilt jar.
2. Place the jar in your Minecraft `mods` folder alongside Meteor Client.
3. Launch the game with Fabric.

## Building
1. Clone the repository.
2. In the repository root, run:
   ```bash
   ./gradlew build
   ```
3. The jar is created in `build/libs`.

## Features
A full module-by-module overview is available in `FEATURES.md`.

## Documentation
- `docs/highwaybuilder-stats-screenshot-simulation.md`
- `docs/hwymonitor-reconnect-simulation.md`

## Contributing
Issues and pull requests are welcome.

## License
Licensed under the GNU General Public License v3.0. See `LICENSE` for details.

## Credits
Thanks to Stainless and BepHax.
