package sfiomn.legendarytabs.client.tabs_menu;

import com.illusivesoulworks.diet.client.screen.DietScreen;
import com.mrcrayfish.backpacked.client.gui.screen.inventory.BackpackScreen;
import com.tiviacz.travelersbackpack.client.screens.AbstractBackpackScreen;
import lain.mods.cos.impl.client.gui.GuiCosArmorInventory;
import majik.rereskillable.client.screen.SkillScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.violetmoon.quark.addons.oddities.client.screen.BackpackInventoryScreen;
import sfiomn.legendarysurvivaloverhaul.client.screens.BodyHealthScreen;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabData;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;
import sfiomn.legendarytabs.config.Config;
import sfiomn.legendarytabs.data.TabDataLoader;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;


public class InventoryTab extends TabBase {
    private final ResourceLocation TAB_ICON = new ResourceLocation(LegendaryTabs.MOD_ID, "textures/gui/inventory.png");

    public InventoryTab() {
        super();
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
                (LegendaryTabs.curiosLoaded && currentScreen instanceof CuriosScreenV2) ||
                (LegendaryTabs.cosmeticArmorLoaded && currentScreen instanceof GuiCosArmorInventory) ;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("tooltip." + LegendaryTabs.MOD_ID + ".tab.inventory.description");
    }

    @Override
    public void initTabOnScreens() {
        if (Config.Baked.includeOpenedScreenTab)
            TabsMenu.addTabToScreen(this, InventoryScreen.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.legendarySurvivalOverhaulLoaded)
            TabsMenu.addTabToScreen(this, BodyHealthScreen.class, (player) -> 176, (player) -> 183, 10);

        if (LegendaryTabs.reskillableLoaded)
            TabsMenu.addTabToScreen(this, SkillScreen.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.reskillableReimaginedLoaded)
            TabsMenu.addTabToScreen(this, net.bandit.reskillable.client.screen.SkillScreen.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.curiosLoaded)
            TabsMenu.addTabToScreen(this, CuriosScreenV2.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.quarkOdditiesLoaded)
            TabsMenu.addTabToScreen(this, BackpackInventoryScreen.class, (player) -> 176, (player) -> 224, 10);

        if (LegendaryTabs.cosmeticArmorLoaded)
            TabsMenu.addTabToScreen(this, GuiCosArmorInventory.class, (player) -> 176, (player) -> 166, 10);

        if (LegendaryTabs.backpackedLoaded)
            addDataDrivenSizedTab(BackpackScreen.class, "backpacked", 10);

        if (LegendaryTabs.travelersBackpackLoaded)
            addDataDrivenSizedTab(com.tiviacz.travelersbackpack.client.screens.BackpackScreen.class, "travelers_backpack", 10);

        if (LegendaryTabs.dietLoaded)
            addDataDrivenSizedTab(DietScreen.class, "diet", 10);
    }

    /**
     * Sizes this tab's button against a screen using the same JSON-declared width/height
     * formula as that screen's own data-driven tab (see legendarytabs:tabs/{tabId}.json),
     * so both buttons agree on the screen's real, per-player size.
     */
    private void addDataDrivenSizedTab(Class<? extends Screen> screenClass, String tabId, int priority) {
        // Resolved lazily (per call) rather than once at registration time, since the
        // JSON tab data is loaded/synced after this tab is registered at client setup.
        TabsMenu.addTabToScreen(this, screenClass,
                (player) -> screenSizeConfig(tabId, screenClass).getWidth(player),
                (player) -> screenSizeConfig(tabId, screenClass).getHeight(player),
                priority);
    }

    private static final TabData.ScreenSizeConfig DEFAULT_SCREEN_SIZE = new TabData.ScreenSizeConfig(176, 166, 10);

    private TabData.ScreenSizeConfig screenSizeConfig(String tabId, Class<? extends Screen> screenClass) {
        TabData tabData = TabDataLoader.getInstance() != null ? TabDataLoader.getInstance().getTabData(tabId) : null;
        if (tabData == null) return DEFAULT_SCREEN_SIZE;
        return tabData.getScreenSizes().getOrDefault(screenClass.getName(), DEFAULT_SCREEN_SIZE);
    }
}
