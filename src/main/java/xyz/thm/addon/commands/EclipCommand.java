/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import xyz.thm.addon.utils.THMUtils;

public class EclipCommand extends Command {
    public EclipCommand() {
        super("eclip", "Elyta clip need elytra bypass most anticheats");
    }

    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("blocks", DoubleArgumentType.doubleArg()).executes(context -> {
            LocalPlayer player = mc.player;
            assert player != null;
            double blocks2 = context.getArgument("blocks", Double.class);
            if (work()) {
                blocks = blocks2;
                MeteorClient.EVENT_BUS.subscribe(this);
            } else {
                ticks = 0;
            }
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("up").executes(c -> {
            if (work()) {
                blocks = findBlock(true, 15);
                MeteorClient.EVENT_BUS.subscribe(this);
            } else {
                ticks = 0;
            }
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("down").executes(c -> {
            if (work()) {
                blocks = findBlock(false, 15);
                if (blocks == 0) {
                    error("No valid position found below");
                } else {
                    MeteorClient.EVENT_BUS.subscribe(this);
                }
            } else {
                ticks = 0;
            }
            return SINGLE_SUCCESS;
        }));
    }

    private boolean work() {
        LocalPlayer player = mc.player;
        assert player != null;
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (elytra.found()) {
            ticks = 0;
            return true;
        } else {
            error(Names.get(Items.ELYTRA) + " not found");
            return false;
        }
    }

    private Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }

    private double findBlock(boolean up, int maximum) {
        if (up) {
            BlockPos pos = mc.player.blockPosition();
            for (int i = maximum; i >= 1; i--) {
                if (getBlock(pos.offset(0, i, 0)) == Blocks.AIR
                    && getBlock(pos.offset(0, i + 1, 0)) == Blocks.AIR
                    && getBlock(pos.offset(0, i - 1, 0)) != Blocks.AIR
                ) {
                    return i;
                }
            }
        } else {
            BlockPos pos = mc.player.blockPosition();
            for (int i = -2; i >= -maximum; i--) {
                if (getBlock(pos.offset(0, i, 0)) != Blocks.AIR
                    && getBlock(pos.offset(0, i + 1, 0)) == Blocks.AIR
                    && getBlock(pos.offset(0, i + 2, 0)) == Blocks.AIR
                ) {
                    return i + 1; // negativer Offset zum Zielblock
                }
            }
        }
        return 0;
    }

    private int ticks = 0;
    private int slot = -1;
    private double blocks = 0;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        clip(blocks);
    }

    private void clip(double blocks) {
        if (blocks != 0) {
            LocalPlayer player = mc.player;
            assert player != null;
            switch (ticks) {
                case 0: {
                    FindItemResult elytra = InvUtils.find(Items.ELYTRA);
                    slot = elytra.slot();
                    InvUtils.move().from(slot).toArmor(2);
                    ticks++;
                    break;
                }
                case 1: {
                    mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
                    ticks++;
                    break;
                }
                case 2: {
                    mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
                    ticks++;
                    break;
                }
                case 3: {
                    THMUtils.startFly();
                    ticks++;
                    break;
                }
                case 4: {
                    double targetY = player.getY() + blocks;
                    player.setPos(player.getX(), targetY, player.getZ());
                    mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        player.getX(), targetY, player.getZ(),
                        false, mc.player.horizontalCollision
                    ));
                    ticks++;
                    break;
                }
                case 5: {
                    THMUtils.startFly();
                    ticks++;
                    break;
                }
                case 6: {
                    ticks = 0;
                    InvUtils.move().fromArmor(2).to(slot);
                    MeteorClient.EVENT_BUS.unsubscribe(this);
                    break;
                }
            }
        } else {
            MeteorClient.EVENT_BUS.unsubscribe(this);
        }
    }
}
