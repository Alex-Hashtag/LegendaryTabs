package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import sfiomn.legendarytabs.LegendaryTabs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class DataDrivenTabBase extends TabBase {
    protected final TabData tabData;
    private final List<Pattern> screenPatterns;

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

        if (!isEnabled(player)) {
            player.sendSystemMessage(Component.translatable("message.legendarytabs.tab_unavailable"));
            return;
        }

        TabData.ScreenOpenAction action = tabData.getScreenOpenAction();
        
        switch (action.getType()) {
            case KEY_PRESS -> action.getKeyBinding().ifPresent(keyBinding ->
                    KeyPressSimulator.press(keyBinding, action.isCloseScreenFirst()));
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
                action.getApiCallName().ifPresent(callName -> executeApiCall(callName, player));
            }
            case REFLECTION -> {
                action.getReflectionClassName().ifPresent(className ->
                    action.getReflectionMethodName().ifPresent(methodName ->
                        executeReflectionCall(className, methodName, action.isReflectionStatic(), action.isCloseScreenFirst())
                    )
                );
            }
            case COMMAND -> {
                action.getCommand().ifPresent(command -> executeCommand(command, action.isCloseScreenFirst()));
            }
        }
    }

    private void executeApiCall(String callName, Player player) {
        LegendaryTabs.LOGGER.warn("api_call '{}' is no longer supported; migrate the tab to key_press or right_click_item", callName);
    }

    private void executeReflectionCall(String className, String methodName, boolean isStatic, boolean closeScreenFirst) {
        try {
            LegendaryTabs.LOGGER.info("Executing reflection action: {}#{} (static={})", className, methodName, isStatic);

            Runnable callTask = () -> {
                try {
                    doReflectionCall(className, methodName, isStatic);
                } catch (Exception e) {
                    LegendaryTabs.LOGGER.warn("Failed reflection call: {}#{}", className, methodName, e);
                }
            };

            if (closeScreenFirst) {
                Minecraft.getInstance().setScreen(null);
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().execute(callTask));
            } else {
                callTask.run();
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to schedule reflection call: {}#{}", className, methodName, e);
        }
    }

    private void doReflectionCall(String className, String methodName, boolean isStatic) throws Exception {
        Class<?> clazz = Class.forName(className);
        java.lang.reflect.Method method = clazz.getMethod(methodName);
        if (isStatic) {
            method.invoke(null);
        } else {
            method.invoke(resolveInstance(clazz));
        }
        LegendaryTabs.LOGGER.info("Reflection call succeeded: {}#{} (static={})", className, methodName, isStatic);
    }

    /**
     * Many mod-facing "manager" APIs are singletons exposed via a public static
     * field (enum singletons like JourneyMap's UIManager.INSTANCE, or a plain
     * "public static final X INSTANCE" constant) rather than a no-arg constructor.
     * Prefer that field when present; only fall back to constructing a fresh
     * instance for classes that are genuinely meant to be instantiated directly.
     */
    private Object resolveInstance(Class<?> clazz) throws Exception {
        try {
            java.lang.reflect.Field instanceField = clazz.getField("INSTANCE");
            return instanceField.get(null);
        } catch (NoSuchFieldException ignored) {
            // not a singleton exposed this way - fall through
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    private void executeCommand(String command, boolean closeScreenFirst) {
        try {
            LegendaryTabs.LOGGER.info("Executing command action: /{}", command);

            if (closeScreenFirst) {
                Minecraft.getInstance().setScreen(null);
            }

            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
                Minecraft.getInstance().player.connection.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(
                        command,
                        java.time.Instant.now(),
                        0L,
                        net.minecraft.commands.arguments.ArgumentSignatures.EMPTY,
                        new net.minecraft.network.chat.LastSeenMessages.Update(0, new java.util.BitSet(20))
                ));
            } else {
                LegendaryTabs.LOGGER.warn("Cannot execute command /{}: player or connection is null", command);
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to execute command: /{}", command, e);
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



    private void applySimulatedKeyPress(net.minecraft.client.KeyMapping keyMapping, InputConstants.Key key, boolean activeBinding) {
        if (activeBinding) {
            // Standard Forge static path. This is what a real physical key press goes through:
            // it updates the KeyMapping instance that mods reference via KeyMapping.ALL / options.
            net.minecraft.client.KeyMapping.click(key);
            net.minecraft.client.KeyMapping.set(key, true);
            LegendaryTabs.LOGGER.info("Registered press via Forge static path for key: {}", key.getName());
        } else {
            // Unbound, or a binding whose key is claimed by another KeyMapping in the static MAP.
            // The static path would hit the wrong mapping (or no mapping), so manipulate the
            // KeyMapping instance directly. This ensures consumeClick() and isDown() still work
            // regardless of whether the binding has a physical key assigned.
            incrementClickCount(keyMapping);
            LegendaryTabs.LOGGER.info("Registered press via direct instance manipulation for: {}", keyMapping.getName());
        }

        // Always mark the binding as held for mods that check isDown() on the instance itself.
        keyMapping.setDown(true);

        // Fire the raw input event for any binding with a real key so mods that listen to
        // InputEvent.Key (e.g. JourneyMap, Pufferfish's Skills) also detect the press.
        if (key != InputConstants.UNKNOWN) {
            try {
                MinecraftForge.EVENT_BUS.post(new InputEvent.Key(key.getValue(), 0, 1, 0));
                LegendaryTabs.LOGGER.debug("Posted InputEvent.Key for: {}", keyMapping.getName());
            } catch (Exception e) {
                LegendaryTabs.LOGGER.debug("Failed to post InputEvent.Key for {}: {}", keyMapping.getName(), e.getMessage());
            }
        }

        LegendaryTabs.LOGGER.info("Key press simulation completed for: {}", keyMapping.getName());

        // Hold the press for the remainder of this tick, then release at the end so mod tick
        // handlers have a full tick to observe isDown/consumeClick before it is cleared.
        MinecraftForge.EVENT_BUS.register(new OneShotClientTickListenerLowest(TickEvent.Phase.END, () -> {
            keyMapping.setDown(false);
            if (activeBinding) {
                net.minecraft.client.KeyMapping.set(key, false);
            }
            LegendaryTabs.LOGGER.debug("Released simulated press for: {}", keyMapping.getName());
        }));
    }

    private boolean isActiveBinding(net.minecraft.client.KeyMapping keyMapping, InputConstants.Key key) {
        try {
            var mapField = net.minecraft.client.KeyMapping.class.getDeclaredField("MAP");
            mapField.setAccessible(true);
            Object map = mapField.get(null);
            if (map instanceof net.minecraftforge.client.settings.KeyMappingLookup lookup) {
                // Forge 1.20+ stores bindings in a KeyMappingLookup that handles conflict contexts/modifiers.
                // If our mapping is among the active ones for this key, the standard Forge path will update it.
                return lookup.getAll(key).contains(keyMapping);
            } else if (map instanceof java.util.Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                java.util.Map<InputConstants.Key, net.minecraft.client.KeyMapping> typedMap =
                        (java.util.Map<InputConstants.Key, net.minecraft.client.KeyMapping>) rawMap;
                return typedMap.get(key) == keyMapping;
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Failed to check active binding for {}: {}", keyMapping.getName(), e.getMessage());
        }
        return false;
    }

    private void incrementClickCount(net.minecraft.client.KeyMapping keyMapping) {
        try {
            var clickCountField = net.minecraft.client.KeyMapping.class.getDeclaredField("clickCount");
            clickCountField.setAccessible(true);
            int currentClicks = clickCountField.getInt(keyMapping);
            clickCountField.setInt(keyMapping, currentClicks + 1);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to increment click count for {}: {}", keyMapping.getName(), e.getMessage());
        }
    }

    private void simulateRightClickItem(ResourceLocation itemId, Player player) {
        var item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) return;

        // Prefer a real right-click through the game mode if the item is currently held,
        // so the server sees the use and any item-specific networking fires correctly.
        if (player.getMainHandItem().is(item) && player.level().isClientSide
                && Minecraft.getInstance().gameMode != null) {
            Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            return;
        }
        if (player.getOffhandItem().is(item) && player.level().isClientSide
                && Minecraft.getInstance().gameMode != null) {
            Minecraft.getInstance().gameMode.useItem(player, InteractionHand.OFF_HAND);
            return;
        }

        // Fallback: find the item somewhere in inventory/curio and call use() on a copy.
        ItemStack held = findItemStack(itemId, player);
        if (held == null) held = new ItemStack(item);
        held.use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    private ItemStack findItemStack(ResourceLocation itemId, Player player) {
        for (ItemStack stack : player.getInventory().items) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.equals(itemId)) return stack;
        }
        if (LegendaryTabs.curiosLoaded) {
            try {
                var helper = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper();
                var lazyOpt = helper.getCuriosHandler(player);
                if (lazyOpt.isPresent()) {
                    var handler = lazyOpt.resolve().get();
                    for (var entry : handler.getCurios().entrySet()) {
                        var stacksHandler = entry.getValue().getStacks();
                        for (int i = 0; i < stacksHandler.getSlots(); i++) {
                            ItemStack stack = stacksHandler.getStackInSlot(i);
                            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                            if (id != null && id.equals(itemId)) return stack;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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
            case OR -> {
                for (TabData.EnabledCondition subCondition : condition.getSubConditions()) {
                    if (checkEnabledCondition(subCondition, player)) yield true;
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
                    inventorySize::getWidth, inventorySize::getHeight, inventorySize.getPriority());
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
                    sizeConfig::getWidth, sizeConfig::getHeight, sizeConfig.getPriority());
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
            addToScreenIfExists(screenClassName, sizeConfig::getWidth, sizeConfig::getHeight, sizeConfig.getPriority());
        } else {
            // Use default size
            addToScreenIfExists(screenClassName, (player) -> 176, (player) -> 166, 50);
        }
    }

    @SuppressWarnings("unchecked")
    private void addToScreenIfExists(String className, Function<Player, Integer> screenWidth, Function<Player, Integer> screenHeight, int priority) {
        try {
            Class<? extends Screen> screenClass = (Class<? extends Screen>) Class.forName(className);
            TabsMenu.addTabToScreen(this, screenClass, screenWidth, screenHeight, priority);
        } catch (ClassNotFoundException e) {
            LegendaryTabs.LOGGER.debug("Screen class not found: " + className);
        }
    }

    public TabData getTabData() {
        return tabData;
    }

    private static class OneShotClientTickListener {
        private final TickEvent.Phase phase;
        private final Runnable task;

        OneShotClientTickListener(TickEvent.Phase phase, Runnable task) {
            this.phase = phase;
            this.task = task;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == this.phase) {
                task.run();
                MinecraftForge.EVENT_BUS.unregister(this);
            }
        }
    }

    private static class OneShotClientTickListenerLowest {
        private final TickEvent.Phase phase;
        private final Runnable task;

        OneShotClientTickListenerLowest(TickEvent.Phase phase, Runnable task) {
            this.phase = phase;
            this.task = task;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == this.phase) {
                task.run();
                MinecraftForge.EVENT_BUS.unregister(this);
            }
        }
    }
}
