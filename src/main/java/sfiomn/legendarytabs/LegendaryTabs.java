package sfiomn.legendarytabs;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;
import sfiomn.legendarytabs.client.tabs_menu.*;
import sfiomn.legendarytabs.config.Config;
import sfiomn.legendarytabs.data.TabDataLoader;
import sfiomn.legendarytabs.data.TabRegistry;

import java.nio.file.Path;
import java.nio.file.Paths;

@Mod(LegendaryTabs.MOD_ID)
public class LegendaryTabs
{
    public static final String MOD_ID = "legendarytabs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Path configPath = FMLPaths.CONFIGDIR.get();
    public static Path modConfigPath = Paths.get(configPath.toAbsolutePath().toString(), "legendarytabs");

    public static boolean backpackedLoaded = false;
    public static boolean travelersBackpackLoaded = false;
    public static boolean legendarySurvivalOverhaulLoaded = false;
    public static boolean curiosLoaded = false;
    public static boolean reskillableLoaded = false;
    public static boolean reskillableReimaginedLoaded = false;
    public static boolean ftbQuestsLoaded = false;
    public static boolean ftbTeamsLoaded = false;
    public static boolean quarkOdditiesLoaded = false;
    public static boolean cosmeticArmorLoaded = false;
    public static boolean mapAtlasesLoaded = false;
    public static boolean xaerosMapLoaded = false;
    public static boolean journeyMapLoaded = false;
    public static boolean dietLoaded = false;
    public static boolean passiveSkillTreeLoaded = false;
    public static boolean pufferfishsSkillsLoaded = false;

    public LegendaryTabs(FMLJavaModLoadingContext context)
    {
        IEventBus modBus = context.getModEventBus();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        modBus.addListener(this::onModConfigLoadEvent);
        modBus.addListener(this::onModConfigReloadEvent);

        Config.register(context);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        modIntegration(forgeBus);
    }

    private void modIntegration(IEventBus forgeBus)
    {
        backpackedLoaded = ModList.get().isLoaded("backpacked");
        travelersBackpackLoaded = ModList.get().isLoaded("travelersbackpack");
        curiosLoaded = ModList.get().isLoaded("curios");
        reskillableLoaded = ModList.get().isLoaded("rereskillable");
        reskillableReimaginedLoaded = ModList.get().isLoaded("reskillable");
        ftbQuestsLoaded = ModList.get().isLoaded("ftbquests");
        ftbTeamsLoaded = ModList.get().isLoaded("ftbteams");
        quarkOdditiesLoaded = ModList.get().isLoaded("quarkoddities");
        legendarySurvivalOverhaulLoaded = ModList.get().isLoaded("legendarysurvivaloverhaul");
        cosmeticArmorLoaded = ModList.get().isLoaded("cosmeticarmorreworked");
        mapAtlasesLoaded = ModList.get().isLoaded("map_atlases");
        xaerosMapLoaded = ModList.get().isLoaded("xaeroworldmap");
        journeyMapLoaded = ModList.get().isLoaded("journeymap");
        dietLoaded = ModList.get().isLoaded("diet");
        passiveSkillTreeLoaded = ModList.get().isLoaded("skilltree");
        pufferfishsSkillsLoaded = ModList.get().isLoaded("puffish_skills");

        if (backpackedLoaded)
            LOGGER.debug("Backpacked is loaded, enabling compatibility");

        if (travelersBackpackLoaded)
            LOGGER.debug("Travelers Backpack is loaded, enabling compatibility");

        if (reskillableLoaded)
            LOGGER.debug("Rereskillable is loaded, enabling compatibility");

        if (reskillableReimaginedLoaded)
            LOGGER.debug("Reskillable Reimagined is loaded, enabling compatibility");

        if (ftbQuestsLoaded)
            LOGGER.debug("FTB Quests is loaded, enabling compatibility");

        if (ftbTeamsLoaded)
            LOGGER.debug("FTB Teams is loaded, enabling compatibility");

        if (curiosLoaded)
            LOGGER.debug("Curios is loaded, enabling compatibility");

        if (quarkOdditiesLoaded)
            LOGGER.debug("Quark Oddities is loaded, enabling compatibility");

        if (legendarySurvivalOverhaulLoaded)
            LOGGER.debug("Legendary Survival Overhaul is loaded, enabling compatibility");

        if (cosmeticArmorLoaded)
            LOGGER.debug("Cosmetic Armor is loaded, enabling compatibility");

        if (mapAtlasesLoaded)
            LOGGER.debug("Map Atlases is loaded, enabling compatibility");

        if (xaerosMapLoaded)
            LOGGER.debug("Xaero's Map is loaded, enabling compatibility");

        if (journeyMapLoaded)
            LOGGER.debug("Journey Map is loaded, enabling compatibility");

        if (dietLoaded)
            LOGGER.debug("Diet is loaded, enabling compatibility");

        if (passiveSkillTreeLoaded)
            LOGGER.debug("Passive Skill Tree is loaded, enabling compatibility");

        if (pufferfishsSkillsLoaded)
            LOGGER.debug("Pufferfish's Skills is loaded, enabling compatibility");
    }

    private void onModConfigLoadEvent(ModConfigEvent.Loading event)
    {
        final ModConfig config = event.getConfig();

        if (config.getSpec() == Config.CLIENT_SPEC)
            Config.Baked.bakeClient();
    }

    private void onModConfigReloadEvent(ModConfigEvent.Reloading event)
    {
        final ModConfig config = event.getConfig();

        // Since client config is not shared, we want it to update instantly whenever it's saved
        if (config.getSpec() == Config.CLIENT_SPEC)
            Config.Baked.bakeClient();
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            Config.Baked.bakeClient();
            TabsMenu.register(new InventoryTab());
            
            // Ensure TabDataLoader is instantiated
            LOGGER.info("Initializing TabDataLoader during client setup");
            if (TabDataLoader.getInstance() == null) {
                LOGGER.info("TabDataLoader not yet instantiated, creating now");
                new TabDataLoader();
            }
            
            // Manual trigger for datapack loading since resource events might not fire on first startup
            LOGGER.info("Manually triggering datapack tab loading during client setup");
            event.enqueueWork(() -> {
                // This runs after client setup is complete
                TabRegistry.getInstance().reloadTabs();
                LOGGER.info("Completed manual datapack tab loading");
            });

            if (LegendaryTabs.backpackedLoaded)
                TabsMenu.register(new BackpackedTab());
            // if (LegendaryTabs.travelersBackpackLoaded)
                // TravelersBackpackTab is now data-driven via JSON datapacks
            // if (LegendaryTabs.legendarySurvivalOverhaulLoaded)
                // BodyDamageTab is now data-driven via JSON datapacks
            // if (LegendaryTabs.ftbQuestsLoaded)
                // FtbQuestsTab is now data-driven via JSON datapacks
            // if (LegendaryTabs.ftbTeamsLoaded)
                // FtbTeamsTab is now data-driven via JSON datapacks
            // if (LegendaryTabs.reskillableLoaded)
                // ReskillableTab is now data-driven via JSON datapacks
            // if (LegendaryTabs.reskillableReimaginedLoaded)
                // ReskillableReimaginedTab is now data-driven via JSON datapacks
            if (LegendaryTabs.mapAtlasesLoaded) 
                TabsMenu.register(new MapAtlasesTab());
            // if (LegendaryTabs.xaerosMapLoaded)
                // XaerosMapTab is now data-driven via JSON datapacks
            // if (LegendaryTabs.journeyMapLoaded)
                // JourneyMapTab is now data-driven via JSON datapacks
//            if (LegendaryTabs.dietLoaded)
//                TabsMenu.register(new DietTab());
            if (LegendaryTabs.passiveSkillTreeLoaded)
                TabsMenu.register(new PassiveSkillTreeTab());
            // if (LegendaryTabs.pufferfishsSkillsLoaded)
                // PufferfishsSkillsTab is now data-driven via JSON datapacks
        }
    }
}