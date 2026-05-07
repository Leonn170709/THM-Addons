# AGENTS.md

## Project
- Name: THM Addons for Meteor Client
- Language: Java 21
- Platform: Fabric + Meteor Client (Minecraft 1.21.11)
- Build tool: Gradle (`./gradlew`)
- Main addon entry: `src/main/java/xyz/thm/addon/THMAddon.java`

## Goal
Maintain and extend THM Addons with stable module behavior, clean Meteor integration, and safe defaults for PvP/highway utility workflows.

## Working Rules
- Keep changes minimal and targeted to the requested feature/fix.
- Do not remove or revert unrelated user changes.
- Preserve existing package layout under `xyz.thm.addon`.
- Follow current coding style (simple classes, explicit overrides, no unnecessary abstractions).
- Prefer deterministic behavior over implicit magic.

## Build & Validation
- Build:
```bash
./gradlew build
```
- Fast compile check:
```bash
./gradlew compileJava
```

## Theme System Notes
- Custom GUI themes live in `src/main/java/xyz/thm/addon/gui/themes`.
- Themes should:
- extend `MeteorGuiTheme`
- implement `RecolorGuiTheme`
- expose a static singleton `INSTANCE`
- be registered in `THMAddon#onInitialize()` via `GuiThemes.add(...)`
- Theme display name is controlled by `getName()` (see `GuiThemeMixin`).

## Module/HUD Registration Notes
- Register modules in `THMAddon#onInitialize()`.
- Register HUD elements through `Hud.get().register(...)`.
- Keep Baritone-dependent modules behind the existing `BaritoneUtils.IS_AVAILABLE` guard.

## Safety Checks Before Finishing
- Confirm code compiles (`./gradlew compileJava` at minimum).
- Verify new classes are imported or covered by wildcard imports.
- Ensure no broken references in `THMAddon`.

## Expected Change Summary Format
When reporting work:
- List changed files.
- Describe behavior impact.
- Mention validation commands executed and outcome.
