package xyz.thm.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class UUIDCommand extends Command {
    public UUIDCommand() {
        super("uuid", "Returns a players uuid.");
    }

    private List<String> getTabPlayerNames() {
        if (mc.getNetworkHandler() == null) return List.of();
        return mc.getNetworkHandler().getPlayerList()
            .stream()
            .map(e -> e.getProfile().name())
            .toList();
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .uuid -> eigene UUID
        builder.executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;
            info("Your UUID is " + mc.player.getUuid().toString());
            return SINGLE_SUCCESS;
        });

        // .uuid normal <name> -> echte UUID via tab list
        builder.then(
            literal("normal").then(
                argument("name", StringArgumentType.string())
                    .suggests((context, suggestionsBuilder) -> {
                        String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                        getTabPlayerNames().stream()
                            .filter(name -> name.toLowerCase().startsWith(remaining))
                            .forEach(suggestionsBuilder::suggest);
                        return suggestionsBuilder.buildFuture();
                    })
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        if (mc.getNetworkHandler() == null) return SINGLE_SUCCESS;

                        PlayerListEntry entry = mc.getNetworkHandler().getPlayerList()
                            .stream()
                            .filter(e -> e.getProfile().name().equalsIgnoreCase(name))
                            .findFirst()
                            .orElse(null);

                        if (entry == null) {
                            warning("Player '" + name + "' not found in player list.");
                        } else {
                            info(entry.getProfile().name() + "'s UUID is " + entry.getProfile().id().toString());
                        }
                        return SINGLE_SUCCESS;
                    })
            )
        );

        // .uuid calculate <name> -> berechnet Offline/Cracked UUID
        builder.then(
            literal("calculate").then(
                argument("name", StringArgumentType.string())
                    .suggests((context, suggestionsBuilder) -> {
                        String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                        getTabPlayerNames().stream()
                            .filter(name -> name.toLowerCase().startsWith(remaining))
                            .forEach(suggestionsBuilder::suggest);
                        return suggestionsBuilder.buildFuture();
                    })
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        UUID offlineUuid = UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)
                        );
                        info("Offline UUID for '" + name + "': " + offlineUuid);
                        info("Only matches if server runs in offline/cracked mode. Calculates it based on the servers algorithm");
                        return SINGLE_SUCCESS;
                    })
            )
        );
    }
}
