/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.settings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DropdownDescriptionsTest {
    @Test
    void everyEnumSettingChoiceHasAShortDescription() throws Exception {
        Set<String> enumTypes = new HashSet<>();
        Pattern settingType = Pattern.compile("Lmeteordevelopment/meteorclient/settings/Setting<L([^;]+);>;");
        try (var paths = Files.walk(Path.of("build/classes/java/main/xyz/thm/addon"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassNode owner = new ClassNode();
                new ClassReader(Files.readAllBytes(path)).accept(owner, ClassReader.SKIP_DEBUG);
                boolean createsEnums = false;
                for (var method : owner.methods) {
                    for (var instruction : method.instructions) {
                        createsEnums |= instruction instanceof TypeInsnNode type
                            && type.desc.equals("meteordevelopment/meteorclient/settings/EnumSetting$Builder");
                    }
                }
                // Managed references to other modules are not addon dropdowns.
                if (!createsEnums) continue;
                for (var field : owner.fields) {
                    if (field.signature == null) continue;
                    var matcher = settingType.matcher(field.signature);
                    if (!matcher.matches()) continue;
                    String name = matcher.group(1);
                    try (var in = getClass().getResourceAsStream("/" + name + ".class")) {
                        ClassNode type = new ClassNode();
                        new ClassReader(in).accept(type, ClassReader.SKIP_CODE);
                        if ((type.access & Opcodes.ACC_ENUM) != 0) enumTypes.add(name);
                    }
                }
            }
        }

        assertFalse(enumTypes.isEmpty());
        for (String name : enumTypes) {
            for (Object option : Class.forName(name.replace('/', '.')).getEnumConstants()) {
                assertShort(DropdownDescriptions.description(option), name + ": " + option);
            }
        }
    }

    @Test
    void branchFiltersExplainDifferentChoices() {
        Set<String> descriptions = new HashSet<>();
        for (String branch : new String[] { "All", "Main", "PvP" }) {
            String description = DropdownDescriptions.providedString("show-branch", branch);
            assertShort(description, branch);
            descriptions.add(description);
        }
        assertEquals(3, descriptions.size());
    }

    @Test
    void dynamicChoicesAndDisabledEntriesStayConcise() {
        for (String setting : new String[] { "thm-cape", "shader" }) {
            String disabled = DropdownDescriptions.providedString(setting, "None");
            String selected = DropdownDescriptions.providedString(setting, "a".repeat(200));
            assertShort(disabled, setting);
            assertShort(selected, setting);
            assertNotEquals(disabled, selected);
        }
        assertNull(DropdownDescriptions.providedString("unrelated-setting", "None"));
        assertNull(DropdownDescriptions.description("unrelated-option"));
    }

    private static void assertShort(String description, String option) {
        assertNotNull(description, option);
        assertFalse(description.isBlank(), option);
        assertTrue(description.length() <= 80, option + ": " + description);
        assertTrue(description.split("\\s+").length <= 14, option + ": " + description);
    }
}
