/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import xyz.thm.addon.THMAddon;

public class AntiPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("The maximum distance to target players.")
        .defaultValue(10)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("The range at which scaffolding can be placed.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> ignoreNaked = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-naked")
        .description("Skips players without armor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the scaffolding when placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders where scaffolding is placed.")
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

    private final BlockPos.Mutable renderPos = new BlockPos.Mutable();
    private boolean rendering;
    private PlayerEntity target;

    public AntiPhase() {
        super(THMAddon.PVP, "anti-phase", "Places scaffolding inside players so their pearls land instead of phasing them.");
    }

    @Override
    public void onActivate() {
        target = null;
        rendering = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        rendering = false;

        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = TargetUtils.getPlayerTarget(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) return;
        }

        if (ignoreNaked.get() && target.getArmor() == 0) return;

        FindItemResult scaffolding = InvUtils.findInHotbar(Items.SCAFFOLDING);
        if (!scaffolding.found()) return;

        BlockPos feet = target.getBlockPos();
        if (!mc.world.getBlockState(feet).isReplaceable()) return;
        if (!PlayerUtils.isWithin(feet.toCenterPos(), reach.get())) return;

        // checkEntities off: scaffolding reports a solid shape to an absent context, so the client
        // check would reject placing inside the target — the server accepts it.
        if (BlockUtils.place(feet, scaffolding, rotate.get(), 50, true, false, true)) {
            renderPos.set(feet);
            rendering = true;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || !rendering) return;

        event.renderer.box(renderPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}
