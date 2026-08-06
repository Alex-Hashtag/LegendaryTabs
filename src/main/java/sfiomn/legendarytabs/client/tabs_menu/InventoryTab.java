package sfiomn.legendarytabs.client.tabs_menu;

import lain.mods.cos.impl.client.gui.GuiCosArmorInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.violetmoon.quark.addons.oddities.client.screen.BackpackInventoryScreen;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;
import sfiomn.legendarytabs.config.Config;
import top.theillusivec4.curios.client.gui.CuriosScreen;


public class InventoryTab extends TabBase {
    private final ResourceLocation TAB_ICON = ResourceLocation.fromNamespaceAndPath(LegendaryTabs.MOD_ID, "textures/gui/inventory.png");

    public InventoryTab() {
        super();
    }

    @Override
    public String getId() {
        return "inventory";
    }

    @Override
    public void openTargetScreen(Player player) {
        InventoryScreen newGui = new InventoryScreen(player);
        Minecraft.getInstance().setScreen(newGui);
    }

    @Override
    public boolean isEnabled(Player player) {
        return Config.Baked.inventoryTabEnabled;
    }

    @Override
    public ResourceLocation getIconTexture() {
        return TAB_ICON;
    }


    @Override
    public boolean isCurrentlyUsed(Screen currentScreen) {
        return currentScreen instanceof InventoryScreen ||
                (LegendaryTabs.curiosLoaded && currentScreen instanceof CuriosScreen) ||
                (LegendaryTabs.cosmeticArmorLoaded && currentScreen instanceof GuiCosArmorInventory) ;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("tooltip." + LegendaryTabs.MOD_ID + ".tab.inventory.description");
    }

    /**
     * Only InventoryScreen and the handful of mod screens with no data-driven tab of their own
     * (no JSON declares them, so nothing else would ever seed them) are hardcoded here. Every
     * other mod screen - anything with a legendarytabs:tabs/*.json entry - seeds itself via
     * DataDrivenTabBase.seedOwnedScreens(), and TabsMenu.fanOutInventoryTab() (see
     * TabRegistry.reloadTabs()) places this tab on every one of those screens automatically, so
     * duplicating that here would just be dead weight.
     */
    @Override
    public void initTabOnScreens() {
        if (Config.Baked.includeOpenedScreenTab)
            TabsMenu.addTabToScreen(this, InventoryScreen.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.curiosLoaded)
            TabsMenu.addTabToScreen(this, CuriosScreen.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.quarkOdditiesLoaded)
            TabsMenu.addTabToScreen(this, BackpackInventoryScreen.class, (player) -> 176, (player) -> 224, 10);

        if (LegendaryTabs.cosmeticArmorLoaded)
            TabsMenu.addTabToScreen(this, GuiCosArmorInventory.class, (player) -> 176, (player) -> 166, 10);
    }
}
