/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.screens.settings.base.SortingHelper;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

// Custom picker for ItemPriorityList — left column is the normal alphabetical "everything" list,
// right column is the selection in actual list order (index 0 on top = highest priority) with
// Up/Down/Minus buttons instead of Meteor's usual alphabetically-sorted selected column.
public class ItemPriorityListScreen extends WindowScreen {
    private final GenericSetting<ItemPriorityList> setting;
    private final List<Item> selected;
    private final Predicate<Item> itemFilter;

    private WTable table;
    private String filterText = "";

    public ItemPriorityListScreen(GuiTheme theme, ItemPriorityList value, GenericSetting<ItemPriorityList> setting) {
        super(theme, "Select Food (top = highest priority)");

        this.setting = setting;
        this.selected = value.selected();
        this.itemFilter = value.filter();
    }

    @Override
    public void initWidgets() {
        WTextBox filter = add(theme.textBox("")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();
            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        Predicate<Item> notSelected = item -> item != Items.AIR && !selected.contains(item)
            && (itemFilter == null || itemFilter.test(item));
        Iterable<Item> left = SortingHelper.sort(BuiltInRegistries.ITEM, notSelected, this::names, filterText);

        Cell<WTable> leftCell = table.add(theme.table()).top();
        WTable leftTable = leftCell.widget();

        left.forEach(item -> {
            leftTable.add(theme.itemWithLabel(item.getDefaultInstance()));

            WPressable add = leftTable.add(theme.plus()).expandCellX().right().widget();
            add.action = () -> {
                selected.add(item);
                changed();
            };

            leftTable.row();
        });
        if (!leftTable.cells.isEmpty()) leftCell.expandX();

        table.add(theme.verticalSeparator()).expandWidgetY();

        Cell<WTable> rightCell = table.add(theme.table()).top();
        WTable rightTable = rightCell.widget();

        for (int i = 0; i < selected.size(); i++) {
            Item item = selected.get(i);
            if (!filterText.isBlank() && !matches(item, filterText)) continue;

            int index = i;
            rightTable.add(theme.itemWithLabel(item.getDefaultInstance()));

            WPressable up = rightTable.add(theme.button("^")).widget();
            up.action = () -> {
                if (index <= 0) return;
                Collections.swap(selected, index, index - 1);
                changed();
            };

            WPressable down = rightTable.add(theme.button("v")).widget();
            down.action = () -> {
                if (index >= selected.size() - 1) return;
                Collections.swap(selected, index, index + 1);
                changed();
            };

            WPressable remove = rightTable.add(theme.minus()).expandCellX().right().widget();
            remove.action = () -> {
                selected.remove(item);
                changed();
            };

            rightTable.row();
        }
        if (!rightTable.cells.isEmpty()) rightCell.expandX();
    }

    private void changed() {
        setting.onChanged();
        table.clear();
        initTable();
    }

    private boolean matches(Item item, String text) {
        for (String name : names(item)) {
            if (name.toLowerCase().contains(text.toLowerCase())) return true;
        }
        return false;
    }

    private String[] names(Item item) {
        return new String[] {
            Names.get(item),
            BuiltInRegistries.ITEM.getKey(item).toString()
        };
    }
}
