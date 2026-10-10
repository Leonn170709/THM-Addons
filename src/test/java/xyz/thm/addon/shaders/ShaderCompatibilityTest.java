/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.shaders;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Compiler and linker checks without a game or GPU context. */
class ShaderCompatibilityTest {
    @TempDir
    Path temporary;

    private record Program(String name, String vertex, String fragment) {}
    private record Result(int exitCode, String log, byte[] spirv) {}

    @BeforeAll
    static void requireCompiler() throws Exception {
        try {
            Process process = new ProcessBuilder("glslangValidator", "--version")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "glslangValidator timed out");
                assertEquals(0, process.exitValue(), "glslangValidator is unavailable");
            } finally {
                process.destroyForcibly();
            }
        } catch (IOException e) {
            String message = "Install glslangValidator and put it on PATH (Arch: glslang; Ubuntu: glslang-tools).";
            if (Boolean.getBoolean("thm.requireShaderCompiler")) fail(message, e);
            assumeTrue(false, "Shader checks skipped: " + message);
        }
    }

    @TestFactory
    Stream<DynamicTest> programsCompileAndLink() throws Exception {
        List<Program> programs = programs();
        return programs.stream().flatMap(program -> Stream.of(false, true).map(vulkan ->
            DynamicTest.dynamicTest(program.name + " / " + backend(vulkan), () -> {
                Result result = compile(program, vulkan);
                assertEquals(0, result.exitCode, result.log);
                if (vulkan && program.name.endsWith(".fsh")) assertNoTextureResources(result.spirv);
            })));
    }

    @Test
    void rejectsUnboundSamplerEvenWhenUnused() throws Exception {
        Result result = compile(new Program("unused-sampler",
            resource("/assets/minecraft/shaders/core/screenquad.vsh"), """
                #version 330 core
                uniform sampler2D Unbound;
                out vec4 fragColor;
                void main() { fragColor = vec4(1.0); }
                """), true);
        assertEquals(0, result.exitCode, result.log);
        assertThrows(AssertionError.class, () -> assertNoTextureResources(result.spirv));
    }

    private static void assertNoTextureResources(byte[] spirv) {
        ByteBuffer words = ByteBuffer.wrap(spirv).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x07230203, words.getInt(), "Invalid SPIR-V magic");
        words.position(20);
        while (words.hasRemaining()) {
            int instruction = words.getInt();
            int size = instruction >>> 16;
            assertTrue(size > 0 && (size - 1) * 4 <= words.remaining(), "Invalid SPIR-V instruction");
            int next = words.position() + (size - 1) * 4;
            // OpVariable's UniformConstant storage includes samplers and images, even unused ones.
            if ((instruction & 0xffff) == 59) {
                assertTrue(size >= 4, "Invalid OpVariable");
                words.getInt();
                words.getInt();
                assertNotEquals(0, words.getInt(), "Background pipeline does not bind samplers or images");
            }
            words.position(next);
        }
    }

    @TestFactory
    Stream<DynamicTest> compilerRejectsBrokenPrograms() throws Exception {
        String vertex = resource("/assets/minecraft/shaders/core/screenquad.vsh");
        List<Program> broken = List.of(
            new Program("invalid-syntax", vertex, "#version 330 core\nvoid main() { invalid GLSL; }"),
            new Program("varying-type-mismatch", vertex, """
                #version 330 core
                in vec3 texCoord;
                out vec4 fragColor;
                void main() { fragColor = vec4(texCoord, 1.0); }
                """)
        );
        return broken.stream().flatMap(program -> Stream.of(false, true).map(vulkan ->
            DynamicTest.dynamicTest(program.name + " / " + backend(vulkan), () -> {
                Result result = compile(program, vulkan);
                assertNotEquals(0, result.exitCode, "Broken shader passed: " + result.log);
                assertTrue(result.log.contains("ERROR"), result.log);
            })));
    }

    private List<Program> programs() throws IOException {
        List<Program> programs = new ArrayList<>();
        String screenquad = resource("/assets/minecraft/shaders/core/screenquad.vsh");
        try (var files = Files.list(Path.of("src/main/resources/assets/thm-addon/shaders"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fsh")).sorted().toList()) {
                programs.add(new Program(file.getFileName().toString(), screenquad,
                    resource("/assets/thm-addon/shaders/" + file.getFileName())));
            }
        }
        assertFalse(programs.isEmpty(), "No background shaders discovered");
        int backgrounds = programs.size();
        try (var files = Files.list(Path.of("src/main/java/xyz/thm/addon/shaders"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                ClassNode type = new ClassNode();
                // ConstantValue reads the shipped GLSL without initializing Minecraft classes.
                try (InputStream in = getClass().getResourceAsStream("/xyz/thm/addon/shaders/" + name + ".class")) {
                    assertNotNull(in, "Missing shader class: " + name);
                    new ClassReader(in).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                Map<String, String> sources = new LinkedHashMap<>();
                for (var field : type.fields) {
                    if (field.name.endsWith("_SRC")) {
                        assertInstanceOf(String.class, field.value, "Shader must be a constant: " + name + "." + field.name);
                        sources.put(field.name, (String) field.value);
                    }
                }
                if (sources.isEmpty()) continue;
                String vertex = sources.remove("VSH_SRC");
                assertNotNull(vertex, "Missing vertex shader: " + name);
                assertFalse(sources.isEmpty(), "Missing fragment shaders: " + name);
                for (var entry : sources.entrySet()) {
                    assertTrue(entry.getKey().endsWith("FSH_SRC"), "Unknown shader stage: " + entry.getKey());
                    programs.add(new Program(name + "." + entry.getKey(), vertex, entry.getValue()));
                }
            }
        }
        assertTrue(programs.size() > backgrounds, "No inline shaders discovered");
        return programs;
    }

    private Result compile(Program program, boolean vulkan) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "shader-");
        Path vertex = directory.resolve("program.vert");
        Path fragment = directory.resolve("program.frag");
        Files.writeString(vertex, source(program.vertex, vulkan));
        Files.writeString(fragment, source(program.fragment, vulkan));
        List<String> command = new ArrayList<>(List.of("glslangValidator", "-l"));
        if (vulkan) command.addAll(List.of("-V", "--target-env", "vulkan1.2",
            "--auto-map-bindings", "--auto-map-locations", "-o", directory.resolve("program.spv").toString()));
        command.add(vertex.toString());
        command.add(fragment.toString());
        Path log = directory.resolve("compiler.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Shader compiler timed out: " + program.name);
            byte[] spirv = vulkan && process.exitValue() == 0 ? Files.readAllBytes(directory.resolve("program.spv")) : null;
            return new Result(process.exitValue(), Files.readString(log), spirv);
        } finally {
            process.destroyForcibly();
        }
    }

    private static String source(String source, boolean vulkan) {
        if (!vulkan) return source;
        // Match Minecraft's Vulkan compiler defines.
        int versionEnd = source.indexOf('\n');
        assertTrue(versionEnd >= 0, "Missing shader version line");
        return source.substring(0, versionEnd + 1)
            + "#define gl_VertexID gl_VertexIndex\n#define gl_InstanceID gl_InstanceIndex\n"
            + source.substring(versionEnd + 1);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ShaderCompatibilityTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "Missing shader resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String backend(boolean vulkan) {
        return vulkan ? "Vulkan" : "OpenGL";
    }
}
