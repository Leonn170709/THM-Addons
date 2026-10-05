/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.utils.PacketPlaceTracker;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;

import java.util.List;

public class ScaffoldTHM extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ListMode> listMode = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("block-list-mode")
        .description("Block list selection mode.")
        .defaultValue(ListMode.Whitelist)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks allowed to scaffold.")
        .defaultValue(Blocks.NETHERRACK)
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .build()
    );

    private final Setting<List<Block>> blacklist = sgGeneral.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("Blocks denied to scaffold.")
        .defaultValue(Blocks.OBSIDIAN)
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .build()
    );

    private final Setting<Integer> ext = sgGeneral.add(new IntSetting.Builder()
        .name("extend")
        .description("How far to place in front of you.")
        .defaultValue(1)
        .range(0, 5)
        .build()
    );

    private final Setting<Boolean> packetPlaceOnce = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-place-once")
        .description("Experimental: one packet per block, sent again only once the server says it's still air.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepY = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-y")
        .description("Places blocks only at a specific Y value.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Y value to scaffold at.")
        .defaultValue(119)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .visible(keepY::get)
        .build()
    );

    private boolean worked = false;

    public ScaffoldTHM() {
        super(THMAddon.MAIN, "scaffold-THM", "Scaffolds blocks under you.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        float f = Mth.sin(mc.player.getYRot() * 0.017453292f);
        float g = Mth.cos(mc.player.getYRot() * 0.017453292f);

        for (int i = 0; i <= (mc.player.getDeltaMovement().x == 0.0 && mc.player.getDeltaMovement().z == 0.0 ? 0 : ext.get()); i++) {
            // Loop body
            Vec3 pos = mc.player.position().add(-f * i, -0.5, g * i);
            if (keepY.get()) ((IVec3) pos).meteor$setY(height.get() - 1.0);

            BlockPos bPos = BlockPos.containing(pos);

            if (!mc.level.getWorldBorder().isWithinBounds(bPos) || !mc.level.getBlockState(bPos).canBeReplaced()) {
                worked = false;
                continue;
            }
            worked = true;
            if (packetPlaceOnce.get() && !PacketPlaceTracker.canSend(bPos)) continue;

            // Find slot with a block
            FindItemResult item;
            if (listMode.get() == ListMode.Whitelist) {
                item = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem && whitelist.get().contains(Block.byItem(itemStack.getItem())));
            } else {
                item = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem && !blacklist.get().contains(Block.byItem(itemStack.getItem())));
            }
            if (!item.found()) {
                return;
            } else {
                InvUtils.swap(item.slot(), true);
            }

            mc.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(pos, Direction.getApproximateNearest(pos).getOpposite(), bPos, true), 0));
            if (packetPlaceOnce.get()) PacketPlaceTracker.markSent(bPos, PacketPlaceTracker.DEFAULT_RESEND_TICKS);

            InvUtils.swapBack();
        }
    }
    public enum ListMode implements DescribedOption {
        Blacklist,
        Whitelist;

        @Override
        public String description() {
            return switch (this) {
                case Blacklist -> "Avoid placing listed blocks.";
                case Whitelist -> "Place only listed blocks.";
            };
        }
    }

    public boolean hasWorked() {
        return worked;
    }
}
