package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
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
    private static final Map<Class<?>, ScreenInfo> tabsScreens = new LinkedHashMap<>();
    private static int leftScreenPos;
    private static int topScreenPos;
    private static int startTabIndex;
    private static int currentTabsCount;
    private static List<TabBase> enabledTabs;
    private static TabBase inventoryTab;

    private TabsMenu() {
    }

    /**
     * Remembers the singleton "return to inventory" tab so fanOutInventoryTab() can place it
     * on every screen once all screens are known, not just the handful InventoryTab hardcodes
     * at client setup (before any data-driven screen even exists).
     */
    public static void setInventoryTab(TabBase tab) {
        inventoryTab = tab;
    }

    /**
     * Places the inventory tab on every screen currently registered, so it isn't limited to
     * InventoryTab's own hardcoded screen list (which predates data-driven screens like a mod's
     * skill GUI). Called before data-driven tabs fan out (see TabRegistry.reloadTabs()) so the
     * inventory tab's position relative to them is identical on every screen. Safe to call
     * repeatedly - ScreenInfo.addTab() de-duplicates, so re-running a reload won't stack copies.
     */
    public static void fanOutInventoryTab() {
        if (inventoryTab == null) {
            return;
        }
        for (Class<?> screenClass : new ArrayList<>(tabsScreens.keySet())) {
            addTabToScreen(inventoryTab, screenClass, (player) -> 176, (player) -> 166, 10);
        }
    }

    /**
     * Some mods (e.g. FTB Library) open every one of their GUIs through the same generic wrapper
     * Screen subclass (e.g. ScreenWrapper), with the actual per-GUI identity only available on
     * the wrapped object returned by its no-arg getGui() method. Keying screens by
     * Screen.getClass() alone would then conflate unrelated GUIs (FTB Quests and FTB Teams both
     * report as ScreenWrapper), so wrapped screens are instead keyed by the wrapped object's
     * class. Uses reflection rather than a hard type reference since the wrapping mod is an
     * optional dependency. Screens without a getGui() method are keyed by their own class as
     * before.
     */
    public static Class<?> resolveScreenIdentity(Screen screen) {
        try {
            java.lang.reflect.Method getGui = screen.getClass().getMethod("getGui");
            Object gui = getGui.invoke(screen);
            if (gui != null) {
                return gui.getClass();
            }
        } catch (NoSuchMethodException ignored) {
            // Not a wrapper screen - identify it by its own class.
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Failed to resolve wrapped GUI for screen {}: {}", screen.getClass().getName(), e.getMessage());
        }
        return screen.getClass();
    }

    /**
     * Draws our tab/next buttons ourselves rather than relying on the target screen's own
     * render() to walk its renderables. event.addListener() adds them to the screen's widget
     * lists for input handling, but plenty of heavily-customized mod screens (e.g. Epic Fight's
     * SkillEditScreen) implement render() by hand and never iterate that list, which left our
     * buttons registered and clickable but invisible. Screens that DO draw renderables normally
     * will draw these widgets twice - harmless, since it's the same buttons at the same spot.
     */
    public static void renderTabButtons(ScreenEvent.Render.Post event) {
        if (!tabsScreens.containsKey(resolveScreenIdentity(event.getScreen()))) {
            return;
        }

        for (GuiEventListener listener : event.getScreen().children()) {
            if (listener instanceof TabButton tabButton) {
                tabButton.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
            } else if (listener instanceof NextTabsButton nextTabsButton) {
                nextTabsButton.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
            }
        }
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

    public static void addTabToScreen(TabBase newTab, Class<?> screen, Function<Player, Integer> screenWidth, Function<Player, Integer> screenHeight, int priority) {
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

    /**
     * Registers that a screen exists (for wildcard tab discovery) without placing any tab on it
     * yet. Used so a tab's own screen becomes visible to every other tab's fan-out pass at the
     * same time as any other screen, rather than being seeded (and thus ordered) earlier than
     * everything else - see DataDrivenTabBase.seedOwnedScreens(). A tab whose JSON sets
     * show_tabs_on_screen to false skips calling this entirely for that screen, so it's never
     * discovered by anyone and never gets a tab bar at all.
     * <p>
     * buttonSkin/iconOffsetX/iconOffsetY (from the owning tab's screen_sizes entry) apply to
     * every tab's button while this screen is open, not just the owning tab's - only applied
     * when this call actually creates the ScreenInfo (first writer wins, same as width/height).
     */
    public static void ensureScreenInfo(Class<?> screen, Function<Player, Integer> screenWidth, Function<Player, Integer> screenHeight,
                                         ResourceLocation buttonSkin, int iconOffsetX, int iconOffsetY) {
        tabsScreens.computeIfAbsent(screen, k -> {
            ScreenInfo screenInfo = new ScreenInfo(screenWidth, screenHeight);
            screenInfo.buttonSkin = buttonSkin;
            screenInfo.iconOffsetX = iconOffsetX;
            screenInfo.iconOffsetY = iconOffsetY;
            return screenInfo;
        });
    }

    public static java.util.Set<Class<?>> getRegisteredScreens() {
        return tabsScreens.keySet();
    }

    public static ScreenInfo getScreenInfo(Class<?> screenClass) {
        return tabsScreens.get(screenClass);
    }

    public static void initScreenButtons(ScreenEvent.Init.Post event) {
        LegendaryTabs.LOGGER.info("initScreenButtons called for screen: {}", event.getScreen().getClass().getSimpleName());
        LegendaryTabs.LOGGER.info("Registered screens: {}", tabsScreens.keySet().stream().map(Class::getSimpleName).toList());
        
        Class<?> screenIdentity = resolveScreenIdentity(event.getScreen());

        if (tabsScreens.containsKey(screenIdentity)) {
            LegendaryTabs.LOGGER.info("Found screen info for: {}", screenIdentity.getSimpleName());

            if (Minecraft.getInstance().player == null) {
                LegendaryTabs.LOGGER.warn("Player is null, skipping tab initialization");
                return;
            }

            ScreenInfo screenInfo = tabsScreens.get(screenIdentity);
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
                event.addListener(new NextTabsButton(currentTabsCount, TabsMenu.leftScreenPos, TabsMenu.topScreenPos, event.getScreen(),
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
        public ResourceLocation buttonSkin;
        public int iconOffsetX;
        public int iconOffsetY;

        public ScreenInfo(Function<Player, Integer> width, Function<Player, Integer> height) {
            this.width = width;
            this.height = height;
            this.tabs = new TreeMap<>();
        }

        public ScreenInfo(Function<Player, Integer> width, Function<Player, Integer> height, TabBase newTab, int priority) {
            this(width, height);
            this.addTab(priority, newTab);
        }

        public void addTab(int priority, TabBase newTab) {
            // A tab (e.g. the persistent inventory tab, or a reloaded data-driven tab) can be
            // fanned out to the same screen more than once across repeated reloads - skip if
            // it's already present anywhere on this screen instead of stacking duplicates.
            for (List<TabBase> existing : this.tabs.values()) {
                if (existing.contains(newTab)) {
                    return;
                }
            }
            this.tabs.computeIfAbsent(priority, k -> new ArrayList<>()).add(newTab);
        }
    }
}
