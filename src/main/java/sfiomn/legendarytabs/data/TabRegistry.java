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
import java.util.TreeMap;

public class TabRegistry {
    private static TabRegistry INSTANCE;

    // TreeMap: registration/screen-discovery order must be deterministic (sorted by tab id)
    // rather than HashMap bucket order, which used to shuffle every time a tab was added or
    // removed and could silently starve screens of tabs depending on iteration order.
    private final Map<String, DataDrivenTabBase> registeredTabs = new TreeMap<>();
    
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
            
            // Sort tab ids so the two phases below run in a fixed, reproducible order.
            for (TabData tabData : new TreeMap<>(loadedTabs).values()) {
                registeredTabs.put(tabData.getId(), new DataDrivenTabBase(tabData));
            }

            // Phase 1: every tab seeds the screen(s) it intrinsically owns first (creating the
            // screen entry, but not placing itself on it yet - see seedOwnedScreens()), so
            // screen discovery for wildcard ("*") tabs never depends on registration order - a
            // screen is known to exist as soon as any tab declares it, not only once some other
            // tab happens to have already registered it.
            for (DataDrivenTabBase tab : registeredTabs.values()) {
                tab.seedOwnedScreens();
            }

            // Phase 2: place the inventory tab on every now-known screen before any data-driven
            // tab, so its position relative to them is identical everywhere instead of only on
            // the handful of screens InventoryTab hardcodes at client setup.
            TabsMenu.fanOutInventoryTab();

            // Phase 3: fan wildcard/pattern tabs out across the now-complete set of screens, in
            // a fixed (alphabetical by id) order, so every screen ends up with the same relative
            // tab order - including a tab's own screen, since it's no longer pre-populated in
            // phase 1.
            for (DataDrivenTabBase tab : registeredTabs.values()) {
                LegendaryTabs.LOGGER.info("Registering tab: {}", tab.getTabData().getId());
                TabsMenu.register(tab);
            }

            LegendaryTabs.LOGGER.info("Registered {} data-driven tabs", registeredTabs.size());
        } else {
            LegendaryTabs.LOGGER.warn("TabDataLoader instance is null - cannot load tabs from datapack");
        }
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
