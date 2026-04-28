package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ServerStatusHandler;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ModuleManager extends Module {
    private static final String DEBUG_FILE_NAME = "module-manager-debug.log";
    private static final String TIMER_TITLE = "Timer";
    private static final String SPEED_TITLE = "Speed";
    private static final String LEASE_MONITOR_RECONNECT = "monitor-reconnect";
    private static final String LEASE_HIGHWAYBUILDER_CENTER = "highwaybuilder-center-speed";
    private static final String LEASE_HIGHWAYBUILDER_ECHEST = "highwaybuilder-echest-break-speed";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDebugging = settings.createGroup("Debugging", true);

    private final Setting<List<Module>> criticalReconnectModules = sgGeneral.add(new ModuleListSetting.Builder()
        .name("critical-reconnect-modules")
        .description("Managed modules that must come back after reconnect before HighwayBuilder resumes.")
        .defaultValue(KillAura.class)
        .onChanged(modules -> validateCriticalReconnectModules())
        .build()
    );

    private final Setting<Boolean> logKillAuraAttacking = sgDebugging.add(new BoolSetting.Builder()
        .name("log-killaura-attacking")
        .description("Logs Kill Aura attacking state edges to the Module Manager debug log.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logStackTraces = sgDebugging.add(new BoolSetting.Builder()
        .name("log-stack-traces")
        .description("Includes trimmed Java stack traces for module toggle call sites.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxStackFrames = sgDebugging.add(new IntSetting.Builder()
        .name("max-stack-frames")
        .description("Maximum stack frames to include per module toggle call trace.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 16)
        .visible(logStackTraces::get)
        .build()
    );

    private final LinkedHashSet<Module> desiredReconnectActiveModules = new LinkedHashSet<>();
    private final LinkedHashSet<Module> frozenReconnectActiveModules = new LinkedHashSet<>();
    private final HashSet<String> warnedExcludedCriticalReconnectModules = new HashSet<>();
    private final LinkedHashSet<Module> lastKnownActiveModules = new LinkedHashSet<>();
    private final LinkedHashMap<String, OwnershipLease> activeLeases = new LinkedHashMap<>();

    private boolean reconnectModuleCacheFrozen;
    private boolean reconnectModuleRestoreInProgress;
    private long frozenReconnectModuleGeneration;
    private boolean lastKillAuraAttackingKnown;
    private boolean lastKillAuraAttacking;
    private boolean debugFileErrorLogged;
    private boolean autoManagedByHighwayBuilder;
    private boolean pendingAutoDisableWhenIdle;

    public ModuleManager() {
        super(THMAddon.MAIN, "module-manager", "Tracks module toggle ownership and restores managed modules after reconnect.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        debugFileErrorLogged = false;
        pendingAutoDisableWhenIdle = false;
        initializeActiveSnapshot();
        seedDesiredReconnectModuleCacheFromLiveState("activate");
        validateCriticalReconnectModules();
        syncKillAuraAttackingSnapshot();
        writeDebugLine(formatEventLine("module-manager-active", "manager enabled", null, Collections.emptyList(), null));
    }

    @Override
    public void onDeactivate() {
        writeDebugLine(formatEventLine("module-manager-active", "manager disabled", null, Collections.emptyList(), null));

        reconnectModuleCacheFrozen = false;
        reconnectModuleRestoreInProgress = false;
        frozenReconnectModuleGeneration = 0L;
        desiredReconnectActiveModules.clear();
        frozenReconnectActiveModules.clear();
        activeLeases.clear();
        lastKnownActiveModules.clear();
        lastKillAuraAttackingKnown = false;
        lastKillAuraAttacking = false;
        autoManagedByHighwayBuilder = false;
        pendingAutoDisableWhenIdle = false;
    }

    public void claimHighwayBuilderOwnershipAndEnable(String reason) {
        if (isActive()) {
            pendingAutoDisableWhenIdle = false;
            writeDebugLine(formatEventLine(
                "manager-ownership",
                String.format(Locale.ROOT, "HighwayBuilder ownership claim ignored because Module Manager is already active (reason=%s).", safeValue(reason)),
                null,
                Collections.emptyList(),
                null
            ));
            return;
        }

        autoManagedByHighwayBuilder = true;
        pendingAutoDisableWhenIdle = false;
        toggle();
        writeDebugLine(formatEventLine(
            "manager-ownership",
            String.format(Locale.ROOT, "HighwayBuilder auto-enabled Module Manager (reason=%s).", safeValue(reason)),
            null,
            Collections.emptyList(),
            null
        ));
    }

    public void releaseHighwayBuilderOwnership(String reason) {
        if (!autoManagedByHighwayBuilder) return;

        if (shouldDelayAutoDisable()) {
            pendingAutoDisableWhenIdle = true;
            writeDebugLine(formatEventLine(
                "manager-ownership",
                String.format(Locale.ROOT, "HighwayBuilder auto-disable deferred until Module Manager becomes idle (reason=%s).", safeValue(reason)),
                null,
                Collections.emptyList(),
                null
            ));
            return;
        }

        autoManagedByHighwayBuilder = false;
        pendingAutoDisableWhenIdle = false;
        if (isActive()) toggle();
    }

    public boolean prepareForMonitorReconnectPause(long generation, String reason) {
        if (!isActive()) return false;
        beginManagedReconnectLease(reason);
        freezeReconnectModuleCacheIfNeeded(generation, reason);
        return true;
    }

    public ReconnectRestoreOutcome restoreReconnectManagedModules(long generation) {
        if (!reconnectModuleCacheFrozen) return ReconnectRestoreOutcome.Success;
        if (frozenReconnectModuleGeneration > 0L && generation > 0L && frozenReconnectModuleGeneration != generation) {
            return ReconnectRestoreOutcome.Success;
        }
        if (frozenReconnectModuleGeneration <= 0L) frozenReconnectModuleGeneration = generation;

        LinkedHashSet<Module> criticalModules = resolveCriticalReconnectModules();
        reconnectModuleRestoreInProgress = true;
        logReconnectSnapshot("restore-start", generation, String.format(Locale.ROOT, "frozen=%d critical=%d", frozenReconnectActiveModules.size(), criticalModules.size()));

        try {
            for (Module module : criticalModules) {
                if (module.isActive()) continue;

                if (!tryEnableReconnectCachedModule(module)) {
                    logReconnectSnapshot("restore-critical-failure", generation, module.title);
                    clearFrozenReconnectModuleCache("critical-restore-failure");
                    return ReconnectRestoreOutcome.CriticalFailure;
                }
            }

            for (Module module : frozenReconnectActiveModules) {
                if (criticalModules.contains(module) || module.isActive()) continue;

                if (!tryEnableReconnectCachedModule(module)) {
                    logReconnectSnapshot("restore-best-effort-failure", generation, module.title);
                }
            }
        } finally {
            reconnectModuleRestoreInProgress = false;
        }

        desiredReconnectActiveModules.clear();
        desiredReconnectActiveModules.addAll(frozenReconnectActiveModules);
        logReconnectSnapshot("restore-success", generation, describeModuleTitles(frozenReconnectActiveModules));
        clearFrozenReconnectModuleCache("restore-success");
        return ReconnectRestoreOutcome.Success;
    }

    public void clearReconnectModuleRestoreSnapshot(String reason) {
        clearFrozenReconnectModuleCache(reason);
        endLease(LEASE_MONITOR_RECONNECT, reason);
    }

    public void beginHighwayBuilderCenterSpeedLease(String reason) {
        beginTimerSpeedLease(
            LEASE_HIGHWAYBUILDER_CENTER,
            "HighwayBuilder center-speed ownership",
            reason
        );
    }

    public void endHighwayBuilderCenterSpeedLease(String reason) {
        endLease(LEASE_HIGHWAYBUILDER_CENTER, reason);
    }

    public void beginHighwayBuilderEChestBreakSpeedLease(String reason) {
        beginTimerSpeedLease(
            LEASE_HIGHWAYBUILDER_ECHEST,
            "HighwayBuilder echest-break-speed ownership",
            reason
        );
    }

    public void endHighwayBuilderEChestBreakSpeedLease(String reason) {
        endLease(LEASE_HIGHWAYBUILDER_ECHEST, reason);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;
        if (!activeLeases.containsKey(LEASE_MONITOR_RECONNECT)) return;
        freezeReconnectModuleCacheIfNeeded(0L, "game-left");
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        if (!isActive()) return;

        LinkedHashSet<Module> currentActiveModules = snapshotActiveModules();
        LinkedHashSet<Module> removed = new LinkedHashSet<>(lastKnownActiveModules);
        removed.removeAll(currentActiveModules);

        LinkedHashSet<Module> added = new LinkedHashSet<>(currentActiveModules);
        added.removeAll(lastKnownActiveModules);

        for (Module module : removed) {
            logModuleActiveEdge(module, true, false, "active-modules-changed");
        }

        for (Module module : added) {
            logModuleActiveEdge(module, false, true, "active-modules-changed");
        }

        lastKnownActiveModules.clear();
        lastKnownActiveModules.addAll(currentActiveModules);
        refreshDesiredReconnectModuleCacheFromLiveState("active-modules-changed");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        if (pendingAutoDisableWhenIdle && !shouldDelayAutoDisable()) {
            pendingAutoDisableWhenIdle = false;
            autoManagedByHighwayBuilder = false;
            writeDebugLine(formatEventLine(
                "manager-ownership",
                "HighwayBuilder deferred auto-disable is now executing.",
                null,
                Collections.emptyList(),
                null
            ));
            toggle();
            return;
        }

        if (logKillAuraAttacking.get()) trackKillAuraAttackingEdge();
    }

    public static void traceModuleMethodInvocation(Module module, String action) {
        ModuleManager manager = getActiveInstance();
        if (manager != null) manager.logModuleMethodInvocation(module, action);
    }

    public static void traceKillAuraMixinEvent(String event, String detail) {
        ModuleManager manager = getActiveInstance();
        if (manager != null) manager.logKillAuraMixinEvent(event, detail);
    }

    private static ModuleManager getActiveInstance() {
        try {
            ModuleManager manager = Modules.get().get(ModuleManager.class);
            return manager != null && manager.isActive() ? manager : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void initializeActiveSnapshot() {
        lastKnownActiveModules.clear();
        lastKnownActiveModules.addAll(snapshotActiveModules());
    }

    private LinkedHashSet<Module> snapshotActiveModules() {
        return new LinkedHashSet<>(Modules.get().getActive());
    }

    private void syncKillAuraAttackingSnapshot() {
        KillAura killAura = Modules.get().get(KillAura.class);
        lastKillAuraAttacking = killAura != null && killAura.isActive() && killAura.attacking;
        lastKillAuraAttackingKnown = true;
    }

    private void trackKillAuraAttackingEdge() {
        KillAura killAura = Modules.get().get(KillAura.class);
        boolean attackingNow = killAura != null && killAura.isActive() && killAura.attacking;
        if (!lastKillAuraAttackingKnown) {
            lastKillAuraAttacking = attackingNow;
            lastKillAuraAttackingKnown = true;
            return;
        }

        if (attackingNow == lastKillAuraAttacking) return;

        ModuleContext context = buildContext(killAura);
        writeDebugLine(formatEventLine(
            "killaura-attacking-edge",
            String.format(Locale.ROOT, "Kill Aura attacking %s -> %s", lastKillAuraAttacking, attackingNow),
            killAura,
            context.leaseLabels(),
            null
        ));
        lastKillAuraAttacking = attackingNow;
    }

    private void logModuleMethodInvocation(Module module, String action) {
        if (module == null) return;
        ModuleContext context = buildContext(module);
        String stack = trimmedStackTrace();
        writeDebugLine(formatEventLine(
            "module-call",
            String.format(
                Locale.ROOT,
                "action=%s activeBefore=%s managed=%s suppressed=%s leases=%s",
                safeValue(action),
                module.isActive(),
                context.managed(),
                context.suppressed(),
                String.join(",", context.leaseLabels())
            ),
            module,
            context.leaseLabels(),
            stack
        ));
    }

    private void logKillAuraMixinEvent(String event, String detail) {
        Module killAura = Modules.get().get(KillAura.class);
        ModuleContext context = buildContext(killAura);
        writeDebugLine(formatEventLine(
            "killaura-mixin",
            String.format(Locale.ROOT, "event=%s detail=%s", safeValue(event), safeValue(detail)),
            killAura,
            context.leaseLabels(),
            null
        ));
    }

    private void logModuleActiveEdge(Module module, boolean oldActive, boolean newActive, String source) {
        ModuleContext context = buildContext(module);
        writeDebugLine(formatEventLine(
            "module-edge",
            String.format(
                Locale.ROOT,
                "source=%s old=%s new=%s managed=%s suppressed=%s leases=%s",
                safeValue(source),
                oldActive,
                newActive,
                context.managed(),
                context.suppressed(),
                String.join(",", context.leaseLabels())
            ),
            module,
            context.leaseLabels(),
            null
        ));
    }

    private void beginManagedReconnectLease(String reason) {
        beginLease(
            LEASE_MONITOR_RECONNECT,
            "THMHwyMonitor reconnect",
            reason,
            LeaseScope.AllManagedModules,
            Collections.emptySet()
        );
    }

    private void beginTimerSpeedLease(String ownerKey, String ownerLabel, String reason) {
        LinkedHashSet<String> moduleTitles = new LinkedHashSet<>();
        moduleTitles.add(TIMER_TITLE);
        moduleTitles.add(SPEED_TITLE);

        beginLease(ownerKey, ownerLabel, reason, LeaseScope.ExactModules, moduleTitles);
    }

    private void beginLease(String ownerKey, String ownerLabel, String reason, LeaseScope scope, Set<String> moduleTitles) {
        if (!isActive()) return;

        OwnershipLease existing = activeLeases.get(ownerKey);
        OwnershipLease next = new OwnershipLease(
            ownerKey,
            ownerLabel,
            reason == null ? "" : reason,
            scope,
            new LinkedHashSet<>(moduleTitles),
            System.currentTimeMillis()
        );

        if (next.equals(existing)) return;
        activeLeases.put(ownerKey, next);
        writeDebugLine(formatEventLine(
            "lease-begin",
            String.format(
                Locale.ROOT,
                "owner=%s scope=%s modules=%s reason=%s",
                ownerLabel,
                scope.name(),
                next.moduleTitles().isEmpty() ? "all-managed" : String.join(",", next.moduleTitles()),
                safeValue(reason)
            ),
            null,
            Collections.emptyList(),
            null
        ));
    }

    private void endLease(String ownerKey, String reason) {
        if (!isActive() && activeLeases.isEmpty()) return;

        OwnershipLease removed = activeLeases.remove(ownerKey);
        if (removed == null) return;

        writeDebugLine(formatEventLine(
            "lease-end",
            String.format(
                Locale.ROOT,
                "owner=%s scope=%s modules=%s reason=%s",
                removed.ownerLabel(),
                removed.scope().name(),
                removed.moduleTitles().isEmpty() ? "all-managed" : String.join(",", removed.moduleTitles()),
                safeValue(reason)
            ),
            null,
            Collections.emptyList(),
            null
        ));
    }

    private boolean shouldDelayAutoDisable() {
        return reconnectModuleCacheFrozen
            || reconnectModuleRestoreInProgress
            || !activeLeases.isEmpty();
    }

    private boolean isEligibleManagedModule(Module module) {
        return module != null
            && module != this
            && !(module instanceof HighwayBuilderTHM)
            && !(module instanceof THMHwyMonitor);
    }

    private void validateCriticalReconnectModules() {
        for (Module module : criticalReconnectModules.get()) {
            if (module == null) continue;
            if (!isEligibleManagedModule(module)) warnExcludedCriticalReconnectModule(module);
        }
    }

    private void warnExcludedCriticalReconnectModule(Module module) {
        if (module == null || module.title == null || module.title.isBlank()) return;
        if (warnedExcludedCriticalReconnectModules.add(module.title)) {
            warning("Ignoring critical reconnect module '%s' because Module Manager excludes that module from management.", module.title);
            writeDebugLine(formatEventLine("critical-module-warning", "excluded-module=" + module.title, module, Collections.emptyList(), null));
        }
    }

    private boolean hasLiveServerConnectionForManagedCache() {
        return mc != null
            && mc.player != null
            && mc.world != null
            && mc.getNetworkHandler() != null
            && mc.getNetworkHandler().getConnection() != null
            && mc.getNetworkHandler().getConnection().isOpen();
    }

    private boolean canCaptureReconnectModuleCacheFromLiveState() {
        return hasLiveServerConnectionForManagedCache()
            && getCommittedServerState() == ServerState.MAIN_SERVER;
    }

    private void seedDesiredReconnectModuleCacheFromLiveState(String reason) {
        desiredReconnectActiveModules.clear();
        if (!canCaptureReconnectModuleCacheFromLiveState()) return;

        for (Module module : Modules.get().getAll()) {
            if (!isEligibleManagedModule(module)) continue;
            if (isSnapshotSuppressedFor(module)) continue;
            if (module.isActive()) desiredReconnectActiveModules.add(module);
        }

        logReconnectSnapshot("seed-live", 0L, reason);
    }

    private void refreshDesiredReconnectModuleCacheFromLiveState(String reason) {
        if (reconnectModuleCacheFrozen || reconnectModuleRestoreInProgress) return;
        if (!canCaptureReconnectModuleCacheFromLiveState()) return;

        for (Module module : Modules.get().getAll()) {
            if (!isEligibleManagedModule(module)) continue;
            if (isSnapshotSuppressedFor(module)) continue;

            if (module.isActive()) desiredReconnectActiveModules.add(module);
            else desiredReconnectActiveModules.remove(module);
        }

        validateCriticalReconnectModules();
        logReconnectSnapshot("refresh-live", 0L, reason);
    }

    private void freezeReconnectModuleCacheIfNeeded(long generation, String reason) {
        if (reconnectModuleCacheFrozen) {
            if (frozenReconnectModuleGeneration <= 0L && generation > 0L) {
                frozenReconnectModuleGeneration = generation;
            }
            return;
        }

        if (desiredReconnectActiveModules.isEmpty()) {
            seedDesiredReconnectModuleCacheFromLiveState("freeze-fallback");
        }

        frozenReconnectActiveModules.clear();
        frozenReconnectActiveModules.addAll(desiredReconnectActiveModules);
        reconnectModuleCacheFrozen = true;
        frozenReconnectModuleGeneration = generation;
        logReconnectSnapshot("freeze", generation, reason);
    }

    private void clearFrozenReconnectModuleCache(String reason) {
        reconnectModuleCacheFrozen = false;
        reconnectModuleRestoreInProgress = false;
        frozenReconnectModuleGeneration = 0L;
        frozenReconnectActiveModules.clear();
        logReconnectSnapshot("clear", 0L, reason);
    }

    private LinkedHashSet<Module> resolveCriticalReconnectModules() {
        LinkedHashSet<Module> criticalModules = new LinkedHashSet<>();

        for (Module module : criticalReconnectModules.get()) {
            if (module == null) continue;
            if (!isEligibleManagedModule(module)) {
                warnExcludedCriticalReconnectModule(module);
                continue;
            }

            if (frozenReconnectActiveModules.contains(module)) criticalModules.add(module);
        }

        return criticalModules;
    }

    private boolean tryEnableReconnectCachedModule(Module module) {
        if (module == null) return false;

        try {
            module.enable();
        } catch (Throwable t) {
            writeDebugLine(formatEventLine(
                "module-restore-error",
                String.format(Locale.ROOT, "module=%s error=%s", module.title, safeValue(t.getMessage())),
                module,
                Collections.emptyList(),
                null
            ));
            return false;
        }

        return module.isActive();
    }

    private boolean isSnapshotSuppressedFor(Module module) {
        if (module == null) return false;

        for (OwnershipLease lease : activeLeases.values()) {
            if (lease.appliesTo(module, this)) return true;
        }

        return false;
    }

    private List<String> leaseLabelsFor(Module module) {
        if (module == null) return Collections.emptyList();

        List<String> labels = new ArrayList<>();
        for (OwnershipLease lease : activeLeases.values()) {
            if (lease.appliesTo(module, this)) labels.add(lease.ownerLabel());
        }
        return labels;
    }

    private String describeModuleTitles(Set<Module> modules) {
        if (modules == null || modules.isEmpty()) return "none";

        List<String> titles = new ArrayList<>();
        for (Module module : modules) {
            if (module != null) titles.add(module.title);
        }
        return titles.isEmpty() ? "none" : String.join(",", titles);
    }

    private void logReconnectSnapshot(String action, long generation, String detail) {
        writeDebugLine(formatEventLine(
            "reconnect-snapshot",
            String.format(
                Locale.ROOT,
                "action=%s generation=%d frozen=%s frozenCount=%d desiredCount=%d detail=%s",
                safeValue(action),
                generation,
                reconnectModuleCacheFrozen,
                frozenReconnectActiveModules.size(),
                desiredReconnectActiveModules.size(),
                safeValue(detail)
            ),
            null,
            Collections.emptyList(),
            null
        ));
    }

    private ModuleContext buildContext(Module module) {
        return new ModuleContext(
            isEligibleManagedModule(module),
            isSnapshotSuppressedFor(module),
            leaseLabelsFor(module)
        );
    }

    private String formatEventLine(String eventType, String detail, Module module, List<String> leaseLabels, String stack) {
        String moduleName = module == null ? "none" : module.name;
        String moduleTitle = module == null ? "none" : module.title;
        String screenName = mc == null || mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
        String serverState = String.valueOf(getCommittedServerState());
        boolean builderActive = isModuleActive(HighwayBuilderTHM.class);
        boolean monitorActive = isModuleActive(THMHwyMonitor.class);
        String leases = leaseLabels == null || leaseLabels.isEmpty() ? "none" : String.join(",", leaseLabels);

        return String.format(
            Locale.ROOT,
            "[%s] [module-manager-debug] type=%s moduleName=%s moduleTitle=%s detail=%s screen=%s playerNull=%s worldNull=%s server=%s builderActive=%s monitorActive=%s leases=%s stack=%s%n",
            Instant.now(),
            safeValue(eventType),
            safeValue(moduleName),
            safeValue(moduleTitle),
            safeValue(detail),
            safeValue(screenName),
            mc == null || mc.player == null,
            mc == null || mc.world == null,
            safeValue(serverState),
            builderActive,
            monitorActive,
            safeValue(leases),
            safeValue(stack == null || stack.isBlank() ? "none" : stack)
        );
    }

    private boolean isModuleActive(Class<? extends Module> moduleClass) {
        try {
            Module module = Modules.get().get(moduleClass);
            return module != null && module.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private ServerState getCommittedServerState() {
        try {
            return ServerStatusHandler.getInstance().getCommittedState();
        } catch (Throwable ignored) {
            return ServerState.UNKNOWN;
        }
    }

    private String trimmedStackTrace() {
        if (!logStackTraces.get()) return "";

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        List<String> frames = new ArrayList<>();
        for (StackTraceElement frame : stackTrace) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())) continue;
            if (className.equals(ModuleManager.class.getName())) continue;
            if (className.equals("meteordevelopment.meteorclient.systems.modules.Module")) continue;
            if (className.equals("xyz.thm.addon.mixin.meteor.ModuleMixin")) continue;

            frames.add(className + "#" + frame.getMethodName() + ":" + frame.getLineNumber());
            if (frames.size() >= maxStackFrames.get()) break;
        }

        return String.join(" <= ", frames);
    }

    private Path getDebugLogPath() {
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("logs").resolve(DEBUG_FILE_NAME);
    }

    private void writeDebugLine(String line) {
        if (line == null || line.isBlank()) return;

        Path path = getDebugLogPath();
        if (path == null) return;

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!debugFileErrorLogged) {
                THMAddon.LOG.warn("Failed to write Module Manager debug log: {}", e.getMessage());
                debugFileErrorLogged = true;
            }
        }
    }

    private static String safeValue(String value) {
        if (value == null || value.isBlank()) return "none";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private enum LeaseScope {
        AllManagedModules,
        ExactModules
    }

    public enum ReconnectRestoreOutcome {
        Success,
        CriticalFailure
    }

    private record OwnershipLease(
        String ownerKey,
        String ownerLabel,
        String reason,
        LeaseScope scope,
        LinkedHashSet<String> moduleTitles,
        long startedAtMs
    ) {
        private boolean appliesTo(Module module, ModuleManager manager) {
            if (module == null) return false;
            return switch (scope) {
                case AllManagedModules -> manager.isEligibleManagedModule(module);
                case ExactModules -> moduleTitles.contains(module.title);
            };
        }
    }

    private record ModuleContext(
        boolean managed,
        boolean suppressed,
        List<String> leaseLabels
    ) {}
}
