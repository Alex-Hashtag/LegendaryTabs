package sfiomn.legendarytabs.api.tabs_menu;

import com.illusivesoulworks.diet.api.type.IDietSuite;
import com.illusivesoulworks.diet.common.data.suite.DietSuites;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.platform.Services;
import com.tiviacz.travelersbackpack.capability.CapabilityUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import sfiomn.legendarytabs.LegendaryTabs;

import java.util.Collection;
import java.util.Set;

/**
 * Named values that a tab-size formula can reference as a {@code builtin} variable,
 * for data that genuinely doesn't live in item NBT (global mod config, in-memory
 * capability state, or logic derived from more than a single NBT field).
 */
public class BuiltinTabVariables {

    private BuiltinTabVariables() {}

    /**
     * @return the resolved value, or {@code null} if {@code id} is not a known builtin.
     */
    public static Double resolve(String id, Player player) {
        return switch (id) {
            case "backpacked_columns" -> (double) backpackedColumns(player);
            case "backpacked_rows" -> (double) backpackedRows(player);
            case "backpacked_visible" -> backpackedVisible(player) ? 1.0 : 0.0;
            case "travelers_tanks_visible" -> travelersTanksVisible(player) ? 1.0 : 0.0;
            case "diet_group_count" -> (double) dietGroupCount(player);
            default -> null;
        };
    }

    private static int backpackedColumns(Player player) {
        if (!LegendaryTabs.backpackedLoaded) return 9;
        ItemStack backpack = Services.BACKPACK.getBackpackStack(player);
        if (backpack.isEmpty() || !(backpack.getItem() instanceof BackpackItem backpackItem)) return 9;
        return backpackItem.getColumnCount();
    }

    private static int backpackedRows(Player player) {
        if (!LegendaryTabs.backpackedLoaded) return 4;
        ItemStack backpack = Services.BACKPACK.getBackpackStack(player);
        if (backpack.isEmpty() || !(backpack.getItem() instanceof BackpackItem backpackItem)) return 4;
        return backpackItem.getRowCount();
    }

    private static boolean backpackedVisible(Player player) {
        return LegendaryTabs.backpackedLoaded && Services.BACKPACK.isBackpackVisible(player);
    }

    private static boolean travelersTanksVisible(Player player) {
        if (!LegendaryTabs.travelersBackpackLoaded) return false;
        BackpackWrapper wrapper = CapabilityUtils.getBackpackWrapper(player);
        return wrapper != null && wrapper.tanksVisible();
    }

    private static int dietGroupCount(Player player) {
        if (!LegendaryTabs.dietLoaded) return 0;
        if (Minecraft.getInstance().level == null) return 0;

        return ((Collection<?>) com.illusivesoulworks.diet.platform.Services.CAPABILITY.get(player)
                .map((tracker) -> (Set) DietSuites.getSuite(Minecraft.getInstance().level, tracker.getSuite())
                        .map(IDietSuite::getGroups).orElse(Set.of()))
                .orElse(Set.of())).size();
    }
}
