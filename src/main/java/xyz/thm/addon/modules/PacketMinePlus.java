package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import xyz.thm.addon.THMAddon;

public class PacketMinePlus extends Module {

    // ==================== CONSTANTS ====================
    private static final double RENDER_PULSE_SPEED = 2.0;
    private static final double RENDER_PULSE_MAX = 2.0;
    private static final double RENDER_PULSE_MIN = -2.0;
    private static final double PROGRESS_DIVISOR = 2.0;

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum mining range.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to block when mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> breakProgress = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-progress")
        .description("When to send break packet (1.0 = 100%).")
        .defaultValue(0.95)
        .min(0.7)
        .max(1.0)
        .sliderMin(0.7)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Mine two blocks at once.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switch to best tool.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Switch tools via packets only (no visual change).")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> reBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("re-break")
        .description("Automatically re-mine blocks that reappear.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant")
        .description("Keep spamming break packets for instant re-break.")
        .defaultValue(true)
        .visible(reBreak::get)
        .build()
    );

    private final Setting<Integer> instantTickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("instant-tick-delay")
        .description("Ticks between instant break packet spam (0 = every tick).")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .visible(() -> reBreak.get() && instant.get())
        .build()
    );

    private final Setting<Boolean> resetOnSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-on-switch")
        .description("Reset mining progress when switching slots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send mining info to chat.")
        .defaultValue(false)
        .build()
    );

    // Render
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render the block being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> animationExp = sgRender.add(new DoubleSetting.Builder()
        .name("animation-exp")
        .description("Ease exponent for render animation. 3-4 looks nice.")
        .defaultValue(3)
        .range(0, 10)
        .sliderRange(0, 10)
        .visible(render::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineStartColor = sgRender.add(new ColorSetting.Builder()
        .name("line-start-color")
        .description("Start of line gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 0))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineEndColor = sgRender.add(new ColorSetting.Builder()
        .name("line-end-color")
        .description("End of line gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideStartColor = sgRender.add(new ColorSetting.Builder()
        .name("side-start-color")
        .description("Start of side gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 0))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideEndColor = sgRender.add(new ColorSetting.Builder()
        .name("side-end-color")
        .description("End of side gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .visible(render::get)
        .build()
    );

    // ==================== STATE ====================
    private MiningData currentMining;
    private MiningData previousMining;
    private int instantTickTimer = 0;
    private int lastSelectedSlot = -1;

    // Render state
    private final RenderAnimator renderAnimator = new RenderAnimator();

    // ==================== CONSTRUCTOR ====================
    public PacketMinePlus() {
        super(THMAddon.PVP, "PacketMinePlus",
            "Packet-based mining system with rendering and silent tool swap.");
    }

    // ==================== LIFECYCLE ====================
    @Override
    public void onActivate() {
        super.onActivate();
        reset();
        renderAnimator.reset();
        if (mc.player != null) {
            lastSelectedSlot = mc.player.getInventory().getSelectedSlot();
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        if (currentMining != null) {
            abortMining(currentMining);
        }
        if (previousMining != null) {
            abortMining(previousMining);
        }
        // Restore slot if needed
        if (lastSelectedSlot != -1 && mc.player != null &&
            mc.player.getInventory().getSelectedSlot() != lastSelectedSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(lastSelectedSlot));
        }
        reset();
    }

    private void reset() {
        currentMining = null;
        previousMining = null;
        instantTickTimer = 0;
        lastSelectedSlot = -1;
    }

    // ==================== EVENT HANDLERS ====================

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos pos = event.blockPos;
        Direction direction = event.direction;

        if (!isInRange(pos)) return;
        if (!canMine(pos)) return;

        // Cancel vanilla mining and use our packet mining instead
        event.cancel();

        // Start our packet mining
        startMining(pos, direction, false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Track slot changes for reset-on-switch
        if (resetOnSwitch.get() && lastSelectedSlot != -1 &&
            mc.player.getInventory().getSelectedSlot() != lastSelectedSlot) {
            if (currentMining != null) {
                currentMining.damage = 0;
                currentMining.ready = false;
            }
        }
        lastSelectedSlot = mc.player.getInventory().getSelectedSlot();

        // Tick instant timer
        if (instantTickTimer > 0) instantTickTimer--;

        // Update current mining
        if (currentMining != null) {
            if (!isInRange(currentMining.pos)) {
                abortMining(currentMining);
                currentMining = null;
                if (chatInfo.get()) info("Aborted mining: out of range");
            } else {
                updateMining(currentMining);
            }
        }

        // Update previous mining (double break)
        if (previousMining != null) {
            if (!isInRange(previousMining.pos)) {
                previousMining = null;
            } else if (mc.world.getBlockState(previousMining.pos).isAir()) {
                previousMining = null;
            } else {
                updateMining(previousMining);
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof BlockUpdateS2CPacket packet)) return;

        // FIXED: getBlockPos() -> getPos()
        BlockPos pPos = packet.getPos();

        if (reBreak.get() && currentMining != null && pPos.equals(currentMining.pos)) {
            if (!packet.getState().isAir() && currentMining.ready) sendStartPackets(currentMining.pos, currentMining.direction);
        }

        if (previousMining != null && pPos.equals(previousMining.pos) && packet.getState().isAir()) previousMining = null;
        if (currentMining != null && pPos.equals(currentMining.pos) && packet.getState().isAir() && !reBreak.get()) currentMining = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        renderAnimator.update();
        if (currentMining != null) renderMiningBlock(event, currentMining);
        if (previousMining != null) renderMiningBlock(event, previousMining);
    }

    private void renderMiningBlock(Render3DEvent event, MiningData data) {
        BlockPos pos = data.pos;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        // Calculate eased progress
        double rawProgress = data.ready ? 1.0 : Math.min(data.damage / breakProgress.get(), 1.0);
        double easedProgress = 1.0 - Math.pow(1.0 - rawProgress, animationExp.get());

        // Get pulse for alpha animation
        double alphaPulse = renderAnimator.getPulse();

        // Calculate box size based on progress
        Box box = getRenderBox(pos, easedProgress / PROGRESS_DIVISOR);

        // Pass 1: Normal pulse
        event.renderer.box(
            box,
            getColor(sideStartColor.get(), sideEndColor.get(), easedProgress, MathHelper.clamp(alphaPulse, 0, 1)),
            getColor(lineStartColor.get(), lineEndColor.get(), easedProgress, MathHelper.clamp(alphaPulse, 0, 1)),
            shapeMode.get(), 0
        );

        // Pass 2: Inverted pulse
        event.renderer.box(
            box,
            getColor(sideStartColor.get(), sideEndColor.get(), easedProgress, MathHelper.clamp(-alphaPulse, 0, 1)),
            getColor(lineStartColor.get(), lineEndColor.get(), easedProgress, MathHelper.clamp(-alphaPulse, 0, 1)),
            shapeMode.get(), 0
        );
    }

    private Box getRenderBox(BlockPos pos, double progress) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double r = MathHelper.clamp(progress, 0.0, 0.5);
        return new Box(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    private Color getColor(SettingColor start, SettingColor end, double progress, double alphaMulti) {
        return new Color(
            lerp(start.r, end.r, progress, 1),
            lerp(start.g, end.g, progress, 1),
            lerp(start.b, end.b, progress, 1),
            lerp(start.a, end.a, progress, alphaMulti)
        );
    }

    private int lerp(double start, double end, double progress, double multiplier) {
        return (int) Math.round((start + (end - start) * progress) * multiplier);
    }

    // ==================== PUBLIC API ====================

    /**
     * Start mining a block at the given position
     */
    public void startMining(BlockPos pos, Direction direction) {
        startMining(pos, direction, false);
    }

    /**
     * Start mining with optional reset
     */
    public void startMining(BlockPos pos, Direction direction, boolean reset) {
        if (pos == null || direction == null) return;
        if (!canMine(pos)) return;
        if (!reset && mc.world.getBlockState(pos).isAir()) return;
        if (!isInRange(pos)) return;

        boolean shouldReset = reset || currentMining == null || !pos.equals(currentMining.pos);

        if (shouldReset) {
            // Handle double break
            if (doubleBreak.get() &&
                currentMining != null &&
                previousMining == null &&
                !mc.world.getBlockState(currentMining.pos).isAir()) {

                previousMining = currentMining;
                if (chatInfo.get()) info("Started double break");
            }

            // Create new mining data
            currentMining = new MiningData(pos, direction);

            // Send start packets (with silent tool switch)
            sendStartPackets(pos, direction);

            if (chatInfo.get()) {
                info("Started mining at " + pos.toShortString());
            }
        }
    }

    /**
     * Stop mining current block
     */
    public void stopMining() {
        if (currentMining != null) {
            abortMining(currentMining);
            currentMining = null;
        }
        if (previousMining != null) {
            abortMining(previousMining);
            previousMining = null;
        }
    }

    /**
     * Check if we can mine at this position
     */
    public boolean canMine(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.isCreative()) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (state.getHardness(mc.world, pos) < 0) return false; // Unbreakable

        // Don't double-mine same position
        if (doubleBreak.get() &&
            previousMining != null &&
            previousMining.pos.equals(pos)) {
            return false;
        }

        return true;
    }

    /**
     * Get current mining position (or null)
     */
    public BlockPos getCurrentPos() {
        return currentMining != null ? currentMining.pos : null;
    }

    /**
     * Get previous mining position (or null)
     */
    public BlockPos getPreviousPos() {
        return previousMining != null ? previousMining.pos : null;
    }

    /**
     * Check if actively mining
     */
    public boolean isMining() {
        return currentMining != null;
    }

    /**
     * Check if mining is ready to break (progress >= threshold)
     */
    public boolean isReady() {
        return currentMining != null && currentMining.ready;
    }

    /**
     * Get mining progress (0.0 to 1.0)
     */
    public double getProgress() {
        if (currentMining == null) return 0;
        return Math.min(currentMining.damage, 1.0);
    }

    /**
     * Get normalized progress (0.0 to 1.0 based on break threshold)
     */
    public double getNormalizedProgress() {
        if (currentMining == null) return 0;
        if (currentMining.ready) return 1.0;
        return Math.min(currentMining.damage / breakProgress.get(), 1.0);
    }

    /**
     * Get current mining direction
     */
    public Direction getCurrentDirection() {
        return currentMining != null ? currentMining.direction : null;
    }

    /**
     * Check if double break is enabled and active
     */
    public boolean isDoubleBreaking() {
        return doubleBreak.get() && previousMining != null;
    }

    // ==================== MINING LOGIC ====================

    private void updateMining(MiningData data) {
        if (data == null) return;

        BlockState state = mc.world.getBlockState(data.pos);

        // If block is air and we're ready, just keep the ready state for re-break
        if (state.isAir()) {
            if (reBreak.get() && data.ready) {
                // Keep ready state, waiting for block to reappear
                return;
            }
            return;
        }

        // If already ready, handle instant re-break spam
        if (data.ready) {
            if (instant.get() && reBreak.get()) {
                // Spam break packets every N ticks
                if (instantTickTimer <= 0) {
                    sendBreakPacketsWithSwap(data);
                    instantTickTimer = instantTickDelay.get();
                }
            }
            return;
        }

        // Calculate damage this tick using best tool
        double delta = calculateBlockBreakingDelta(data.pos);
        data.damage += delta;
        data.damage = Math.min(data.damage, 1.0);

        // Check if ready to break
        if (data.damage >= breakProgress.get()) {
            data.ready = true;
            attemptBreak(data);
        }
    }

    private void attemptBreak(MiningData data) {
        sendBreakPacketsWithSwap(data);

        if (chatInfo.get()) {
            info("Broke block at " + data.pos.toShortString());
        }

        // If not re-breaking, clear the mining
        if (!reBreak.get()) {
            if (data == currentMining) {
                currentMining = null;
            }
        }
        // If re-breaking, keep data.ready = true so we keep spamming
    }

    private void sendBreakPacketsWithSwap(MiningData data) {
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int toolSlot = -1;

        // Find and switch to best tool
        if (autoSwitch.get()) {
            BlockState state = mc.world.getBlockState(data.pos);
            if (!state.isAir()) {
                FindItemResult tool = InvUtils.findFastestTool(state);
                if (tool.found() && tool.isHotbar() && tool.slot() != originalSlot) {
                    toolSlot = tool.slot();

                    if (silentSwitch.get()) {
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(toolSlot));
                    } else {
                        InvUtils.swap(toolSlot, false);
                    }
                }
            }
        }

        // Rotate if needed
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(data.pos), Rotations.getPitch(data.pos));
        }

        // Send break packets
        sendBreakPackets(data.pos, data.direction);

        // Swap back
        if (toolSlot != -1) {
            if (silentSwitch.get()) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            } else {
                InvUtils.swap(originalSlot, false);
            }
        }
    }

    private void abortMining(MiningData data) {
        if (data == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
            data.pos,
            data.direction
        ));
    }

    // ==================== PACKET SENDING ====================

    private void sendStartPackets(BlockPos pos, Direction direction) {
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int toolSlot = -1;

        // Find and switch to best tool for start packets
        if (autoSwitch.get()) {
            BlockState state = mc.world.getBlockState(pos);
            FindItemResult tool = InvUtils.findFastestTool(state);
            if (tool.found() && tool.isHotbar() && tool.slot() != originalSlot) {
                toolSlot = tool.slot();

                if (silentSwitch.get()) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(toolSlot));
                } else {
                    InvUtils.swap(toolSlot, false);
                }
            }
        }

        if (doubleBreak.get()) {
            // Double break sequence
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
        } else {
            // Single break - send START packet
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
        }

        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        // Swap back after sending start packets
        if (toolSlot != -1) {
            if (silentSwitch.get()) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            } else {
                InvUtils.swap(originalSlot, false);
            }
        }
    }

    private void sendBreakPackets(BlockPos pos, Direction direction) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));

        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    // ==================== CALCULATIONS ====================

    /**
     * Calculate block breaking delta using the best available tool
     */
    private double calculateBlockBreakingDelta(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        float hardness = state.getHardness(mc.world, pos);

        if (hardness == -1.0f) return 0;

        // Find best tool for accurate progress calculation
        ItemStack tool = mc.player.getMainHandStack();
        if (autoSwitch.get()) {
            FindItemResult bestTool = InvUtils.findFastestTool(state);
            if (bestTool.found() && bestTool.isHotbar()) {
                tool = mc.player.getInventory().getStack(bestTool.slot());
            }
        }

        int i = canHarvest(state, tool) ? 30 : 100;
        return getBlockBreakingSpeed(state, tool) / hardness / (float) i;
    }

    private boolean canHarvest(BlockState state, ItemStack tool) {
        if (!state.isToolRequired()) return true;
        return tool.isSuitableFor(state);
    }

    private int getEnchantmentLevel(ItemStack stack, String enchantmentName) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        for (var entry : enchantments.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            String description = enchantment.getIdAsString();
            if (description.toLowerCase().contains(enchantmentName.toLowerCase())) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private float getBlockBreakingSpeed(BlockState block, ItemStack tool) {
        float speed = tool.getMiningSpeedMultiplier(block);

        if (speed > 1.0f) {
            int efficiency = getEnchantmentLevel(tool, "efficiency");

            if (efficiency > 0 && !tool.isEmpty()) {
                speed += efficiency * efficiency + 1;
            }
        }

        if (StatusEffectUtil.hasHaste(mc.player)) {
            speed *= 1.0f + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2f;
        }

        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier();
            float k = switch (amplifier) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1E-4f;
            };
            speed *= k;
        }

        if (mc.player.isSubmergedIn(FluidTags.WATER)) {
            ItemStack helmet = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
            boolean hasAquaAffinity = getEnchantmentLevel(helmet, "aqua_affinity") > 0;

            if (!hasAquaAffinity) {
                speed /= 5.0f;
            }
        }

        if (!mc.player.isOnGround()) {
            speed /= 5.0f;
        }

        return speed;
    }

    private boolean isInRange(BlockPos pos) {
        double rangeSq = range.get() * range.get();
        return mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq;
    }

    // ==================== INNER CLASSES ====================

    /**
     * Handles render animation state with pulsing effect
     */
    private class RenderAnimator {
        private long lastTimeMs = 0;
        private double pulse = 1.0;
        private int direction = 1;

        void update() {
            long now = System.currentTimeMillis();
            double deltaSeconds = (now - lastTimeMs) / 1000.0;
            lastTimeMs = now;

            pulse += deltaSeconds * RENDER_PULSE_SPEED * direction;

            if (pulse >= RENDER_PULSE_MAX) {
                pulse = RENDER_PULSE_MAX;
                direction = -1;
            } else if (pulse <= RENDER_PULSE_MIN) {
                pulse = RENDER_PULSE_MIN;
                direction = 1;
            }
        }

        double getPulse() {
            return pulse;
        }

        void reset() {
            lastTimeMs = System.currentTimeMillis();
            pulse = 1.0;
            direction = 1;
        }
    }

    /**
     * Mining data for tracking block break progress
     */
    private static class MiningData {
        final BlockPos pos;
        final Direction direction;
        double damage = 0;
        boolean ready = false;

        MiningData(BlockPos pos, Direction direction) {
            this.pos = pos.toImmutable();
            this.direction = direction;
        }
    }
}
