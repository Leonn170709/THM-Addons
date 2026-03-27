# THMHwyMonitor Reconnect Wrong-Direction Fix Simulation

```text
SIMULATION ONLY

This document is a scratch design/review artifact.
It copies current code from the live .java files and edits that copied text to simulate proposed changes.
The real .java files are not being modified by this document.
All review feedback should target the simulated text here first.
```

## 1. Goal and Scope

Goal:
- Stop THMHwyMonitor from resuming HighwayBuilder in the wrong direction after reconnect.

Scope:
- Phase 1 direction safety
- Phase 2 transactional reconnect resume hardening
- Phase 3 reconnect-only Speed/Timer ownership cleanup
- reconnect safety-stop truthfulness and stale disconnect UI cleanup
- only the reconnect-related parts of those phases, not unrelated module refactors

Out of scope for this simulation:
- non-reconnect monitor cleanup
- non-reconnect Builder movement/state refactors
- stats-cache or statistics lifecycle work
- broad module cleanup outside reconnect safety

## 2. Source Files Copied From

- `src/main/java/xyz/thm/addon/modules/THMHwyMonitor.java`
- `src/main/java/xyz/thm/addon/modules/HighwayBuilderTHM.java`

## 3. Current Code Excerpts and Simulated Changes

### Reconnect Delayed Finalization Entry

=== FILE: THMHwyMonitor.java ===
=== CURRENT ===

```java
1040:    private void maybeRunDelayedMainServerResumeFinalization() {
1041:        if (!delayedMainServerResumePending) return;
1042:
1043:        if (!isActive()) {
1044:            clearDelayedMainServerResumeState();
1045:            return;
1046:        }
1047:
1048:        if (delayedMainServerResumeCycleId <= 0L || delayedMainServerResumeCycleId != activeReconnectCycleId) {
1049:            clearDelayedMainServerResumeState();
1050:            return;
1051:        }
1052:
1053:        long now = System.currentTimeMillis();
1054:        if (now < delayedMainServerResumeAtMs) return;
1055:        if (mc == null || mc.player == null || mc.world == null) return;
1056:
1057:        long cycleId = delayedMainServerResumeCycleId;
1058:        String contextTag = delayedMainServerResumeContext;
1059:        clearDelayedMainServerResumeState();
1060:
1061:        info(
1062:            "Reconnect MAIN_SERVER delay complete (%s). Running post-main-server finalization (cycle %d).",
1063:            contextTag,
1064:            cycleId
1065:        );
1066:        completePostRejoinSuccessFlow();
1067:        clearRestartRecoveryState("resume-success-delayed", false, false);
```

=== SIMULATED CHANGE ===

```java
private void maybeRunDelayedMainServerResumeFinalization() {
    if (!delayedMainServerResumePending) return;

    if (!isActive()) {
        clearDelayedMainServerResumeState();
        return;
    }

    if (delayedMainServerResumeCycleId <= 0L || delayedMainServerResumeCycleId != activeReconnectCycleId) {
        clearDelayedMainServerResumeState();
        return;
    }

    long now = System.currentTimeMillis();
    if (now < delayedMainServerResumeAtMs) return;
    if (mc == null || mc.player == null || mc.world == null) return;

    long cycleId = delayedMainServerResumeCycleId;
    String contextTag = delayedMainServerResumeContext;
    clearDelayedMainServerResumeState();

    info(
        "Reconnect MAIN_SERVER delay complete (%s). Entering reconnect direction gate (cycle %d).",
        contextTag,
        cycleId
    );

    beginPostRejoinDirectionGate(cycleId, contextTag);
}
```

=== WHY ===

- Reconnect should enter a retrying direction gate, not resume HighwayBuilder immediately.
- Restart recovery state must not clear until the reconnect gate succeeds or terminally fails.

### Post-Rejoin Direction Resolution and Resume Handoff

=== FILE: THMHwyMonitor.java ===
=== CURRENT ===

```java
1545:    private void completePostRejoinSuccessFlow() {
1546:
1547:        restorePostJoinModuleStatesIfNeeded();
1548:
1549:        HorizontalDirection direction = determinePostRejoinWorkingDirection();
1550:        if (direction == null && mc != null && mc.player != null) {
1551:            direction = HorizontalDirection.get(mc.player.getYaw());
1552:        }
1553:
1554:        if (direction != null) {
1555:            applyDirectionAndEnableHighwayBuilder(direction);
1556:        } else {
1557:            warning("Unable to resolve post-rejoin working direction. THM HighwayBuilder enable skipped.");
1558:        }
1559:
1560:        maybeTakeDeferredRestartScreenshotAfterReconnect("main-server-ready");
1561:        clearRestartRecoveryState("post-main-server finalization complete", false, false);
1562:    }
1563:
1564:    private void applyDirectionAndEnableHighwayBuilder(HorizontalDirection workingDirection) {
1565:        applyPostRejoinYaw(workingDirection);
1566:        info("Post-rejoin direction selected: %s.", workingDirection.name);
1567:
1568:        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
1569:        if (builder == null) {
1570:            warning("THM HighwayBuilder module not found, cannot resume.");
1571:            return;
1572:        }
1573:
1574:        if (!builder.isActive()) {
1575:            builder.toggle();
1576:            if (builder.isActive()) info("Resumed THM HighwayBuilder after post-rejoin checks.");
1577:            else warning("Failed to resume THM HighwayBuilder after post-rejoin checks.");
1578:        } else {
1579:            info("THM HighwayBuilder already active after post-rejoin checks.");
1580:        }
1581:    }
1582:
1583:    private HorizontalDirection determinePostRejoinWorkingDirection() {
1584:        if (mc.player == null || mc.world == null) return null;
1585:        int probeDistance = postRejoinAxisProbeDistanceForCurrentAttempt();
1586:
1587:        HorizontalDirection[] axisDirections = resolvePostRejoinAxisDirections();
1588:        if (axisDirections == null) {
1589:            warning("Unable to resolve highway axis after rejoin. Using current facing direction.");
1590:            return HorizontalDirection.get(mc.player.getYaw());
1591:        }
1592:
1593:        HorizontalDirection dirA = axisDirections[0];
1594:        HorizontalDirection dirB = axisDirections[1];
1595:        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
1596:        boolean pavingSelected = builder != null && isPavingMode(builder);
1597:
1598:        if (pavingSelected) {
1599:            boolean dirAObsidianY119 = isObsidianAtAxisProbe(dirA, probeDistance, 119);
1600:            boolean dirBObsidianY119 = isObsidianAtAxisProbe(dirB, probeDistance, 119);
1601:            if (dirAObsidianY119 != dirBObsidianY119) {
1602:                HorizontalDirection selected = dirAObsidianY119 ? dirB : dirA;
1603:                return selected;
1604:            }
1605:
1606:            warning("Post-rejoin obsidian direction checks at Y=119 were ambiguous. Deferring HighwayBuilder enable.");
1607:            return null;
1608:        }
1609:
1610:        boolean dirAAirY122 = isAirAtAxisProbe(dirA, probeDistance, 122);
1611:        boolean dirBAirY122 = isAirAtAxisProbe(dirB, probeDistance, 122);
1612:        if (dirAAirY122 != dirBAirY122) {
1613:            HorizontalDirection selected = dirAAirY122 ? dirB : dirA;
1614:            return selected;
1615:        }
1616:
1617:        if (dirAAirY122 && dirBAirY122) {
1618:            warning("Post-rejoin direction not ready yet: both axis directions are air at Y=122. Deferring HighwayBuilder enable.");
1619:            return null;
1620:        }
1621:
1622:        warning("Post-rejoin direction checks were ambiguous. Deferring HighwayBuilder enable.");
1623:        return null;
1624:    }
1625:
1626:    private int postRejoinAxisProbeDistanceForCurrentAttempt() {
1627:        return POST_REJOIN_AXIS_PROBE_DISTANCE;
1628:    }
1629:
1630:    private HorizontalDirection[] resolvePostRejoinAxisDirections() {
1631:        WorkLine line = trackedLine;
1632:        if (line == null && mc.player != null) {
1633:            double centerOffset = trueCenterMode.get() ? 0.5 : 0.0;
1634:            line = nearestWorkLine(mc.player.getX(), mc.player.getZ(), centerOffset, trueCenterMode.get());
1635:        }
1636:        if (line == null) return null;
1637:
1638:        return switch (line) {
1639:            case CardinalNS -> new HorizontalDirection[] {HorizontalDirection.South, HorizontalDirection.North};
1640:            case CardinalEW -> new HorizontalDirection[] {HorizontalDirection.East, HorizontalDirection.West};
1641:            case DiagonalNWSE -> new HorizontalDirection[] {HorizontalDirection.NorthWest, HorizontalDirection.SouthEast};
1642:            case DiagonalNESW -> new HorizontalDirection[] {HorizontalDirection.NorthEast, HorizontalDirection.SouthWest};
1643:        };
```

=== SIMULATED CHANGE ===

```java
private static final long POST_REJOIN_DIRECTION_RETRY_DELAY_MS = 1_000L;
private static final int POST_REJOIN_DIRECTION_RETRY_LIMIT = 30;
private static final double RECONNECT_LINE_MAX_DISTANCE = 6.0;

private record PostRejoinDirectionResult(
    HorizontalDirection direction,
    String reason,
    String summary
) {
    private static PostRejoinDirectionResult success(HorizontalDirection direction, String summary) {
        return new PostRejoinDirectionResult(direction, "", summary);
    }

    private static PostRejoinDirectionResult blocked(String reason, String summary) {
        return new PostRejoinDirectionResult(null, reason, summary);
    }

    private boolean conclusive() {
        return direction != null;
    }
}

private record ReconnectLineResolution(
    WorkLine line,
    double distance
) {}

private void beginPostRejoinDirectionGate(long cycleId, String contextTag) {
    if (!isActive()) return;
    if (cycleId <= 0L || cycleId != activeReconnectCycleId) return;

    postRejoinDirectionGateActive = true;
    postRejoinDirectionRetryCount = 0;
    postRejoinDirectionNextAttemptAtMs = 0L;
    postRejoinDirectionBlockReason = "waiting";
    postRejoinDirectionBlockSummary = contextTag == null ? "" : contextTag;
    postRejoinBlockedScreenshotTaken = false;
    postRejoinTerminalScreenshotTaken = false;
    postRejoinLastCompleteProbeWinner = null;
}

private void maybeRunPostRejoinDirectionGate() {
    if (!postRejoinDirectionGateActive) return;
    if (!isActive()) {
        clearPostRejoinDirectionGateState();
        return;
    }
    if (activeReconnectCycleId <= 0L || mc == null || mc.player == null || mc.world == null) return;

    long now = System.currentTimeMillis();
    if (postRejoinDirectionNextAttemptAtMs > now) return;

    PostRejoinDirectionResult result = determineConclusivePostRejoinWorkingDirection();
    if (result.conclusive()) {
        if (applyDirectionAndEnableHighwayBuilder(result.direction())) {
            finishSuccessfulReconnectResume();
        }
        return;
    }

    postRejoinDirectionRetryCount++;
    postRejoinDirectionBlockReason = result.reason();
    postRejoinDirectionBlockSummary = result.summary();
    showReconnectBlockedOverlay(result.reason(), postRejoinDirectionRetryCount, result.summary());

    if (autoScreenshotOnRestartDetection.get() && postRejoinDirectionRetryCount >= 3 && !postRejoinBlockedScreenshotTaken) {
        postRejoinBlockedScreenshotTaken = true;
        takeRestartScreenshot();
    }

    if (postRejoinDirectionRetryCount >= POST_REJOIN_DIRECTION_RETRY_LIMIT) {
        enterReconnectSafetyStop("Reconnect resume stopped: " + result.reason());
        return;
    }

    postRejoinDirectionNextAttemptAtMs = now + POST_REJOIN_DIRECTION_RETRY_DELAY_MS;
}

private boolean applyDirectionAndEnableHighwayBuilder(HorizontalDirection workingDirection) {
    applyPostRejoinYaw(workingDirection);
    info("Post-rejoin direction selected: %s.", workingDirection.name);

    HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
    if (builder == null) {
        enterReconnectSafetyStop("HighwayBuilder module not found.");
        return false;
    }

    if (!builder.resumeFromReconnect(workingDirection, activeReconnectCycleId)) {
        enterReconnectSafetyStop("HighwayBuilder refused reconnect resume for locked direction.");
        return false;
    }

    return true;
}

private void finishSuccessfulReconnectResume() {
    maybeTakeDeferredRestartScreenshotAfterReconnect("main-server-ready");
    clearRestartAutomationState("post-main-server finalization complete", true, true);
}

private PostRejoinDirectionResult determineConclusivePostRejoinWorkingDirection() {
    if (mc.player == null || mc.world == null) return PostRejoinDirectionResult.blocked("player-or-world-missing", "state=missing");

    HorizontalDirection[] axisDirections = resolvePostRejoinAxisDirections();
    if (axisDirections == null) {
        return PostRejoinDirectionResult.blocked("axis-unresolved", "line=unresolved");
    }

    HorizontalDirection dirA = axisDirections[0];
    HorizontalDirection dirB = axisDirections[1];
    HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
    boolean pavingSelected = builder != null && isPavingMode(builder);

    AxisProbeSummary summary = pavingSelected
        ? probeAxisForObsidian(dirA, dirB, 20, 119)
        : probeAxisForAir(dirA, dirB, 20, 122);

    if (!summary.allSamplesLoaded()) return PostRejoinDirectionResult.blocked("probe-unloaded", summary.toString());
    if (!summary.isConclusive()) return PostRejoinDirectionResult.blocked("probe-ambiguous", summary.toString());

    return PostRejoinDirectionResult.success(summary.selectedDirection(), summary.toString());
}

private HorizontalDirection[] resolvePostRejoinAxisDirections() {
    if (mc.player == null) return null;

    WorkLine line = resolveReconnectLineFromCurrentPosition();
    if (line == null) return null;

    return switch (line) {
        case CardinalNS -> new HorizontalDirection[] {HorizontalDirection.South, HorizontalDirection.North};
        case CardinalEW -> new HorizontalDirection[] {HorizontalDirection.East, HorizontalDirection.West};
        case DiagonalNWSE -> new HorizontalDirection[] {HorizontalDirection.NorthWest, HorizontalDirection.SouthEast};
        case DiagonalNESW -> new HorizontalDirection[] {HorizontalDirection.NorthEast, HorizontalDirection.SouthWest};
    };
}

private WorkLine resolveReconnectLineFromCurrentPosition() {
    double centerOffset = trueCenterMode.get() ? 0.5 : 0.0;
    ReconnectLineResolution resolved = nearestWorkLineWithAmbiguityBlock(
        mc.player.getX(),
        mc.player.getZ(),
        centerOffset,
        trueCenterMode.get(),
        0.05
    );
    if (resolved == null) return null;
    if (resolved.distance() > RECONNECT_LINE_MAX_DISTANCE) return null;
    return resolved.line();
}

private static ReconnectLineResolution nearestWorkLineWithAmbiguityBlock(
    double playerX,
    double playerZ,
    double centerOffset,
    boolean trueCenterMode,
    double ambiguityThreshold
) {
    double dCardinalNs = Math.abs(playerX - centerOffset);
    double dCardinalEw = Math.abs(playerZ - centerOffset);
    double dDiagNwSe = distanceToLine(playerX, playerZ, 1.0, -1.0, 0.0);
    double c = trueCenterMode ? 1.0 : 0.0;
    double dDiagNeSw = distanceToLine(playerX, playerZ, 1.0, 1.0, c);

    double best = dCardinalNs;
    double second = Double.POSITIVE_INFINITY;
    WorkLine line = WorkLine.CardinalNS;

    double[] distances = new double[] {dCardinalEw, dDiagNwSe, dDiagNeSw};
    WorkLine[] lines = new WorkLine[] {WorkLine.CardinalEW, WorkLine.DiagonalNWSE, WorkLine.DiagonalNESW};
    for (int i = 0; i < distances.length; i++) {
        double distance = distances[i];
        if (distance < best) {
            second = best;
            best = distance;
            line = lines[i];
        } else if (distance < second) {
            second = distance;
        }
    }

    if (Math.abs(second - best) <= ambiguityThreshold) return null;
    return new ReconnectLineResolution(line, best);
}
```

=== WHY ===

- Reconnect must fail closed instead of guessing from yaw.
- Reconnect axis choice should come from current position first, not stale cached line state.
- Reconnect should not probe at all when the player is too far from any valid highway line.
- Direction proof should come from a conclusive 20-block probe only.
- HighwayBuilder should resume through an explicit locked-direction entrypoint, not a blind toggle.
- The retry cadence, retry cap, blocked overlay, blocked screenshot, and terminal safety-stop policy must be explicit in the reconnect gate itself.
- Successful reconnect must fully clear reconnect automation state, not just recovery state.
- Post-handoff watchdog hardening is intentionally left out of this smaller reconnect simulation slice.

### Live Recovery Direction Inference

=== FILE: THMHwyMonitor.java ===
=== CURRENT ===

```java
1459:    private static RecoveryTarget determineRecoveryTarget(
1460:        double playerX,
1461:        double playerZ,
1462:        float playerYaw,
1463:        int recoveryGoalY,
1464:        boolean trueCenterMode,
1465:        WorkLine preferredLine,
1466:        String preferredDirection
1467:    ) {
1468:        double centerOffset = trueCenterMode ? 0.5 : 0.0;
1469:        WorkLine line = preferredLine != null ? preferredLine : nearestWorkLine(playerX, playerZ, centerOffset, trueCenterMode);
1470:        if (line == null) return null;
1471:
1472:        double targetX = playerX;
1473:        double targetZ = playerZ;
1474:        switch (line) {
1475:            case CardinalNS -> targetX = centerOffset;
1476:            case CardinalEW -> targetZ = centerOffset;
1477:            case DiagonalNWSE -> {
1478:                double[] point = closestPointOnLine(playerX, playerZ, 1.0, -1.0, 0.0);
1479:                targetX = point[0];
1480:                targetZ = point[1];
1481:            }
1482:            case DiagonalNESW -> {
1483:                double c = trueCenterMode ? 1.0 : 0.0;
1484:                double[] point = closestPointOnLine(playerX, playerZ, 1.0, 1.0, c);
1485:                targetX = point[0];
1486:                targetZ = point[1];
1487:            }
1488:        }
1489:
1490:        String direction = normalizeDirection(preferredDirection);
1491:        if (!isDirectionCompatible(line, direction)) direction = inferDirectionForLine(line, playerYaw);
1492:
1493:        double distance = Math.hypot(playerX - targetX, playerZ - targetZ);
1494:        int goalX = floorToBlock(targetX);
1495:        int goalY = recoveryGoalY;
1496:        int goalZ = floorToBlock(targetZ);
1497:        String highway = (line == WorkLine.CardinalNS || line == WorkLine.CardinalEW) ? "Cardinal" : "Diagonal";
1498:
1499:        return new RecoveryTarget(highway, direction, targetX, targetZ, goalX, goalY, goalZ, directionToYaw(direction), distance, line);
```

=== SIMULATED CHANGE ===

```java
private static RecoveryTarget determineRecoveryTarget(
    double playerX,
    double playerZ,
    float playerYaw,
    int recoveryGoalY,
    boolean trueCenterMode,
    WorkLine preferredLine,
    String preferredDirection
) {
    double centerOffset = trueCenterMode ? 0.5 : 0.0;
    WorkLine line = preferredLine != null ? preferredLine : nearestWorkLine(playerX, playerZ, centerOffset, trueCenterMode);
    if (line == null) return null;

    double targetX = playerX;
    double targetZ = playerZ;
    switch (line) {
        case CardinalNS -> targetX = centerOffset;
        case CardinalEW -> targetZ = centerOffset;
        case DiagonalNWSE -> {
            double[] point = closestPointOnLine(playerX, playerZ, 1.0, -1.0, 0.0);
            targetX = point[0];
            targetZ = point[1];
        }
        case DiagonalNESW -> {
            double c = trueCenterMode ? 1.0 : 0.0;
            double[] point = closestPointOnLine(playerX, playerZ, 1.0, 1.0, c);
            targetX = point[0];
            targetZ = point[1];
        }
    }

    String direction = normalizeDirection(preferredDirection);
    if (!isDirectionCompatible(line, direction)) return null;

    double distance = Math.hypot(playerX - targetX, playerZ - targetZ);
    int goalX = floorToBlock(targetX);
    int goalY = recoveryGoalY;
    int goalZ = floorToBlock(targetZ);
    String highway = (line == WorkLine.CardinalNS || line == WorkLine.CardinalEW) ? "Cardinal" : "Diagonal";

    return new RecoveryTarget(highway, direction, targetX, targetZ, goalX, goalY, goalZ, directionToYaw(direction), distance, line);
}
```

=== WHY ===

- Live recovery should fail closed if Builder direction is not usable.
- Current-yaw inference is the unsafe behavior we are trying to eliminate.

### Builder Activation-Time Direction Seeding

=== FILE: HighwayBuilderTHM.java ===
=== CURRENT ===

```java
1125:    public void onActivate() {
1126:        if (mc.player == null || mc.world == null) return;
1127:        if (!Utils.canUpdate()) return;
1128:        clearKitbotOrderTracking("module-activate");
1129:
1130:        if (centerSpeedMonitorRecoveryOwned && !resumeStatsSessionOnNextActivate) {
1131:            restockDebug("Center/Speed stale monitor recovery baseline cleared on activate (lastReason=%s).", centerSpeedLastReason);
1132:            clearCenterSpeedOwnership("activate-stale-monitor-baseline");
1133:        }
1134:
1135:        if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnActivate();
1136:        loadStatsCacheFromDisk();
1137:
1138:        previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
1139:        pauseOnLostFocusChanged = previousPauseOnLostFocus;
1140:        if (pauseOnLostFocusChanged) togglePauseOnLostFocus(false);
1141:
1142:        updateVariables();
1143:        updateSignBreakRegex();
1144:        dir = HorizontalDirection.get(mc.player.getYaw());
1145:        leftDir = dir.rotateLeftSkipOne();
1146:        rightDir = leftDir.opposite();
1147:
1148:        blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();
1149:        state = State.Forward;
1150:        setState(State.Center);
```

=== SIMULATED CHANGE ===

```java
public void onActivate() {
    if (mc.player == null || mc.world == null) return;
    if (!Utils.canUpdate()) return;
    clearKitbotOrderTracking("module-activate");

    if (centerSpeedMonitorRecoveryOwned && !resumeStatsSessionOnNextActivate) {
        restockDebug("Center/Speed stale monitor recovery baseline cleared on activate (lastReason=%s).", centerSpeedLastReason);
        clearCenterSpeedOwnership("activate-stale-monitor-baseline");
    }

    if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnActivate();
    loadStatsCacheFromDisk();

    previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
    pauseOnLostFocusChanged = previousPauseOnLostFocus;
    if (pauseOnLostFocusChanged) togglePauseOnLostFocus(false);

    updateVariables();
    updateSignBreakRegex();

    dir = HorizontalDirection.get(mc.player.getYaw());
    leftDir = dir.rotateLeftSkipOne();
    rightDir = leftDir.opposite();
    blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();
    state = State.Forward;
    setState(State.Center);
}

public boolean resumeFromReconnect(HorizontalDirection lockedDirection, long generation) {
    if (lockedDirection == null) return false;
    if (mc.player == null || mc.world == null) return false;
    if (!Utils.canUpdate()) return false;
    if (!enterReconnectActiveMode(lockedDirection, generation)) return false; // simulated dedicated activation path, not normal onActivate()

    boolean success = false;
    clearKitbotOrderTracking("reconnect-activate");
    if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnActivate();
    loadStatsCacheFromDisk();

    previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
    pauseOnLostFocusChanged = previousPauseOnLostFocus;
    if (pauseOnLostFocusChanged) togglePauseOnLostFocus(false);

    updateVariables();
    updateSignBreakRegex();

    dir = lockedDirection;
    leftDir = dir.rotateLeftSkipOne();
    rightDir = leftDir.opposite();
    blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();

    suspended = true;
    state = State.Forward;

    if (!consumeReconnectBaseline(generation)) {
        abortReconnectActivation("baseline-consume-failed");
        return false;
    }

    setState(State.Center);
    suspended = false;
    success = isActive() && dir == lockedDirection;
    if (!success) abortReconnectActivation("direction-mismatch-after-resume");
    return success;
}

private boolean enterReconnectActiveMode(HorizontalDirection lockedDirection, long generation) {
    if (isActive()) return false;

    // Dedicated reconnect activation prelude:
    // - activates Builder only into reconnect-owned suspended mode
    // - must not start real build execution before resumeFromReconnect(...) finishes
    // - must remain fully rollback-able by abortReconnectActivation(...)
    reconnectResumeContext = new ReconnectResumeContext(lockedDirection, generation);
    suppressThmHwyMonitorSync = true;
    try {
        toggle();
    } finally {
        suppressThmHwyMonitorSync = false;
    }

    boolean activeInReconnectMode = isActive() && suspended;
    if (!activeInReconnectMode) reconnectResumeContext = null;
    return activeInReconnectMode;
}

private void abortReconnectActivation(String reason) {
    // Roll back reconnect-only activation if baseline consume or verification fails.
    // This helper is responsible for:
    // - keeping Builder disabled/inactive
    // - clearing reconnect-only startup context
    // - clearing untrusted direction/provider state
    // - undoing reconnect activation prelude side effects that must not survive failure
    suspended = true;
    reconnectResumeContext = null;
    dir = null;
    leftDir = null;
    rightDir = null;
    blockPosProvider = null;
    disableForReconnectResumeFailure(reason);
}
```

=== WHY ===

- Reconnect resume needs a dedicated Builder-side entrypoint instead of latent pending-resume flags.
- That entrypoint bypasses yaw-derived activation setup entirely and installs the locked direction first.
- The reconnect path still stays suspended until direction bind and baseline restore verify synchronously.
- Failed reconnect activation must roll back to a disabled Builder state instead of leaving partial startup side effects behind.

### Minimal Direction-Dependent State Area

=== FILE: HighwayBuilderTHM.java ===
=== CURRENT ===

```java
1684:    private void setState(State state) {
1685:        setState(state, this.state);
1686:    }
1687:
1688:    private void setState(State state, State lastState) {
1689:        State previousState = this.state;
1690:        this.lastState = lastState;
1691:        this.state = state;
1692:
1693:        if (shouldLogRestockStateTransition(previousState, state, lastState)) {
1694:            restockDebug("state %s -> %s (last=%s, active=%s, pending=%s, blockadeReady=%s, sequence=%s)",
1695:                stateName(previousState),
1696:                stateName(state),
```

=== SIMULATED CHANGE ===

```java
private void setState(State state) {
    setState(state, this.state);
}

private void setState(State state, State lastState) {
    State previousState = this.state;
    this.lastState = lastState;
    this.state = state;

    // Reconnect resume must arrive here only after dir/leftDir/rightDir/blockPosProvider
    // have already been seeded from the locked monitor direction.
    if (shouldLogRestockStateTransition(previousState, state, lastState)) {
        restockDebug("state %s -> %s (last=%s, active=%s, pending=%s, blockadeReady=%s, sequence=%s)",
            stateName(previousState),
            stateName(state),
```

=== WHY ===

- The reconnect entrypoint has to make direction-dependent state valid before normal state startup proceeds.
- This note makes the review target explicit without simulating the entire later transactional resume design.

### Builder Suspended Release During Reconnect Resume

=== FILE: HighwayBuilderTHM.java ===
=== CURRENT ===

```java
1367:            pauseExecutionForServerState(committedState);
1368:            return;
1369:        }
1370:
1371:        executionPausedByServerState = false;
1372:        tickDeferredCenterSpeedRestore();
1373:
1374:        if (statuslog.get()) {
1375:            statusLogTimer++;
1376:            if (statusLogTimer >= 6000) { // 5 minutes
1377:                sendStatusLog();
1378:                statusLogTimer = 0;
1379:            }
1380:        }
1381:
1382:        if (dir == null) {
1383:            onActivate();
1384:            return;
1385:        }
1386:
1387:        if (suspended) {
1388:            if (inventory && Utils.canUpdate()) {
1389:                updateVariables();
1390:                suspended = false;
1391:            }
1392:            else return;
1393:        }
```

=== SIMULATED CHANGE ===

```java
if (dir == null) {
    onActivate();
    return;
}

if (suspended) {
    // Reconnect no longer relies on a pending-resume tick handshake.
    // By the time generic execution reaches here, resumeFromReconnect(...)
    // has already installed the locked direction and consumed the reconnect baseline.
    if (inventory && Utils.canUpdate()) {
        updateVariables();
        suspended = false;
    } else return;
}
```

=== WHY ===

- Phase 2 still needs suspend to prevent generic execution from racing resume setup.
- The reconnect path is simpler now because it does not depend on persistent pending-resume flags.

### Monitor Reconnect Pause Ownership Handoff

=== FILE: THMHwyMonitor.java ===
=== CURRENT ===

```java
774:    private void ensureHighwayBuilderDisabledForRestart(String source, boolean verbose) {
775:        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
776:        if (builder == null || !builder.isActive()) return;
777:
778:        builder.disableForMonitorRealignPause();
779:        boolean disabled = !builder.isActive();
780:
781:        if (disabled) {
782:            if (verbose) info("Disabled THM HighwayBuilder during restart handling (%s).", source);
783:        } else {
784:            warning("Failed to disable THM HighwayBuilder during restart handling (%s).", source);
785:        }
786:    }

1254:    private boolean pauseAllActiveModulesForRecovery() {
1255:        if (recoveryModulesPaused) return true;
1256:
1257:        recoveryPausedModules.clear();
1258:        Module highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
1259:        Module timer = Modules.get().get(Timer.class);
1260:        Module speed = Modules.get().get(Speed.class);
1261:
1262:        internalTimerSpeedToggleInProgress = true;
1263:        try {
1264:            pauseModuleForRecovery(highwayBuilder);
1265:            pauseModuleForRecovery(timer);
1266:            pauseModuleForRecovery(speed);
1267:        } finally {
1268:            internalTimerSpeedToggleInProgress = false;
1269:        }
1270:
1271:        recoveryModulesPaused = true;
1272:        return true;
1273:    }

1275:    private void pauseModuleForRecovery(Module module) {
1276:        if (module == null || module == this) return;
1277:        if (!module.isActive()) return;
1278:
1279:        recoveryPausedModules.add(module);
1280:        if (module instanceof HighwayBuilderTHM builder) {
1281:            builder.preserveCenterSpeedBaselineForMonitorRecovery("thm-monitor-pause");
1282:            builder.disableForMonitorRealignPause();
1283:        } else {
1284:            module.disable();
1285:        }
```

=== SIMULATED CHANGE ===

```java
private void ensureHighwayBuilderDisabledForRestart(String source, boolean verbose) {
    HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
    if (builder == null || !builder.isActive()) return;

    if (!builder.prepareForMonitorReconnectPause(activeReconnectCycleId)) {
        enterReconnectSafetyStop("Unable to establish reconnect baseline before restart pause.");
        return;
    }

    builder.disableForMonitorRealignPause();
    boolean disabled = !builder.isActive();

    if (disabled) {
        if (verbose) info("Disabled THM HighwayBuilder during restart handling (%s).", source);
    } else {
        warning("Failed to disable THM HighwayBuilder during restart handling (%s).", source);
    }
}

private boolean pauseAllActiveModulesForRecovery() {
    if (recoveryModulesPaused) return true;

    recoveryPausedModules.clear();
    Module highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
    Module timer = Modules.get().get(Timer.class);
    Module speed = Modules.get().get(Speed.class);

    internalTimerSpeedToggleInProgress = true;
    try {
        pauseModuleForRecovery(highwayBuilder);
        pauseModuleForRecovery(timer);
        pauseModuleForRecovery(speed);
    } finally {
        internalTimerSpeedToggleInProgress = false;
    }

    recoveryModulesPaused = true;
    return true;
}

private void pauseModuleForRecovery(Module module) {
    if (module == null || module == this) return;
    if (!module.isActive()) return;

    recoveryPausedModules.add(module);
    if (module instanceof HighwayBuilderTHM builder) {
        if (!builder.prepareForMonitorReconnectPause(activeReconnectCycleId)) {
            enterReconnectSafetyStop("Unable to establish reconnect baseline before monitor pause.");
            return;
        }
        builder.disableForMonitorRealignPause();
    } else {
        module.disable();
    }
}
```

=== WHY ===

- Phase 3 needs reconnect baseline ownership to be established before monitor-driven pause, not guessed later.
- The monitor should only gate the pause and never become the source of truth for restore.

### Monitor Post-Join Timer/Speed Restore Bypass

=== FILE: THMHwyMonitor.java ===
=== CURRENT ===

```java
1677:    private void restorePostJoinModuleStatesIfNeeded() {
1678:        if (!postJoinModuleStateCaptured) return;
1679:        if (!isSuccessfullyConnectedToServer()) {
1680:            return;
1681:        }
1682:
1683:        Timer timer = Modules.get().get(Timer.class);
1684:        Speed speed = Modules.get().get(Speed.class);
1685:        internalTimerSpeedToggleInProgress = true;
1686:        try {
1687:            if (timerWasActiveBeforePostJoin && timer != null && !timer.isActive()) {
1688:                timer.toggle();
1689:                info("Restored Timer state after post-join routine.");
1690:            }
1691:
1692:            if (speedWasActiveBeforePostJoin && speed != null && !speed.isActive()) {
1693:                speed.toggle();
1694:                info("Restored Speed state after post-join routine.");
1695:            }
1696:        } finally {
1697:            internalTimerSpeedToggleInProgress = false;
1698:        }
1699:
1700:        boolean timerRestored = !timerWasActiveBeforePostJoin || (timer != null && timer.isActive());
1701:        boolean speedRestored = !speedWasActiveBeforePostJoin || (speed != null && speed.isActive());
1702:        if (!timerRestored || !speedRestored) {
1703:            return;
1704:        }
1705:
1706:        postJoinModuleStateCaptured = false;
1707:        timerWasActiveBeforePostJoin = false;
1708:        speedWasActiveBeforePostJoin = false;
```

=== SIMULATED CHANGE ===

```java
private void restorePostJoinModuleStatesIfNeeded() {
    if (activeReconnectCycleId > 0L) {
        // Reconnect generations no longer restore Timer/Speed here.
        // Builder-owned reconnect baseline logic is the only restore authority.
        return;
    }

    if (!postJoinModuleStateCaptured) return;
    if (!isSuccessfullyConnectedToServer()) {
        return;
    }

    Timer timer = Modules.get().get(Timer.class);
    Speed speed = Modules.get().get(Speed.class);
    internalTimerSpeedToggleInProgress = true;
    try {
        if (timerWasActiveBeforePostJoin && timer != null && !timer.isActive()) {
            timer.toggle();
            info("Restored Timer state after post-join routine.");
        }

        if (speedWasActiveBeforePostJoin && speed != null && !speed.isActive()) {
            speed.toggle();
            info("Restored Speed state after post-join routine.");
        }
    } finally {
        internalTimerSpeedToggleInProgress = false;
    }

    boolean timerRestored = !timerWasActiveBeforePostJoin || (timer != null && timer.isActive());
    boolean speedRestored = !speedWasActiveBeforePostJoin || (speed != null && speed.isActive());
    if (!timerRestored || !speedRestored) {
        return;
    }

    postJoinModuleStateCaptured = false;
    timerWasActiveBeforePostJoin = false;
    speedWasActiveBeforePostJoin = false;
}
```

=== WHY ===

- Phase 3 needs a single reconnect restore owner.
- THMHwyMonitor may keep its generic non-reconnect restore path, but reconnect generations must not use it.

### Ghost Disconnect Safety Stop Hardening

=== FILE: THMHwyMonitor.java ===
=== CURRENT ===

```java
@EventHandler
private void onGameJoined(GameJoinedEvent event) {
    wasConnectedLastTick = true;
    clearNonRestartHardFailSignal();
    clearRestartHardFailSignal();
    nonRestartHardFailArmed = false;
    pendingDisconnectScreenEvidenceCheck = false;
    pendingDisconnectScreenEvidenceUntilMs = 0L;
    unresolvedMainServerDisconnectCandidate = false;
}

@EventHandler
private void onGameLeft(GameLeftEvent event) {
    HighwayBuilderTHM builderBeforeDisconnect = Modules.get().get(HighwayBuilderTHM.class);
    boolean builderWasActiveAtDisconnect = builderBeforeDisconnect != null && builderBeforeDisconnect.isActive();
    unresolvedMainServerDisconnectCandidate = builderWasActiveAtDisconnect;
    wasConnectedLastTick = false;
    String disconnectScreenReason = readDisconnectedScreenReasonLower();
    pendingDisconnectScreenEvidenceCheck = false;
    pendingDisconnectScreenEvidenceUntilMs = 0L;

    boolean nonRestartHardFail = nonRestartHardFailArmed || consumeNonRestartHardFailSignal();
    if (!nonRestartHardFail && isKnownNonRestartHardFailMessage(disconnectScreenReason)) {
        nonRestartHardFail = true;
    }
    nonRestartHardFailArmed = false;

    if (nonRestartHardFail) {
        unresolvedMainServerDisconnectCandidate = false;
        handleDetectedNonRestartHardFail("onGameLeft");
        return;
    }

    if (consumeRestartHardFailSignal()) {
        armRestartDisconnectEvidence("hb-signal");
    }

    String restartEvidence = consumeRestartDisconnectEvidence();

    if (restartEvidence != null) {
        unresolvedMainServerDisconnectCandidate = false;
        info("Disconnect matched restart evidence (%s). Treating as restart.", restartEvidence);
        handleRestartDetectionTrigger();
        return;
    }

    pendingDisconnectScreenEvidenceCheck = true;
    pendingDisconnectScreenEvidenceUntilMs = System.currentTimeMillis() + DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS;
    info("Disconnect detected without immediate hard-fail evidence. Waiting up to 3.0s for disconnect-screen reason.");
}

private void handleReconnectAutomationTickLane() {
    handleAutoReconnectToggleTransitions();
    refreshReconnectBaselineValidity();
    maybeRunDelayedMainServerResumeFinalization();
    maybeRunPostRejoinDirectionGate();

    boolean connectedNow = isSuccessfullyConnectedToServer();
    handlePendingDisconnectScreenEvidenceCheck(connectedNow);
    wasConnectedLastTick = connectedNow;

    if (consumeNonRestartHardFailSignal()) {
        nonRestartHardFailArmed = true;
        handleDetectedNonRestartHardFail("signal");
    }

    if (reconnectService().isReconnectArmed() && restartRecoveryActive) {
        ensureHighwayBuilderDisabledForRestart("tick guard", false);
    }

    if (connectedNow) maybeTakeDeferredRestartScreenshotAfterReconnect("tick");
}

private void enterReconnectSafetyStop(String reason) {
    HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
    long cycleId = activeReconnectCycleId;

    if (builder != null) {
        builder.restoreCenterSpeedBaselineForFailedReconnect(cycleId);
        builder.disableForReconnectSafetyStop();
    }

    if (autoScreenshotOnRestartDetection.get() && !postRejoinTerminalScreenshotTaken) {
        postRejoinTerminalScreenshotTaken = true;
        takeRestartScreenshot();
    }

    clearRestartRecoveryState("reconnect-safety-stop", true, true);

    if (mc != null) {
        mc.setScreen(new DisconnectedScreen(
            new TitleScreen(),
            Text.of("THMHwyMonitor Safety Stop"),
            Text.of(reason + " HighwayBuilder stayed off for safety.")
        ));
    }

    if (isActive()) toggle();
}

private boolean isSuccessfullyConnectedToServer() {
    return mc != null
        && mc.player != null
        && mc.world != null
        && mc.getNetworkHandler() != null
        && !(mc.currentScreen instanceof DisconnectedScreen);
}
```

=== SIMULATED CHANGE ===

```java
private boolean intentionalSafetyDisconnectArmed;
private boolean disableMonitorAfterIntentionalSafetyDisconnect;

private boolean hasLiveServerConnection() {
    return mc != null
        && mc.player != null
        && mc.world != null
        && mc.getNetworkHandler() != null
        && mc.getNetworkHandler().getConnection() != null
        && mc.getNetworkHandler().getConnection().isOpen();
}

private boolean isSuccessfullyConnectedToServer() {
    return hasLiveServerConnection() && !(mc.currentScreen instanceof DisconnectedScreen);
}

private boolean reconnectRecoveryInFlight() {
    boolean cycleBound = activeReconnectCycleId != 0L;
    if (!cycleBound) return false;

    return reconnectService().isReconnectArmed()
        || restartBuilderDisableGraceScheduled
        || restartRecoveryActive
        || restartEvidenceGateCycleId == activeReconnectCycleId
        || delayedMainServerResumePending
        || delayedMainServerResumeCycleId == activeReconnectCycleId
        || postRejoinDirectionGateActive
        || deferredRestartScreenshotAfterReconnectPending;
}

private void clearStaleDisconnectedScreenIfLiveConnected() {
    if (!hasLiveServerConnection()) return;
    if (!(mc.currentScreen instanceof DisconnectedScreen)) return;
    info("Clearing stale DisconnectedScreen while client is already live in-world.");
    mc.setScreen(null);
}

private void clearRestartAutomationStateForTerminalStop(String reason) {
    boolean preserveIntentionalSafetyDisconnect = intentionalSafetyDisconnectArmed;
    boolean preserveDeferredDisable = disableMonitorAfterIntentionalSafetyDisconnect;
    clearRestartAutomationState(reason, true, true);
    intentionalSafetyDisconnectArmed = preserveIntentionalSafetyDisconnect;
    disableMonitorAfterIntentionalSafetyDisconnect = preserveDeferredDisable;
}

@EventHandler
private void onGameJoined(GameJoinedEvent event) {
    intentionalSafetyDisconnectArmed = false;
    disableMonitorAfterIntentionalSafetyDisconnect = false;
    wasConnectedLastTick = true;
    clearNonRestartHardFailSignal();
    clearRestartHardFailSignal();
    nonRestartHardFailArmed = false;
    pendingDisconnectScreenEvidenceCheck = false;
    pendingDisconnectScreenEvidenceUntilMs = 0L;
    unresolvedMainServerDisconnectCandidate = false;
    clearStaleDisconnectedScreenIfLiveConnected();
}

@EventHandler
private void onGameLeft(GameLeftEvent event) {
    wasConnectedLastTick = false;
    pendingDisconnectScreenEvidenceCheck = false;
    pendingDisconnectScreenEvidenceUntilMs = 0L;

    if (intentionalSafetyDisconnectArmed) {
        intentionalSafetyDisconnectArmed = false;
        clearRestartAutomationState("intentional-safety-disconnect", true, true);
        unresolvedMainServerDisconnectCandidate = false;
        if (disableMonitorAfterIntentionalSafetyDisconnect) {
            disableMonitorAfterIntentionalSafetyDisconnect = false;
            if (isActive()) toggle();
        }
        return;
    }

    if (reconnectRecoveryInFlight()) {
        unresolvedMainServerDisconnectCandidate = false;
        info("Reconnect transfer hop observed while reconnect recovery is already in flight; suppressing fresh disconnect-evidence cycle.");
        return;
    }

    HighwayBuilderTHM builderBeforeDisconnect = Modules.get().get(HighwayBuilderTHM.class);
    boolean builderWasActiveAtDisconnect = builderBeforeDisconnect != null && builderBeforeDisconnect.isActive();
    unresolvedMainServerDisconnectCandidate = builderWasActiveAtDisconnect;
    String disconnectScreenReason = readDisconnectedScreenReasonLower();

    boolean nonRestartHardFail = nonRestartHardFailArmed || consumeNonRestartHardFailSignal();
    if (!nonRestartHardFail && isKnownNonRestartHardFailMessage(disconnectScreenReason)) {
        nonRestartHardFail = true;
    }
    nonRestartHardFailArmed = false;

    if (nonRestartHardFail) {
        unresolvedMainServerDisconnectCandidate = false;
        handleDetectedNonRestartHardFail("onGameLeft");
        return;
    }

    if (consumeRestartHardFailSignal()) {
        armRestartDisconnectEvidence("hb-signal");
    }

    String restartEvidence = consumeRestartDisconnectEvidence();
    if (restartEvidence != null) {
        unresolvedMainServerDisconnectCandidate = false;
        info("Disconnect matched restart evidence (%s). Treating as restart.", restartEvidence);
        handleRestartDetectionTrigger();
        return;
    }

    pendingDisconnectScreenEvidenceCheck = true;
    pendingDisconnectScreenEvidenceUntilMs = System.currentTimeMillis() + DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS;
    info("Disconnect detected without immediate hard-fail evidence. Waiting up to 3.0s for disconnect-screen reason.");
}

private void handleReconnectAutomationTickLane() {
    handleAutoReconnectToggleTransitions();
    refreshReconnectBaselineValidity();
    maybeRunDelayedMainServerResumeFinalization();
    maybeRunPostRejoinDirectionGate();

    boolean liveConnectedNow = hasLiveServerConnection();
    clearStaleDisconnectedScreenIfLiveConnected();
    handlePendingDisconnectScreenEvidenceCheck(liveConnectedNow);
    wasConnectedLastTick = liveConnectedNow;

    if (consumeNonRestartHardFailSignal()) {
        nonRestartHardFailArmed = true;
        handleDetectedNonRestartHardFail("signal");
    }

    if (reconnectService().isReconnectArmed() && restartRecoveryActive) {
        ensureHighwayBuilderDisabledForRestart("tick guard", false);
    }

    if (liveConnectedNow) maybeTakeDeferredRestartScreenshotAfterReconnect("tick");
}

private void enterReconnectSafetyStop(String reason) {
    HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
    long cycleId = activeReconnectCycleId;

    if (builder != null) {
        builder.restoreCenterSpeedBaselineForFailedReconnect(cycleId);
        builder.disableForReconnectSafetyStop();
    }

    if (autoScreenshotOnRestartDetection.get() && !postRejoinTerminalScreenshotTaken) {
        postRejoinTerminalScreenshotTaken = true;
        takeRestartScreenshot();
    }

    intentionalSafetyDisconnectArmed = true;
    disableMonitorAfterIntentionalSafetyDisconnect = true;
    clearRestartAutomationStateForTerminalStop("reconnect-safety-stop");

    if (hasLiveServerConnection()) {
        mc.getNetworkHandler().getConnection().disconnect(Text.of("THMHwyMonitor Safety Stop: " + reason));
        return;
    }

    intentionalSafetyDisconnectArmed = false;
    disableMonitorAfterIntentionalSafetyDisconnect = false;
    if (mc != null) {
        mc.setScreen(new DisconnectedScreen(
            new TitleScreen(),
            Text.of("THMHwyMonitor Safety Stop"),
            Text.of(reason + " HighwayBuilder stayed off for safety.")
        ));
    }

    if (isActive()) toggle();
}
```

=== WHY ===

- Terminal reconnect safety-stop must not leave the player live on the server behind a fake disconnect UI.
- Cleanup for terminal stop must preserve the intentional-disconnect flag until `onGameLeft()` consumes it, and the monitor should only disable itself after that intentional disconnect is observed.
- Reconnect transfer hops should stay in a single reconnect lane instead of spawning fresh disconnect-evidence cycles, but suppression must stay scoped to the active reconnect cycle.
- Live connection truth should come from player/world/network state, not stale `DisconnectedScreen` UI.

### Builder Reconnect Baseline Ownership

=== FILE: HighwayBuilderTHM.java ===
=== CURRENT ===

```java
1809:    private boolean ensureCenterSpeedSnapshotCaptured(String reason) {
1810:        if (centerSpeedSnapshotOwned && centerSpeedSnapshot != null) {
1811:            restockDebug("Center/Speed baseline reused from memory (reason=%s, active=%s, timerActive=%s, monitorOwned=%s, lastReason=%s).",
1812:                reason,
1813:                centerSpeedSnapshot.wasActive(),
1814:                centerSpeedSnapshot.timerWasActive(),
1815:                centerSpeedMonitorRecoveryOwned,
1816:                centerSpeedLastReason
1817:            );
1818:            return true;
1819:        }
...
1862:    public void preserveCenterSpeedBaselineForMonitorRecovery(String reason) {
1863:        if (centerSpeedSnapshotOwned && centerSpeedSnapshot != null) {
1864:            centerSpeedMonitorRecoveryOwned = true;
1865:            centerSpeedLastReason = "monitor-reuse:" + reason;
1866:            restockDebug("Center/Speed baseline preserved for monitor recovery (reason=%s, reused=true, active=%s, timerActive=%s, overrideActive=%s).",
1867:                reason,
1868:                centerSpeedSnapshot.wasActive(),
1869:                centerSpeedSnapshot.timerWasActive(),
1870:                centerSpeedOverrideActive
1871:            );
1872:            return;
1873:        }
...
1928:    private void restoreCenterSpeedIfOwned(String reason) {
1929:        if (!centerSpeedSnapshotOwned || centerSpeedSnapshot == null) return;
...
1961:            if (!isCenterSpeedStateRestored(speed, timer)) {
1962:                centerSpeedRestorePending = true;
1963:                if (centerSpeedRestoreRetryTicks <= 0) centerSpeedRestoreRetryTicks = CENTER_SPEED_RESTORE_RETRY_WINDOW_TICKS;
1964:                centerSpeedLastReason = "restore-deferred:" + reason;
...
2002:    private void tickDeferredCenterSpeedRestore() {
2003:        if (!centerSpeedRestorePending || !centerSpeedSnapshotOwned || centerSpeedSnapshot == null) return;
...
2043:    private boolean isCenterSpeedStateRestored(Speed speed, Timer timer) {
2044:        if (speed == null || centerSpeedSnapshot == null) return false;
...
2046:        boolean timerStateMatches = timer == null
2047:            ? !centerSpeedSnapshot.timerWasActive()
2048:            : timer.isActive() == centerSpeedSnapshot.timerWasActive();
...
2070:    private void clearCenterSpeedOwnership(String reason) {
2071:        centerSpeedSnapshotOwned = false;
2072:        centerSpeedSnapshot = null;
2073:        centerSpeedOverrideActive = false;
2074:        centerSpeedMonitorRecoveryOwned = false;
2075:        centerSpeedRestorePending = false;
2076:        centerSpeedRestoreRetryTicks = 0;
2077:        centerSpeedLastReason = reason == null ? "" : reason;
2078:    }
```

=== SIMULATED CHANGE ===

```java
private enum ReconnectBaselineLeaseState {
    CAPTURED,
    INVALIDATED,
    CONSUMED
}

private record ReconnectBaselinePayload(
    String speedModeName,
    double vanillaSpeed,
    double ncpSpeed,
    boolean ncpSpeedLimit,
    double timer,
    boolean inLiquids,
    boolean whenSneaking,
    boolean vanillaOnGround,
    boolean speedWasActive,
    boolean timerWasActive,
    String timerOverrideName
) {}

private record ReconnectBaselineLease(
    long generation,
    ReconnectBaselineLeaseState state,
    ReconnectBaselinePayload payload
) {}

private ReconnectBaselineLease reconnectBaselineLease;
private boolean reconnectBaselineRestoreInProgress;

public boolean prepareForMonitorReconnectPause(long generation) {
    if (hasUsableReconnectBaselineLease(generation)) return true;
    if (reconnectBaselineLease != null && reconnectBaselineLease.generation() == generation) return false;

    if (centerSpeedOverrideActive) {
        // Never overwrite the real user baseline with temporary Center override state.
        return false;
    }

    ReconnectBaselinePayload payload = captureReconnectBaselinePayload();
    if (payload == null) return false;

    reconnectBaselineLease = new ReconnectBaselineLease(generation, ReconnectBaselineLeaseState.CAPTURED, payload);
    return true;
}

public void refreshReconnectBaselineValidity(long activeGeneration) {
    if (reconnectBaselineLease == null || reconnectBaselineRestoreInProgress) return;
    if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return;

    if (activeGeneration <= 0L || reconnectBaselineLease.generation() != activeGeneration) {
        invalidateReconnectBaseline("generation-changed");
        return;
    }

    if (!reconnectBaselineMatchesLiveState(reconnectBaselineLease.payload())) {
        invalidateReconnectBaseline("live-state-mismatch");
    }
}

private boolean hasUsableReconnectBaselineLease(long generation) {
    return reconnectBaselineLease != null
        && reconnectBaselineLease.generation() == generation
        && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED
        && reconnectBaselineMatchesLiveState(reconnectBaselineLease.payload());
}

private ReconnectBaselinePayload captureReconnectBaselinePayload() {
    Speed speed = Modules.get().get(Speed.class);
    Timer timer = Modules.get().get(Timer.class);
    if (speed == null) return null;

    return new ReconnectBaselinePayload(
        speed.speedMode.get().name(),
        speed.vanillaSpeed.get(),
        speed.ncpSpeed.get(),
        speed.ncpSpeedLimit.get(),
        speed.timer.get(),
        speed.inLiquids.get(),
        speed.whenSneaking.get(),
        speed.vanillaOnGround.get(),
        speed.isActive(),
        timer != null && timer.isActive(),
        timer == null ? "NONE" : timer.getOverride().name()
    );
}

private boolean reconnectBaselineMatchesLiveState(ReconnectBaselinePayload payload) {
    Speed speed = Modules.get().get(Speed.class);
    Timer timer = Modules.get().get(Timer.class);
    if (speed == null || payload == null) return false;

    boolean timerStateMatches = timer == null
        ? !payload.timerWasActive()
        : timer.isActive() == payload.timerWasActive()
            && timer.getOverride().name().equals(payload.timerOverrideName());

    return timerStateMatches
        && speed.isActive() == payload.speedWasActive()
        && speed.speedMode.get() == parseCenterSpeedModeOrDefault(payload.speedModeName())
        && Double.compare(speed.vanillaSpeed.get(), payload.vanillaSpeed()) == 0
        && Double.compare(speed.ncpSpeed.get(), payload.ncpSpeed()) == 0
        && speed.ncpSpeedLimit.get() == payload.ncpSpeedLimit()
        && Double.compare(speed.timer.get(), payload.timer()) == 0
        && speed.inLiquids.get() == payload.inLiquids()
        && speed.whenSneaking.get() == payload.whenSneaking()
        && speed.vanillaOnGround.get() == payload.vanillaOnGround();
}

private boolean consumeReconnectBaseline(long generation) {
    if (reconnectBaselineLease == null) return false;
    if (reconnectBaselineLease.generation() != generation) return false;
    if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return false;

    Speed speed = Modules.get().get(Speed.class);
    Timer timer = Modules.get().get(Timer.class);
    if (speed == null) return false;

    ReconnectBaselinePayload payload = reconnectBaselineLease.payload();
    reconnectBaselineRestoreInProgress = true;
    try {
        speed.speedMode.set(parseCenterSpeedModeOrDefault(payload.speedModeName()));
        speed.vanillaSpeed.set(payload.vanillaSpeed());
        speed.ncpSpeed.set(payload.ncpSpeed());
        speed.ncpSpeedLimit.set(payload.ncpSpeedLimit());
        speed.timer.set(payload.timer());
        speed.inLiquids.set(payload.inLiquids());
        speed.whenSneaking.set(payload.whenSneaking());
        speed.vanillaOnGround.set(payload.vanillaOnGround());

        if (payload.speedWasActive() != speed.isActive()) speed.toggle();
        if (timer != null) {
            if (payload.timerWasActive() != timer.isActive()) timer.toggle();
            timer.setOverride(parseReconnectTimerOverrideOrDefault(payload.timerOverrideName()));
        }
    } finally {
        reconnectBaselineRestoreInProgress = false;
    }

    if (!reconnectBaselineMatchesLiveState(payload)) return false;

    reconnectBaselineLease = new ReconnectBaselineLease(
        generation,
        ReconnectBaselineLeaseState.CONSUMED,
        payload
    );
    return true;
}

public boolean restoreCenterSpeedBaselineForFailedReconnect(long generation) {
    if (reconnectBaselineLease == null || reconnectBaselineLease.generation() != generation) return false;
    if (reconnectBaselineLease.state() == ReconnectBaselineLeaseState.INVALIDATED) return false;
    return consumeReconnectBaseline(generation);
}

private void invalidateReconnectBaseline(String reason) {
    if (reconnectBaselineLease == null || reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return;

    reconnectBaselineLease = new ReconnectBaselineLease(
        reconnectBaselineLease.generation(),
        ReconnectBaselineLeaseState.INVALIDATED,
        reconnectBaselineLease.payload()
    );
}

private void onExternalReconnectRestoreStateMutation() {
    if (reconnectBaselineRestoreInProgress) return;
    invalidateReconnectBaseline("external-mutation");
}

private void tickDeferredCenterSpeedRestore() {
    if (reconnectBaselineLease != null && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED) return;
    // Existing generic non-reconnect deferred restore behavior remains here.
}
```

=== WHY ===

- Phase 3 is simpler and safer as a one-shot reconnect baseline lease: captured, invalidated, or consumed.
- Invalidated or consumed reconnect generations are terminal and may not be recaptured.
- Reconnect baselines must never be restored later by deferred generic logic.
- Effective Timer override/runtime state must be restored from the captured override value, not a placeholder.
- Reconnect restore writes must not self-invalidate the lease; external mutation invalidation applies only outside the guarded restore window.

### Builder Reconnect Baseline Invalidation Wiring

=== FILE: HighwayBuilderTHM.java ===
=== CURRENT ===

```java
1233:        if (!isMonitorPauseDeactivate) restoreCenterSpeedIfOwned("module-deactivate");
1234:        Modules.get().get(Timer.class).setOverride(Timer.OFF);
```

=== SIMULATED CHANGE ===

```java
if (!isMonitorPauseDeactivate) {
    invalidateReconnectBaseline("non-monitor-deactivate");
    restoreCenterSpeedIfOwned("module-deactivate");
}
Modules.get().get(Timer.class).setOverride(Timer.OFF);

// Also wired from reconnect-owned mutation observers:
// - Speed mode/value changes outside resumeFromReconnect(...)
// - Timer active-state changes outside resumeFromReconnect(...)
// - Timer override changes outside resumeFromReconnect(...)
// Those paths call onExternalReconnectRestoreStateMutation().
```

=== WHY ===

- Outside mutation invalidation has to be wired to real reconnect-owned call paths, not left as an unused helper.
- Non-monitor deactivate must invalidate the reconnect lease before any later reconnect restore attempt.

## 4. Notes and Assumptions

- This is a review artifact only, not executable code.
- The live `.java` files remain unchanged while this simulation is reviewed.
- This simulation now covers:
  - monitor-side direction proof
  - dedicated reconnect-only Builder activation handoff
  - reconnect-only Speed/Timer ownership cleanup sketch
  - reconnect safety-stop truthfulness and stale disconnect UI cleanup
- It still does not simulate:
  - the full production implementation details of every helper or state transition
  - unrelated monitor/Builder refactors outside reconnect safety

## 5. Questions for Review

```text
Questions for review:
1. Is there any remaining reconnect path here that still resumes from yaw?
2. Is the simulated Builder reconnect entrypoint enough to avoid wrong-way activate-time init?
3. Is any reconnect ambiguity path still failing open instead of failing closed?
4. Is there any remaining reconnect Speed/Timer ownership hole that would restore the wrong state or leave stale ownership behind?
5. Is any safety-stop path still able to show disconnect UI while leaving a live server connection open?
6. Can the intentional safety-disconnect flag still be cleared too early or survive long enough to poison a later unrelated disconnect?
```
