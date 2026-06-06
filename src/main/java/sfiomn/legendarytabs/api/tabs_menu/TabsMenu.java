package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ScreenEvent;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.client.screens.NextTabsButton;
import sfiomn.legendarytabs.client.screens.TabButton;
import sfiomn.legendarytabs.config.Config;

import java.util.*;
import java.util.function.Function;

import static sfiomn.legendarytabs.api.tabs_menu.TabBase.TAB_HEIGHT;
import static sfiomn.legendarytabs.api.tabs_menu.TabBase.TAB_WIDTH;

public class TabsMenu {
    private static final Map<Class<? extends Screen>, ScreenInfo> tabsScreens = new HashMap<>();
    private static int leftScreenPos;
    private static int topScreenPos;
    private static int startTabIndex;
    private static int currentTabsCount;
    private static List<TabBase> enabledTabs;

    private TabsMenu() {
    }

    public static void updateButtonsPosition(Screen screen, int leftScreenPos, int topScreenPos) {
        if (TabsMenu.leftScreenPos != leftScreenPos || TabsMenu.topScreenPos != topScreenPos) {
            TabsMenu.leftScreenPos = leftScreenPos;
            TabsMenu.topScreenPos = topScreenPos;
            for (GuiEventListener button: screen.children()) {
                if (button instanceof TabButton tabButton) {
                    tabButton.updatePosition(TabsMenu.leftScreenPos, TabsMenu.topScreenPos);
                }
                if (button instanceof NextTabsButton tabButton) {
                    tabButton.updatePosition(TabsMenu.leftScreenPos, TabsMenu.topScreenPos);
                }
            }
        }
    }

    public static void addTabToScreen(TabBase newTab, Class<? extends Screen> screen, Function<Player, Integer> screenWidth, Function<Player, Integer> screenHeight, int priority) {
        LegendaryTabs.LOGGER.info("addTabToScreen called for tab {} on screen {} with priority {}", 
                newTab.getClass().getSimpleName(), screen.getSimpleName(), priority);
        
        if (tabsScreens.containsKey(screen)) {
            tabsScreens.get(screen).addTab(priority, newTab);
            LegendaryTabs.LOGGER.info("Added tab {} to existing screen info for {}", 
                    newTab.getClass().getSimpleName(), screen.getSimpleName());
        } else {
            ScreenInfo screenInfo = new ScreenInfo(screenWidth, screenHeight, newTab, priority);
            tabsScreens.put(screen, screenInfo);
            LegendaryTabs.LOGGER.info("Created new screen info for {} and added tab {}", 
                    screen.getSimpleName(), newTab.getClass().getSimpleName());
        }
        
        LegendaryTabs.LOGGER.info("Total screens with tabs: {}", tabsScreens.size());
    }
    
    public static java.util.Set<Class<? extends Screen>> getRegisteredScreens() {
        return tabsScreens.keySet();
    }
    
    public static ScreenInfo getScreenInfo(Class<? extends Screen> screenClass) {
        return tabsScreens.get(screenClass);
    }

    public static void initScreenButtons(ScreenEvent.Init.Post event) {
        LegendaryTabs.LOGGER.info("initScreenButtons called for screen: {}", event.getScreen().getClass().getSimpleName());
        LegendaryTabs.LOGGER.info("Registered screens: {}", tabsScreens.keySet().stream().map(Class::getSimpleName).toList());
        
        if (tabsScreens.containsKey(event.getScreen().getClass())) {
            LegendaryTabs.LOGGER.info("Found screen info for: {}", event.getScreen().getClass().getSimpleName());
            
            if (Minecraft.getInstance().player == null) {
                LegendaryTabs.LOGGER.warn("Player is null, skipping tab initialization");
                return;
            }

            ScreenInfo screenInfo = tabsScreens.get(event.getScreen().getClass());
            TabsMenu.leftScreenPos = (event.getScreen().width - screenInfo.width.apply(Minecraft.getInstance().player)) / 2;
            TabsMenu.topScreenPos = (event.getScreen().height - screenInfo.height.apply(Minecraft.getInstance().player)) / 2;

            if (TabsMenu.topScreenPos - TAB_HEIGHT < 0) {
                LegendaryTabs.LOGGER.warn("Not enough space for tabs (topScreenPos: {})", TabsMenu.topScreenPos);
                return;
            }

            startTabIndex = 0;
            currentTabsCount = 0;
            enabledTabs = new ArrayList<>();
            
            LegendaryTabs.LOGGER.info("Processing {} priority groups for screen {}", screenInfo.tabs.size(), event.getScreen().getClass().getSimpleName());
            
            for (List<TabBase> tabBases: screenInfo.tabs.values()) {
                LegendaryTabs.LOGGER.info("Processing {} tabs in priority group", tabBases.size());
                for (TabBase tabBase : tabBases) {
                    boolean enabled = tabBase.isEnabled(Minecraft.getInstance().player);
                    LegendaryTabs.LOGGER.info("Tab {} enabled: {}", tabBase.getClass().getSimpleName(), enabled);
                    if (enabled) {
                        enabledTabs.add(tabBase);
                    }
                }
            }
            
            LegendaryTabs.LOGGER.info("Total enabled tabs for screen {}: {}", event.getScreen().getClass().getSimpleName(), enabledTabs.size());

            int remainingWidth = screenInfo.width.apply(Minecraft.getInstance().player) - Config.Baked.tabsMenuOffsetX;
            for (TabBase tabBase: enabledTabs) {
                if (remainingWidth > TAB_WIDTH) {
                    event.addListener(new TabButton(tabBase, Minecraft.getInstance().player, event.getScreen(), currentTabsCount, TabsMenu.leftScreenPos, TabsMenu.topScreenPos));

                    remainingWidth -= TAB_WIDTH + 1;
                    currentTabsCount++;
                }
            }

            if (enabledTabs.size() > currentTabsCount)
                event.addListener(new NextTabsButton(currentTabsCount, TabsMenu.leftScreenPos, TabsMenu.topScreenPos,
                        button -> nextTabButtons(event.getScreen())));
        }
    }

    public static void nextTabButtons(Screen screen) {
        List<? extends GuiEventListener> tabButtons = screen.children().stream().filter(button -> button instanceof TabButton).toList();

        if (startTabIndex + currentTabsCount >= enabledTabs.size())
            startTabIndex = 0;
        else
            startTabIndex += currentTabsCount + Math.min(enabledTabs.size() - currentTabsCount * 2 - startTabIndex, 0);

        int currentTabIndex = 0;
        for (TabBase tabBase: enabledTabs) {
            int tabIndexToUpdate = currentTabIndex - startTabIndex;
            if (tabIndexToUpdate >= currentTabsCount)
                break;

            if (tabIndexToUpdate >= 0)
                ((TabButton) tabButtons.get(tabIndexToUpdate)).setTabBase(tabBase);

            currentTabIndex++;
        }
    }

    public static void register(TabBase tabBase) {
        LegendaryTabs.LOGGER.info("TabsMenu.register() called for tab: " + tabBase.getClass().getName());
        try {
            tabBase.initTabOnScreens();
            LegendaryTabs.LOGGER.info("Successfully initialized tab on screens: " + tabBase.getClass().getName());
        } catch (Exception e) {
            LegendaryTabs.LOGGER.error("Failed to initialize tab on screens: " + tabBase.getClass().getName(), e);
        }
    }
    
    public static void clearDataDrivenTabs() {
        LegendaryTabs.LOGGER.info("Clearing all data-driven tabs from TabsMenu");
        // Clear all tabs from all screens
        // We need to be careful not to remove hardcoded tabs, only data-driven ones
        for (ScreenInfo screenInfo : tabsScreens.values()) {
            for (List<TabBase> tabList : screenInfo.tabs.values()) {
                tabList.removeIf(tab -> tab instanceof DataDrivenTabBase);
            }
        }
        LegendaryTabs.LOGGER.info("Cleared data-driven tabs from TabsMenu");
    }

    public static class ScreenInfo {
        public Function<Player, Integer> width;
        public Function<Player, Integer> height;
        public Map<Integer, List<TabBase>> tabs;
        public ScreenInfo(Function<Player, Integer> width, Function<Player, Integer> height, TabBase newTab, int priority) {
            this.width = width;
            this.height = height;
            this.tabs = new TreeMap<>();
            this.addTab(priority, newTab);
        }

        public void addTab(int priority, TabBase newTab) {
            if (this.tabs.containsKey(priority))
                this.tabs.get(priority).add(newTab);
            else {
                ArrayList<TabBase> newTabsForPriority = new ArrayList<>();
                newTabsForPriority.add(newTab);
                this.tabs.put(priority, newTabsForPriority);
            }
        }
    }
}
