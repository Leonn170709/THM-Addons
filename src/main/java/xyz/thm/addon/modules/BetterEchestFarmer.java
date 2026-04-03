package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import xyz.thm.addon.THMAddon;

public class BetterEchestFarmer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- General Settings ---
    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("self-toggle")
        .description("Disables when you reach the desired amount of obsidian.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("The amount of obsidian to farm.")
        .defaultValue(64)
        .range(8, 512)
        .sliderRange(8, 128)
        .visible(selfToggle::get)
        .build()
    );

    private final Setting<Boolean> ignoreExisting = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-existing")
        .description("Ignores existing obsidian in your inventory and mines the total target amount.")
        .defaultValue(true)
        .visible(selfToggle::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate when placing and mining the ender chest.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Silently swap to the ender chest and tools, then swap back.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instaMine = sgGeneral.add(new BoolSetting.Builder()
        .name("insta-mine")
        .description("Use the packet exploit for instant breaking. Turn off for vanilla-style mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> packetDelay = sgGeneral.add(new IntSetting.Builder()
        .name("packet-delay")
        .description("Ticks between place and rebreak cycles. (0-1 recommended)")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .visible(instaMine::get)
        .build()
    );

    private final Setting<Boolean> useTimer = sgGeneral.add(new BoolSetting.Builder()
        .name("use-timer")
        .description("Use Timer module override while the farmer is active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> timerMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer-multiplier")
        .description("Timer speed multiplier while the farmer is active.")
        .defaultValue(1.5)
        .range(1.0, 5.0)
        .sliderRange(1.0, 3.0)
        .visible(useTimer::get)
        .build()
    );

    // --- Render Settings ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a block overlay where the obsidian will be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("swing")
        .description("Renders the swing when placing echests")
        .defaultValue(true)
        .build()
    );

    private enum State { Breaking, Placing }
    private State currentState;

    private final VoxelShape SHAPE = Block.createCuboidShape(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    private BlockPos target;
    private int startCount;
    private boolean rebreakPrimed;
    private double miningProgress;
    private int delayTimer;

    public BetterEchestFarmer() {
        super(THMAddon.MAIN, "Better-echest-farmer", "Better echest farmer that uses instant rebreak exploit");
    }

    @Override
    public void onActivate() {
        target = null;
        startCount = InvUtils.find(Items.OBSIDIAN).count();
        rebreakPrimed = false;
        miningProgress = 0;
        delayTimer = 0;
        currentState = State.Breaking; // Start by trying to break whatever is there
    }

    @Override
    public void onDeactivate() {
        if (silentSwap.get()) InvUtils.swapBack();
        setTimer(1.0);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (useTimer.get()) setTimer(timerMultiplier.get());
        else setTimer(1.0);

        if (target == null) {
            HitResult hit = mc.player.raycast(4.5, 0, false);
            if (hit.getType() == HitResult.Type.BLOCK) target = ((BlockHitResult) hit).getBlockPos().up();
            else return;
        }

        if (selfToggle.get()
            && InvUtils.find(Items.OBSIDIAN).count() - (ignoreExisting.get() ? startCount : 0) >= amount.get()) {
            toggle();
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // Logic ignores world state and follows the internal state machine
        if (currentState == State.Breaking) {
            doBreak();
        } else {
            doPlace();
        }
    }

    private void doBreak() {
        int tool = findBestTool();
        if (tool == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (prevSlot != tool) InvUtils.swap(tool, false);
        Direction dir = Direction.UP;

        if (instaMine.get()) {
            if (!rebreakPrimed) {
                // Initial slow break
                if (miningProgress == 0) sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, dir));

                miningProgress += mc.world.getBlockState(target).calcBlockBreakingDelta(mc.player, mc.world, target);

                if (miningProgress >= 1.0) {
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, dir));
                    currentState = State.Placing; // Switch state immediately without waiting for server air
                }
            } else {
                // Instamine loop
                if (rotate.get()) rotateTo(target);
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, dir));
                if (swing.get() && mc.player != null) mc.player.swingHand(Hand.MAIN_HAND);

                currentState = State.Placing; // Transition immediately
                delayTimer = packetDelay.get();
            }
        } else {
            // Normal mining still needs to check world state slightly to know when to stop
            if (mc.world.getBlockState(target).isOf(Blocks.ENDER_CHEST)) {
                if (!silentSwap.get() && prevSlot != tool) InvUtils.swap(tool, true);
                BlockUtils.breakBlock(target, swing.get());
            } else {
                currentState = State.Placing;
            }
        }

        if (silentSwap.get() && prevSlot != tool) InvUtils.swap(prevSlot, false);
    }

    private void doPlace() {
        FindItemResult echest = ensureEchestInHotbar();
        if (!echest.found()) {
            error("Out of echests.");
            toggle();
            return;
        }

        // Place the block
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        int chestSlot = echest.slot();
        if (prevSlot != chestSlot) InvUtils.swap(chestSlot, false);
        BlockUtils.place(target, Hand.MAIN_HAND, chestSlot, rotate.get(), 0, swing.get(), true, false);
        if (silentSwap.get() && prevSlot != chestSlot) InvUtils.swap(prevSlot, false);

        // Immediately ready the tool and move to next state
        int tool = findBestTool();
        if (!silentSwap.get() && tool != -1 && prevSlot != tool) InvUtils.swap(tool, false);

        if (instaMine.get() && miningProgress >= 1.0) rebreakPrimed = true;

        currentState = State.Breaking; // Assume it's placed and ready to be broken
        delayTimer = packetDelay.get();
    }

    // --- Helpers ---

    private void sendPacket(net.minecraft.network.packet.Packet<?> packet) {
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendPacket(packet);
    }

    private int findBestTool() {
        double bestScore = -1;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) continue;
            double score = stack.getMiningSpeedMultiplier(Blocks.ENDER_CHEST.getDefaultState());
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private FindItemResult ensureEchestInHotbar() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (hotbar.found()) return hotbar;
        FindItemResult inv = InvUtils.find(Items.ENDER_CHEST);
        if (inv.found()) {
            int tool = findBestTool();
            for (int i = 0; i < 9; i++) {
                if (i != tool) {
                    InvUtils.move().from(inv.slot()).toHotbar(i);
                    return InvUtils.findInHotbar(Items.ENDER_CHEST);
                }
            }
        }
        return hotbar;
    }

    private void rotateTo(BlockPos pos) { Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos)); }

    private void setTimer(double value) {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(value);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null || !render.get()) return;
        Box box = SHAPE.getBoundingBoxes().get(0);
        event.renderer.box(target.getX() + box.minX, target.getY() + box.minY, target.getZ() + box.minZ,
            target.getX() + box.maxX, target.getY() + box.maxY, target.getZ() + box.maxZ,
            sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
    }
}
