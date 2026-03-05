package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import xyz.thm.addon.THMAddon;

public class AntiConcrete extends Module {
    public AntiConcrete() {
        super(THMAddon.PVP, "AntiConcrete",
            "Places a button under yourself when enemies are nearby or dropping blocks above you.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // -------------------- General Settings -------------------- //
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("When to place the button.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("smart-range")
        .description("Enemy range to trigger placing.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 7)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-inventory-swap")
        .description("Temporarily moves a button to hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotbarSlotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Which hotbar slot to place the button into (1-9).")
        .defaultValue(1)
        .min(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> returnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("return-delay")
        .description("Delay before returning button to inventory (in ticks). 20 ticks = 1 second.")
        .defaultValue(40)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate toward blocks when placing.")
        .defaultValue(true)
        .build()
    );

    // -------------------- State / Cooldowns -------------------- //
    private int returnTimer = 0;
    private int originalSlot = -1; // store original inventory slot index when we moved item
    private boolean waitingToReturn = false;
    private int noButtonCooldown = 0;
    private int placeCooldown = 0; // short cooldown to avoid placing every tick

    private static final int HOTBAR_MAX_INDEX = 8; // 0..8

    // -------------------- Event Handlers -------------------- //
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // safety null checks
        if (mc.player == null || mc.world == null) return;

        // handle returning a swapped button
        if (waitingToReturn) {
            if (--returnTimer <= 0) {
                // try to move back only if hotbar still has the button at configured slot
                int hotbarIndex = hotbarSlotSetting.get() - 1;
                if (hotbarIndex < 0) hotbarIndex = 0;
                if (originalSlot >= 0) {
                    InvUtils.move().from(hotbarIndex).to(originalSlot);
                }
                waitingToReturn = false;
                originalSlot = -1;
            }
        }

        if (noButtonCooldown > 0) noButtonCooldown--;
        if (placeCooldown > 0) placeCooldown--;

        // target check
        if (TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestDistance) != null) {
            if (mode.get() == Mode.Smart) {
                if (isConcreteAbove()) tryPlaceButton();
            } else {
                tryPlaceButton();
            }
        }
    }

    // -------------------- Core Logic -------------------- //
    private void tryPlaceButton() {
        // don't spam placements
        if (placeCooldown > 0) return;

        BlockPos currentPos = mc.player.getBlockPos();

        // don't place if there's already a button block or if there is no solid block to place onto
        Block blockAt = mc.world.getBlockState(currentPos).getBlock();
        if (isButtonBlock(blockAt)) return;

        // check if we can place at this position (BlockUtils.place will still try, but pre-check avoids waste)
        if (!BlockUtils.canPlace(currentPos)) return;

        FindItemResult button = InvUtils.findInHotbar(stack -> isButton(stack.getItem()));

        originalSlot = -1;
        boolean swapped = false;

        if (!button.found() && silentSwap.get()) {
            // find in inventory (not hotbar)
            FindItemResult invButton = InvUtils.find(stack -> isButton(stack.getItem()));
            if (invButton.found() && invButton.slot() > HOTBAR_MAX_INDEX) {
                int hotbarSlot = Math.max(0, Math.min(HOTBAR_MAX_INDEX, hotbarSlotSetting.get() - 1));
                originalSlot = invButton.slot();
                // move it to hotbar slot
                InvUtils.move().from(invButton.slot()).to(hotbarSlot);
                swapped = true;

                // re-find in hotbar
                button = InvUtils.findInHotbar(stack -> isButton(stack.getItem()));
            }
        }

        if (!button.found()) {
            if (noButtonCooldown == 0) {
                warning("No button in hotbar or inventory.");
                noButtonCooldown = 40; // 2 seconds
            }
            return;
        }

        // rotate only briefly — rotate to center of block (yaw/pitch)
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(currentPos.toCenterPos()), Rotations.getPitch(currentPos.toCenterPos()));
        }

        // place and set cooldown to avoid placing every tick
        BlockUtils.place(currentPos, button, rotate.get(), 0);
        placeCooldown = 6; // ~6 ticks = 0.3s, adjust as desired

        // schedule return of swapped item
        if (swapped && originalSlot != -1) {
            returnTimer = Math.max(1, returnDelay.get());
            waitingToReturn = true;
        }
    }

    private boolean isConcreteAbove() {
        BlockPos base = mc.player.getBlockPos();

        for (int i = 1; i <= 3; i++) {
            if (isFallingTrapBlock(mc.world.getBlockState(base.up(i)).getBlock())) return true;
        }

        Box box = new Box(
            mc.player.getX() - 0.5, mc.player.getY() + 1, mc.player.getZ() - 0.5,
            mc.player.getX() + 0.5, mc.player.getY() + 4, mc.player.getZ() + 0.5
        );

        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (entity instanceof FallingBlockEntity falling) {
                if (isFallingTrapBlock(falling.getBlockState().getBlock())) return true;
            }
        }

        return false;
    }

    private boolean isFallingTrapBlock(Block block) {
        return block.toString().contains("concrete_powder") ||
            block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.RED_SAND ||
            block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL;
    }

    private boolean isButtonBlock(Block block) {
        return block.toString().toLowerCase().contains("button");
    }

    private boolean isButton(Item item) {
        return item.toString().toLowerCase().contains("button");
    }

    // -------------------- Enums -------------------- //
    public enum Mode {
        Strict,
        Smart
    }
}
