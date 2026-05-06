package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ExplosionS2CPacketAccessor;
import xyz.thm.addon.utils.PacketPlaceUtils;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SurroundPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place Logic");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgCenter = settings.createGroup("Center Logic");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to use for surrounding.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .build()
    );

    private final Setting<Boolean> packet = sgPlace.add(new BoolSetting.Builder()
        .name("packet")
        .description("Only place via packets (no client-side block set).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> tagSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("tag-switch")
        .description("Disables the module immediately after placing missing blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Tick delay between block placements.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgPlace.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Maximum blocks to place per tick.")
        .defaultValue(4)
        .min(1)
        .sliderMax(8)
        .build()
    );

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends rotation packets when placing (Crucial for GrimAC).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> extend = sgPlace.add(new BoolSetting.Builder()
        .name("extend")
        .description("Encases your feet even when standing on the edge of blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> strict = sgPlace.add(new BoolSetting.Builder()
        .name("strict-directions")
        .description("Only places on visible block faces to bypass strict anti-cheats.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support")
        .description("Places a block under your feet if open air.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> attackCrystals = sgPlace.add(new BoolSetting.Builder()
        .name("attack-crystals")
        .description("Attacks crystals in the way before placing.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> headLevel = sgPlace.add(new BoolSetting.Builder()
        .name("head-level")
        .description("Also places surround at Y+1.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> coverHead = sgPlace.add(new BoolSetting.Builder()
        .name("cover-head")
        .description("Places a block at Y+2.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> mineExtend = sgPlace.add(new BoolSetting.Builder()
        .name("mine-extend")
        .description("Extends surround outward when a surround block is being mined.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> multitask = sgPlace.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Allows placing while using items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<TimingMode> timingMode = sgTiming.add(new EnumSetting.Builder<TimingMode>()
        .name("timing-mode")
        .description("Timing mode for replacement.")
        .defaultValue(TimingMode.Sequential)
        .build()
    );
    private final Setting<Boolean> prePlaceExplosion = sgTiming.add(new BoolSetting.Builder()
        .name("pre-place-explosion")
        .description("Attempts immediate replacement on explosion packets.")
        .defaultValue(true)
        .visible(() -> timingMode.get() == TimingMode.Sequential)
        .build()
    );
    private final Setting<Boolean> prePlaceCrystalSpawn = sgTiming.add(new BoolSetting.Builder()
        .name("pre-place-crystal-spawn")
        .description("Attempts immediate replacement when crystals spawn on surround.")
        .defaultValue(true)
        .visible(() -> timingMode.get() == TimingMode.Sequential)
        .build()
    );
    private final Setting<Double> shiftDelay = sgTiming.add(new DoubleSetting.Builder()
        .name("shift-delay")
        .description("Minimum delay between retries for the same surround position.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgPlace.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only activates when you are on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableOnJump = sgPlace.add(new BoolSetting.Builder()
        .name("disable-on-jump")
        .description("Automatically disables the module if you jump.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnYChange = sgPlace.add(new BoolSetting.Builder()
        .name("disable-on-y-change")
        .description("Disables if your Y level changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<CenterMode> centerMode = sgCenter.add(new EnumSetting.Builder<CenterMode>()
        .name("center-mode")
        .description("Method used to center the player.")
        .defaultValue(CenterMode.NCP)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the block placements.")
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
        .description("Fades the rendered block over time.")
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

    private final Map<BlockPos, Long> renderMap = new HashMap<>();
    private final Map<BlockPos, Long> packetPlacedAt = new HashMap<>();
    private final List<BlockPos> surroundCache = new ArrayList<>();
    // Thread-safe queue for placements triggered from the packet (Netty) thread
    private final Queue<BlockPos> fallbackQueue = new ConcurrentLinkedQueue<>();
    private int delayTimer;
    private BlockPos initialPos;

    public SurroundPlus() {
        super(THMAddon.PVP, "surround-plus", "Surrounds feet with Obsidian using strict logic.");
    }

    @Override
    public void onActivate() {
        delayTimer = 0;
        renderMap.clear();
        packetPlacedAt.clear();
        surroundCache.clear();
        fallbackQueue.clear();
        if (mc.player == null) return;
        initialPos = mc.player.getBlockPos();

        if (centerMode.get() == CenterMode.Teleport) {
            PlayerUtils.centerPlayer();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!multitask.get() && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND) return;

        if ((disableOnJump.get() && mc.options.jumpKey.isPressed()) || (disableOnYChange.get() && mc.player.getY() != initialPos.getY())) {
            toggle();
            return;
        }

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        handleCentering();

        // Process fallback placements queued from the packet (Netty) thread — must run on main thread
        BlockPos fallback;
        while ((fallback = fallbackQueue.poll()) != null) {
            FindItemResult fallbackItem = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
            if (fallbackItem.found()) placeBlock(fallback, fallbackItem);
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) return;

        int placed = 0;
        Set<BlockPos> insideBlocks = getInsideBlocks();

        if (support.get()) {
            for (BlockPos inside : insideBlocks) {
                BlockPos underPos = inside.down();
                if (mc.world.getBlockState(underPos).isReplaceable()) {
                    if (placed >= blocksPerTick.get()) break;
                    if (placeBlock(underPos, block)) {
                        placed++;
                    }
                }
            }
        }

        Set<BlockPos> surroundPositions = getSurroundPositions(insideBlocks);
        surroundCache.clear();
        surroundCache.addAll(surroundPositions);
        if (attackCrystals.get()) attackCrystals(surroundCache);

        boolean allPlaced = true;

        for (BlockPos pos : surroundPositions) {
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;
            if (shiftDelay.get() > 0.0) {
                Long last = packetPlacedAt.get(pos);
                if (last != null && System.currentTimeMillis() - last < shiftDelay.get() * 50.0) continue;
            }

            // If support is enabled and the target block has no placeable side,
            // try to place a support block underneath first.
            if (support.get() && BlockUtils.getPlaceSide(pos) == null) {
                BlockPos supportPos = pos.down();
                if (mc.world.getBlockState(supportPos).isReplaceable()) {
                    if (placed >= blocksPerTick.get()) {
                        allPlaced = false;
                        break;
                    }
                    if (placeBlock(supportPos, block)) {
                        placed++;
                    } else {
                        allPlaced = false;
                        continue;
                    }
                } else {
                    // Can't place support and no side to place on: skip for now.
                    allPlaced = false;
                    continue;
                }
            }

            if (!BlockUtils.canPlace(pos)) {
                allPlaced = false;
                continue;
            }

            if (placed >= blocksPerTick.get()) {
                allPlaced = false;
                break;
            }

            if (placeBlock(pos, block)) {
                placed++;
            } else {
                allPlaced = false;
            }
        }

        if (placed > 0) {
            delayTimer = delay.get();
        }

        if (tagSwitch.get() && allPlaced) {
            toggle();
        }
    }

    private boolean placeBlock(BlockPos pos, FindItemResult item) {
        if (packet.get()) {
            if (!PacketPlaceUtils.placeBlockPacket(pos, item, rotate.get(), 50)) return false;
            renderMap.put(pos, System.currentTimeMillis());
            packetPlacedAt.put(pos, System.currentTimeMillis());
            return true;
        }

        if (BlockUtils.place(pos, item, rotate.get(), 50, true)) {
            setBlock(pos, item);
            renderMap.put(pos, System.currentTimeMillis());
            packetPlacedAt.put(pos, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    private void setBlock(BlockPos pos, FindItemResult item) {
        Item it = mc.player.getInventory().getStack(item.slot()).getItem();
        if (!(it instanceof BlockItem block)) return;

        mc.world.setBlockState(pos, block.getBlock().getDefaultState());
        mc.world.playSound(mc.player, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1, 1);
    }

    private void handleCentering() {
        if (centerMode.get() != CenterMode.NCP) return;

        Vec3d centerPos = Vec3d.ofBottomCenter(mc.player.getBlockPos());
        double xDiff = Math.abs(centerPos.x - mc.player.getX());
        double zDiff = Math.abs(centerPos.z - mc.player.getZ());

        if (xDiff <= 0.1 && zDiff <= 0.1) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        double motionX = (centerPos.x - mc.player.getX()) / 2.0;
        double motionZ = (centerPos.z - mc.player.getZ()) / 2.0;

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
    }

    private Set<BlockPos> getInsideBlocks() {
        BlockPos base = mc.player.getBlockPos();
        LinkedHashSet<BlockPos> inside = new LinkedHashSet<>();

        if (!extend.get()) {
            inside.add(base);
            return inside;
        }

        int[] size = getSize(mc.player);
        for (int x = size[0]; x <= size[1]; x++) {
            for (int z = size[2]; z <= size[3]; z++) {
                inside.add(base.add(x, 0, z));
            }
        }

        return inside;
    }

    private Set<BlockPos> getSurroundPositions(Set<BlockPos> insideBlocks) {
        LinkedHashSet<BlockPos> surround = new LinkedHashSet<>();
        Set<BlockPos> footBlocks = new LinkedHashSet<>(insideBlocks);
        for (BlockPos pos : insideBlocks) {
            BlockPos north = pos.north();
            BlockPos south = pos.south();
            BlockPos east = pos.east();
            BlockPos west = pos.west();

            if (!insideBlocks.contains(north)) surround.add(north);
            if (!insideBlocks.contains(south)) surround.add(south);
            if (!insideBlocks.contains(east)) surround.add(east);
            if (!insideBlocks.contains(west)) surround.add(west);
        }

        if (headLevel.get()) {
            LinkedHashSet<BlockPos> head = new LinkedHashSet<>();
            for (BlockPos foot : footBlocks) {
                BlockPos up = foot.up();
                head.add(up.north());
                head.add(up.south());
                head.add(up.east());
                head.add(up.west());
            }
            for (BlockPos foot : footBlocks) head.remove(foot.up());
            surround.addAll(head);
        }

        if (coverHead.get()) {
            for (BlockPos foot : footBlocks) surround.add(foot.up(2));
        }

        if (mineExtend.get()) {
            LinkedHashSet<BlockPos> ext = new LinkedHashSet<>();
            for (BlockPos pos : new ArrayList<>(surround)) {
                BlockState s = mc.world.getBlockState(pos);
                if (s.isReplaceable()) continue;
                if (s.getHardness(mc.world, pos) < 0) continue;
                for (Direction d : Direction.Type.HORIZONTAL) {
                    BlockPos e = pos.offset(d);
                    if (!footBlocks.contains(e) && !surround.contains(e)) ext.add(e);
                }
            }
            surround.addAll(ext);
        }
        return surround;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (timingMode.get() != TimingMode.Sequential) return;

        if (event.packet instanceof BundlePacket<?> bundle) {
            for (Object sub : bundle.getPackets()) {
                if (sub instanceof Packet<?> p) handlePacket(p);
            }
        } else if (event.packet instanceof Packet<?> p) {
            handlePacket(p);
        }
    }

    private void handlePacket(Packet<?> packet) {
        if (packet instanceof BlockUpdateS2CPacket p) {
            BlockPos pos = p.getPos();
            if (!surroundCache.contains(pos)) return;
            BlockState state = p.getState();
            if (state.isReplaceable() && mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent())) {
                placeFallbackDirect(pos);
            } else if (!state.isReplaceable()) {
                packetPlacedAt.remove(pos);
            }
            return;
        }

        if (packet instanceof ExplosionS2CPacket p && prePlaceExplosion.get()) {
            Vec3d c = ((ExplosionS2CPacketAccessor) (Object) p).getCenter();
            BlockPos pos = BlockPos.ofFloored(c.x, c.y, c.z);
            if (surroundCache.contains(pos)) placeFallbackDirect(pos);
            return;
        }

        if (packet instanceof EntitySpawnS2CPacket p && prePlaceCrystalSpawn.get() && p.getEntityType() == EntityType.END_CRYSTAL) {
            BlockPos pos = BlockPos.ofFloored(p.getX(), p.getY(), p.getZ());
            if (surroundCache.contains(pos)) placeFallbackDirect(pos);
        }
    }

    // Queue the position for placement on the main thread instead of placing directly
    // (BlockUtils.place triggers a chunk rebuild which must run on the render/main thread)
    private void placeFallbackDirect(BlockPos pos) {
        fallbackQueue.add(pos);
    }

    private void attackCrystals(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            Entity crystal = mc.world.getOtherEntities(null, new Box(pos)).stream()
                .filter(e -> e instanceof EndCrystalEntity)
                .findFirst()
                .orElse(null);
            if (crystal != null) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                return;
            }
        }
    }

    private int[] getSize(PlayerEntity player) {
        int[] size = new int[] {0, 0, 0, 0};

        double x = player.getX() - player.getBlockX();
        double z = player.getZ() - player.getBlockZ();

        if (x < 0.3) size[0] = -1;
        if (x > 0.7) size[1] = 1;
        if (z < 0.3) size[2] = -1;
        if (z > 0.7) size[3] = 1;

        return size;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderMap.isEmpty()) return;

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

    public enum CenterMode {
        Teleport,
        NCP,
        None
    }

    public enum TimingMode {
        Vanilla,
        Sequential
    }
}
