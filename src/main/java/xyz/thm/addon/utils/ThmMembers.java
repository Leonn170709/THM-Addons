/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;


public final class ThmMembers {
    public static final class Member {
        public final String name;
        public final String[] mcNames;
        public final String rank;
        public final String rankId;
        public final String branch;
        public final String discordName;

        public Member(String name, String[] mcNames, String rank, String rankId, String branch, String discordName) {
            this.name = name;
            this.mcNames = mcNames;
            this.rank = rank;
            this.rankId = rankId;
            this.branch = branch;
            this.discordName = discordName;
        }
    }

    /** THM ranks, highest first — the ordering rank filters compare against. */
    public static final List<String> RANK_HIERARCHY = List.of(
        "King/Owner",
        "Prince/Co-Owner",
        "Prince",
        "The Chosen One",
        "Major",
        "Mayor",
        "Elite Highway Man",
        "Journeyman",
        "Highway Man",
        "PvP Manager",
        "PvP Lead",
        "PvP Branch",
        "Apprentice",
        "Retired",
        "Novice",
        "PVP Novice",
        "Bot"
    );

    /** Position in {@link #RANK_HIERARCHY} (0 = highest), or -1 for an absent or unlisted rank. */
    public static int rankIndex(String rank) {
        if (rank == null) return -1;
        String trimmed = rank.trim();
        for (int i = 0; i < RANK_HIERARCHY.size(); i++) {
            if (RANK_HIERARCHY.get(i).equalsIgnoreCase(trimmed)) return i;
        }
        return -1;
    }

    private static List<Member> cachedMembers = null;
    private static Map<String, Member> cachedByMcName = null;
    private static boolean fetchInProgress = false;
    private static Thread fetchThread = null;
    private static boolean startupFetchStarted = false;

    private static final long HIGHWAY_STATUS_REFRESH_MS = 10 * 60 * 1000; // 10 minutes
    private static Map<String, String> cachedHighwayByMcName = new HashMap<>();
    private static Map<String, String> cachedCapeByMcName = new HashMap<>();
    private static boolean eventSubscribed = false;

    private static volatile boolean joinedOnce = false;

    private static final Object SERVER_JOIN_LISTENER = new Object() {
        @EventHandler
        private void onGameJoined(GameJoinedEvent event) {
            if (joinedOnce) return; // dimension change, not server join
            joinedOnce = true;
            refreshNow();
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            joinedOnce = false;
        }
    };
    private static long lastHighwayStatusFetchTime = 0;
    private static boolean highwayStatusPollingStarted = false;

    private ThmMembers() {
    }

    private static void refreshIfNeeded() {
        if (cachedMembers == null) startFetch(false);
    }

    private static void startFetch(boolean force) {
        synchronized (ThmMembers.class) {
            if (fetchInProgress) return;
            if (!force && startupFetchStarted) return;

            fetchInProgress = true;
            startupFetchStarted = true;
            fetchThread = new Thread(() -> runFetchLoop(force), "THM-MemberFetch");
            fetchThread.setDaemon(true);
            fetchThread.start();
        }
    }

    private static void runFetchLoop(boolean force) {
        long delayMs = 2000;
        while (true) {
            List<Member> members = APIUtils.fetchMembersFromApi();
            if (members != null) {
                synchronized (ThmMembers.class) {
                    updateCache(members);
                    fetchInProgress = false;
                }
                return;
            }

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                synchronized (ThmMembers.class) {
                    fetchInProgress = false;
                }
                Thread.currentThread().interrupt();
                return;
            }

            delayMs = Math.min(delayMs * 2, 30000);
            if (!force && startupFetchStarted && cachedMembers != null) return;
        }
    }

    private static void updateCache(List<Member> members) {
        cachedMembers = members;
        cachedByMcName = new HashMap<>();
        for (Member member : members) {
            for (String mcName : member.mcNames) {
                String normalized = normalizeMcName(mcName);
                if (normalized == null) continue;
                Member existing = cachedByMcName.get(normalized);
                if (existing == null || (!isKillOnSight(existing) && isKillOnSight(member))) {
                    cachedByMcName.put(normalized, member);
                }
            }
        }
    }

    public static synchronized List<Member> getCachedMembers() {
        refreshIfNeeded();
        return cachedMembers == null ? Collections.emptyList() : cachedMembers;
    }

    public static synchronized Member getMemberByMcName(String mcName) {
        refreshIfNeeded();
        if (cachedByMcName == null) return null;
        String normalized = normalizeMcName(mcName);
        if (normalized == null) return null;
        return cachedByMcName.get(normalized);
    }

    public static synchronized List<Member> getCachedKosMembers() {
        refreshIfNeeded();
        if (cachedMembers == null) return Collections.emptyList();
        return cachedMembers.stream()
            .filter(ThmMembers::isKillOnSight)
            .toList();
    }

    public static boolean isKillOnSight(Member member) {
        if (member == null) return false;
        return isKillOnSight(member.rank, member.rankId, member.branch);
    }

    public static boolean isIgnore(Member member) {
        if (member == null) return false;
        return isIgnore(member.rank, member.rankId, member.branch);
    }

    public static synchronized boolean isIgnore(String mcName) {
        Member member = getMemberByMcName(mcName);
        return isIgnore(member);
    }

    public static boolean isKillOnSight(String rank, String branch) {
        return isKillOnSight(rank, null, branch);
    }

    public static boolean isKillOnSight(String rank, String rankId, String branch) {
        String rankNorm = normalizeRankField(rank);
        String rankIdNorm = normalizeRankField(rankId);
        String branchNorm = normalizeRankField(branch);

        if (rankNorm == null && rankIdNorm == null && branchNorm == null) return false;

        if (rankIdNorm != null && rankIdNorm.equals("kos")) return true;
        if (branchNorm != null && branchNorm.equals("kos")) return true;

        if (rankNorm == null) return false;
        return rankNorm.equals("kos") || rankNorm.equals("kill on sight") || rankNorm.equals("kill-on-sight");
    }

    public static boolean isIgnore(String rank, String rankId, String branch) {
        String rankNorm = normalizeRankField(rank);
        String rankIdNorm = normalizeRankField(rankId);
        String branchNorm = normalizeRankField(branch);

        if (rankNorm == null && rankIdNorm == null && branchNorm == null) return false;

        if (rankIdNorm != null && rankIdNorm.equals("ignore")) return true;
        if (branchNorm != null && branchNorm.equals("ignore")) return true;

        if (rankNorm == null) return false;
        return rankNorm.equals("ignore");
    }

    public static synchronized boolean isThmMember(PlayerEntity player) {
        if (player == null) return false;
        Member member = getMemberByMcName(player.getGameProfile().name());
        if (member == null) return false;
        return !isKillOnSight(member) && !isIgnore(member);
    }

    public static synchronized boolean isIgnoredMcName(String mcName) {
        Member member = getMemberByMcName(mcName);
        return isIgnore(member);
    }
    public static synchronized boolean isNovice(String mcName) {
        return hasRank(mcName, "Novice");
    }
    public static synchronized boolean hasRank(String mcName, String rank) {
        Member member = getMemberByMcName(mcName);
        if (member == null || member.rank == null) return false;
        return member.rank.trim().equalsIgnoreCase(rank);
    }

    private static String normalizeMcName(String mcName) {
        if (mcName == null) return null;
        String trimmed = mcName.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || "Unknown".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }

    public static synchronized void resetCache() {
        cachedMembers = null;
        cachedByMcName = null;
        startupFetchStarted = false;
    }

    public static synchronized void refreshNow() {
        resetCache();
        startFetch(true);
        refreshCapeList();
        refreshHighwayStatus();
        CapeManager.redownload();
    }

    public static void refreshHighwayStatus() {
        Thread t = new Thread(() -> {
            Map<String, String> fetched = APIUtils.fetchHighwayStatusFromApi();
            if (fetched != null) {
                synchronized (ThmMembers.class) {
                    cachedHighwayByMcName = fetched;
                    lastHighwayStatusFetchTime = System.currentTimeMillis();
                }
            }
        }, "THM-HighwayStatusFetch");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void initialize() {
        if (!eventSubscribed) {
            MeteorClient.EVENT_BUS.subscribe(SERVER_JOIN_LISTENER);
            eventSubscribed = true;
        }
        startFetch(false);
        startHighwayStatusPollingIfNeeded();
        refreshCapeList();
    }

    public static void refreshCapeList() {
        Thread t = new Thread(() -> {
            Map<String, String> fetched = APIUtils.fetchCapeListFromApi();
            if (fetched != null) {
                synchronized (ThmMembers.class) {
                    cachedCapeByMcName = fetched;
                }
            }
        }, "THM-CapeFetch");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized String getCapeByMcName(String mcName) {
        String normalized = normalizeMcName(mcName);
        if (normalized == null) return null;
        return cachedCapeByMcName.get(normalized);
    }

    private static void startHighwayStatusPollingIfNeeded() {
        synchronized (ThmMembers.class) {
            if (highwayStatusPollingStarted) return;
            highwayStatusPollingStarted = true;
        }

        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Map<String, String> fetched = APIUtils.fetchHighwayStatusFromApi();
                if (fetched != null) {
                    synchronized (ThmMembers.class) {
                        cachedHighwayByMcName = fetched;
                        lastHighwayStatusFetchTime = System.currentTimeMillis();
                    }
                }

                try {
                    Thread.sleep(HIGHWAY_STATUS_REFRESH_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "THM-HighwayStatusFetch");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized String getHighwayStatusByMcName(String mcName) {
        startHighwayStatusPollingIfNeeded();
        String normalized = normalizeMcName(mcName);
        if (normalized == null || cachedHighwayByMcName == null) return null;
        return cachedHighwayByMcName.get(normalized);
    }

    /** Returns the max distance from origin (blocks) this member is allowed to claim via $update. Mirrors the server-side getMemberDistanceLimit logic. */
    public static double getDistanceLimit(Member member) {
        if (member == null || member.rank == null) return 50000;
        String rank = member.rank.trim().toLowerCase(Locale.ROOT);
        if (rank.contains("king") || rank.contains("prince")
            || rank.contains("major") || rank.contains("chosen")) return Double.MAX_VALUE;
        return switch (rank) {
            case "mayor", "elite highway man" -> 500000;
            case "journeyman"        -> 300000;
            case "highway man"       -> 100000;
            case "apprentice"        -> 75000;
            default                  -> 50000;
        };
    }

    public static Color getRankColor(String rankName) {
        return switch (rankName) {
            case "King","King/Owner" -> new Color(255, 217, 94, 255); // Orange
            case "Prince", "Prince/Co-Owner" -> new Color(218, 160, 52, 255); // Deep Pink
            case "The Chosen One" -> new Color(255, 215, 0, 255); // Gold
            case "Major" -> new Color(249, 204, 158, 255); // Tan
            case "Mayor" -> new Color(156, 232, 180, 255); // Light Green
            case "Elite Highway Man" -> new Color(185, 230, 88, 255); // Yellow Green
            case "Journeyman" -> new Color(116, 148, 114, 255); // Sage Green
            case "Highway Man" -> new Color(133, 89, 221, 255); // Purple
            case "PvP Manager" -> new Color(255, 0, 55, 255); // Red
            case "PvP Lead" -> new Color(218, 109, 255, 255); // Magenta
            case "PvP Branch" -> new Color(255, 0, 4, 255); // Bright Red
            case "Apprentice" -> new Color(95, 70, 53, 255); // Brown
            case "Retired" -> new Color(0, 1, 1, 255); // Black
            case "Novice" -> new Color(76, 173, 208, 255); // Cyan
            case "PVP Novice" -> new Color(	156, 60, 62, 255); // Red
            case "Bot" -> new Color(52, 152, 219, 255); // Blue
            case "Kill on Sight", "Kill-on-Sight", "KOS" -> new Color(255, 0, 0, 255); // Red
            default -> new Color(255, 255, 255, 255); // White fallback
        };
    }

    private static String normalizeRankField(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
