package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.thm.addon.THMAddon;

import java.util.Arrays;
import java.util.List;

public class AntiDrop extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> checkShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("check-shulkers")
        .description("If shulkers should be checked.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> allItems = sgGeneral.add(new BoolSetting.Builder()
        .name("all-items")
        .description("If to disable dropping completely.")
        .defaultValue(false)
        .build());

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("The items to stop dropping.")
        .defaultValue(Arrays.asList(
            Items.OBSIDIAN,
            Items.ENDER_CHEST,
            Items.DIAMOND_PICKAXE,
            Items.NETHERITE_PICKAXE
        ))
        .visible(() -> !allItems.get())
        .build()
    );

    public AntiDrop() {
        super(THMAddon.MAIN, "AntiDrop", "Stops you from dropping certain items.");
    }

    @EventHandler
    private void onDrop(DropItemsEvent event) {
        if (allItems.get()) {
            event.cancel();
            return;
        }
        if (items.get().contains(event.itemStack.getItem())) {
            event.cancel();
            return;
        }
        if (Utils.isShulker(event.itemStack.getItem()) && checkShulkers.get()) {
            ItemStack[] itemStacks = new ItemStack[27];
            Utils.getItemsInContainerItem(event.itemStack, itemStacks);
            for (ItemStack item : itemStacks) {
                if (items.get().contains(item.getItem())) {
                    event.cancel();
                    return;
                }
            }
        }
    }
}
