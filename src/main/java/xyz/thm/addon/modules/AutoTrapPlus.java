/*
 * Base module copied from Meteor Client AutoTrap
 * https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/AutoTrap.java
 */
package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.PacketPlaceUtils;

import java.util.*;

public class AutoTrapPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks to use for trapping.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range at which blocks can be placed.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to place when behind blocks.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to target players.")
        .defaultValue(3)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between block placements.")
        .defaultValue(1)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("top-blocks")
        .description("Which blocks to place at head height.")
        .defaultValue(TopMode.Full)
        .build()
    );

    private final Setting<HeightMode> heightMode = sgGeneral.add(new EnumSetting.Builder<HeightMode>()
        .name("height-mode")
        .description("Where to build the trap: around feet or around eye height.")
        .defaultValue(HeightMode.Feet)
        .build()
    );

    // Bottom blocks are always placed in Full mode (with crystal/anchor gap support)

    private final Setting<BuildOrder> buildOrder = sgGeneral.add(new EnumSetting.Builder<BuildOrder>()
        .name("build-order")
        .description("Order to build columns: bottom-to-top or top-to-bottom.")
        .defaultValue(BuildOrder.BottomToTop)
        .build()
    );

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("self-toggle")
        .description("Toggle off after placing all blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards blocks when placing.")
        .defaultValue(false)
        .build()
    );
    // WIP: packet placing support
    private final Setting<Boolean> packet = sgGeneral.add(new BoolSetting.Builder()
        .name("packet")
        .description("Only place via packets (no client-side block set). WIP")
        .defaultValue(false)
        .build()
    );

    private final Setting<GapSide> gapSide = sgGeneral.add(new EnumSetting.Builder<GapSide>()
        .name("Crystal/Anchor gap")
        .description("Leave one feet-level side as air for crystals/anchors.")
        .defaultValue(GapSide.None)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allows placing blocks in the air. Disable to build supports first.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders queued and recently placed blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color.")
        .defaultValue(new SettingColor(THMAddon.THMSideColor.r, THMAddon.THMSideColor.g, THMAddon.THMSideColor.b, THMAddon.THMSideColor.a))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, THMAddon.THMColor.a))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> fade = sgRender.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fades recently placed blocks over time.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How long the fade lasts in seconds.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMax(2)
        .visible(() -> render.get() && fade.get())
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private PlayerEntity target;
    private boolean placedAny;
    private int timer;
    private BlockPos gapPos;
    private final Map<BlockPos, Long> renderMap = new HashMap<>();

    public AutoTrapPlus() {
        super(THMAddon.PVP, "auto-trap+", "Traps a target player. Adds an optional anti-cheat friendly support placement mode.");
    }

    @Override
    public void onActivate() {
        target = null;
        placePositions.clear();
        timer = 0;
        placedAny = false;
        gapPos = null;
        renderMap.clear();
    }

    @Override
    public void onDeactivate() {
        placePositions.clear();
        renderMap.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (selfToggle.get() && placedAny && placePositions.isEmpty()) {
            placedAny = false;
            toggle();
            return;
        }

        // Find blocks in hotbar
        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) return;

        // Find targets
        List<PlayerEntity> targets = getTargets();
        if (targets.isEmpty()) {
            target = null;
            placePositions.clear();
            return;
        }

        boolean doPlace = timer >= delay.get();
        int placedCount = 0;
        LinkedHashSet<BlockPos> allPositions = new LinkedHashSet<>();

        for (PlayerEntity t : targets) {
            target = t;

            // Compute gap position (feet-level) to skip and to avoid filling with supports
            gapPos = null;
            if (gapSide.get() != GapSide.None) {
                int feetY = (int) Math.floor(t.getBoundingBox().minY);
                int gapY = heightMode.get() == HeightMode.Eye ? (int) Math.floor(t.getEyeY()) : feetY;
                BlockPos center = BlockPos.ofFloored(t.getX(), gapY, t.getZ());
                gapPos = center.add(getGapOffsetX(t), 0, getGapOffsetZ(t));
            }

            // Compute trap positions for this target
            fillPlaceArray(t);
            allPositions.addAll(placePositions);

            // Place following sorted order (column-wise, respecting build order)
            if (doPlace && !placePositions.isEmpty()) {
                for (BlockPos placePos : placePositions) {
                    if (placedCount >= blocksPerTick.get()) break;
                    if (tryPlaceWithSupports(placePos, block)) {
                        placedAny = true;
                        placedCount++;
                    }
                }
            }

            if (placedCount >= blocksPerTick.get()) break;
        }

        placePositions.clear();
        placePositions.addAll(allPositions);

        if (doPlace) timer = 0;
        else timer++;
    }

    private List<PlayerEntity> getTargets() {
        List<Entity> entities = new ArrayList<>();
        TargetUtils.getList(entities, entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isSpectator() || !player.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, targetRange.get())) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer) return !fakePlayer.noHit;
            return EntityUtils.getGameMode(player) == GameMode.SURVIVAL;
        }, priority.get(), Integer.MAX_VALUE);

        List<PlayerEntity> players = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            if (entity instanceof PlayerEntity p) players.add(p);
        }
        return players;
    }

    private boolean tryPlaceWithSupports(BlockPos placePos, FindItemResult block) {
        if (isBlockedByOtherEntity(placePos, target)) return false;
        // Direct place if allowed
        if (airPlace.get()) {
            if (placeBlock(placePos, block)) {
                markPlaced(placePos);
                return true;
            }
            return false;
        }

        // Need a neighbor face; first try direct
        if (BlockUtils.getPlaceSide(placePos) != null) {
            if (placeBlock(placePos, block)) {
                markPlaced(placePos);
                return true;
            }
            return false;
        }
        if (!mc.world.getBlockState(placePos).isReplaceable()) return false;

        // Build candidate directions with outward first, then others
        net.minecraft.util.math.Direction outward = net.minecraft.util.math.Direction.NORTH;
        if (target != null) {
            BlockPos center = BlockPos.ofFloored(target.getX(), Math.floor(target.getBoundingBox().minY), target.getZ());
            int dx = placePos.getX() - center.getX();
            int dz = placePos.getZ() - center.getZ();
            if (Math.abs(dx) >= Math.abs(dz)) outward = dx > 0 ? net.minecraft.util.math.Direction.EAST : net.minecraft.util.math.Direction.WEST;
            else outward = dz > 0 ? net.minecraft.util.math.Direction.SOUTH : net.minecraft.util.math.Direction.NORTH;
        }

        net.minecraft.util.math.Direction[] dirs = new net.minecraft.util.math.Direction[] {
            outward,
            outward.rotateYClockwise(),
            outward.rotateYCounterclockwise(),
            outward.getOpposite()
        };

        // Optimize supports: place exactly one side support adjacent to target, preferring outward first
        for (net.minecraft.util.math.Direction d : dirs) {
            BlockPos s = placePos.offset(d);
            // don't block the feet gap column
            if (gapPos != null && s.down().equals(gapPos)) continue;
            if (isBlockedByOtherEntity(s, target)) continue;
            if (BlockUtils.getPlaceSide(s) == null) continue;
            if (placeBlock(s, block)) {
                markPlaced(s);
                if (BlockUtils.getPlaceSide(placePos) != null && placeBlock(placePos, block)) {
                    markPlaced(placePos);
                    return true;
                }
                return false;
            }
        }

        return false;
    }

    private boolean placeBlock(BlockPos pos, FindItemResult block) {
        if (packet.get()) {
            return PacketPlaceUtils.placeBlockPacket(pos, block, rotate.get(), 50);
        }
        return BlockUtils.place(pos, block, rotate.get(), 50, true);
    }

    // Removed old helper methods for broad support search to keep logic minimal

    private void fillPlaceArray(PlayerEntity t) {
        placePositions.clear();

        double epsilon = 1e-5;
        Box box = t.getBoundingBox();
        List<BlockPos> corners = new ArrayList<>();
        corners.add(BlockPos.ofFloored(box.minX, box.minY, box.minZ));
        corners.add(BlockPos.ofFloored(box.minX, box.minY, box.maxZ - epsilon));
        corners.add(BlockPos.ofFloored(box.maxX - epsilon, box.minY, box.minZ));
        corners.add(BlockPos.ofFloored(box.maxX - epsilon, box.minY, box.maxZ - epsilon));

        Set<BlockPos> overlappedPositions = new LinkedHashSet<>(corners);
        for (BlockPos base : overlappedPositions) {
            if (heightMode.get() == HeightMode.Eye) {
                int eyeY = (int) Math.floor(t.getEyeY());
                BlockPos eyeBase = new BlockPos(base.getX(), eyeY, base.getZ());
                // Eye-height ring
                add(eyeBase.add(1, 0, 0));
                add(eyeBase.add(-1, 0, 0));
                add(eyeBase.add(0, 0, -1));
                add(eyeBase.add(0, 0, 1));
                // Top block above eye height to prevent jumping
                add(eyeBase.add(0, 1, 0));
                continue;
            }

            switch (topPlacement.get()) {
                case Full -> {
                    add(base.add(0, 2, 0));
                    add(base.add(1, 1, 0));
                    add(base.add(-1, 1, 0));
                    add(base.add(0, 1, 1));
                    add(base.add(0, 1, -1));
                }
                case Face -> {
                    add(base.add(1, 1, 0));
                    add(base.add(-1, 1, 0));
                    add(base.add(0, 1, 1));
                    add(base.add(0, 1, -1));
                }
                case Top -> add(base.add(0, 2, 0));
                case None -> {}
            }
            // Bottom - always Full: platform below (y -1) and full ring at feet level (y 0)
            add(base.add(0, -1, 0));
            add(base.add(1, -1, 0));
            add(base.add(-1, -1, 0));
            add(base.add(0, -1, 1));
            add(base.add(0, -1, -1));

            add(base.add(1, 0, 0));
            add(base.add(-1, 0, 0));
            add(base.add(0, 0, -1));
            add(base.add(0, 0, 1));
        }

        // Apply gap: remove only the feet-level block on the chosen side
        if (gapSide.get() != GapSide.None) {
            if (gapPos != null) placePositions.remove(gapPos);
        }
        // Column-wise build order: group by exact (x,z), then order Y strictly by buildOrder
        boolean bottomToTop = buildOrder.get() == BuildOrder.BottomToTop;
        placePositions.sort((a, b) -> {
            if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
            if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
            return bottomToTop ? Integer.compare(a.getY(), b.getY()) : Integer.compare(b.getY(), a.getY());
        });
    }

    private void add(BlockPos blockPos) {
        if (placePositions.contains(blockPos)) return;
        if (!canQueue(blockPos)) return;
        if (isOutOfRange(blockPos)) return;
        placePositions.add(blockPos);
    }

    private boolean canQueue(BlockPos pos) {
        // Allow positions without neighbors; supports/air-place handle those later.
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        // Ignore entity collisions here so straddling targets still queue correctly.
        if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent())) return false;
        return !isBlockedByOtherEntity(pos, target);
    }

    private boolean isBlockedByOtherEntity(BlockPos pos, PlayerEntity allowed) {
        Box checkBox = Box.from(Vec3d.ofCenter(pos));
        List<net.minecraft.entity.Entity> entities = mc.world.getOtherEntities(null, checkBox);
        for (net.minecraft.entity.Entity entity : entities) {
            if (entity == allowed) continue;
            if (!entity.isSpectator() && entity.isAlive()) return true;
        }
        return false;
    }

    private void markPlaced(BlockPos pos) {
        if (render.get()) renderMap.put(pos, System.currentTimeMillis());
    }

    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3d pos = blockPos.toCenterPos();
        if (!PlayerUtils.isWithin(pos, placeRange.get())) return true;

        RaycastContext raycastContext = new RaycastContext(mc.player.getEyePos(), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(raycastContext);
        if (result == null || !result.getBlockPos().equals(blockPos)) return !PlayerUtils.isWithin(pos, placeWallsRange.get());
        return false;
    }

    // Removed unused isAirPlace()

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : placePositions) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }

        if (renderMap.isEmpty()) return;

        renderMap.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > fadeTime.get() * 1000);
        renderMap.forEach((pos, time) -> {
            double progress = 1.0;
            if (fade.get()) {
                long alive = System.currentTimeMillis() - time;
                progress = 1.0 - MathHelper.clamp((double) alive / (fadeTime.get() * 1000), 0.0, 1.0);
            }

            SettingColor sColor = new SettingColor(sideColor.get());
            SettingColor lColor = new SettingColor(lineColor.get());
            sColor.a = (int) (sColor.a * progress);
            lColor.a = (int) (lColor.a * progress);

            event.renderer.box(pos, sColor, lColor, shapeMode.get(), 0);
        });
    }

    private int getGapOffsetX(PlayerEntity t) {
        return switch (gapSide.get()) {
            case East -> 1;
            case West -> -1;
            case South, North -> 0;
            case TowardPlayer -> {
                //? if >=1.21.9 {
                Vec3d toPlayer = mc.player.getEntityPos().subtract(t.getEntityPos());
                //?} else
                /*Vec3d toPlayer = mc.player.getPos().subtract(t.getPos());
                */
                if (Math.abs(toPlayer.x) >= Math.abs(toPlayer.z)) yield toPlayer.x > 0 ? 1 : -1;
                else yield 0;
            }
            case None -> 0;
        };
    }

    private int getGapOffsetZ(PlayerEntity t) {
        return switch (gapSide.get()) {
            case South -> 1;
            case North -> -1;
            case East, West -> 0;
            case TowardPlayer -> {
                //? if >=1.21.9 {
                Vec3d toPlayer = mc.player.getEntityPos().subtract(t.getEntityPos());
                //?} else
                /*Vec3d toPlayer = mc.player.getPos().subtract(t.getPos());
                */
                if (Math.abs(toPlayer.z) > Math.abs(toPlayer.x)) yield toPlayer.z > 0 ? 1 : -1;
                else yield 0;
            }
            case None -> 0;
        };
    }

    public enum TopMode {
        Full,
        Top,
        Face,
        None
    }

    public enum HeightMode {
        Feet,
        Eye
    }

    // Bottom mode is fixed to Full; enum removed

    public enum GapSide {
        None,
        TowardPlayer,
        North,
        South,
        East,
        West
    }

    public enum BuildOrder {
        BottomToTop,
        TopToBottom
    }
}
