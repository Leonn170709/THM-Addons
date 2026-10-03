/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.settings.ItemSettingScreen;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import xyz.thm.addon.THMAddon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class HotbarManager extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("move-delay")
        .description("Delay in ticks between moving items.")
        .defaultValue(1)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> replace = sgGeneral.add(new BoolSetting.Builder()
        .name("replace")
        .description("Replace items already in your hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggle = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle")
        .description("Toggle off automatically after one pass through the hotbar.")
        .defaultValue(false)
        .build()
    );

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton reset = theme.button("Reset");
        reset.action = () -> itemSettings.forEach(Setting::reset);
        return reset;
    }

    private final List<Setting<Item>> itemSettings = new ArrayList<>();
    private final Item[] defaultItems = {
        Items.AIR,          // Slot 1
        Items.AIR,          // Slot 2
        Items.AIR,          // Slot 3
        Items.AIR,          // Slot 4
        Items.ENCHANTED_GOLDEN_APPLE,          // Slot 5
        Items.NETHERRACK,          // Slot 6
        Items.ENDER_CHEST,          // Slot 7
        Items.NETHERITE_PICKAXE,          // Slot 8
        Items.OBSIDIAN            // Slot 9
    };

    private double ticksLeft;

    public HotbarManager() {
        super(THMAddon.MAIN, "hotbar-manager", "Automatically move items to your hotbar.");
        final SettingGroup sgHotbar = settings.createGroup("Hotbar");

        for (int i = 0; i < 9; i++) {
            Setting<Item> setting = sgHotbar.add(new ClearableItemSetting.Builder()
                .name("slot-" + (i + 1))
                .description("The item to store in slot " + (i + 1) + ".")
                .defaultValue(defaultItems[i])
                .build()
            );
            itemSettings.add(setting);
        }
    }

    @Override
    public void onActivate() {
        ticksLeft = 0.0;
    }

    public boolean managesSlot(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= itemSettings.size()) return false;
        return itemSettings.get(hotbarSlot).get() != Items.AIR;
    }

    public Item getManagedItem(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= itemSettings.size()) return Items.AIR;
        return itemSettings.get(hotbarSlot).get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if ((ticksLeft -= TickRate.INSTANCE.getTickRate() / 20.0) > 0.0) return;

        int highestSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (ticksLeft > 0.0) return;
            highestSlot = Math.max(highestSlot, i);

            Item targetItem = itemSettings.get(i).get();
            if (targetItem == Items.AIR) continue;

            Item slotItem = mc.player.getInventory().getItem(i).getItem();
            if (slotItem != Items.AIR && !replace.get()) continue;
            if (slotItem == targetItem) continue;

            FindItemResult result = InvUtils.find(stack -> stack.getItem() == targetItem, i, 35);
            if (!result.found()) continue;

            InvUtils.move().from(result.slot()).to(i);
            ticksLeft = delay.get();
        }

        if (highestSlot == 8 && toggle.get()) toggle();
    }

    private static void registerClearableItemSettingWidget() {
        SettingsWidgetFactory.registerCustomFactory(ClearableItemSetting.class, theme -> (table, setting) ->
            fillClearableItemSettingRow(theme, table, (ClearableItemSetting) setting)
        );
    }

    private static void fillClearableItemSettingRow(GuiTheme theme, WTable table, ClearableItemSetting setting) {
        WHorizontalList list = table.add(theme.horizontalList()).expandX().widget();

        WItem item = list.add(theme.item(setting.get().getDefaultInstance())).widget();

        WButton select = list.add(theme.button("Select")).widget();
        select.action = () -> {
            ItemSettingScreen screen = new ItemSettingScreen(theme, setting);
            screen.onClosed(() -> item.set(setting.get().getDefaultInstance()));
            MeteorClient.mc.setScreen(screen);
        };

        WButton clear = list.add(theme.button("Clear")).widget();
        clear.action = () -> {
            setting.set(Items.AIR);
            item.set(Items.AIR.getDefaultInstance());
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            item.set(setting.get().getDefaultInstance());
        };
        reset.tooltip = "Reset";
    }

    static {
        registerClearableItemSettingWidget();
    }

    private static class ClearableItemSetting extends ItemSetting {
        public ClearableItemSetting(String name, String description, Item defaultValue, Consumer<Item> onChanged, Consumer<Setting<Item>> onModuleActivated, IVisible visible, Predicate<Item> filter) {
            super(name, description, defaultValue, onChanged, onModuleActivated, visible, filter);
        }

        @Override
        public Item load(CompoundTag tag) {
            if (tag.getList("value").isPresent()) {
                String value = tag.getListOrEmpty("value").getStringOr(0, "minecraft:air");
                Item item = parseImpl(value);
                setValue(item == null ? Items.AIR : item);
                return get();
            }

            return super.load(tag);
        }

        @Override
        protected boolean isValueValid(Item value) {
            return value == Items.AIR || super.isValueValid(value);
        }

        private void setValue(Item item) {
            value = item;
        }

        private static class Builder extends SettingBuilder<xyz.thm.addon.modules.HotbarManager.ClearableItemSetting.Builder, Item, ClearableItemSetting> {
            private Predicate<Item> filter;

            public Builder() {
                super(Items.AIR);
            }

            public xyz.thm.addon.modules.HotbarManager.ClearableItemSetting.Builder defaultValue(Item defaultValue) {
                this.defaultValue = defaultValue == null ? Items.AIR : defaultValue;
                return this;
            }

            public xyz.thm.addon.modules.HotbarManager.ClearableItemSetting.Builder filter(Predicate<Item> filter) {
                this.filter = filter;
                return this;
            }

            @Override
            public ClearableItemSetting build() {
                return new ClearableItemSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, filter);
            }
        }
    }
}
