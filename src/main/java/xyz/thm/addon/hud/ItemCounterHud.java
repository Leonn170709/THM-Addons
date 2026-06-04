package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import xyz.thm.addon.THMAddon;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ItemCounterHud extends HudElement {
    public static final HudElementInfo<ItemCounterHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP, "item-counter", "Counts selected items across your inventory and shulker boxes.", ItemCounterHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to count.")
        .defaultValue(Items.ENDER_CHEST, Items.DIAMOND_PICKAXE)
        .build()
    );

    private final Setting<SettingColor> labelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("label-color")
        .description("Color of the item name label.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> countColor = sgGeneral.add(new ColorSetting.Builder()
        .name("count-color")
        .description("Color of the item count number.")
        .defaultValue(new SettingColor(255, 100, 100, 255))
        .build()
    );

    private final Setting<Boolean> scanShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-shulkers")
        .description("Also count items inside shulker boxes in your inventory.")
        .defaultValue(true)
        .build()
    );

    public ItemCounterHud() {
        super(INFO);
    }

    private int countItem(Item target) {
        if (mc.player == null) return 0;
        int total = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() == target) total += stack.getCount();

            if (scanShulkers.get() && isShulkerBox(stack)) {
                ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
                if (container != null) {
                    for (ItemStack inner : container.iterateNonEmpty()) {
                        if (inner.getItem() == target) total += inner.getCount();
                    }
                }
            }
        }

        return total;
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private String itemLabel(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    @Override
    public void render(HudRenderer renderer) {
        List<Item> itemList = items.get();

        if (itemList.isEmpty()) {
            renderer.text("Item Counter", x, y, labelColor.get(), true);
            setSize(renderer.textWidth("Item Counter", true), renderer.textHeight(true));
            return;
        }

        double lineHeight = renderer.textHeight(true);
        double maxWidth = 0;

        for (Item item : itemList) {
            String label = itemLabel(item) + ": ";
            String count = String.valueOf(countItem(item));
            maxWidth = Math.max(maxWidth, renderer.textWidth(label, true) + renderer.textWidth(count, true));
        }

        setSize(maxWidth, lineHeight * itemList.size());

        double currentY = y;
        for (Item item : itemList) {
            String label = itemLabel(item) + ": ";
            String count = String.valueOf(countItem(item));

            renderer.text(label, x, currentY, labelColor.get(), true);
            renderer.text(count, x + renderer.textWidth(label, true), currentY, countColor.get(), true);

            currentY += lineHeight;
        }
    }
}
