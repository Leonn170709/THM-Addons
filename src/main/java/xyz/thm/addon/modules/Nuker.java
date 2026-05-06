package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.utils.Enums;
import xyz.thm.addon.utils.InventoryManager;

import java.util.*;

public class Nuker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape")
        .description("The shape of nuking algorithm.")
        .defaultValue(Shape.Sphere)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The way the blocks are broken.")
        .defaultValue(Mode.Flatten)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The break range.")
        .defaultValue(4)
        .min(0)
        .visible(() -> shape.get() != Shape.Cube)
        .build()
    );

    private final Setting<Integer> rangeUp = sgGeneral.add(new IntSetting.Builder().name("up").description("The break range.").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).build());
    private final Setting<Integer> rangeDown = sgGeneral.add(new IntSetting.Builder().name("down").description("The break range.").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).build());
    private final Setting<Integer> rangeLeft = sgGeneral.add(new IntSetting.Builder().name("left").description("The break range.").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).build());
    private final Setting<Integer> rangeRight = sgGeneral.add(new IntSetting.Builder().name("right").description("The break range.").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).build());
    private final Setting<Integer> rangeForward = sgGeneral.add(new IntSetting.Builder().name("forward").description("The break range.").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).build());
    private final Setting<Integer> rangeBack = sgGeneral.add(new IntSetting.Builder().name("back").description("The break range.").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).build());

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder().name("walls-range").description("Range in which to break when behind blocks.").defaultValue(4.0).min(0).sliderMax(6).build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder().name("delay").description("Delay in ticks between breaking blocks.").defaultValue(0).build());
    private final Setting<Integer> maxBlocksPerTick = sgGeneral.add(new IntSetting.Builder().name("max-blocks-per-tick").description("Maximum blocks to try to break per tick.").defaultValue(1).min(1).build());

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>().name("sort-mode").description("The blocks you want to mine first.").defaultValue(SortMode.Closest).build());
    private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder().name("packet-mine").description("Attempt to instamine everything at once.").defaultValue(false).build());
    private final Setting<Boolean> suitableTools = sgGeneral.add(new BoolSetting.Builder().name("only-suitable-tools").description("Only mines when using an appropriate tool for the block.").defaultValue(false).build());
    private final Setting<Boolean> interact = sgGeneral.add(new BoolSetting.Builder().name("interact").description("Interacts with the block instead of mining.").defaultValue(false).build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Rotates server-side to the block being mined.").defaultValue(true).build());

    private final Setting<Enums.NukerSwapModes> swapMode = sgGeneral.add(new EnumSetting.Builder<Enums.NukerSwapModes>().name("swap-mode").description("How Nuker swaps to the best tool.").defaultValue(Enums.NukerSwapModes.None).build());
    private final Setting<Boolean> avoidLiquidContact = sgGeneral.add(new BoolSetting.Builder().name("avoid-liquid-contact").description("Skips mining blocks that are touching water or lava.").defaultValue(false).build());
    private final Setting<Boolean> mineBedrock = sgGeneral.add(new BoolSetting.Builder().name("mine-bedrock").description("Allows Nuker to mine bedrock.").defaultValue(false).build());
    private final Setting<Double> bedrockRange = sgGeneral.add(new DoubleSetting.Builder().name("bedrock-range").description("Range for bedrock mining.").defaultValue(10.0).min(0.0).sliderRange(0.0, 20.0).visible(mineBedrock::get).build());
    private final Setting<Boolean> doubleMine = sgGeneral.add(new BoolSetting.Builder().name("double-mine").description("Mines non-instaminable blocks using normal and packet mining simultaneously.").defaultValue(false).build());

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>().name("list-mode").description("Selection mode.").defaultValue(ListMode.Blacklist).build());
    private final Setting<List<Block>> blacklist = sgWhitelist.add(new BlockListSetting.Builder().name("blacklist").description("The blocks you don't want to mine.").visible(() -> listMode.get() == ListMode.Blacklist).build());
    private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder().name("whitelist").description("The blocks you want to mine.").visible(() -> listMode.get() == ListMode.Whitelist).build());
    private final Setting<Keybind> selectBlockBind = sgWhitelist.add(new KeybindSetting.Builder().name("select-block-bind").description("Adds targeted block to list when this button is pressed.").defaultValue(Keybind.none()).build());

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder().name("swing").description("Whether to swing hand client-side.").defaultValue(true).build());
    private final Setting<Boolean> enableRenderBounding = sgRender.add(new BoolSetting.Builder().name("bounding-box").description("Enable rendering bounding box for Cube and Uniform Cube.").defaultValue(true).build());
    private final Setting<ShapeMode> shapeModeBox = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("nuke-box-mode").description("How the shape for the bounding box is rendered.").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColorBox = sgRender.add(new ColorSetting.Builder().name("side-color").description("The side color of the bounding box.").defaultValue(new SettingColor(16,106,144, 100)).build());
    private final Setting<SettingColor> lineColorBox = sgRender.add(new ColorSetting.Builder().name("line-color").description("The line color of the bounding box.").defaultValue(new SettingColor(16,106,144, 255)).build());
    private final Setting<Boolean> enableRenderBreaking = sgRender.add(new BoolSetting.Builder().name("broken-blocks").description("Enable rendering shapes for broken blocks.").defaultValue(true).build());
    private final Setting<ShapeMode> shapeModeBreak = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("nuke-block-mode").description("How the shapes for broken blocks are rendered.").defaultValue(ShapeMode.Both).visible(enableRenderBreaking::get).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("target-side-color").description("The side color of target block rendering.").defaultValue(new SettingColor(255, 0, 0, 80)).visible(enableRenderBreaking::get).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("target-line-color").description("The line color of target block rendering.").defaultValue(new SettingColor(255, 0, 0, 255)).visible(enableRenderBreaking::get).build());

    private final List<BlockPos> blocks = new ArrayList<>();
    private final Set<BlockPos> interacted = new ObjectOpenHashSet<>();
    private final InventoryManager inventoryManager = InventoryManager.getInstance();
    private final ArrayDeque<BlockPos> doubleMineQueue = new ArrayDeque<>();

    private boolean firstBlock;
    private final BlockPos.Mutable lastBlockPos = new BlockPos.Mutable();
    private final BlockPos.Mutable pos1 = new BlockPos.Mutable();
    private final BlockPos.Mutable pos2 = new BlockPos.Mutable();

    private DoubleMineTarget normalMining;
    private DoubleMineTarget packetMining;
    private int timer;
    private int noBlockTimer;
    private int silentSyncTicks;
    private int maxh;
    private int maxv;
    private int maxBlocksPerTickBeforeDoubleMine = -1;
    private BlockPos activeBedrockPos;

    public Nuker() {
        super(Categories.World, "nuker", "Nuker with many additions");
    }

    @Override
    public void onActivate() {
        firstBlock = true;
        timer = 0;
        noBlockTimer = 0;
        silentSyncTicks = 0;
        interacted.clear();
        activeBedrockPos = null;
        doubleMineQueue.clear();
        normalMining = null;
        packetMining = null;
        maxBlocksPerTickBeforeDoubleMine = -1;
    }

    @Override
    public void onDeactivate() {
        restoreBlocksPerTickIfNeeded();
        doubleMineQueue.clear();
        normalMining = null;
        packetMining = null;
        activeBedrockPos = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (enableRenderBounding.get() && shape.get() != Shape.Sphere && mode.get() != Mode.Smash) {
            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX());
            int maxY = Math.max(pos1.getY(), pos2.getY());
            int maxZ = Math.max(pos1.getZ(), pos2.getZ());
            event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, sideColorBox.get(), lineColorBox.get(), shapeModeBox.get(), 0);
        }

        if (doubleMine.get()) {
            if (normalMining != null) normalMining.renderLetter();
            if (packetMining != null) packetMining.renderLetter();
        }
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action == KeyAction.Press) addTargetedBlockToList();
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action == KeyAction.Press) addTargetedBlockToList();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        tickSilentSwap();
        tickBedrockMining();
        tickDoubleMine();

        if (timer > 0) {
            timer--;
            return;
        }

        double pX = mc.player.getX();
        double pY = mc.player.getY();
        double pZ = mc.player.getZ();
        double rangeSq = range.get() * range.get();
        BlockPos playerBlockPos = mc.player.getBlockPos();

        if (shape.get() == Shape.UniformCube) range.set((double) Math.round(range.get()));

        double pX_ = pX;
        double pZ_ = pZ;
        int r = (int) Math.round(range.get());

        if (shape.get() == Shape.UniformCube) {
            pX_ += 1;
            pos1.set(pX_ - r, pY - r + 1, pZ - r + 1);
            pos2.set(pX_ + r - 1, pY + r, pZ + r);
            maxh = 0;
            maxv = 0;
        } else if (shape.get() == Shape.Cube) {
            Direction direction = mc.player.getHorizontalFacing();
            switch (direction) {
                case NORTH -> {
                    pZ_ += 1;
                    pX_ += 1;
                    pos1.set(pX_ - (rangeRight.get() + 1), Math.ceil(pY) - rangeDown.get(), pZ_ - (rangeBack.get() + 1));
                    pos2.set(pX_ + rangeLeft.get(), Math.ceil(pY + rangeUp.get() + 1), pZ_ + rangeForward.get());
                }
                case EAST -> {
                    pos1.set(pX_ - rangeForward.get(), Math.ceil(pY) - rangeDown.get(), pZ_ - rangeRight.get());
                    pos2.set(pX_ + rangeBack.get() + 1, Math.ceil(pY + rangeUp.get() + 1), pZ_ + rangeLeft.get() + 1);
                }
                case SOUTH -> {
                    pX_ += 1;
                    pZ_ += 1;
                    pos1.set(pX_ - (rangeLeft.get() + 1), Math.ceil(pY) - rangeDown.get(), pZ_ - (rangeForward.get() + 1));
                    pos2.set(pX_ + rangeRight.get(), Math.ceil(pY + rangeUp.get() + 1), pZ_ + rangeBack.get());
                }
                case WEST -> {
                    pX_ += 1;
                    pos1.set(pX_ - (rangeBack.get() + 1), Math.ceil(pY) - rangeDown.get(), pZ_ - rangeLeft.get());
                    pos2.set(pX_ + rangeForward.get(), Math.ceil(pY + rangeUp.get() + 1), pZ_ + rangeRight.get() + 1);
                }
                default -> {}
            }
            maxh = 1 + Math.max(Math.max(Math.max(rangeBack.get(), rangeRight.get()), rangeForward.get()), rangeLeft.get());
            maxv = 1 + Math.max(rangeUp.get(), rangeDown.get());
        }

        if (mode.get() == Mode.Flatten) pos1.setY((int) Math.floor(pY + 0.5));

        Box box = new Box(pos1.toCenterPos(), pos2.toCenterPos());

        BlockIterator.register(Math.max((int) Math.ceil(range.get() + 1), maxh), Math.max((int) Math.ceil(range.get()), maxv), (blockPos, blockState) -> {
            Vec3d center = blockPos.toCenterPos();

            switch (shape.get()) {
                case Sphere -> {
                    if (Utils.squaredDistance(pX, pY, pZ, center.x, center.y, center.z) > rangeSq) return;
                }
                case UniformCube -> {
                    if (chebyshevDist(playerBlockPos.getX(), playerBlockPos.getY(), playerBlockPos.getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ()) >= range.get()) return;
                }
                case Cube -> {
                    if (!box.contains(center)) return;
                }
            }

            if (mode.get() == Mode.Flatten && blockPos.getY() + 0.5 < pY) return;
            if (mode.get() == Mode.Smash && blockState.getHardness(mc.world, blockPos) != 0) return;
            if (suitableTools.get() && !interact.get() && !mc.player.getMainHandStack().isSuitableFor(blockState)) return;
            if (!BlockUtils.canBreak(blockPos, blockState) && !interact.get()) return;
            if (avoidLiquidContact.get() && touchesLiquid(blockPos)) return;
            if (isOutOfRange(blockPos)) return;
            if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(blockState.getBlock())) return;
            if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(blockState.getBlock())) return;
            if (interact.get() && interacted.contains(blockPos)) return;

            blocks.add(blockPos.toImmutable());
        });

        BlockIterator.after(() -> {
            if (sortMode.get() == SortMode.TopDown) {
                blocks.sort(Comparator.comparingDouble(v -> -v.getY()));
            } else if (sortMode.get() != SortMode.None) {
                blocks.sort(Comparator.comparingDouble(v -> Utils.squaredDistance(pX, pY, pZ, v.getX() + 0.5, v.getY() + 0.5, v.getZ() + 0.5) * (sortMode.get() == SortMode.Closest ? 1 : -1)));
            }

            if (blocks.isEmpty()) {
                interacted.clear();
                if (noBlockTimer++ >= delay.get()) firstBlock = true;
                return;
            } else {
                noBlockTimer = 0;
            }

            if (!firstBlock && !lastBlockPos.equals(blocks.getFirst())) {
                timer = delay.get();
                firstBlock = false;
                lastBlockPos.set(blocks.getFirst());
                if (timer > 0) return;
            }

            int count = 0;
            for (BlockPos block : blocks) {
                if (count >= maxBlocksPerTick.get()) break;

                boolean canInstaMine = BlockUtils.canInstaBreak(block);
                if (rotate.get()) Rotations.rotate(Rotations.getYaw(block), Rotations.getPitch(block), () -> breakBlock(block));
                else breakBlock(block);

                if (enableRenderBreaking.get()) RenderUtils.renderTickingBlock(block, sideColor.get(), lineColor.get(), shapeModeBreak.get(), 0, 8, true, false);
                lastBlockPos.set(block);

                count++;
                if (!canInstaMine && !packetMine.get() && !doubleMine.get()) break;
            }

            firstBlock = false;
            blocks.clear();
        });
    }

    private void breakBlock(BlockPos blockPos) {
        if (interact.get()) {
            BlockUtils.interact(new BlockHitResult(blockPos.toCenterPos(), BlockUtils.getDirection(blockPos), blockPos, true), Hand.MAIN_HAND, swing.get());
            interacted.add(blockPos);
            return;
        }

        if (doubleMine.get() && shouldQueueDoubleMine(blockPos)) {
            BlockPos immutable = blockPos.toImmutable();
            if ((normalMining == null || !immutable.equals(normalMining.blockPos))
                && (packetMining == null || !immutable.equals(packetMining.blockPos))
                && !doubleMineQueue.contains(immutable)) {
                doubleMineQueue.add(immutable);
            }
            return;
        }

        if (swapMode.get() != Enums.NukerSwapModes.None) {
            int bestSlot = getBestToolSlot(mc.world.getBlockState(blockPos));
            if (swapMode.get() == Enums.NukerSwapModes.Normal) {
                int selected = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                if (selected != bestSlot) {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(bestSlot);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
                }
            } else if (swapMode.get() == Enums.NukerSwapModes.Silent) {
                int serverSlot = inventoryManager.getServerSlot();
                if (serverSlot != bestSlot) {
                    inventoryManager.setSlotForced(bestSlot);
                    silentSyncTicks = 1;
                }
            }
        }

        if (packetMine.get()) {
            Direction dir = BlockUtils.getDirection(blockPos);
            if (dir == null) dir = Direction.UP;
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, dir));
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, dir));
        } else {
            BlockUtils.breakBlock(blockPos, swing.get());
        }
    }

    private void tickSilentSwap() {
        if (silentSyncTicks > 0) {
            silentSyncTicks--;
            if (silentSyncTicks == 0 && swapMode.get() == Enums.NukerSwapModes.Silent && !interact.get() && isActive() && inventoryManager.isDesynced()) {
                inventoryManager.syncToClient();
            }
        }
    }

    private void tickBedrockMining() {
        if (!mineBedrock.get()) {
            activeBedrockPos = null;
            return;
        }
        if (mode.get() == Mode.Smash) {
            activeBedrockPos = null;
            return;
        }

        if (activeBedrockPos != null && mc.world.getBlockState(activeBedrockPos).getBlock() != Blocks.BEDROCK) activeBedrockPos = null;
        if (activeBedrockPos != null && isOutOfBedrockRange(activeBedrockPos)) activeBedrockPos = null;
        if (activeBedrockPos != null && mode.get() == Mode.Flatten && activeBedrockPos.getY() + 0.5 < mc.player.getY()) activeBedrockPos = null;

        if (activeBedrockPos == null) {
            activeBedrockPos = findNearestBedrock();
            if (activeBedrockPos == null) return;
        }

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(activeBedrockPos), Rotations.getPitch(activeBedrockPos));
        Direction direction = BlockUtils.getDirection(activeBedrockPos);
        if (direction == null) direction = Direction.UP;
        mc.interactionManager.updateBlockBreakingProgress(activeBedrockPos, direction);
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void tickDoubleMine() {
        if (!doubleMine.get() || interact.get() || !isActive()) {
            restoreBlocksPerTickIfNeeded();
            doubleMineQueue.clear();
            normalMining = null;
            packetMining = null;
            return;
        }

        ensureBlocksPerTickAtLeastTwo();

        if (normalMining != null) {
            if (normalMining.shouldRemove()) normalMining = null;
            else if (mc.world.getBlockState(normalMining.blockPos).getBlock() != normalMining.block) normalMining = null;
            else if (normalMining.isReady()) normalMining.stopDestroying();
        }

        if (packetMining != null) {
            if (packetMining.shouldRemove()) packetMining = null;
            else if (mc.world.getBlockState(packetMining.blockPos).getBlock() != packetMining.block) packetMining = null;
        }

        while (!doubleMineQueue.isEmpty()) {
            BlockPos next = doubleMineQueue.peek();
            if (next == null) break;
            BlockState state = mc.world.getBlockState(next);
            if (state.isAir() || isInstaminable(next, state)) doubleMineQueue.pop();
            else break;
        }

        if (normalMining == null && !doubleMineQueue.isEmpty()) {
            normalMining = new DoubleMineTarget(doubleMineQueue.pop()).startDestroying();
        }

        if (packetMining == null && normalMining != null && !doubleMineQueue.isEmpty()) {
            DoubleMineTarget block = new DoubleMineTarget(doubleMineQueue.pop());
            packetMining = normalMining.packetMine();
            normalMining = block.startDestroying();
        }
    }

    private void ensureBlocksPerTickAtLeastTwo() {
        int current = maxBlocksPerTick.get();
        if (current >= 2) return;
        if (maxBlocksPerTickBeforeDoubleMine == -1) maxBlocksPerTickBeforeDoubleMine = current;
        maxBlocksPerTick.set(2);
    }

    private void restoreBlocksPerTickIfNeeded() {
        if (maxBlocksPerTickBeforeDoubleMine != -1) {
            maxBlocksPerTick.set(maxBlocksPerTickBeforeDoubleMine);
            maxBlocksPerTickBeforeDoubleMine = -1;
        }
    }

    private boolean shouldQueueDoubleMine(BlockPos pos) {
        if (packetMine.get()) return false;
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && !isInstaminable(pos, state);
    }

    private boolean isInstaminable(BlockPos pos, BlockState state) {
        return BlockUtils.canInstaBreak(pos);
    }

    private boolean touchesLiquid(BlockPos pos) {
        if (isWaterOrLava(pos)) return true;
        for (Direction direction : Direction.values()) {
            if (isWaterOrLava(pos.offset(direction))) return true;
        }
        return false;
    }

    private boolean isWaterOrLava(BlockPos pos) {
        FluidState fluidState = mc.world.getFluidState(pos);
        return fluidState.isIn(FluidTags.WATER) || fluidState.isIn(FluidTags.LAVA);
    }

    private int getBestToolSlot(BlockState state) {
        int selected = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        int bestSlot = selected;
        float bestSpeed = 0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private BlockPos findNearestBedrock() {
        double range = getBedrockRange();
        double rangeSq = range * range;

        BlockPos origin = mc.player.getBlockPos();
        int radius = (int) Math.ceil(range);
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).getBlock() != Blocks.BEDROCK) continue;

                    Vec3d center = pos.toCenterPos();
                    double distSq = mc.player.squaredDistanceTo(center);
                    if (distSq > rangeSq) continue;
                    if (mode.get() == Mode.Flatten && pos.getY() + 0.5 < mc.player.getY()) continue;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = pos.toImmutable();
                    }
                }
            }
        }

        return best;
    }

    private boolean isOutOfBedrockRange(BlockPos pos) {
        double range = getBedrockRange();
        return mc.player.squaredDistanceTo(pos.toCenterPos()) > range * range;
    }

    private double getBedrockRange() {
        return Math.max(0.0, bedrockRange.get());
    }

    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3d pos = blockPos.toCenterPos();
        RaycastContext raycastContext = new RaycastContext(mc.player.getEyePos(), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(raycastContext);
        if (result == null || !result.getBlockPos().equals(blockPos)) {
            return !PlayerUtils.isWithin(pos, wallsRange.get());
        }
        return false;
    }

    private void addTargetedBlockToList() {
        if (!selectBlockBind.get().isPressed() || mc.currentScreen != null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        Block targetBlock = mc.world.getBlockState(pos).getBlock();

        List<Block> list = listMode.get() == ListMode.Whitelist ? whitelist.get() : blacklist.get();
        String modeName = listMode.get().name();

        if (list.contains(targetBlock)) {
            list.remove(targetBlock);
            info("Removed " + Names.get(targetBlock) + " from " + modeName);
        } else {
            list.add(targetBlock);
            info("Added " + Names.get(targetBlock) + " to " + modeName);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = 0;
    }

    private class DoubleMineTarget {
        private final BlockPos blockPos;
        private final BlockState blockState;
        private final Block block;
        private final Direction direction;
        private final Vector3d vec3 = new Vector3d(0);
        private int normalStartTime;
        private int packetStartTime;
        private boolean packet;

        private DoubleMineTarget(BlockPos pos) {
            this.blockPos = pos.toImmutable();
            this.blockState = mc.world.getBlockState(this.blockPos);
            this.block = this.blockState.getBlock();
            Direction dir = BlockUtils.getDirection(pos);
            this.direction = dir != null ? dir : Direction.UP;
        }

        private DoubleMineTarget startDestroying() {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, this.blockPos, this.direction));
            normalStartTime = mc.player.age;
            return this;
        }

        private DoubleMineTarget stopDestroying() {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
            return this;
        }

        private DoubleMineTarget packetMine() {
            packetStartTime = mc.player.age;
            packet = true;
            return stopDestroying();
        }

        private boolean isReady() {
            return progress() >= 1.0;
        }

        private boolean shouldRemove() {
            boolean distance = !packet && mc.player.squaredDistanceTo(this.blockPos.toCenterPos()) > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange();
            boolean timeout = progress() > 2.0 && (mc.player.age - (packet ? packetStartTime : normalStartTime) > 60);
            return distance || timeout;
        }

        private double progress() {
            int slot = mc.player.getInventory().getSelectedSlot();
            return BlockUtils.getBreakDelta(slot, blockState) * ((mc.player.age - (packet ? packetStartTime : normalStartTime)) + 1);
        }

        private void renderLetter() {
            vec3.set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            if (!NametagUtils.to2D(vec3, 2)) return;

            NametagUtils.begin(vec3);
            TextRenderer.get().begin(1.0, false, true);

            String letter = packet ? "P" : "N";
            double w = TextRenderer.get().getWidth(letter) / 2.0;
            TextRenderer.get().render(letter, -w, 0.0, Color.WHITE, true);

            TextRenderer.get().end();
            NametagUtils.end();
        }
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }

    public enum Mode {
        All,
        Flatten,
        Smash
    }

    public enum SortMode {
        None,
        Closest,
        Furthest,
        TopDown
    }

    public enum Shape {
        Cube,
        UniformCube,
        Sphere
    }

    public static int chebyshevDist(int x1, int y1, int z1, int x2, int y2, int z2) {
        int dX = Math.abs(x2 - x1);
        int dY = Math.abs(y2 - y1);
        int dZ = Math.abs(z2 - z1);
        return Math.max(Math.max(dX, dY), dZ);
    }
}
