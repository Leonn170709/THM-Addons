package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ThmMembers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class AutoIgnore extends Module {
    private static final String IGNORE_KEY = "auto-ignore";
    private static final String IGNORE_FILE = "auto-ignored.txt";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> commandDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("command-delay-ticks")
        .description("Ticks to wait between /ignore commands.")
        .defaultValue(40)
        .range(1, 200)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Allow ignoring your own username if it appears in the API.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> logActions = sgGeneral.add(new BoolSetting.Builder()
        .name("log-actions")
        .description("Log auto-ignore actions in chat.")
        .defaultValue(true)
        .build()
    );

    private final Deque<IgnoreTarget> pendingTargets = new ArrayDeque<>();
    private final Set<String> ignoredNames = new HashSet<>();
    private final Set<String> pendingNames = new HashSet<>();
    private final Map<String, String> latestDisplayNames = new HashMap<>();

    private int commandDelay;
    private Path ignorePath;

    public AutoIgnore() {
        super(THMAddon.MAIN, "auto-ignore", "Auto-ignores Spambots and fake KitBots");
    }

    @Override
    public void onActivate() {
        ignorePath = THMAddon.GetConfigFile(IGNORE_KEY, IGNORE_FILE).toPath();
        ignoredNames.clear();
        pendingTargets.clear();
        pendingNames.clear();
        latestDisplayNames.clear();
        commandDelay = 0;
        loadIgnored();
    }

    @Override
    public void onDeactivate() {
        pendingTargets.clear();
        pendingNames.clear();
        latestDisplayNames.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        refreshTargets();

        if (commandDelay > 0) {
            commandDelay--;
            return;
        }

        if (pendingTargets.isEmpty()) return;

        IgnoreTarget target = pendingTargets.pollFirst();
        if (target == null) return;

        pendingNames.remove(target.normalized);
        if (ignoredNames.contains(target.normalized)) return;

        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;

        ChatUtils.sendPlayerMsg("/ignore " + target.displayName);
        ignoredNames.add(target.normalized);
        appendIgnored(target.normalized);
        commandDelay = commandDelayTicks.get();
        if (logActions.get()) info("Ignored " + target.displayName);
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (event.message == null) return;
        String message = event.message.trim();
        if (message.isEmpty()) return;

        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/ignore ")) {
            String name = message.substring(8).trim();
            if (name.isEmpty()) return;

            // /ignore toggles: if already ignored, it unignores.
            if (isIgnored(name)) {
                removeIgnored(name);
            } else {
                addManualIgnored(name);
            }
        }
    }

    private void refreshTargets() {
        List<ThmMembers.Member> members = ThmMembers.getCachedMembers();
        if (members.isEmpty()) return;

        String selfName = normalizeName(mc.player != null ? mc.player.getGameProfile().name() : null);

        List<IgnoreTarget> newTargets = new ArrayList<>();
        latestDisplayNames.clear();

        for (ThmMembers.Member member : members) {
            if (!ThmMembers.isIgnore(member)) continue;
            if (member.mcNames == null || member.mcNames.length == 0) continue;
            for (String name : member.mcNames) {
                String normalized = normalizeName(name);
                if (normalized == null) continue;
                latestDisplayNames.put(normalized, name);
                if (!ignoreSelf.get() && normalized.equals(selfName)) continue;
                if (ignoredNames.contains(normalized) || pendingNames.contains(normalized)) continue;
                newTargets.add(new IgnoreTarget(name, normalized));
            }
        }

        for (IgnoreTarget target : newTargets) {
            pendingTargets.addLast(target);
            pendingNames.add(target.normalized);
        }
    }

    private void loadIgnored() {
        if (ignorePath == null) return;
        if (!Files.exists(ignorePath)) return;
        try {
            for (String line : Files.readAllLines(ignorePath)) {
                String normalized = normalizeName(line);
                if (normalized != null) ignoredNames.add(normalized);
            }
        } catch (IOException e) {
            error("Failed to read ignored list: " + e.getMessage());
        }
    }

    private void appendIgnored(String normalized) {
        if (ignorePath == null || normalized == null) return;
        try {
            Path parent = ignorePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(ignorePath, normalized + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            error("Failed to write ignored list: " + e.getMessage());
        }
    }

    private void removeIgnored(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return;
        if (!ignoredNames.remove(normalized)) return;
        pendingNames.remove(normalized);
        pendingTargets.removeIf(target -> target.normalized.equals(normalized));
        rewriteIgnoredFile();
        if (logActions.get()) info("Unignored " + resolveDisplayName(normalized));
    }

    private void addManualIgnored(String name) {
        String normalized = normalizeName(name);
        if (normalized == null || ignoredNames.contains(normalized)) return;
        ignoredNames.add(normalized);
        appendIgnored(normalized);
        if (logActions.get()) info("Recorded ignore for " + resolveDisplayName(normalized));
    }

    private boolean isIgnored(String name) {
        String normalized = normalizeName(name);
        return normalized != null && ignoredNames.contains(normalized);
    }

    private void rewriteIgnoredFile() {
        if (ignorePath == null) return;
        try {
            Path parent = ignorePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (ignoredNames.isEmpty()) {
                Files.deleteIfExists(ignorePath);
                return;
            }
            StringBuilder out = new StringBuilder();
            for (String name : ignoredNames) {
                out.append(name).append('\n');
            }
            Files.writeString(ignorePath, out.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            error("Failed to rewrite ignored list: " + e.getMessage());
        }
    }

    private String resolveDisplayName(String normalized) {
        String display = latestDisplayNames.get(normalized);
        return display != null ? display : normalized;
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record IgnoreTarget(String displayName, String normalized) {
    }
}
