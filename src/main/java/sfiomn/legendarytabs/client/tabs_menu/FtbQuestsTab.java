package sfiomn.legendarytabs.client.tabs_menu;

import com.illusivesoulworks.diet.client.screen.DietScreen;
import com.mrcrayfish.backpacked.client.gui.screen.inventory.BackpackScreen;
import com.tiviacz.travelersbackpack.client.screens.AbstractBackpackScreen;
import dev.ftb.mods.ftbquests.client.FTBQuestsClient;
import lain.mods.cos.impl.client.gui.GuiCosArmorInventory;
import majik.rereskillable.client.screen.SkillScreen;
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
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;
import sfiomn.legendarytabs.config.Config;
import sfiomn.legendarytabs.utils.IntegrationUtils;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;

public class FtbQuestsTab extends TabBase {
    private final ResourceLocation TAB_ICON = new ResourceLocation(LegendaryTabs.MOD_ID, "textures/gui/ftbquests.png");

    public FtbQuestsTab() {
        super();
    }

    @Override
    public void openTargetScreen(Player player) {
        if (LegendaryTabs.ftbQuestsLoaded && player.level().isClientSide)
            FTBQuestsClient.openGui();
    }

    @Override
    public boolean isEnabled(Player player) {
        return Config.Baked.ftbQuestsTabEnabled && LegendaryTabs.ftbQuestsLoaded;
    }

    @Override
    public ResourceLocation getIconTexture() {
        return TAB_ICON;
    }


    @Override
    public boolean isCurrentlyUsed(Screen currentScreen) {
        return false;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("tooltip." + LegendaryTabs.MOD_ID + ".tab.ftb_quests.description");
    }

    @Override
    public void initTabOnScreens() {
        TabsMenu.addTabToScreen(this, InventoryScreen.class, (player) -> 176, (player) -> 166, 70);

        if (LegendaryTabs.curiosLoaded)
            TabsMenu.addTabToScreen(this, CuriosScreenV2.class, (player) -> 176, (player) -> 166, 70);

        if (LegendaryTabs.legendarySurvivalOverhaulLoaded)
            TabsMenu.addTabToScreen(this, BodyHealthScreen.class, (player) -> 176, (player) -> 183, 70);

        if (LegendaryTabs.reskillableLoaded)
            TabsMenu.addTabToScreen(this, SkillScreen.class, (player) -> 176, (player) -> 166, 70);

        if (LegendaryTabs.reskillableReimaginedLoaded)
            TabsMenu.addTabToScreen(this, net.bandit.reskillable.client.screen.SkillScreen.class, (player) -> 176, (player) -> 166, 70);

        if (LegendaryTabs.quarkOdditiesLoaded)
            TabsMenu.addTabToScreen(this, BackpackInventoryScreen.class, (player) -> 176, (player) -> 224, 70);

        if (LegendaryTabs.cosmeticArmorLoaded)
            TabsMenu.addTabToScreen(this, GuiCosArmorInventory.class, (player) -> 176, (player) -> 166, 70);

        if (LegendaryTabs.backpackedLoaded)
            TabsMenu.addTabToScreen(this, BackpackScreen.class, (IntegrationUtils::getBackpackWidth), (IntegrationUtils::getBackpackHeight), 70);

        if (LegendaryTabs.travelersBackpackLoaded)
            TabsMenu.addTabToScreen(this, com.tiviacz.travelersbackpack.client.screens.BackpackScreen.class, IntegrationUtils::getTravelersBackpackWidth, IntegrationUtils::getTravelersBackpackHeight, 70);

        if (LegendaryTabs.dietLoaded)
            TabsMenu.addTabToScreen(this, DietScreen.class, (player) -> 248, IntegrationUtils::getDietHeight, 70);
    }
}
