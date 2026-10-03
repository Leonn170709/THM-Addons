#!/usr/bin/env bash
#
# This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
# Copyright (c) THM Addons contributors. Credit the devs, keep the link.
# By using this code you agree to the license terms and to keep your repo public.
#

set -euo pipefail
cd "$(dirname "$0")/../.."
command -v glslangValidator >/dev/null || { echo "Install glslangValidator to check shaders." >&2; exit 1; }
python3 - <<'PYTHON'
from pathlib import Path
import re
import subprocess
import tempfile

shaders = [(p.name, "frag", p.read_text()) for p in Path("src/main/resources/assets/thm-addon/shaders").glob("*.fsh")]
for path in Path("src/main/java/xyz/thm/addon/shaders").glob("*.java"):
    for match in re.finditer(r'private static final String (\w+)_SRC = """\n(.*?)\n\s*""";', path.read_text(), re.S):
        shaders.append((path.stem + "_" + match[1], "vert" if match[1] == "VSH" else "frag", match[2]))
failures = []
with tempfile.TemporaryDirectory(prefix="thm-shaders-") as directory:
    root = Path(directory)
    for name, stage, source in shaders:
        for backend in ("OpenGL", "Vulkan"):
            # Minecraft's Vulkan compiler maps this built-in to gl_VertexIndex.
            text = source.replace("gl_VertexID", "gl_VertexIndex") if backend == "Vulkan" else source
            path = root / (name + "." + stage)
            path.write_text(text)
            args = ["glslangValidator", "-S", stage]
            if backend == "Vulkan":
                args += ["-V", "--auto-map-bindings", "--auto-map-locations", "-o", str(root / (name + ".spv"))]
            result = subprocess.run(args + [str(path)], capture_output=True, text=True)
            if result.returncode:
                failures.append(f"{name} ({backend}):\n{result.stdout}{result.stderr}")
print(f"{len(shaders)} shader sources, {len(shaders) * 2} checks, {len(failures)} failures")
for failure in failures:
    print(failure)
raise SystemExit(bool(failures))
PYTHON
