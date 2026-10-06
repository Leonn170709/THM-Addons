/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.PearlPhaser;
import xyz.thm.addon.utils.PlacementUtils;
import xyz.thm.addon.utils.RotationUtils;

public class Phase extends Module {

    private final Setting<Boolean> selfWeb = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("self-web")
        .description("Place a cobweb at head level before phasing.")
        .defaultValue(false)
        .build());

    /** Owns the Pearl / Self Place setting groups and the throw itself. */
    private final PearlPhaser pearl = new PearlPhaser(settings);

    public Phase() {
        super(THMAddon.PVP, "phase", "Allows player to phase through solid blocks using ender pearls.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }
        try {
            if (selfWeb.get()) {
                if (mc.gameMode == null || !pearl.canThrow()) return;
                BlockPos head = BlockPos.containing(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
                if (!mc.level.getBlockState(head).is(Blocks.COBWEB)) {
                    if (!mc.level.getBlockState(head).canBeReplaced()) {
                        warning("Head position is blocked.");
                        return;
                    }
                    boolean offhand = mc.player.getOffhandItem().is(Items.COBWEB);
                    int webSlot = -1;
                    for (int i = 0; i < 36; i++) {
                        if (mc.player.getInventory().getItem(i).is(Items.COBWEB)) {
                            webSlot = i;
                            break;
                        }
                    }
                    if (!offhand && webSlot == -1) {
                        warning("No cobwebs available.");
                        return;
                    }
                    if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) {
                        warning("Close the container and clear the cursor first.");
                        return;
                    }
                    Direction side = PlacementUtils.getPlaceSide(head);
                    if (side == null) {
                        warning("No placement face at head level.");
                        return;
                    }
                    BlockPos neighbor = head.relative(side);
                    Vec3 hitPos = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(side.getOpposite().getUnitVec3i()).scale(0.5));
                    float[] angles = RotationUtils.getRotationsTo(mc.player.getEyePosition(), hitPos);
                    InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    RotationUtils rotation = RotationUtils.getInstance();
                    if (!offhand) InventoryManager.swapTo(webSlot, false, true);
                    try {
                        rotation.setRotationSilent(angles[0], angles[1]);
                        var result = mc.gameMode.useItemOn(mc.player, hand,
                            new BlockHitResult(hitPos, side.getOpposite(), neighbor, false));
                        if (!result.consumesAction() || !mc.level.getBlockState(head).is(Blocks.COBWEB)) {
                            warning("Could not place the head cobweb.");
                            return;
                        }
                        mc.player.swing(hand);
                    } finally {
                        if (!offhand) InventoryManager.swapBack(false);
                        rotation.setRotationSilentSync();
                    }
                }
            }
            pearl.throwPearl(null, selfWeb.get());
        } finally {
            toggle();
        }
    }

    @Override
    public String getInfoString() {
        return "Pearl Mode";
    }
}
