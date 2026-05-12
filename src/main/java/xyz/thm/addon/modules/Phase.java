package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.PlacementUtils;
import xyz.thm.addon.utils.RotationUtils;
import xyz.thm.addon.utils.THMUtils;

public class Phase extends Module {
    private final SettingGroup sgPearl = settings.createGroup("Pearl");

    private final Setting<Integer> pitch = sgPearl.add(new IntSetting.Builder()
        .name("pitch")
        .description("The pitch angle to throw pearls.")
        .defaultValue(85)
        .range(70, 90)
        .build()
    );
    private final Setting<Boolean> swapAlternative = sgPearl.add(new BoolSetting.Builder()
        .name("swap-alternative")
        .description("Uses inventory swap for swapping to pearls.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> attack = sgPearl.add(new BoolSetting.Builder()
        .name("attack")
        .description("Attacks entities in the way of the pearl phase.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> swing = sgPearl.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings the hand when throwing pearls.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> selfFill = sgPearl.add(new BoolSetting.Builder()
        .name("self-fill")
        .description("Automatically fills blocks you are phasing on.")
        .defaultValue(false)
        .build()
    );

    private final SettingGroup sgSelfPlace = settings.createGroup("Self Place");

    private final Setting<Boolean> selfPlace = sgSelfPlace.add(new BoolSetting.Builder()
        .name("self-place")
        .description("Places fire or a cobweb at your position when phase is triggered.")
        .defaultValue(false)
        .build()
    );
    private final Setting<SelfPlaceType> selfPlaceType = sgSelfPlace.add(new EnumSetting.Builder<SelfPlaceType>()
        .name("type")
        .description("Fire: ignites the block below you using flint-and-steel or a fire charge. Web: places a cobweb at your feet.")
        .defaultValue(SelfPlaceType.Fire)
        .visible(selfPlace::get)
        .build()
    );
    private final Setting<Boolean> optimize = sgSelfPlace.add(new BoolSetting.Builder()
        .name("optimize")
        .description("Reuses the pearl throw rotation for self-place: rotates once, places, then throws — no extra rotation step.")
        .defaultValue(false)
        .visible(selfPlace::get)
        .build()
    );
    private final Setting<Boolean> selfPlaceRotate = sgSelfPlace.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotates for the self-place action.")
        .defaultValue(true)
        .visible(() -> selfPlace.get() && !optimize.get())
        .build()
    );

    private InventoryManager inventoryManager;

    public Phase() {
        super(THMAddon.PVP, "phase", "Allows player to phase through solid blocks using ender pearls.");
        inventoryManager = InventoryManager.getInstance();
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        performPearlPhase();
        toggle();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.noClip = false;
        }
    }

    @EventHandler
    private void onPushOutOfBlocks(THMUtils event) {
        if (isActive()) {
            event.cancel();
        }
    }

    private void performPearlPhase() {
        int pearlSlot = PlacementUtils.getEnderPearlSlot();
        if (pearlSlot == -1 || mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
            return;
        }
        final Vec3d pearlTargetVec = new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0.0, Math.floor(mc.player.getZ()) + 0.5);
        float[] rotations = RotationUtils.getRotationsTo(mc.player.getEyePos(), pearlTargetVec);
        float yaw = rotations[0] + 180.0f;

        if (attack.get()) {
            handlePearlAttacks(yaw);
        }
        if (selfFill.get()) {
            handleSelfFill(yaw);
        }
        // Non-optimized: self-place has its own rotation and runs before the pearl swap.
        if (selfPlace.get() && !optimize.get()) {
            performSelfPlace(false);
        }

        RotationUtils rotationManager = RotationUtils.getInstance();
        int targetSlot;
        if (swapAlternative.get()) {
            targetSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            performInventorySwapPVP(pearlSlot);
        } else if (pearlSlot < 9) {
            targetSlot = pearlSlot;
        } else {
            return;
        }

        inventoryManager.setSlot(targetSlot, InventoryManager.Priority.PEARL_PHASE);
        rotationManager.setRotationSilent(yaw, pitch.get());
        // Optimized: pearl is swapped in and rotation is set; self-place reuses that rotation.
        if (selfPlace.get() && optimize.get()) {
            performSelfPlace(true);
        }
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, pitch.get()));

        if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (swapAlternative.get()) {
            performInventorySwapPVP(pearlSlot);
        }

        inventoryManager.syncToClient();
        rotationManager.setRotationSilentSync();
    }

    private void handlePearlAttacks(float yaw) {
        BlockHitResult hitResult = (BlockHitResult) mc.player.raycast(3.0, 0, false);
        Box searchBox = Box.from(Vec3d.ofCenter(hitResult.getBlockPos())).expand(0.2);
        for (Entity entity : mc.world.getOtherEntities(null, searchBox)) {
            if (entity instanceof ItemFrameEntity itemFrame) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }
        BlockState state = mc.world.getBlockState(mc.player.getBlockPos());
        if (state.getBlock() instanceof ScaffoldingBlock) {
            BlockPos pos = mc.player.getBlockPos();
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }
    }

    private void handleSelfFill(float yaw) {
        float yaw1 = yaw % 360.0f;
        if (yaw1 < 0.0f) {
            yaw1 += 360.0f;
        }
        BlockPos blockPos = mc.player.getBlockPos();
        if (yaw1 >= 22.5 && yaw1 < 67.5) {
            blockPos = blockPos.south().west();
        } else if (yaw1 >= 67.5 && yaw1 < 112.5) {
            blockPos = blockPos.west();
        } else if (yaw1 >= 112.5 && yaw1 < 157.5) {
            blockPos = blockPos.north().west();
        } else if (yaw1 >= 157.5 && yaw1 < 202.5) {
            blockPos = blockPos.north();
        } else if (yaw1 >= 202.5 && yaw1 < 247.5) {
            blockPos = blockPos.north().east();
        } else if (yaw1 >= 247.5 && yaw1 < 292.5) {
            blockPos = blockPos.east();
        } else if (yaw1 >= 292.5 && yaw1 < 337.5) {
            blockPos = blockPos.south().east();
        } else {
            blockPos = blockPos.south();
        }
        FindItemResult resistantBlock = PlacementUtils.findResistantBlock();
        if (resistantBlock.found() && blockPos != null && !mc.world.getBlockState(blockPos.down()).isReplaceable()) {
            RotationUtils rotationManager = RotationUtils.getInstance();
            PlacementUtils.placeBlock(blockPos, true, true, true);
        }
    }

    private int findSelfPlaceItem() {
        if (selfPlaceType.get() == SelfPlaceType.Fire) {
            for (int i = 0; i < 45; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.FLINT_AND_STEEL || stack.getItem() == Items.FIRE_CHARGE) return i;
            }
        } else {
            for (int i = 0; i < 45; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.COBWEB) return i;
            }
        }
        return -1;
    }

    private void performSelfPlace(boolean optimized) {
        int itemSlot = findSelfPlaceItem();
        if (itemSlot == -1) return;

        int targetSlot;
        if (swapAlternative.get()) {
            targetSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            performInventorySwapPVP(itemSlot);
        } else if (itemSlot < 9) {
            targetSlot = itemSlot;
        } else {
            return;
        }

        inventoryManager.setSlot(targetSlot, InventoryManager.Priority.PEARL_PHASE);
        RotationUtils rotationManager = RotationUtils.getInstance();

        if (selfPlaceType.get() == SelfPlaceType.Fire) {
            BlockPos belowPos = mc.player.getBlockPos().down();
            Vec3d hitVec = Vec3d.ofCenter(belowPos).add(0, 0.5, 0);
            if (!optimized && selfPlaceRotate.get()) {
                rotationManager.setRotationSilent(mc.player.getYaw(), 90.0f);
            }
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, belowPos, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        } else {
            BlockPos feetPos = mc.player.getBlockPos();
            Direction side = PlacementUtils.getPlaceSide(feetPos);
            if (side == null) side = Direction.DOWN;
            BlockPos neighbor = feetPos.offset(side);
            Direction opposite = side.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(neighbor).add(Vec3d.of(opposite.getVector()).multiply(0.5));
            if (!optimized && selfPlaceRotate.get()) {
                float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), hitVec);
                rotationManager.setRotationSilent(rot[0], rot[1]);
            }
            BlockHitResult hitResult = new BlockHitResult(hitVec, opposite, neighbor, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (swapAlternative.get()) {
            performInventorySwapPVP(itemSlot);
        }

        // In the optimized path the pearl flow owns the final sync; don't duplicate it here.
        if (!optimized) {
            inventoryManager.syncToClient();
            if (selfPlaceRotate.get()) {
                rotationManager.setRotationSilentSync();
            }
        }
    }

    private void performInventorySwapPVP(int pearlSlot) {
        mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() + 36, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    @Override
    public String getInfoString() {
        return "Pearl Mode";
    }

    public enum SelfPlaceType {
        Fire, Web
    }
}
