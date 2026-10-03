/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import xyz.thm.addon.modules.THMStashMover;

public class THMStashMoverCommand extends Command {
    public THMStashMoverCommand() {
        super("stashmover", "Manages THM Stash mover kit pairs.", "smover");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("pair")
            .then(literal("list").executes(context -> {
                THMStashMover module = module();
                info(module.describePairs());
                return SINGLE_SUCCESS;
            }))
            .then(literal("create")
                .then(argument("name", StringArgumentType.greedyString())
                    .executes(context -> {
                        THMStashMover module = module();
                        String name = StringArgumentType.getString(context, "name");
                        info(module.createPair(name));
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("delete")
                .then(argument("name", StringArgumentType.greedyString())
                    .executes(context -> {
                        THMStashMover module = module();
                        String name = StringArgumentType.getString(context, "name");
                        info(module.deletePair(name));
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("rename")
                .then(argument("old", StringArgumentType.string())
                    .then(argument("new", StringArgumentType.greedyString())
                        .executes(context -> {
                            THMStashMover module = module();
                            String oldName = StringArgumentType.getString(context, "old");
                            String newName = StringArgumentType.getString(context, "new");
                            info(module.renamePair(oldName, newName));
                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
            .then(literal("select")
                .then(argument("name", StringArgumentType.greedyString())
                    .executes(context -> {
                        THMStashMover module = module();
                        String name = StringArgumentType.getString(context, "name");
                        info(module.selectPair(name));
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );

        builder.then(literal("capture")
            .then(literal("input-approach").executes(context -> {
                info(module().captureApproach(true));
                return SINGLE_SUCCESS;
            }))
            .then(literal("deposit-approach").executes(context -> {
                info(module().captureApproach(false));
                return SINGLE_SUCCESS;
            }))
            .then(literal("input-ender").executes(context -> {
                info(module().captureEnderAccess(true));
                return SINGLE_SUCCESS;
            }))
            .then(literal("deposit-ender").executes(context -> {
                info(module().captureEnderAccess(false));
                return SINGLE_SUCCESS;
            }))
            .then(literal("input-ender-approach").executes(context -> {
                info(module().captureEnderApproach(true));
                return SINGLE_SUCCESS;
            }))
            .then(literal("deposit-ender-approach").executes(context -> {
                info(module().captureEnderApproach(false));
                return SINGLE_SUCCESS;
            }))
        );
    }

    private THMStashMover module() {
        return Modules.get().get(THMStashMover.class);
    }
}
