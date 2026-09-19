<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Running the unit tests in IntelliJ

You don't need to install a plugin. IntelliJ IDEA (Community and Ultimate) already includes the
**JUnit** and **Gradle** plugins, and those give you the test runner window with a green/red tree
for each test.

## One-time check

1. **Settings → Plugins → Installed:** check that **JUnit** and **Gradle** are enabled. Both are on
   by default.
2. Open the project as a Gradle project (open the folder containing `build.gradle.kts`). After the
   Gradle sync, `src/test/java` shows up as a green test folder.
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle → Run tests using:** leave this
   on **Gradle** (the default). Gradle runs `generateApiEndpoints` before the tests, and the
   `GeneratedApiEndpointsTest` needs that. With "IntelliJ IDEA" selected it only works after one
   normal build.

## Run tests

| What | How |
| --- | --- |
| All tests | Right-click `src/test/java` → **Run 'All Tests'**, or in the **Gradle** tool window run **Tasks → verification → test**. |
| One test class | Click the green ▶ in the gutter next to `class XyzTest`. |
| One test method | Click the green ▶ next to that method. |
| One case of a parameterized test | Run the method, then right-click the case in the results tree → **Run**. |
| Re-run only failures | In the Run window, click **Rerun Failed Tests** (the ▶ with a red ✕). |
| Re-run on every change | In the Run window, toggle **Toggle auto-test** (circular arrow icon). |
| Coverage | ▶ gutter menu → **Run with Coverage**. It shows which lines the tests reach. |

## Reading the results

The **Run** window shows a tree of every test class and method:

- ✔ green: passed. ✖ red: failed. ⚠ yellow: error or ignored.
- Click a failed test to see the assertion message and stack trace on the right. **Click to see
  difference** opens a side-by-side comparison of expected and actual values.
- The two icons at the top of the tree show or hide passed and ignored tests. Hide the passed ones to
  see only what broke.

Outside the IDE, the same results are in `build/reports/tests/test/index.html` after running
`./gradlew test`. Open that file in a browser.

## What's covered

| Test | What it checks |
| --- | --- |
| `TrustedHttpTest` | SSRF guard: private, loopback and metadata addresses blocked; URL scheme and credential rules; logs never contain the API host or URL. |
| `GeneratedApiEndpointsTest` | Every encrypted API URL decrypts to the value in `secrets.properties` (or the example file). |
| `RangeUtilsTest` | Nuker-style reach: distance to the nearest point of the block, edges count. |
| `Vp8LDecoderTest` | WebP decoder: pixel-exact against libwebp for several images and the shipped icons; truncated or corrupted files never crash it. |
| `KitbotChatCommandParserTest` | `$goto`/`$update`/`$kit`/`$send`/`$token`/`$claim` parsing, errors, and tab-completion. |
| `AdaptiveRateTest` | Fractional per-tick rates (1.5 → 1-2-1-2 = 30/s, no float drift) and adaptive-placements step up/down. |
| `GhostBlockProbeTest` | Ghost-block check: packet pairing, verify, ghost and timeout handling. |

Tests run without a game, so they can't cover anything that needs a live world or Minecraft's
registries. Test that in the client.
