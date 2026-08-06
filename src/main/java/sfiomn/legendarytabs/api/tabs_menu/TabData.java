package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

public class TabData {
    private final String id;
    private final boolean enabled;
    private final IconData iconData;
    private final ScreenOpenAction screenOpenAction;
    private final String tooltipKey;
    private final List<String> requiredMods;
    private final String targetScreenClass;
    private final Map<String, ScreenSizeConfig> screenSizes;
    private final List<EnabledCondition> enabledConditions;
    private final boolean showTabsOnScreen;

    public TabData(String id, boolean enabled, IconData iconData, ScreenOpenAction screenOpenAction,
                   String tooltipKey, List<String> requiredMods, String targetScreenClass,
                   Map<String, ScreenSizeConfig> screenSizes, List<EnabledCondition> enabledConditions,
                   boolean showTabsOnScreen) {
        this.id = id;
        this.enabled = enabled;
        this.iconData = iconData;
        this.screenOpenAction = screenOpenAction;
        this.tooltipKey = tooltipKey;
        this.requiredMods = requiredMods;
        this.targetScreenClass = targetScreenClass;
        this.screenSizes = screenSizes != null ? screenSizes : Map.of();
        this.enabledConditions = enabledConditions != null ? enabledConditions : List.of();
        this.showTabsOnScreen = showTabsOnScreen;
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public IconData getIconData() { return iconData; }
    public ScreenOpenAction getScreenOpenAction() { return screenOpenAction; }
    public String getTooltipKey() { return tooltipKey; }
    public List<String> getRequiredMods() { return requiredMods; }
    public Optional<String> getTargetScreenClass() { return Optional.ofNullable(targetScreenClass); }
    public Map<String, ScreenSizeConfig> getScreenSizes() { return screenSizes; }
    public List<EnabledCondition> getEnabledConditions() { return enabledConditions; }
    /**
     * When false, this tab's own screen (its screen_sizes/target_screen_class entry) never
     * gets a tab bar at all - not this tab's button, not any other tab's. For screens with
     * dense custom UIs (a full skill tree, a jobs menu) where the shared tab bar would just be
     * clutter or overlap content. Defaults to true: the tab bar shows on every screen any tab
     * registers, with every currently-enabled tab in it.
     */
    public boolean isShowTabsOnScreen() { return showTabsOnScreen; }

    public static class IconData {
        private final IconType type;
        private final ResourceLocation textureLocation;
        private final int textureX;
        private final int textureY;
        private final ResourceLocation itemId;

        private IconData(IconType type, ResourceLocation textureLocation, int textureX, int textureY, ResourceLocation itemId) {
            this.type = type;
            this.textureLocation = textureLocation;
            this.textureX = textureX;
            this.textureY = textureY;
            this.itemId = itemId;
        }

        public static IconData texture(ResourceLocation textureLocation, int textureX, int textureY) {
            return new IconData(IconType.TEXTURE, textureLocation, textureX, textureY, null);
        }

        public static IconData texture(ResourceLocation textureLocation) {
            return new IconData(IconType.TEXTURE, textureLocation, 0, 0, null);
        }

        public static IconData item(ResourceLocation itemId) {
            return new IconData(IconType.ITEM, null, 0, 0, itemId);
        }

        public IconType getType() { return type; }
        public Optional<ResourceLocation> getTextureLocation() { return Optional.ofNullable(textureLocation); }
        public int getTextureX() { return textureX; }
        public int getTextureY() { return textureY; }
        public Optional<ResourceLocation> getItemId() { return Optional.ofNullable(itemId); }

        public enum IconType {
            TEXTURE,
            ITEM
        }
    }

    public static class ScreenOpenAction {
        private final ActionType type;
        private final String keyBinding;
        private final ResourceLocation itemToUse;
        private final String screenClassName;
        private final List<String> constructorArgs;
        private final boolean closeScreenFirst;

        // Reflection action fields
        private final String reflectionClassName;
        private final String reflectionMethodName;
        private final boolean reflectionStatic;

        // Command action field
        private final String command;

        private ScreenOpenAction(ActionType type, String keyBinding, ResourceLocation itemToUse, String screenClassName,
                                 List<String> constructorArgs, boolean closeScreenFirst, String reflectionClassName,
                                 String reflectionMethodName, boolean reflectionStatic, String command) {
            this.type = type;
            this.keyBinding = keyBinding;
            this.itemToUse = itemToUse;
            this.screenClassName = screenClassName;
            this.constructorArgs = constructorArgs != null ? constructorArgs : List.of();
            this.closeScreenFirst = closeScreenFirst;
            this.reflectionClassName = reflectionClassName;
            this.reflectionMethodName = reflectionMethodName;
            this.reflectionStatic = reflectionStatic;
            this.command = command;
        }

        public static ScreenOpenAction keyPress(String keyBinding, boolean closeScreenFirst) {
            return new ScreenOpenAction(ActionType.KEY_PRESS, keyBinding, null, null, null, closeScreenFirst, null, null, false, null);
        }

        public static ScreenOpenAction rightClickItem(ResourceLocation itemToUse) {
            return new ScreenOpenAction(ActionType.RIGHT_CLICK_ITEM, null, itemToUse, null, null, false, null, null, false, null);
        }

        public static ScreenOpenAction openScreen(String screenClassName, List<String> constructorArgs) {
            return new ScreenOpenAction(ActionType.OPEN_SCREEN, null, null, screenClassName, constructorArgs, false, null, null, false, null);
        }

        public static ScreenOpenAction reflection(String className, String methodName, boolean isStatic, boolean closeScreenFirst) {
            return new ScreenOpenAction(ActionType.REFLECTION, null, null, null, null, closeScreenFirst, className, methodName, isStatic, null);
        }

        public static ScreenOpenAction command(String command, boolean closeScreenFirst) {
            return new ScreenOpenAction(ActionType.COMMAND, null, null, null, null, closeScreenFirst, null, null, false, command);
        }

        public ActionType getType() { return type; }
        public boolean isCloseScreenFirst() { return closeScreenFirst; }
        public Optional<String> getKeyBinding() { return Optional.ofNullable(keyBinding); }
        public Optional<ResourceLocation> getItemToUse() { return Optional.ofNullable(itemToUse); }
        public Optional<String> getScreenClassName() { return Optional.ofNullable(screenClassName); }
        /**
         * Ordered list of named engine-value providers (see DataDrivenTabBase's constructor arg
         * resolver) to pass to the OPEN_SCREEN target's constructor. Empty means "use the no-arg
         * constructor" - most vanilla-style screens have one, but some (e.g. AdvancementsScreen)
         * need a real argument, which this lets a tab supply declaratively instead of Java having
         * to special-case that one screen.
         */
        public List<String> getConstructorArgs() { return constructorArgs; }

        public Optional<String> getReflectionClassName() { return Optional.ofNullable(reflectionClassName); }
        public Optional<String> getReflectionMethodName() { return Optional.ofNullable(reflectionMethodName); }
        public boolean isReflectionStatic() { return reflectionStatic; }
        public Optional<String> getCommand() { return Optional.ofNullable(command); }

        public enum ActionType {
            KEY_PRESS,
            RIGHT_CLICK_ITEM,
            OPEN_SCREEN,
            REFLECTION,
            COMMAND
        }
    }

    public static class EnabledCondition {
        public enum ConditionType {
            ITEM_IN_INVENTORY,
            ITEM_IN_HOTBAR,
            ITEM_IN_CURIO,
            OR
        }

        private final ConditionType type;
        private final String itemPattern;
        private final String curioSlot;
        private final List<EnabledCondition> subConditions;

        public EnabledCondition(ConditionType type, String itemPattern, String curioSlot) {
            this(type, itemPattern, curioSlot, List.of());
        }

        public EnabledCondition(ConditionType type, String itemPattern, String curioSlot, List<EnabledCondition> subConditions) {
            this.type = type;
            this.itemPattern = itemPattern;
            this.curioSlot = curioSlot;
            this.subConditions = subConditions != null ? subConditions : List.of();
        }

        public ConditionType getType() { return type; }
        public String getItemPattern() { return itemPattern; }
        public Optional<String> getCurioSlot() { return Optional.ofNullable(curioSlot); }
        public List<EnabledCondition> getSubConditions() { return subConditions; }

        public boolean matchesItem(ResourceLocation itemId) {
            if (itemPattern == null) return true;
            if (itemPattern.endsWith(":*")) {
                String namespace = itemPattern.substring(0, itemPattern.length() - 2);
                return itemId.getNamespace().equals(namespace);
            }
            return itemId.toString().equals(itemPattern);
        }
    }

    public static class ScreenSizeConfig {
        private final int width;
        private final int height;
        private final int priority;
        private final Map<String, SizeVariable> variables;
        private final String widthFormula;
        private final String heightFormula;
        private final ResourceLocation buttonSkin;
        private final int iconOffsetX;
        private final int iconOffsetY;

        public ScreenSizeConfig(int width, int height, int priority) {
            this(width, height, priority, Map.of(), null, null, null, 0, 0);
        }

        public ScreenSizeConfig(int width, int height, int priority, Map<String, SizeVariable> variables,
                                 String widthFormula, String heightFormula, ResourceLocation buttonSkin,
                                 int iconOffsetX, int iconOffsetY) {
            this.width = width;
            this.height = height;
            this.priority = priority;
            this.variables = variables != null ? variables : Map.of();
            this.widthFormula = widthFormula;
            this.heightFormula = heightFormula;
            this.buttonSkin = buttonSkin;
            this.iconOffsetX = iconOffsetX;
            this.iconOffsetY = iconOffsetY;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getPriority() { return priority; }
        public Map<String, SizeVariable> getVariables() { return variables; }
        public Optional<String> getWidthFormula() { return Optional.ofNullable(widthFormula); }
        public Optional<String> getHeightFormula() { return Optional.ofNullable(heightFormula); }
        /**
         * Replaces the shared button-background sheet (legendarytabs:textures/gui/buttons.png)
         * for every tab's button while THIS screen is open - not just the tab that declared it.
         * The tab bar is shared UI chrome, so its skin is a property of the screen being viewed,
         * the same way its width/height/priority are.
         */
        public Optional<ResourceLocation> getButtonSkin() { return Optional.ofNullable(buttonSkin); }
        /** Added to the default icon offset (4, 4) for every tab button while this screen is open - lets a custom button_skin's icon slot sit somewhere other than the default sheet's. */
        public int getIconOffsetX() { return iconOffsetX; }
        public int getIconOffsetY() { return iconOffsetY; }

        public int getWidth(Player player) {
            return widthFormula != null ? (int) Math.round(evaluate(widthFormula, player)) : width;
        }

        public int getHeight(Player player) {
            return heightFormula != null ? (int) Math.round(evaluate(heightFormula, player)) : height;
        }

        private double evaluate(String formula, Player player) {
            try {
                Map<String, Double> resolved = new LinkedHashMap<>();
                for (Map.Entry<String, SizeVariable> entry : variables.entrySet()) {
                    resolved.put(entry.getKey(), entry.getValue().resolve(player, resolved));
                }
                return FormulaEvaluator.evaluate(formula, resolved);
            } catch (Exception e) {
                sfiomn.legendarytabs.LegendaryTabs.LOGGER.warn("Failed to evaluate tab size formula '{}': {}", formula, e.getMessage());
                return 0;
            }
        }
    }

    public static class SizeVariable {
        public enum Source { CONSTANT, ITEM_NBT, BUILTIN, FORMULA }

        private final Source source;
        private final double constantValue;
        private final String locator;
        private final String path;
        private final ItemNbtResolver.ValueType valueType;
        private final double defaultValue;
        private final String builtinId;
        private final String formulaExpr;

        private SizeVariable(Source source, double constantValue, String locator, String path,
                              ItemNbtResolver.ValueType valueType, double defaultValue,
                              String builtinId, String formulaExpr) {
            this.source = source;
            this.constantValue = constantValue;
            this.locator = locator;
            this.path = path;
            this.valueType = valueType;
            this.defaultValue = defaultValue;
            this.builtinId = builtinId;
            this.formulaExpr = formulaExpr;
        }

        public static SizeVariable constant(double value) {
            return new SizeVariable(Source.CONSTANT, value, null, null, null, 0, null, null);
        }

        public static SizeVariable itemNbt(String locator, String path, ItemNbtResolver.ValueType valueType, double defaultValue) {
            return new SizeVariable(Source.ITEM_NBT, 0, locator, path, valueType, defaultValue, null, null);
        }

        public static SizeVariable builtin(String builtinId) {
            return new SizeVariable(Source.BUILTIN, 0, null, null, null, 0, builtinId, null);
        }

        public static SizeVariable formula(String expr) {
            return new SizeVariable(Source.FORMULA, 0, null, null, null, 0, null, expr);
        }

        public Source getSource() { return source; }

        public double resolve(Player player, Map<String, Double> previouslyResolved) {
            return switch (source) {
                case CONSTANT -> constantValue;
                case ITEM_NBT -> {
                    Double value = ItemNbtResolver.resolve(player, locator, path, valueType);
                    yield value != null ? value : defaultValue;
                }
                case BUILTIN -> {
                    Double value = BuiltinTabVariables.resolve(builtinId, player);
                    if (value == null) {
                        sfiomn.legendarytabs.LegendaryTabs.LOGGER.warn("Unknown builtin tab size variable: {}", builtinId);
                        yield 0;
                    }
                    yield value;
                }
                case FORMULA -> FormulaEvaluator.evaluate(formulaExpr, previouslyResolved);
            };
        }
    }
}
