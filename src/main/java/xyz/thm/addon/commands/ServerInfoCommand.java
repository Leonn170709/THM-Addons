/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xyz.thm.addon.utils.server.ServerTelemetry;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;

public class ServerInfoCommand extends Command {
    private final ServerTelemetry telemetry = new ServerTelemetry();
    private volatile int generation;
    private long joinedNs;
    private ResourceKey<Level> timeDimension;

    public ServerInfoCommand() {
        super("serverinfo", "Show server details and clock-based uptime estimates.");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> show("all"));
        for (String section : new String[]{"all", "uptime", "tps", "host", "brand", "version", "ping", "players", "world", "network", "session"}) {
            builder.then(literal(section).executes(context -> show(section)));
        }
        for (String section : new String[]{"plugins", "commands", "channels"}) {
            builder.then(literal(section).executes(context -> show(section))
                .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> show(section, IntegerArgumentType.getInteger(context, "page")))));
        }
        builder.then(literal("help").executes(context -> {
            info("%s", "serverinfo [all|uptime|tps|host|brand|version|ping|players|world|network|session]");
            info("%s", "serverinfo [plugins|commands|channels] [page]");
            info("Uptime is inferred from keepalives; world age survives server restarts.");
            return SINGLE_SUCCESS;
        }));
    }

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        reset();
        joinedNs = System.nanoTime();
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) { reset(); }

    private void reset() {
        generation++;
        joinedNs = 0;
        timeDimension = null;
        telemetry.reset();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceive(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        if (event.packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> packet : bundle.subPackets()) observe(packet);
        } else observe(event.packet);
    }

    private void observe(Packet<?> packet) {
        if (!(packet instanceof ClientboundKeepAlivePacket) && !(packet instanceof ClientboundSetTimePacket)) return;
        var connection = mc.getConnection();
        if (connection == null) return;
        int expectedGeneration = generation;
        long receivedNs = System.nanoTime();
        mc.execute(() -> {
            if (generation != expectedGeneration || connection != mc.getConnection() || mc.level == null) return;
            if (packet instanceof ClientboundKeepAlivePacket keepalive) telemetry.observeKeepalive(keepalive.getId(), receivedNs);
            else if (packet instanceof ClientboundSetTimePacket time) {
                updateTimeDimension();
                telemetry.observeWorldTime(time.gameTime(), receivedNs);
            }
        });
    }

    private void updateTimeDimension() {
        if (!mc.level.dimension().equals(timeDimension)) {
            telemetry.resetWorldTime();
            timeDimension = mc.level.dimension();
        }
    }

    private int show(String section) {
        return show(section, 1);
    }

    private int show(String section, int page) {
        if (mc.getConnection() == null || mc.level == null || mc.player == null) {
            warning("Join a server first.");
            return SINGLE_SUCCESS;
        }
        if (section.equals("all")) {
            for (String part : new String[]{"host", "brand", "version", "uptime", "tps", "ping", "players", "world", "network", "session", "plugins", "commands", "channels"}) show(part);
            return SINGLE_SUCCESS;
        }
        var connection = mc.getConnection();
        var server = connection.getServerData();
        updateTimeDimension();
        long now = System.nanoTime();
        switch (section) {
            case "plugins" -> {
                var names = connection.getCommands().getRoot().getChildren().stream().map(CommandNode::getName).toList();
                showList(section, "Plugin/mod namespace hints", ServerTelemetry.pluginHints(names), page);
                info("Command namespaces are hints; hidden plugins and versions cannot be determined.");
            }
            case "commands" -> {
                var names = connection.getCommands().getRoot().getChildren().stream().map(CommandNode::getName).sorted().toList();
                showList(section, "Server-advertised commands", names, page);
                info("Availability depends on server permissions and filtering.");
            }
            case "channels" -> {
                var names = ClientPlayNetworking.getSendable().stream().map(Object::toString).sorted().toList();
                showList(section, "Server-advertised receive channels", names, page);
                info("Channel names can hint at plugins, mods or proxies; they are not an installed list.");
            }
            case "uptime" -> showUptime(now);
            case "tps" -> {
                double tps = telemetry.observedTps(now);
                info("%s", Double.isFinite(tps) ? String.format(Locale.ROOT, "Observed world TPS: %.2f (last update %.1fs ago)", tps, telemetry.timeUpdateAgeMillis(now) / 1000.0)
                    : "Observed world TPS: unavailable; waiting for fresh time packets.");
                info("%s", String.format(Locale.ROOT, "Configured tick rate: %.2f; frozen: %s", mc.level.tickRateManager().tickrate(), mc.level.tickRateManager().isFrozen()));
            }
            case "host" -> {
                info("%s", "Server address: " + (server == null ? "local connection" : server.ip));
                var remote = connection.getConnection().getRemoteAddress();
                if (remote instanceof InetSocketAddress endpoint) {
                    String ip = endpoint.getAddress() == null ? endpoint.getHostString() : endpoint.getAddress().getHostAddress();
                    info("%s", "Connected endpoint: " + (ip.contains(":") ? "[" + ip + "]" : ip) + ":" + endpoint.getPort());
                    info("A proxy may hide the backend host and hosting provider.");
                } else info("Connection is local; no remote host address.");
            }
            case "brand" -> info("%s", "Reported server brand: " + (connection.serverBrand() == null ? "unavailable" : connection.serverBrand()));
            case "version" -> info("%s", server != null && (server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE)
                ? "Advertised version: " + server.version.getString() + "; protocol: " + server.protocol + " (server-list cache)"
                : "Advertised server version: unavailable; translators can hide the backend version.");
            case "ping" -> {
                var player = connection.getPlayerInfo(mc.player.getUUID());
                info("%s", player == null ? "Tab-list ping: unavailable." : "Server-reported tab ping: " + player.getLatency() + " ms");
                if (server != null && server.players != null && server.ping >= 0) info("%s", "Server-list ping: " + server.ping + " ms (cached)");
            }
            case "players" -> {
                info("%s", "Known players: " + connection.getOnlinePlayers().size() + "; tab-listed: " + connection.getListedOnlinePlayers().size());
                if (server != null && server.players != null) info("%s", "Advertised players: " + server.players.online() + "/" + server.players.max() + " (server-list cache)");
                info("%s", "Server-reported online authentication: " + connection.onlineMode());
            }
            case "world" -> {
                info("%s", "Dimension: " + mc.level.dimension().identifier());
                info("%s", telemetry.worldTicks() < 0 ? "Server world age: waiting for a time packet."
                    : "Server world age: " + telemetry.worldTicks() + " ticks (persists across restarts)");
                info("%s", "Simulation distance: " + mc.level.getServerSimulationDistance() + " chunks; requested view distance: " + mc.options.renderDistance().get());
                info("%s", "Loaded client chunks: " + mc.level.getChunkSource().getLoadedChunksCount());
            }
            case "network" -> info("%s", String.format(Locale.ROOT, "Recent packets/s: %.1f received, %.1f sent; last keepalive: %s",
                connection.getConnection().getAverageReceivedPackets(), connection.getConnection().getAverageSentPackets(),
                telemetry.keepaliveAgeMillis(now) < 0 ? "not received" : String.format(Locale.ROOT, "%.1fs ago", telemetry.keepaliveAgeMillis(now) / 1000.0)));
            case "session" -> info("%s", joinedNs == 0 ? "Session duration: unavailable." : "Connected for: " + ServerTelemetry.duration((now - joinedNs) / 1_000_000));
        }
        return SINGLE_SUCCESS;
    }

    private void showList(String section, String label, List<String> values, int page) {
        if (values.isEmpty()) {
            info("%s", label + ": none exposed.");
            return;
        }
        int pages = (values.size() - 1) / 25 + 1;
        if (page > pages) {
            warning("%s", "Page out of range; choose 1-" + pages + ".");
            return;
        }
        int start = (page - 1) * 25;
        info("%s", label + " (" + values.size() + ", page " + page + "/" + pages + "): "
            + String.join(", ", values.subList(start, Math.min(start + 25, values.size()))));
        if (page < pages) info("%s", "Next page: " + toString(section, Integer.toString(page + 1)));
    }

    private void showUptime(long now) {
        long uptime = telemetry.uptimeMillis(now);
        if (uptime >= 0) {
            info("%s", "Host-clock uptime estimate: " + ServerTelemetry.duration(uptime) + " (" + telemetry.clockType() + " keepalives)");
            info("Clock origins and proxies can make this differ from host or process uptime.");
        } else {
            info("%s", switch (telemetry.clockType()) {
                case Collecting -> "Uptime: collecting three consistent keepalives; samples: " + telemetry.keepaliveSamples() + "/3.";
                case Epoch -> "Uptime unavailable: keepalives use an epoch-like clock.";
                case Unsupported -> "Uptime unavailable: keepalive IDs are not a supported clock.";
                default -> "Uptime unavailable: keepalive samples are stale.";
            });
        }
    }
}
