/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Friend lists of other clients, for {@link xyz.thm.addon.modules.FriendsSyncModule}.
 *
 * <p>None of these are compile-time dependencies. Clients that can run in the same JVM as Meteor
 * (RusherHack, LiquidBounce, AnarchyClient, BleachHack) are reached through their own API by
 * reflection, so they are detected by class presence and their own code handles persistence. The
 * rest are read from their friend files, and only count when their jar in mods/ is enabled.
 *
 * <p>Every accessor throws on failure — callers report the failure instead of silently syncing 0.
 */
public final class FriendClients {
    private FriendClients() {}

    private static final com.google.gson.Gson PRETTY = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    /**
     * {@code add} returns whether the name was really added — false for an already-known friend, or
     * one this client cannot take (Lambda stores UUIDs and can only take players it can resolve).
     * {@code add == null} means read-only: we can read that client's list but not write to it.
     */
    public record Source(String name, BooleanSupplier detect, Supplier<List<String>> read, Predicate<String> add) {
        public boolean installed() {
            try {
                return detect.getAsBoolean();
            } catch (Throwable t) {
                return false;
            }
        }

        /** Whether friends can be written *into* this client, i.e. whether it has an Import button. */
        public boolean canReceive() {
            return add != null;
        }
    }

    public static final List<Source> ALL =
        List.of(rusherHack(), liquidBounce(), anarchyClient(), autismClient(), lambda(),
            boze(), wurst(), bleachHack(), mio(), future());

    // Lambda (the Fabric/Kotlin one) is the odd source out: FriendHandler stores a Set<UUID>, not
    // names, so both directions go through its own resolvers, which look the player up in the
    // current server's list or Lambda's profile cache. Anyone they can't resolve is skipped rather
    // than guessed at — that is why add() reports a boolean, so the counts stay honest. Lambda's
    // latestGameProfile is the one that would hit Mojang, and it's a suspend fun we can't call
    // from here anyway, so nothing on this path blocks the render thread.
    private static Source lambda() {
        String handler = "com.lambda.interaction.handlers.FriendHandler";

        return new Source("Lambda",
            () -> cls(handler) != null,
            () -> {
                List<String> names = new ArrayList<>();
                for (Object uuid : (Collection<?>) call(instance(handler), "getFriends")) {
                    String name = (String) call(instance(handler), "friendDisplayName",
                        new Class[]{java.util.UUID.class}, uuid);

                    // friendDisplayName falls back to the raw UUID when it can't resolve one.
                    if (!name.equals(uuid.toString())) names.add(name);
                }
                return names;
            },
            name -> {
                Object profile = call(instance(handler), "gameProfile", new Class[]{String.class}, name);
                if (profile == null) return false;

                return (Boolean) call(instance(handler), "befriend",
                    new Class[]{com.mojang.authlib.GameProfile.class}, profile);
            });
    }

    // Boze (addon-api 3.3, docs.boze.dev): dev.boze.api.client.FriendManager is entirely static,
    // and its addFriend already returns whether the name was really taken — the record's contract
    // exactly. The client owns persistence, same as every other in-JVM source here.
    private static Source boze() {
        String fm = "dev.boze.api.client.FriendManager";

        return new Source("Boze",
            () -> cls(fm) != null,
            () -> asNames(staticCall(fm, "getFriends")),
            name -> (Boolean) staticCall(fm, "addFriend", new Class[]{String.class}, name));
    }

    // Autism Client keeps friends on TeamsModule as plain names behind static helpers, and its own
    // addFriend already dedupes case-insensitively, persists via setValue and returns whether it
    // took the name — exactly the contract this record wants. It also prints a chat line per add,
    // so a big export is chatty; that's its behaviour, not something worth suppressing.
    private static Source autismClient() {
        String teams = "autismclient.modules.TeamsModule";

        return new Source("Autism Client",
            () -> cls(teams) != null,
            () -> asNames(staticCall(teams, "storedFriendNames")),
            name -> (Boolean) staticCall(teams, "addFriend", new Class[]{String.class}, name));
    }

    // AnarchyClient is a plain Fabric mod, so its live FriendManager is reachable and its own add()
    // persists correctly — no reason to touch config/anarchyclient-friends.txt ourselves.
    private static Source anarchyClient() {
        String main = "net.blockhost.anarchyclient.AnarchyClient";

        return new Source("AnarchyClient",
            () -> cls(main) != null,
            () -> asNames(call(staticField(main, "FRIENDS"), "friends")),
            name -> (Boolean) call(staticField(main, "FRIENDS"), "add", new Class[]{String.class}, name));
    }

    // Verified against org.rusherhack:rusherhack-api:1.21.11 — IRelationManager#getFriends/addFriend,
    // PlayerRelation#username.
    private static Source rusherHack() {
        return new Source("RusherHack",
            () -> cls("org.rusherhack.client.api.RusherHackAPI") != null,
            () -> {
                List<String> names = new ArrayList<>();
                for (Object relation : (Collection<?>) call(relations(), "getFriends")) {
                    names.add((String) call(relation, "username"));
                }
                return names;
            },
            name -> {
                if ((Boolean) call(relations(), "isFriend", new Class[]{String.class}, name)) return false;
                call(relations(), "addFriend", new Class[]{String.class}, name);
                return true;
            });
    }

    private static Object relations() {
        return staticCall("org.rusherhack.client.api.RusherHackAPI", "getRelationManager");
    }

    // LiquidBounce NextGen: FriendManager is a Kotlin object, `val friends` is a live mutable set of
    // FriendManager.Friend(name, alias).
    private static Source liquidBounce() {
        String fm = "net.ccbluex.liquidbounce.features.misc.FriendManager";

        return new Source("LiquidBounce",
            () -> cls(fm) != null,
            () -> {
                List<String> names = new ArrayList<>();
                for (Object friend : (Collection<?>) call(instance(fm), "getFriends")) {
                    names.add((String) call(friend, "getName"));
                }
                return names;
            },
            name -> {
                Object friend = newInstance(fm + "$Friend", new Class[]{String.class, String.class}, name, null);
                return (Boolean) call(call(instance(fm), "getFriends"), "add", new Class[]{Object.class}, friend);
            });
    }

    // <gamedir>/wurst/friends.json — a plain JSON array of names (Wurst7 FriendsList).
    private static Source wurst() {
        Path path = gameDir("wurst", "friends.json");

        return new Source("Wurst",
            () -> modJar("wurst") && Files.exists(path),
            () -> {
                List<String> names = new ArrayList<>();
                for (JsonElement element : JsonParser.parseString(read(path)).getAsJsonArray()) {
                    names.add(element.getAsString());
                }
                return names;
            },
            name -> {
                Set<String> merged = new LinkedHashSet<>();
                for (JsonElement element : JsonParser.parseString(read(path)).getAsJsonArray()) {
                    merged.add(element.getAsString());
                }
                if (!merged.add(name)) return false;

                JsonArray array = new JsonArray();
                merged.forEach(array::add);
                write(path, array.toString());
                return true;
            });
    }

    // BleachHack, either variant: upstream and Leon's 1.21.11 fork share the package, the
    // BleachHack.friendMang field and the whole FriendManager surface — they differ only in that
    // upstream lowercases names, which the case-insensitive dedupe in FriendsSyncModule absorbs.
    private static Source bleachHack() {
        String main = "org.bleachhack.BleachHack";

        return new Source("BleachHack",
            () -> cls(main) != null,
            () -> asNames(call(staticField(main, "friendMang"), "getFriends")),
            name -> {
                Object manager = staticField(main, "friendMang");
                if ((Boolean) call(manager, "has", new Class[]{String.class}, name)) return false;

                call(manager, "add", new Class[]{String.class}, name);

                // add() only touches the in-memory set — this is how BleachHack's own friend
                // GUI and /friends command get it written back out.
                call(staticField("org.bleachhack.util.io.BleachFileHelper", "SCHEDULE_SAVE_FRIENDS"),
                    "set", new Class[]{boolean.class}, true);
                return true;
            });
    }

    // <gamedir>/mio-fabric/socials.json — {"socials":[{"name":..,"role":"friend"}]}, and those two
    // fields are the whole entry (see clients/socials.json), so writing one is safe.
    private static Source mio() {
        Path path = gameDir("mio-fabric", "socials.json");

        return new Source("Mio",
            () -> modJar("mio") && Files.exists(path),
            () -> {
                List<String> names = new ArrayList<>();
                for (JsonElement element : socials(path)) {
                    JsonObject social = element.getAsJsonObject();
                    if ("friend".equalsIgnoreCase(social.get("role").getAsString())) {
                        names.add(social.get("name").getAsString());
                    }
                }
                return names;
            },
            name -> {
                JsonObject root = JsonParser.parseString(read(path)).getAsJsonObject();
                JsonArray socials = root.getAsJsonArray("socials");

                // Match on name against every role, not just "friend" — someone already listed as
                // something else must not gain a second, contradicting entry.
                for (JsonElement element : socials) {
                    if (name.equalsIgnoreCase(element.getAsJsonObject().get("name").getAsString())) return false;
                }

                JsonObject social = new JsonObject();
                social.addProperty("name", name);
                social.addProperty("role", "friend");
                socials.add(social);

                write(path, PRETTY.toJson(root));
                return true;
            });
    }

    private static JsonArray socials(Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject().getAsJsonArray("socials");
    }

    // Future Client is the odd one out: its config lives in the user's home directory, not the game
    // directory. ~/Future/friends.json is a top-level array of {"friend-label", "friend-alias"};
    // every existing entry has the two equal, so new ones alias to the name.
    private static Source future() {
        Path path = homeDir("Future", "friends.json");

        return new Source("Future",
            () -> modJar("future") && Files.exists(path),
            () -> {
                List<String> names = new ArrayList<>();
                for (JsonElement element : JsonParser.parseString(read(path)).getAsJsonArray()) {
                    names.add(element.getAsJsonObject().get("friend-label").getAsString());
                }
                return names;
            },
            name -> {
                JsonArray friends = JsonParser.parseString(read(path)).getAsJsonArray();
                for (JsonElement element : friends) {
                    if (name.equalsIgnoreCase(element.getAsJsonObject().get("friend-label").getAsString())) return false;
                }

                JsonObject friend = new JsonObject();
                friend.addProperty("friend-label", name);
                friend.addProperty("friend-alias", name);
                friends.add(friend);

                write(path, PRETTY.toJson(friends));
                return true;
            });
    }

    /** A jar in mods/ named after the client; Fabric skips anything not ending in exactly ".jar". */
    private static boolean modJar(String keyword) {
        try (var files = Files.list(gameDir("mods", ""))) {
            return files.map(f -> f.getFileName().toString().toLowerCase())
                .anyMatch(n -> n.endsWith(".jar") && n.contains(keyword));
        } catch (Exception e) {
            return false;
        }
    }

    private static Path gameDir(String folder, String file) {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(folder).resolve(file);
    }

    private static Path homeDir(String folder, String file) {
        return Path.of(System.getProperty("user.home"), folder, file);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object instance(String className) {
        return staticField(className, "INSTANCE");
    }

    private static Object staticField(String className, String field) {
        try {
            return cls(className).getField(field).get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asNames(Object collection) {
        return new ArrayList<>((Collection<String>) collection);
    }

    private static Object newInstance(String className, Class<?>[] types, Object... args) {
        try {
            return cls(className).getConstructor(types).newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object staticCall(String className, String method) {
        return staticCall(className, method, new Class[0]);
    }

    private static Object staticCall(String className, String method, Class<?>[] types, Object... args) {
        try {
            return cls(className).getMethod(method, types).invoke(null, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object call(Object target, String method) {
        return call(target, method, new Class[0]);
    }

    // setAccessible because a manager reached through its interface is often a package-private impl,
    // whose public methods reflection still refuses to invoke.
    private static Object call(Object target, String method, Class<?>[] types, Object... args) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
