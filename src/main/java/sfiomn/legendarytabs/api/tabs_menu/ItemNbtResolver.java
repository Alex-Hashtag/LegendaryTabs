package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import sfiomn.legendarytabs.LegendaryTabs;

/**
 * Resolves an {@code item_nbt} tab-size variable: locates an ItemStack connected to
 * the player (held, carried, worn in a Curios slot, or via a mod-specific accessor),
 * then reads a nested NBT path off it.
 */
public class ItemNbtResolver {

    public enum ValueType { INT, DOUBLE, BOOLEAN }

    private ItemNbtResolver() {}

    /**
     * @return the resolved numeric value, or {@code null} if no matching item was found
     *         or the NBT path is missing/of the wrong type.
     */
    public static Double resolve(Player player, String locator, String path, ValueType type) {
        ItemStack stack = locateItem(player, locator);
        if (stack == null || stack.isEmpty()) return null;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();

        String[] segments = path.split("\\.");
        CompoundTag current = tag;
        for (int i = 0; i < segments.length - 1; i++) {
            if (!current.contains(segments[i], Tag.TAG_COMPOUND)) return null;
            current = current.getCompound(segments[i]);
        }

        String leaf = segments[segments.length - 1];
        return switch (type) {
            case INT -> current.contains(leaf, Tag.TAG_ANY_NUMERIC) ? (double) current.getInt(leaf) : null;
            case DOUBLE -> current.contains(leaf, Tag.TAG_ANY_NUMERIC) ? current.getDouble(leaf) : null;
            case BOOLEAN -> current.contains(leaf, Tag.TAG_ANY_NUMERIC) ? (current.getBoolean(leaf) ? 1.0 : 0.0) : null;
        };
    }

    private static ItemStack locateItem(Player player, String locator) {
        if (locator == null) return null;

        if (locator.equals("main_hand")) {
            return player.getMainHandItem();
        }
        if (locator.equals("off_hand")) {
            return player.getOffhandItem();
        }
        if (locator.equals("travelers_backpack_wearable")) {
            if (!LegendaryTabs.travelersBackpackLoaded) return null;
            var wrapper = com.tiviacz.travelersbackpack.capability.AttachmentUtils.getBackpackWrapper(player);
            return wrapper != null ? wrapper.getBackpackStack() : null;
        }
        if (locator.startsWith("inventory:")) {
            return findInInventory(player, locator.substring("inventory:".length()));
        }
        if (locator.startsWith("curio:")) {
            return findInCurios(player, locator.substring("curio:".length()));
        }

        LegendaryTabs.LOGGER.warn("Unknown item_nbt locator: {}", locator);
        return null;
    }

    private static ItemStack findInInventory(Player player, String pattern) {
        for (ItemStack stack : player.getInventory().items) {
            if (matchesPattern(stack, pattern)) return stack;
        }
        return null;
    }

    private static ItemStack findInCurios(Player player, String pattern) {
        if (!LegendaryTabs.curiosLoaded) return null;
        try {
            var handlerOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
            if (handlerOpt.isPresent()) {
                var handler = handlerOpt.get();
                for (var entry : handler.getCurios().entrySet()) {
                    var stacksHandler = entry.getValue().getStacks();
                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
                        ItemStack stack = stacksHandler.getStackInSlot(i);
                        if (matchesPattern(stack, pattern)) return stack;
                    }
                }
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Error scanning curios for item_nbt lookup: {}", e.getMessage());
        }
        return null;
    }

    private static boolean matchesPattern(ItemStack stack, String pattern) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        if (pattern.endsWith(":*")) {
            String namespace = pattern.substring(0, pattern.length() - 2);
            return id.getNamespace().equals(namespace);
        }
        return id.toString().equals(pattern);
    }
}
