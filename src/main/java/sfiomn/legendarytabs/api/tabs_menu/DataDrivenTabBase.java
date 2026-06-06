package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import sfiomn.legendarytabs.LegendaryTabs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class DataDrivenTabBase extends TabBase {
    protected final TabData tabData;
    private final List<Pattern> screenPatterns;
    private static final Map<String, net.minecraft.client.KeyMapping> keyMappingCache = new HashMap<>();

    public DataDrivenTabBase(TabData tabData) {
        this.tabData = tabData;
        this.screenPatterns = tabData.getScreenPatterns().stream()
                .map(this::compileScreenPattern)
                .toList();
    }

    private Pattern compileScreenPattern(String pattern) {
        // Convert glob-like patterns to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return Pattern.compile(regex);
    }

    @Override
    public void openTargetScreen(Player player) {
        LegendaryTabs.LOGGER.info("openTargetScreen() called for tab: {} by player: {}", tabData.getId(), player.getName().getString());
        TabData.ScreenOpenAction action = tabData.getScreenOpenAction();
        
        switch (action.getType()) {
            case KEY_PRESS -> {
                action.getKeyBinding().ifPresent(keyBinding -> {
                    try {
                        simulateKeyPress(keyBinding, action.isCloseScreenFirst());
                    } catch (Exception e) {
                        LegendaryTabs.LOGGER.warn("Failed to simulate key press for binding: " + keyBinding, e);
                    }
                });
            }
            case RIGHT_CLICK_ITEM -> {
                action.getItemToUse().ifPresent(itemId -> {
                    try {
                        simulateRightClickItem(itemId, player);
                    } catch (Exception e) {
                        LegendaryTabs.LOGGER.warn("Failed to simulate right click for item: " + itemId, e);
                    }
                });
            }
            case CUSTOM -> {
                action.getCustomAction().ifPresent(this::executeCustomAction);
            }
            case OPEN_SCREEN -> {
                action.getScreenClassName().ifPresent(this::openScreenByClassName);
            }
            case API_CALL -> {
                action.getApiCallName().ifPresent(this::executeApiCall);
            }
        }
    }

    private void executeApiCall(String callName) {
        try {
            switch (callName) {
                case "journeymap:open_fullscreen_map" -> {
                    Minecraft.getInstance().setScreen(null);
                    journeymap.client.ui.UIManager.INSTANCE.openFullscreenMap();
                }
                case "puffish_skills:open_screen" -> {
                    net.puffish.skillsmod.client.SkillsClientMod.getInstance().openScreen(Optional.empty());
                }
                case "travelersbackpack:open_backpack" -> {
                    com.tiviacz.travelersbackpack.network.ServerboundActionTagPacket.create(1, new Object[0]);
                }
                default -> LegendaryTabs.LOGGER.warn("Unknown api_call: {}", callName);
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to execute api_call: {}", callName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void openScreenByClassName(String screenClassName) {
        try {
            Class<? extends Screen> screenClass = (Class<? extends Screen>) Class.forName(screenClassName);
            Screen screen = screenClass.getDeclaredConstructor().newInstance();
            Minecraft.getInstance().setScreen(screen);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to open screen by class name: {}", screenClassName, e);
        }
    }

    private void simulateKeyPress(String keyBinding, boolean closeScreenFirst) {
        try {
            LegendaryTabs.LOGGER.info("Attempting to simulate key press for: {}", keyBinding);
            
            if (closeScreenFirst) {
                Minecraft.getInstance().setScreen(null);
                // Double-defer: first execute() runs end-of-current-tick (screen closes),
                // second execute() runs next tick when screen is fully null for mod handlers
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().execute(() -> {
                    try {
                        LegendaryTabs.LOGGER.info("Executing delayed key press for: {}", keyBinding);
                        net.minecraft.client.KeyMapping keyMapping = findKeyMapping(keyBinding);
                        if (keyMapping != null) {
                            simulateActualKeyPress(keyMapping);
                        } else {
                            LegendaryTabs.LOGGER.warn("KeyMapping not found: {}", keyBinding);
                        }
                    } catch (Exception e) {
                        LegendaryTabs.LOGGER.warn("Failed to execute delayed key press for: " + keyBinding, e);
                    }
                }));
                return;
            }
            
            // Schedule key press for next tick to allow screen to fully close
            Minecraft.getInstance().execute(() -> {
                try {
                    LegendaryTabs.LOGGER.info("Executing delayed key press for: {}", keyBinding);
                    
                    // Find the keybinding in Minecraft's registry
                    net.minecraft.client.KeyMapping keyMapping = findKeyMapping(keyBinding);
                    if (keyMapping != null) {
                        LegendaryTabs.LOGGER.info("Found key mapping, simulating press");
                        // Instead of consumeClick(), we need to simulate actual key press
                        simulateActualKeyPress(keyMapping);
                    } else {
                        LegendaryTabs.LOGGER.warn("KeyMapping not found: {}", keyBinding);
                        // Try alternative key mapping search approaches
                        if (tryAlternativeKeyMappingSearch(keyBinding)) {
                            LegendaryTabs.LOGGER.info("Successfully found and activated key mapping via alternative search");
                        } else {
                            LegendaryTabs.LOGGER.error("Key mapping not found in any registry: {}", keyBinding);
                        }
                    }
                } catch (Exception e) {
                    LegendaryTabs.LOGGER.warn("Failed to execute delayed key press for: " + keyBinding, e);
                }
            });
            
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to simulate key press for: " + keyBinding, e);
        }
    }

    private net.minecraft.client.KeyMapping findKeyMapping(String keyBindingName) {
        if (keyMappingCache.containsKey(keyBindingName)) {
            return keyMappingCache.get(keyBindingName);
        }
        try {
            LegendaryTabs.LOGGER.debug("Looking for key mapping: {}", keyBindingName);
            var allField = net.minecraft.client.KeyMapping.class.getDeclaredField("ALL");
            allField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, net.minecraft.client.KeyMapping> allMappings =
                (java.util.Map<String, net.minecraft.client.KeyMapping>) allField.get(null);
            net.minecraft.client.KeyMapping mapping = allMappings.get(keyBindingName);
            if (mapping != null) {
                LegendaryTabs.LOGGER.info("Found and cached key mapping: {}", keyBindingName);
                keyMappingCache.put(keyBindingName, mapping);
                return mapping;
            }
            LegendaryTabs.LOGGER.warn("Key mapping not found: {}", keyBindingName);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Error finding key mapping: " + keyBindingName, e);
        }
        return null;
    }

    private void simulateActualKeyPress(net.minecraft.client.KeyMapping keyMapping) {
        try {
            LegendaryTabs.LOGGER.info("Simulating actual key press for: {}", keyMapping.getName());
            
            // Check if the key binding has an actual key assigned
            boolean hasKey = keyMapping.getKey() != null && 
                           !keyMapping.getKey().equals(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            
            if (hasKey) {
                // Use the static click method to register clicks - DON'T consume them
                // Let the mod's normal tick handlers consume them naturally
                net.minecraft.client.KeyMapping.click(keyMapping.getKey());
                LegendaryTabs.LOGGER.info("Registered click for bound key: {}", keyMapping.getKey().getName());
            } else {
                // For unbound keys, we need to directly increment the click counter
                // Access the clickCount field via reflection
                try {
                    var clickCountField = net.minecraft.client.KeyMapping.class.getDeclaredField("clickCount");
                    clickCountField.setAccessible(true);
                    int currentClicks = clickCountField.getInt(keyMapping);
                    clickCountField.setInt(keyMapping, currentClicks + 1);
                    LegendaryTabs.LOGGER.info("Directly incremented click count for unbound key: {} (now {})", 
                                            keyMapping.getName(), currentClicks + 1);
                } catch (Exception e) {
                    LegendaryTabs.LOGGER.warn("Failed to increment click count via reflection: {}", e.getMessage());
                }
            }
            
            // Set as momentarily pressed for mods that check isDown()
            // This works regardless of whether a physical key is bound
            keyMapping.setDown(true);
            
            // Schedule the key release for next tick to simulate a real key press/release cycle
            Minecraft.getInstance().execute(() -> {
                keyMapping.setDown(false);
                LegendaryTabs.LOGGER.info("Key release scheduled for: {}", keyMapping.getName());
            });
            
            LegendaryTabs.LOGGER.info("Key press simulation completed for: {} - clicks available for mod handlers", keyMapping.getName());
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to simulate actual key press: {}", e.getMessage());
        }
    }

    private boolean tryAlternativeKeyMappingSearch(String keyBinding) {
        try {
            LegendaryTabs.LOGGER.info("Trying alternative key mapping search for: {}", keyBinding);
            
            // Try searching in KeyMapping.ALL static field via reflection
            if (tryKeyMappingAllField(keyBinding)) return true;
            
            // Try searching through client options more thoroughly
            if (tryExtensiveOptionsSearch(keyBinding)) return true;
            
            // Try searching loaded mods' key mappings
            if (tryModKeyMappingSearch(keyBinding)) return true;
            
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Alternative key mapping search failed: {}", e.getMessage());
        }
        return false;
    }
    
    private boolean tryKeyMappingAllField(String keyBinding) {
        try {
            // Access KeyMapping.ALL via reflection
            var keyMappingClass = net.minecraft.client.KeyMapping.class;
            var allField = keyMappingClass.getDeclaredField("ALL");
            allField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            java.util.Map<String, net.minecraft.client.KeyMapping> allMappings = 
                (java.util.Map<String, net.minecraft.client.KeyMapping>) allField.get(null);
            
            LegendaryTabs.LOGGER.info("Found {} key mappings in KeyMapping.ALL", allMappings.size());
            LegendaryTabs.LOGGER.debug("Available key mappings: {}", allMappings.keySet());
            
            var mapping = allMappings.get(keyBinding);
            if (mapping != null) {
                LegendaryTabs.LOGGER.info("Found key mapping in KeyMapping.ALL: {}", keyBinding);
                simulateActualKeyPress(mapping);
                return true;
            }
            
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("KeyMapping.ALL search failed: {}", e.getMessage());
        }
        return false;
    }
    
    private boolean tryExtensiveOptionsSearch(String keyBinding) {
        try {
            // Search through all fields in Options, not just KeyMapping fields
            var options = Minecraft.getInstance().options;
            var allFields = options.getClass().getDeclaredFields();
            
            for (var field : allFields) {
                field.setAccessible(true);
                var value = field.get(options);
                
                if (value instanceof net.minecraft.client.KeyMapping mapping) {
                    if (keyBinding.equals(mapping.getName())) {
                        LegendaryTabs.LOGGER.info("Found key mapping in options field {}: {}", field.getName(), keyBinding);
                        simulateActualKeyPress(mapping);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Extensive options search failed: {}", e.getMessage());
        }
        return false;
    }
    
    private boolean tryModKeyMappingSearch(String keyBinding) {
        // Generic search through loaded mod registries could be implemented here
        // For now, return false to avoid hardcoded mod logic
        return false;
    }

    private void simulateRightClickItem(ResourceLocation itemId, Player player) {
        var item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item != null) {
            ItemStack itemStack = new ItemStack(item);
            // Simulate right-click usage
            itemStack.use(player.level(), player, player.getUsedItemHand());
        }
    }

    private void executeCustomAction(String customAction) {
        // Placeholder for custom actions
        LegendaryTabs.LOGGER.info("Executing custom action: " + customAction);
    }

    @Override
    public boolean isEnabled(Player player) {
        LegendaryTabs.LOGGER.info("Checking isEnabled for tab: {}", tabData.getId());
        
        if (!tabData.isEnabled()) {
            LegendaryTabs.LOGGER.info("Tab {} disabled in data", tabData.getId());
            return false;
        }
        
        // Check if all required mods are loaded
        for (String modId : tabData.getRequiredMods()) {
            boolean modLoaded = ModList.get().isLoaded(modId);
            LegendaryTabs.LOGGER.info("Required mod {} for tab {}: {}", modId, tabData.getId(), modLoaded ? "LOADED" : "NOT LOADED");
            if (!modLoaded) {
                return false;
            }
        }
        
        // Check enabled_conditions — all conditions must pass
        for (TabData.EnabledCondition condition : tabData.getEnabledConditions()) {
            if (!checkEnabledCondition(condition, player)) {
                return false;
            }
        }

        LegendaryTabs.LOGGER.info("Tab {} is ENABLED", tabData.getId());
        return true;
    }

    private boolean checkEnabledCondition(TabData.EnabledCondition condition, Player player) {
        return switch (condition.getType()) {
            case ITEM_IN_INVENTORY -> {
                for (ItemStack stack : player.getInventory().items) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null && condition.matchesItem(id)) yield true;
                }
                yield false;
            }
            case ITEM_IN_HOTBAR -> {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = player.getInventory().items.get(i);
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null && condition.matchesItem(id)) yield true;
                }
                yield false;
            }
            case ITEM_IN_CURIO -> {
                if (!LegendaryTabs.curiosLoaded) yield false;
                try {
                    var helper = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper();
                    String slotId = condition.getCurioSlot().orElse(null);
                    var lazyOpt = helper.getCuriosHandler(player);
                    if (lazyOpt.isPresent()) {
                        var handler = lazyOpt.resolve().get();
                        var curios = handler.getCurios();
                        for (var entry : curios.entrySet()) {
                            if (slotId != null && !entry.getKey().equals(slotId)) continue;
                            var stacksHandler = entry.getValue().getStacks();
                            for (int i = 0; i < stacksHandler.getSlots(); i++) {
                                ItemStack stack = stacksHandler.getStackInSlot(i);
                                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                                if (id != null && condition.matchesItem(id)) yield true;
                            }
                        }
                    }
                } catch (Exception e) {
                    LegendaryTabs.LOGGER.debug("Error checking curio condition for tab {}: {}", tabData.getId(), e.getMessage());
                }
                yield false;
            }
        };
    }

    @Override
    public ResourceLocation getIconTexture() {
        TabData.IconData iconData = tabData.getIconData();
        
        if (iconData.getType() == TabData.IconData.IconType.TEXTURE) {
            return iconData.getTextureLocation().orElse(null);
        }
        
        // For items, we'll return a default texture and override the render method
        return new ResourceLocation(LegendaryTabs.MOD_ID, "textures/gui/item_placeholder.png");
    }

    @Override
    public int getIconTexX() {
        TabData.IconData iconData = tabData.getIconData();
        return iconData.getType() == TabData.IconData.IconType.TEXTURE ? iconData.getTextureX() : 0;
    }

    @Override
    public int getIconTexY() {
        TabData.IconData iconData = tabData.getIconData();
        return iconData.getType() == TabData.IconData.IconType.TEXTURE ? iconData.getTextureY() : 0;
    }

    @Override
    public void render(GuiGraphics gui, int x, int y, boolean hover) {
        TabData.IconData iconData = tabData.getIconData();
        
        if (iconData.getType() == TabData.IconData.IconType.ITEM) {
            // Render button background first
            int bgTexX = hover ? 27 : 0;
            int bgTexY = 0;
            ResourceLocation buttonsTexture = new ResourceLocation(LegendaryTabs.MOD_ID, "textures/gui/buttons.png");
            gui.blit(buttonsTexture, x, y, bgTexX, bgTexY, TAB_WIDTH, TAB_HEIGHT, 64, 64);
            
            // Render item icon
            iconData.getItemId().ifPresent(itemId -> {
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item != null) {
                    ItemStack itemStack = new ItemStack(item);
                    gui.renderItem(itemStack, x + ICON_OFFSET_X, y + ICON_OFFSET_Y);
                }
            });
        } else {
            // Use default texture rendering
            super.render(gui, x, y, hover);
        }
    }

    @Override
    public boolean isCurrentlyUsed(Screen currentScreen) {
        String screenClassName = currentScreen.getClass().getName();
        
        // Check if the tab specifies a target screen class
        var targetScreenClass = tabData.getTargetScreenClass();
        if (targetScreenClass.isPresent()) {
            // Only consider the tab "used" when we're on the target screen
            boolean isOnTargetScreen = screenClassName.equals(targetScreenClass.get());
            LegendaryTabs.LOGGER.debug("Tab {} target screen check: current={}, target={}, isUsed={}", 
                    tabData.getId(), screenClassName, targetScreenClass.get(), isOnTargetScreen);
            return isOnTargetScreen;
        }
        
        // If no target screen class is specified, never disable the button
        // This allows the tab to be clicked from any screen
        LegendaryTabs.LOGGER.debug("Tab {} has no target screen class specified - never disabled", tabData.getId());
        return false;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable(tabData.getTooltipKey());
    }

    @Override
    public void initTabOnScreens() {
        LegendaryTabs.LOGGER.info("Initializing tab {} on screens with patterns: {}", tabData.getId(), tabData.getScreenPatterns());
        for (String screenPattern : tabData.getScreenPatterns()) {
            LegendaryTabs.LOGGER.info("Processing screen pattern: {}", screenPattern);
            initTabOnScreenPattern(screenPattern);
        }
    }

    private void initTabOnScreenPattern(String screenPattern) {
        // Handle special patterns
        if ("*".equals(screenPattern)) {
            // Add to all known screen types
            initTabOnAllScreens();
            return;
        }
        
        if (screenPattern.startsWith("*/")) {
            // Handle exclusion patterns like */BodyHealthScreen,ReskillableTab
            String exclusionPart = screenPattern.substring(2);
            String[] exclusions = exclusionPart.split(",");
            initTabOnAllScreensExcept(Arrays.asList(exclusions));
            return;
        }
        
        if (screenPattern.contains(",")) {
            // Handle comma-separated list
            String[] screens = screenPattern.split(",");
            for (String screen : screens) {
                initTabOnSingleScreen(screen.trim());
            }
            return;
        }
        
        // Handle single screen
        initTabOnSingleScreen(screenPattern);
    }

    private void initTabOnAllScreens() {
        LegendaryTabs.LOGGER.info("initTabOnAllScreens() called for tab: {}", tabData.getId());
        
        // Add to standard vanilla screens first
        String inventoryScreenClass = "net.minecraft.client.gui.screens.inventory.InventoryScreen";
        TabData.ScreenSizeConfig inventorySize = tabData.getScreenSizes().get(inventoryScreenClass);
        
        if (inventorySize != null) {
            LegendaryTabs.LOGGER.info("Adding tab {} to InventoryScreen with custom size", tabData.getId());
            TabsMenu.addTabToScreen(this, net.minecraft.client.gui.screens.inventory.InventoryScreen.class, 
                    (player) -> inventorySize.getWidth(), (player) -> inventorySize.getHeight(), inventorySize.getPriority());
        } else {
            LegendaryTabs.LOGGER.info("Adding tab {} to InventoryScreen with default size", tabData.getId());
            TabsMenu.addTabToScreen(this, net.minecraft.client.gui.screens.inventory.InventoryScreen.class, 
                    (player) -> 176, (player) -> 166, 50);
        }
        
        // Use existing screen registry from other tabs to discover available screens
        // This way we don't hardcode mod-specific screens
        var existingScreens = TabsMenu.getRegisteredScreens();
        
        for (Class<? extends Screen> screenClass : existingScreens) {
            // Skip InventoryScreen since we already added it above
            if (screenClass == net.minecraft.client.gui.screens.inventory.InventoryScreen.class) {
                continue;
            }
            
            String screenClassName = screenClass.getName();
            TabData.ScreenSizeConfig sizeConfig = tabData.getScreenSizes().get(screenClassName);
            
            if (sizeConfig != null) {
                LegendaryTabs.LOGGER.info("Adding tab {} to screen {} with custom size: {}x{}", 
                        tabData.getId(), screenClass.getSimpleName(), sizeConfig.getWidth(), sizeConfig.getHeight());
                TabsMenu.addTabToScreen(this, screenClass, 
                    (player) -> sizeConfig.getWidth(), (player) -> sizeConfig.getHeight(), sizeConfig.getPriority());
            } else {
                LegendaryTabs.LOGGER.info("Adding tab {} to screen {} with default size", 
                        tabData.getId(), screenClass.getSimpleName());
                TabsMenu.addTabToScreen(this, screenClass, 
                    (player) -> 176, (player) -> 166, 50);
            }
        }
        
        LegendaryTabs.LOGGER.info("Completed initTabOnAllScreens() for tab: {}", tabData.getId());
    }

    private void initTabOnAllScreensExcept(List<String> exclusions) {
        // Implementation for exclusion patterns
        initTabOnAllScreens(); // For now, just add to all - this would need more sophisticated logic
    }

    private void initTabOnSingleScreen(String screenClassName) {
        // Check if we have a custom size configuration for this screen
        TabData.ScreenSizeConfig sizeConfig = tabData.getScreenSizes().get(screenClassName);
        
        if (sizeConfig != null) {
            // Use configured size
            LegendaryTabs.LOGGER.debug("Using custom size for {}: {}x{}, priority: {}", 
                    screenClassName, sizeConfig.getWidth(), sizeConfig.getHeight(), sizeConfig.getPriority());
            addToScreenIfExists(screenClassName, sizeConfig.getWidth(), sizeConfig.getHeight(), sizeConfig.getPriority());
        } else {
            // Use default size
            addToScreenIfExists(screenClassName, 176, 166, 50);
        }
    }

    @SuppressWarnings("unchecked")
    private void addToScreenIfExists(String className, int width, int height, int priority) {
        try {
            Class<? extends Screen> screenClass = (Class<? extends Screen>) Class.forName(className);
            TabsMenu.addTabToScreen(this, screenClass, (player) -> width, (player) -> height, priority);
        } catch (ClassNotFoundException e) {
            LegendaryTabs.LOGGER.debug("Screen class not found: " + className);
        }
    }

    public TabData getTabData() {
        return tabData;
    }
}
