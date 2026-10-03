/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;
//Thank You BepHax for your awesome Mixins
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.interfaces.NoSlowAntiClimb;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.utils.InventoryManager;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
@Mixin(value = NoSlow.class, remap = false)
public abstract class NoSlowMixin implements NoSlowAntiClimb {
    @Shadow @Final protected SettingGroup sgGeneral;
    @Unique private Setting<Boolean> bephax$grimBypass;
    @Unique private Setting<Boolean> bephax$grimV3Bypass;
    @Unique private Setting<Boolean> bephax$grimWebBypass;
    @Unique private Setting<Boolean> bephax$strictMode;
    @Unique private Setting<Boolean> bephax$disableOnElytra;
    @Unique private Setting<NoSlowAntiClimb.Mode> thm$antiClimb;
    @Unique private Setting<Double> bephax$inputMultiplier;
    @Unique private Setting<Double> bephax$grimV3Multiplier;
    @Unique private boolean bephax$sneaking = false;
    @Unique private int bephax$sequenceId = 0;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        bephax$grimBypass = sgGeneral.add(new BoolSetting.Builder()
            .name("grim-bypass")
            .description("Bypasses GrimAC using opposite hand interaction packets")
            .defaultValue(false)
            .build()
        );
        bephax$grimV3Bypass = sgGeneral.add(new BoolSetting.Builder()
            .name("grim-v3-bypass")
            .description("Bypasses GrimAC V3 using item use timing checks")
            .defaultValue(false)
            .build()
        );
        bephax$grimWebBypass = sgGeneral.add(new BoolSetting.Builder()
            .name("grim-web-bypass")
            .description("Bypasses GrimAC web slowdown using block break packets")
            .defaultValue(false)
            .build()
        );
        bephax$strictMode = sgGeneral.add(new BoolSetting.Builder()
            .name("strict-mode")
            .description("Strict NCP bypass for ground slowdowns")
            .defaultValue(true)
            .build()
        );
        thm$antiClimb = sgGeneral.add(new EnumSetting.Builder<NoSlowAntiClimb.Mode>()
            .name("anti-climb")
            .description("Stops ladders, vines and scaffolding from lifting you. Smart keeps catching falls over a block.")
            .defaultValue(NoSlowAntiClimb.Mode.Off)
            .build()
        );
        bephax$disableOnElytra = sgGeneral.add(new BoolSetting.Builder()
            .name("disable-on-elytra")
            .description("Disables NoSlow while flying with an elytra")
            .defaultValue(true)
            .build()
        );
        bephax$inputMultiplier = sgGeneral.add(new DoubleSetting.Builder()
            .name("input-multiplier")
            .description("Multiplier for movement input (Grim bypass mode)")
            .defaultValue(5.0)
            .min(1.0)
            .max(10.0)
            .sliderMin(1.0)
            .sliderMax(10.0)
            .visible(() -> !bephax$grimV3Bypass.get())
            .build()
        );
        bephax$grimV3Multiplier = sgGeneral.add(new DoubleSetting.Builder()
            .name("grimv3-multiplier")
            .description("Multiplier for GrimV3 bypass (try 3.0-5.0 if detected)")
            .defaultValue(5.0)
            .min(1.0)
            .max(10.0)
            .sliderRange(1.0, 10.0)
            .decimalPlaces(1)
            .visible(() -> bephax$grimV3Bypass.get())
            .build()
        );
    }
    @EventHandler
    @Inject(method = "onPreTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void bephax$onPreTick(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null || mc.level == null) return;
        NoSlow noSlow = (NoSlow) (Object) this;
        if (!noSlow.isActive()) return;
        if (bephax$disableOnElytra.get() && mc.player.isFallFlying()) return;
        if (bephax$grimBypass.get() && mc.player.isUsingItem() && !mc.player.isShiftKeyDown()) {
            if (mc.player.getUsedItemHand() == InteractionHand.OFF_HAND && bephax$checkStack(mc.player.getMainHandItem())) {
                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, bephax$sequenceId++, mc.player.getYRot(), mc.player.getXRot()));
            } else if (bephax$checkStack(mc.player.getOffhandItem())) {
                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, bephax$sequenceId++, mc.player.getYRot(), mc.player.getXRot()));
            }
        }
        if ((bephax$grimBypass.get() || bephax$grimV3Bypass.get()) && bephax$grimWebBypass.get()) {
            AABB bb = bephax$grimBypass.get() ? mc.player.getBoundingBox().inflate(1.0) : mc.player.getBoundingBox();
            for (BlockPos pos : bephax$getIntersectingWebs(bb)) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.DOWN));
            }
        }
    }
    @Override
    public NoSlowAntiClimb.Mode thm$antiClimbMode() {
        return thm$antiClimb == null ? NoSlowAntiClimb.Mode.Off : thm$antiClimb.get();
    }

    @Unique
    private boolean bephax$checkStack(ItemStack stack) {
        return !stack.getComponents().has(DataComponents.FOOD)
            && stack.getItem() != Items.BOW
            && stack.getItem() != Items.CROSSBOW
            && stack.getItem() != Items.SHIELD;
    }
    @Unique
    private boolean bephax$checkSlowed() {
        if (mc.player == null) return false;
        if (bephax$grimV3Bypass.get() && !bephax$checkGrimNew()) {
            return false;
        }
        return !mc.player.isHandsBusy()
            && !mc.player.isShiftKeyDown()
            && (mc.player.isUsingItem() || (mc.player.isBlocking() && !bephax$grimV3Bypass.get() && !bephax$grimBypass.get()));
    }
    @Unique
    private boolean bephax$checkGrimNew() {
        if (mc.player == null) return true;
        return !mc.player.isShiftKeyDown()
            && !mc.player.isVisuallyCrawling()
            && !mc.player.isHandsBusy()
            && (mc.player.getUseItemRemainingTicks() < 5 || ((mc.player.getTicksUsingItem() > 1) && mc.player.getTicksUsingItem() % 2 != 0));
    }
    @Unique
    private List<BlockPos> bephax$getIntersectingWebs(AABB boundingBox) {
        List<BlockPos> blocks = new ArrayList<>();
        if (mc.level == null) return blocks;
        int minX = (int) Math.floor(boundingBox.minX);
        int minY = (int) Math.floor(boundingBox.minY);
        int minZ = (int) Math.floor(boundingBox.minZ);
        int maxX = (int) Math.ceil(boundingBox.maxX);
        int maxY = (int) Math.ceil(boundingBox.maxY);
        int maxZ = (int) Math.ceil(boundingBox.maxZ);
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.getBlock() instanceof WebBlock) {
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }
    @Unique
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;
        NoSlow noSlow = (NoSlow) (Object) this;
        if (!noSlow.isActive()) return;
        if (bephax$disableOnElytra.get() && mc.player.isFallFlying()) return;
        if (bephax$strictMode.get() && event.packet instanceof ServerboundMovePlayerPacket packet) {
            if (!packet.hasPosition()) return;
            if (!bephax$checkSlowed()) return;
            InventoryManager.getInstance().setSlotForced(((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot());
        }
    }
}
//Thank You BepHax for your awesome Mixins
