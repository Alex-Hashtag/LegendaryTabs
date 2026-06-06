package sfiomn.legendarytabs.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.data.TabDataLoader;
import sfiomn.legendarytabs.data.TabRegistry;

@Mod.EventBusSubscriber(modid = LegendaryTabs.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ResourceReloadHandler {
    
    private static TabDataLoader tabDataLoader;
    
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        LegendaryTabs.LOGGER.info("RegisterClientReloadListenersEvent fired - registering TabDataLoader");
        tabDataLoader = new TabDataLoader();
        event.registerReloadListener(tabDataLoader);
        LegendaryTabs.LOGGER.info("Successfully registered TabDataLoader for resource reloading");
    }
    
    public static void onDatapackReload() {
        // This will be called after datapacks are loaded
        if (tabDataLoader != null) {
            TabRegistry.getInstance().reloadTabs();
            LegendaryTabs.LOGGER.info("Reloaded data-driven tabs from datapacks");
        }
    }
}
