/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.commands;
import meteordevelopment.meteorclient.commands.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import xyz.thm.addon.modules.Loadouts;

public class Loadout extends Command {
    public Loadout() { super("loadout", "Save and load inventory configurations."); }
    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("save").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String loadoutName = ctx.getArgument("name", String.class);
            Modules mods = Modules.get();
            if (mods == null) return SINGLE_SUCCESS;
            Loadouts loadouts = mods.get(Loadouts.class);
            if (loadouts.noLoadout(loadoutName)) {
                info("Saving loadout..!", loadouts.name);
            } else {
                info("Overwriting loadout..!", loadouts.name);
            }
            loadouts.saveLoadout(loadoutName);
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("load").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String loadoutName = ctx.getArgument("name", String.class);
            Modules mods = Modules.get();
            if (mods == null) return SINGLE_SUCCESS;
            Loadouts loadouts = mods.get(Loadouts.class);
            if (!loadouts.isActive()) {
                loadouts.toggle();
                loadouts.sendToggledMsg();
            }
            if (loadouts.noLoadout(loadoutName)) {
                info("No loadout was found with the name \"§c§o" + loadoutName + "§7\"§c..!", loadouts.name);
            } else {
                loadouts.loadLoadout(loadoutName);
                info("Loading loadout \"§a§o" + loadoutName + "§7\"§a..!", loadouts.name);
            }
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("delete").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String loadoutName = ctx.getArgument("name", String.class);
            Modules mods = Modules.get();
            if (mods == null) return SINGLE_SUCCESS;
            Loadouts loadouts = mods.get(Loadouts.class);
            if (loadouts.noLoadout(loadoutName)) {
                info("No loadout \"§5§o" + loadoutName + "§7\" to delete§c..!", loadouts.name);
            } else {
                loadouts.deleteLoadout(loadoutName);
            }
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("clear").executes(ctx -> {
            Modules mods = Modules.get();
            if (mods == null) return SINGLE_SUCCESS;
            Loadouts loadouts = mods.get(Loadouts.class);
            loadouts.clearLoadouts();
            info("Loadouts cleared.", loadouts.name);
            return SINGLE_SUCCESS;
        }));
    }
}
