/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.IGeneric;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

// Like StringMultiSelect, but the selection order IS the value — index 0 is highest priority.
// Its screen shows the selected column in list order with Up/Down buttons instead of sorting it
// alphabetically, so the user can rank items instead of just picking them.
public class ItemPriorityList implements IGeneric<ItemPriorityList> {
    private final List<Item> selected = new ArrayList<>();
    private final Predicate<Item> filter;

    public ItemPriorityList() {
        this(null);
    }

    /** filter, if non-null, limits which items the picker's left (candidate) column offers. */
    public ItemPriorityList(Predicate<Item> filter) {
        this.filter = filter;
    }

    /** Live, mutable backing list — index 0 is highest priority. The settings screen edits this directly. */
    public List<Item> selected() {
        return selected;
    }

    /** Null means no restriction — every item is offered. */
    public Predicate<Item> filter() {
        return filter;
    }

    @Override
    public WidgetScreen createScreen(GuiTheme theme, GenericSetting<ItemPriorityList> setting) {
        return new ItemPriorityListScreen(theme, this, setting);
    }

    @Override
    public ItemPriorityList set(ItemPriorityList value) {
        selected.clear();
        selected.addAll(value.selected);
        return this;
    }

    @Override
    public ItemPriorityList copy() {
        ItemPriorityList copy = new ItemPriorityList(filter);
        copy.selected.addAll(selected);
        return copy;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Item item : selected) list.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
        tag.put("selected", list);
        return tag;
    }

    @Override
    public ItemPriorityList fromTag(CompoundTag tag) {
        selected.clear();
        tag.getListOrEmpty("selected").forEach(el -> {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(el.asString().orElse("minecraft:air")));
            if (item != Items.AIR) selected.add(item);
        });
        return this;
    }
}
