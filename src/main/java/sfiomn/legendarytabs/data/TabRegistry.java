package sfiomn.legendarytabs.data;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.api.tabs_menu.DataDrivenTabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabData;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TabRegistry {
    private static TabRegistry INSTANCE;
    
    private final Map<String, DataDrivenTabBase> registeredTabs = new HashMap<>();
    
    public static TabRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TabRegistry();
        }
        return INSTANCE;
    }
    
    public void reloadTabs() {
        LegendaryTabs.LOGGER.info("TabRegistry.reloadTabs() called");
        
        // Clear existing data-driven tabs from TabsMenu
        TabsMenu.clearDataDrivenTabs();
        
        // Unregister existing tabs from registry
        registeredTabs.clear();
        
        // Load new tabs from datapack
        TabDataLoader loader = TabDataLoader.getInstance();
        LegendaryTabs.LOGGER.info("TabDataLoader instance: {}", loader);
        
        if (loader != null) {
            Map<String, TabData> loadedTabs = loader.getLoadedTabs();
            LegendaryTabs.LOGGER.info("Loaded tabs from TabDataLoader: {} tabs", loadedTabs.size());
            
            // If no tabs loaded via datapack system, try manual fallback loading
            if (loadedTabs.isEmpty()) {
                LegendaryTabs.LOGGER.info("No tabs loaded from datapack system, trying manual fallback");
                loader.manuallyLoadBuiltInTabs();
                loadedTabs = loader.getLoadedTabs();
                LegendaryTabs.LOGGER.info("After manual loading: {} tabs", loadedTabs.size());
            }
            
            for (TabData tabData : loadedTabs.values()) {
                LegendaryTabs.LOGGER.info("Registering tab: {}", tabData.getId());
                registerTab(tabData);
            }
            
            LegendaryTabs.LOGGER.info("Registered {} data-driven tabs", registeredTabs.size());
        } else {
            LegendaryTabs.LOGGER.warn("TabDataLoader instance is null - cannot load tabs from datapack");
        }
    }
    
    private void registerTab(TabData tabData) {
        DataDrivenTabBase tab = new DataDrivenTabBase(tabData);
        registeredTabs.put(tabData.getId(), tab);
        
        // Register with TabsMenu
        TabsMenu.register(tab);
        
        LegendaryTabs.LOGGER.debug("Registered tab: {}", tabData.getId());
    }
    
    public List<TabBase> getEnabledTabs(Player player) {
        List<TabBase> enabledTabs = new ArrayList<>();
        
        for (DataDrivenTabBase tab : registeredTabs.values()) {
            if (tab.isEnabled(player)) {
                enabledTabs.add(tab);
            }
        }
        
        return enabledTabs;
    }
    
    public DataDrivenTabBase getTab(String id) {
        return registeredTabs.get(id);
    }
    
    public Map<String, DataDrivenTabBase> getAllTabs() {
        return new HashMap<>(registeredTabs);
    }
    
    public void clear() {
        registeredTabs.clear();
    }
}
