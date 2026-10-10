<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Main Menu Background Shader — Requirements

To add a new animated background for the title screen / main-menu panorama, drop a `.fsh`
file into `src/main/resources/assets/thm-addon/shaders/<name>.fsh`. No Java/registration
code needed — `ShaderManager.availableShaders()` discovers it automatically via
`ResourceManager#findResources`, and it shows up in `shaderChoice`/`shaderPool` immediately.

## Required header

Every shader file must start with exactly this shape:

```glsl
#version 330 core
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

void main(void) {
    // ... your logic, must write to fragColor
    fragColor.a = 1.;
}
```

- **`#version 330 core`** must be line 1. If you need `#extension GL_OES_standard_derivatives`,
  drop it — desktop GLSL 330 already has `dFdx`/`dFdy`/`fwidth` as core, no extension needed
  (an `#extension` line placed after other declarations is illegal per spec and won't compile).
- **`ThmShaderData`** is the *only* uniform input available — no individual `uniform float time;`
  declarations. Blaze3D only binds whole uniform buffers, not per-scalar `glUniform` calls.
  Field names/order/types must match exactly (`float time; vec2 mouse; vec2 resolution;`) —
  the Java side (`ShaderBackground.UNIFORM_BUFFER_SIZE = 32`, offsets 0/8/16) is std140-padded
  for this exact layout. Don't add/remove/reorder fields without updating that Java constant.
- **`fragColor`** (`out vec4`) is the required output variable name.
- Fragment shader only — there is no accompanying vertex shader to write; every background
  reuses vanilla's own attributeless `minecraft:core/screenquad` vertex shader, so the whole
  screen is a single full-viewport triangle and `gl_FragCoord` is the only per-pixel input.

## Porting a Shadertoy / RusherHack shader

Existing shaders in this repo are adapted from RusherHack (Shadertoy-style: `uniform float
iTime`/`iResolution`, `mainImage(out vec4, in vec2)`). The pattern used throughout:

```glsl
#define iTime time
#define iResolution resolution

// ... original mainImage(out vec4 fragColor, in vec2 fragCoord) body, unchanged ...

void main(void) {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.;
}
```

i.e. keep the original `mainImage` function body as-is, `#define` the Shadertoy uniform
names to the `ThmShaderData` field names, and add a thin `main()` wrapper that calls it and
forces alpha to `1.0`.

## Constraints

- No previous-frame / feedback texture support (no ping-pong buffers) — a shader needing the
  last frame as input (e.g. `lines.fsh`, RusherHack) can't be ported; drop it.
- No extra textures/samplers — `ThmShaderData` is the only bound resource.
- Keep it self-contained in one file (helper functions are fine, just no `#include`).
- A shader that fails to compile is caught and disabled automatically (falls back to the
  vanilla panorama for that pick) — check the client log for `[THM] Main-menu shader '<name>'
  failed to compile` for the driver's actual error if something doesn't work.

## Automated checks

Install `glslangValidator` on PATH (`glslang` on Arch, `glslang-tools` on Ubuntu), then run:

```bash
./gradlew checkShaders
```

`tools/scripts/check-shaders.sh` runs the same task. These JUnit tests also run with `test` and
`build` when the compiler is available. Local builds skip these checks without the compiler;
`checkShaders` and CI require it. CI installs the compiler before building.

`ShaderCompatibilityTest` discovers every background `.fsh` and pairs it with the actual
`minecraft:core/screenquad` vertex resource from the Minecraft dependency. It reads the compiled
`*_SRC` constants for inline blur and trip programs without initializing Minecraft classes.
Each complete program compiles and links under OpenGL GLSL and Vulkan 1.2 SPIR-V rules.
Vulkan uses automatic bindings/locations and Minecraft's vertex/instance ID defines.
The [glslang reference compiler](https://github.com/KhronosGroup/glslang) checks GLSL and stage linking.

Negative cases verify that invalid GLSL and incompatible vertex/fragment types fail on both
backends. Background SPIR-V is checked for unbound texture resources, including unused samplers
that OpenGL may remove. Missing shader sources and compiler errors fail the checks.
An unavailable compiler fails `checkShaders` and CI; local `test`/`build` skips the shader tests.
Results: `build/reports/tests/checkShaders/index.html`, or `build/reports/tests/test/index.html`
after `test`/`build`.

These are compiler/linker tests. They do not create a GPU context or validate driver behavior,
texture bindings, effect appearance, blur coordinates, or resize handling. In-game logs and
visual checks remain required for both active backends; confirm each backend in F3.
