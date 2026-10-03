/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks target bytecode without bootstrapping Minecraft or launching a client. */
class MixinTargetsTest {
    private final Map<String, ClassNode> classes = new HashMap<>();

    @Test
    void configuredMixinMembersExist() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String resource : List.of("thm-addon.mixins.json", "thm-addon.sodium.mixins.json", "thm-addon.xaero.mixins.json")) {
            try (InputStream in = getClass().getResourceAsStream("/" + resource)) {
                var config = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                String base = config.get("package").getAsString().replace('.', '/');
                for (String group : List.of("mixins", "client")) {
                    if (!config.has(group)) continue;
                    for (var entry : config.getAsJsonArray(group)) {
                        String name = base + "/" + entry.getAsString().replace('.', '/');
                        ClassNode mixin = read(name);
                        AnnotationNode annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                        List<String> targets = new ArrayList<>();
                        for (Object type : list(value(annotation, "value"))) targets.add(((Type) type).getInternalName());
                        for (Object target : list(value(annotation, "targets"))) targets.add(target.toString().replace('.', '/'));
                        for (String targetName : targets) {
                            ClassNode target = read(targetName);
                            if (target == null) {
                                failures.add(name + ": missing target " + targetName);
                                continue;
                            }
                            for (var field : mixin.fields) {
                                for (var a : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
                                    if (a.desc.endsWith("/Shadow;") && !hasField(target, field.name, field.desc))
                                        failures.add(name + ": shadow " + field.name + field.desc);
                                }
                            }
                            for (MethodNode method : mixin.methods) {
                                for (var a : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                                    if (a.desc.endsWith("/Shadow;") && !hasMethod(target, method.name + method.desc))
                                        failures.add(name + ": shadow method " + method.name + method.desc);
                                    if (a.desc.endsWith("/Accessor;")) {
                                        String field = (String) value(a, "value");
                                        Type signature = Type.getMethodType(method.desc);
                                        String descriptor = signature.getArgumentTypes().length == 0
                                            ? signature.getReturnType().getDescriptor() : signature.getArgumentTypes()[0].getDescriptor();
                                        if (field == null || !hasField(target, field, descriptor))
                                            failures.add(name + ": accessor " + field + descriptor);
                                    }
                                    if (a.desc.endsWith("/Invoker;")) {
                                        String desc = value(a, "value").equals("<init>")
                                            ? method.desc.substring(0, method.desc.indexOf(')') + 1) + "V" : method.desc;
                                        if (!hasMethod(target, value(a, "value") + desc)) failures.add(name + ": invoker " + value(a, "value") + desc);
                                    }
                                    Object selectors = value(a, "method");
                                    if (selectors != null) {
                                        List<MethodNode> selected = new ArrayList<>();
                                        for (Object selector : list(selectors)) {
                                            var matching = target.methods.stream().filter(m -> matches(m, selector.toString())).toList();
                                            selected.addAll(matching);
                                            if (matching.isEmpty())
                                                failures.add(name + ": injection " + selector);
                                            if (a.desc.endsWith("/Inject;")) checkCallback(name, method, matching, failures);
                                        }
                                        checkReferences(name, a, failures);
                                        checkInstructions(name, a, selected, failures);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void checkCallback(String mixin, MethodNode handler, List<MethodNode> targets, List<String> failures) {
        Type[] args = Type.getArgumentTypes(handler.desc);
        int callback = 0;
        while (callback < args.length && !args[callback].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.")) callback++;
        if (callback == 0) return;
        for (MethodNode target : targets) {
            Type[] expected = Type.getArgumentTypes(target.desc);
            if (callback > expected.length) { failures.add(mixin + ": callback " + handler.name + " has too many target arguments"); continue; }
            for (int i = 0; i < callback; i++) if (!args[i].equals(expected[i]))
                failures.add(mixin + ": callback " + handler.name + " argument " + i + " expects " + expected[i] + " instead of " + args[i]);
        }
    }

    private static void checkInstructions(String mixin, AnnotationNode node, List<MethodNode> methods, List<String> failures) {
        if (node.desc.endsWith("/At;")) {
            String kind = (String) value(node, "value");
            String reference = (String) value(node, "target");
            if (reference != null && reference.startsWith("L") && (kind.startsWith("INVOKE") || kind.equals("FIELD"))) {
                int split = reference.indexOf(';');
                String owner = reference.substring(1, split), member = reference.substring(split + 1);
                int count = 0;
                for (MethodNode method : methods) for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode invoke && invoke.owner.equals(owner)
                        && (member.equals(invoke.name) || member.equals(invoke.name + invoke.desc))) count++;
                    if (instruction instanceof FieldInsnNode field && field.owner.equals(owner)
                        && member.equals(field.name + ":" + field.desc)) count++;
                }
                Integer ordinal = (Integer) value(node, "ordinal");
                if (count == 0 || (ordinal != null && ordinal >= count)) failures.add(mixin + ": unmatched instruction " + reference);
            }
        }
        if (node.values != null) for (int i = 1; i < node.values.size(); i += 2) {
            Object v = node.values.get(i);
            if (v instanceof AnnotationNode nested) checkInstructions(mixin, nested, methods, failures);
            if (v instanceof List<?> list) for (Object item : list)
                if (item instanceof AnnotationNode nested) checkInstructions(mixin, nested, methods, failures);
        }
    }

    private void checkReferences(String mixin, AnnotationNode annotation, List<String> failures) throws Exception {
        if (annotation.values == null) return;
        for (int i = 1; i < annotation.values.size(); i += 2) {
            Object v = annotation.values.get(i);
            if (v instanceof AnnotationNode nested) {
                if (nested.desc.endsWith("/At;")) {
                    String reference = (String) value(nested, "target");
                    if (reference != null && reference.startsWith("L") && reference.contains(";")) {
                        int split = reference.indexOf(';');
                        ClassNode owner = read(reference.substring(1, split));
                        String member = reference.substring(split + 1);
                        if (owner != null && member.contains("(") && !hasMethod(owner, member))
                            failures.add(mixin + ": reference " + reference);
                    }
                }
                checkReferences(mixin, nested, failures);
            } else if (v instanceof List<?> list) {
                for (Object item : list) if (item instanceof AnnotationNode nested) checkReferences(mixin, nested, failures);
            }
        }
    }

    private boolean hasMethod(ClassNode owner, String selector) throws Exception {
        if (owner.methods.stream().anyMatch(m -> matches(m, selector))) return true;
        if (owner.superName != null && read(owner.superName) != null && hasMethod(read(owner.superName), selector)) return true;
        for (String iface : owner.interfaces) if (read(iface) != null && hasMethod(read(iface), selector)) return true;
        return false;
    }

    private boolean hasField(ClassNode owner, String name, String descriptor) throws Exception {
        if (owner.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor))) return true;
        return owner.superName != null && read(owner.superName) != null && hasField(read(owner.superName), name, descriptor);
    }

    private static boolean matches(MethodNode method, String selector) {
        return selector.equals(method.name) || selector.equals(method.name + method.desc);
    }

    private ClassNode read(String name) throws Exception {
        if (classes.containsKey(name)) return classes.get(name);
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".class")) {
            ClassNode node = null;
            if (in != null) {
                node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            classes.put(name, node);
            return node;
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode node, String key) {
        if (node.values != null) for (int i = 0; i < node.values.size(); i += 2)
            if (node.values.get(i).equals(key)) return node.values.get(i + 1);
        return null;
    }

    private static List<?> list(Object value) {
        return value == null ? List.of() : value instanceof List<?> list ? list : List.of(value);
    }
}
