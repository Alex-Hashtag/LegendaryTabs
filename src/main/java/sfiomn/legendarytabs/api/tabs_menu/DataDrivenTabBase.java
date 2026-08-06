package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import sfiomn.legendarytabs.LegendaryTabs;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DataDrivenTabBase extends TabBase {
    private static final int DEFAULT_ORDER_PRIORITY = 50;

    protected final TabData tabData;

    public DataDrivenTabBase(TabData tabData) {
        this.tabData = tabData;
    }

    @Override
    public String getId() {
        return tabData.getId();
    }

    /**
     * A single ordering priority for this tab, used for every screen it appears on - its own
     * screen and every screen it fans out to via "*". Without this, a tab's position relative
     * to its peers could differ from screen to screen depending on whether the JSON happened to
     * declare a priority for that particular screen (its own screen_sizes entry) or fell back
     * to a hardcoded default elsewhere, which made tab order inconsistent across screens.
     */
    private int resolveOrderPriority() {
        return tabData.getScreenSizes().values().stream()
                .findFirst()
                .map(TabData.ScreenSizeConfig::getPriority)
                .orElse(DEFAULT_ORDER_PRIORITY);
    }

    /**
     * Registers that the screen(s) this tab intrinsically belongs to (its own mod's screen,
     * declared via screen_sizes and/or target_screen_class) exist, WITHOUT placing this tab on
     * them yet - that happens uniformly in initTabOnScreens() below, alongside every other tab's
     * fan-out, so a tab's position relative to its peers on its own screen matches its position
     * everywhere else. Screen discovery for other tabs only ever sees screens that are already
     * known, so every screen must be seeded here first - otherwise a mod's own screen never
     * becomes visible to any tab, including its own, regardless of registration order.
     * <p>
     * If show_tabs_on_screen is false, this is skipped entirely: the screen is never registered,
     * so nobody (including this tab) ever discovers it, and it never gets a tab bar at all.
     */
    public void seedOwnedScreens() {
        if (!tabData.isShowTabsOnScreen()) {
            return;
        }

        for (Map.Entry<String, TabData.ScreenSizeConfig> entry : tabData.getScreenSizes().entrySet()) {
            seedScreen(entry.getKey(), entry.getValue());
        }

        tabData.getTargetScreenClass().ifPresent(className -> {
            if (!tabData.getScreenSizes().containsKey(className)) {
                seedScreen(className, null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void seedScreen(String className, TabData.ScreenSizeConfig sizeConfig) {
        try {
            Class<? extends Screen> screenClass = (Class<? extends Screen>) Class.forName(className);
            Function<Player, Integer> width = sizeConfig != null ? sizeConfig::getWidth : (player) -> 176;
            Function<Player, Integer> height = sizeConfig != null ? sizeConfig::getHeight : (player) -> 166;
            ResourceLocation buttonSkin = sizeConfig != null ? sizeConfig.getButtonSkin().orElse(null) : null;
            int iconOffsetX = sizeConfig != null ? sizeConfig.getIconOffsetX() : 0;
            int iconOffsetY = sizeConfig != null ? sizeConfig.getIconOffsetY() : 0;
            TabsMenu.ensureScreenInfo(screenClass, width, height, buttonSkin, iconOffsetX, iconOffsetY);
        } catch (ClassNotFoundException e) {
            LegendaryTabs.LOGGER.debug("Screen class not found: " + className);
        }
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
            case OPEN_SCREEN -> {
                action.getScreenClassName().ifPresent(className -> openScreenByClassName(className, action.getConstructorArgs(), player));
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
                Minecraft.getInstance().player.connection.send(
                        new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(command));
            } else {
                LegendaryTabs.LOGGER.warn("Cannot execute command /{}: player or connection is null", command);
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to execute command: /{}", command, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void openScreenByClassName(String screenClassName, List<String> constructorArgSources, Player player) {
        try {
            Class<? extends Screen> screenClass = (Class<? extends Screen>) Class.forName(screenClassName);
            Screen screen;
            if (constructorArgSources.isEmpty()) {
                screen = screenClass.getDeclaredConstructor().newInstance();
            } else {
                Object[] args = constructorArgSources.stream().map(source -> resolveConstructorArg(source, player)).toArray();
                Constructor<?> constructor = Arrays.stream(screenClass.getDeclaredConstructors())
                        .filter(c -> c.getParameterCount() == args.length)
                        .findFirst()
                        .orElseThrow(() -> new NoSuchMethodException(
                                screenClassName + " has no constructor taking " + args.length + " argument(s)"));
                constructor.setAccessible(true);
                screen = (Screen) constructor.newInstance(args);
            }
            Minecraft.getInstance().setScreen(screen);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to open screen by class name: {}", screenClassName, e);
        }
    }

    /**
     * Named engine-values an OPEN_SCREEN action's constructor_args can ask for - a small,
     * reusable set (not tied to any one mod's screen) that a tab JSON can combine to match
     * whatever constructor its target screen actually has, instead of Java needing a bespoke
     * case for each screen that isn't a plain no-arg constructor.
     */
    private static Object resolveConstructorArg(String source, Player player) {
        return switch (source) {
            case "player" -> player;
            case "minecraft" -> Minecraft.getInstance();
            case "client_advancements" -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null
                    ? Minecraft.getInstance().player.connection.getAdvancements() : null;
            default -> {
                LegendaryTabs.LOGGER.warn("Unknown constructor_args source: {}", source);
                yield null;
            }
        };
    }



    private void applySimulatedKeyPress(net.minecraft.client.KeyMapping keyMapping, InputConstants.Key key, boolean activeBinding) {
        if (activeBinding) {
            // Standard static path. This is what a real physical key press goes through:
            // it updates the KeyMapping instance that mods reference via KeyMapping.ALL / options.
            net.minecraft.client.KeyMapping.click(key);
            net.minecraft.client.KeyMapping.set(key, true);
            LegendaryTabs.LOGGER.info("Registered press via static path for key: {}", key.getName());
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
                NeoForge.EVENT_BUS.post(new InputEvent.Key(key.getValue(), 0, 1, 0));
                LegendaryTabs.LOGGER.debug("Posted InputEvent.Key for: {}", keyMapping.getName());
            } catch (Exception e) {
                LegendaryTabs.LOGGER.debug("Failed to post InputEvent.Key for {}: {}", keyMapping.getName(), e.getMessage());
            }
        }

        LegendaryTabs.LOGGER.info("Key press simulation completed for: {}", keyMapping.getName());

        // Hold the press for the remainder of this tick, then release at the end so mod tick
        // handlers have a full tick to observe isDown/consumeClick before it is cleared.
        NeoForge.EVENT_BUS.register(new OneShotClientTickListenerLowest(() -> {
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
            if (map instanceof net.neoforged.neoforge.client.settings.KeyMappingLookup lookup) {
                // NeoForge stores bindings in a KeyMappingLookup that handles conflict contexts/modifiers.
                // If our mapping is among the active ones for this key, the standard path will update it.
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
        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
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
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && id.equals(itemId)) return stack;
        }
        if (LegendaryTabs.curiosLoaded) {
            try {
                var handlerOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
                if (handlerOpt.isPresent()) {
                    var handler = handlerOpt.get();
                    for (var entry : handler.getCurios().entrySet()) {
                        var stacksHandler = entry.getValue().getStacks();
                        for (int i = 0; i < stacksHandler.getSlots(); i++) {
                            ItemStack stack = stacksHandler.getStackInSlot(i);
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            if (id != null && id.equals(itemId)) return stack;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && condition.matchesItem(id)) yield true;
                }
                yield false;
            }
            case ITEM_IN_HOTBAR -> {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = player.getInventory().items.get(i);
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && condition.matchesItem(id)) yield true;
                }
                yield false;
            }
            case ITEM_IN_CURIO -> {
                if (!LegendaryTabs.curiosLoaded) yield false;
                try {
                    String slotId = condition.getCurioSlot().orElse(null);
                    var handlerOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
                    if (handlerOpt.isPresent()) {
                        var handler = handlerOpt.get();
                        var curios = handler.getCurios();
                        for (var entry : curios.entrySet()) {
                            if (slotId != null && !entry.getKey().equals(slotId)) continue;
                            var stacksHandler = entry.getValue().getStacks();
                            for (int i = 0; i < stacksHandler.getSlots(); i++) {
                                ItemStack stack = stacksHandler.getStackInSlot(i);
                                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        return ResourceLocation.fromNamespaceAndPath(LegendaryTabs.MOD_ID, "textures/gui/item_placeholder.png");
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
    public void render(GuiGraphics gui, int x, int y, boolean hover, ResourceLocation buttonTexture, int iconOffsetX, int iconOffsetY) {
        TabData.IconData iconData = tabData.getIconData();

        if (iconData.getType() == TabData.IconData.IconType.ITEM) {
            // Render button background first
            int bgTexX = hover ? 27 : 0;
            int bgTexY = 0;
            gui.blit(buttonTexture != null ? buttonTexture : DEFAULT_BUTTONS_TEXTURE, x, y, bgTexX, bgTexY, TAB_WIDTH, TAB_HEIGHT, 64, 64);

            // Render item icon
            iconData.getItemId().ifPresent(itemId -> {
                var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                if (item != null) {
                    ItemStack itemStack = new ItemStack(item);
                    gui.renderItem(itemStack, x + ICON_OFFSET_X + iconOffsetX, y + ICON_OFFSET_Y + iconOffsetY);
                }
            });
        } else {
            // Use default texture rendering
            super.render(gui, x, y, hover, buttonTexture, iconOffsetX, iconOffsetY);
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

    /**
     * Fans this tab out to every screen currently registered - the tab bar's default behaviour
     * is simply "every screen that shows a tab bar shows every currently-enabled tab". A screen
     * only ever gets left out if its owning tab set show_tabs_on_screen to false, in which case
     * it was never seeded in the first place (see seedOwnedScreens()) and so never shows up in
     * TabsMenu.getRegisteredScreens() for anyone, including its own tab, to fan out onto.
     */
    @Override
    public void initTabOnScreens() {
        LegendaryTabs.LOGGER.info("Initializing tab {} on all registered screens", tabData.getId());

        int priority = resolveOrderPriority();

        // Add to standard vanilla screens first
        String inventoryScreenClass = "net.minecraft.client.gui.screens.inventory.InventoryScreen";
        TabData.ScreenSizeConfig inventorySize = tabData.getScreenSizes().get(inventoryScreenClass);

        if (inventorySize != null) {
            LegendaryTabs.LOGGER.info("Adding tab {} to InventoryScreen with custom size", tabData.getId());
            TabsMenu.addTabToScreen(this, net.minecraft.client.gui.screens.inventory.InventoryScreen.class,
                    inventorySize::getWidth, inventorySize::getHeight, priority);
        } else {
            LegendaryTabs.LOGGER.info("Adding tab {} to InventoryScreen with default size", tabData.getId());
            TabsMenu.addTabToScreen(this, net.minecraft.client.gui.screens.inventory.InventoryScreen.class,
                    (player) -> 176, (player) -> 166, priority);
        }

        // Use existing screen registry from other tabs to discover available screens
        // This way we don't hardcode mod-specific screens
        var existingScreens = TabsMenu.getRegisteredScreens();

        for (Class<? extends Screen> screenClass : existingScreens) {
            // Skip InventoryScreen since we already added it above. Screens this tab owns
            // (seeded but not yet populated by seedOwnedScreens()) are handled below like any
            // other screen, so this tab's own position among its peers is the same everywhere.
            if (screenClass == net.minecraft.client.gui.screens.inventory.InventoryScreen.class) {
                continue;
            }

            String screenClassName = screenClass.getName();
            TabData.ScreenSizeConfig sizeConfig = tabData.getScreenSizes().get(screenClassName);

            if (sizeConfig != null) {
                LegendaryTabs.LOGGER.info("Adding tab {} to screen {} with custom size: {}x{}",
                        tabData.getId(), screenClass.getSimpleName(), sizeConfig.getWidth(), sizeConfig.getHeight());
                TabsMenu.addTabToScreen(this, screenClass,
                    sizeConfig::getWidth, sizeConfig::getHeight, priority);
            } else {
                LegendaryTabs.LOGGER.info("Adding tab {} to screen {} with default size",
                        tabData.getId(), screenClass.getSimpleName());
                TabsMenu.addTabToScreen(this, screenClass,
                    (player) -> 176, (player) -> 166, priority);
            }
        }

        LegendaryTabs.LOGGER.info("Completed initializing tab {} on all registered screens", tabData.getId());
    }

    public TabData getTabData() {
        return tabData;
    }

    private static class OneShotClientTickListenerLowest {
        private final Runnable task;

        OneShotClientTickListenerLowest(Runnable task) {
            this.task = task;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onClientTick(ClientTickEvent.Post event) {
            task.run();
            NeoForge.EVENT_BUS.unregister(this);
        }
    }
}
