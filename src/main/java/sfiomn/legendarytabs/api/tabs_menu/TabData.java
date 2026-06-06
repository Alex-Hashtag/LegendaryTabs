package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

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
    private final List<String> screenPatterns;
    private final List<String> requiredMods;
    private final String targetScreenClass;
    private final Map<String, ScreenSizeConfig> screenSizes;
    private final List<EnabledCondition> enabledConditions;

    public TabData(String id, boolean enabled, IconData iconData, ScreenOpenAction screenOpenAction, 
                   String tooltipKey, List<String> screenPatterns, List<String> requiredMods, String targetScreenClass,
                   Map<String, ScreenSizeConfig> screenSizes, List<EnabledCondition> enabledConditions) {
        this.id = id;
        this.enabled = enabled;
        this.iconData = iconData;
        this.screenOpenAction = screenOpenAction;
        this.tooltipKey = tooltipKey;
        this.screenPatterns = screenPatterns;
        this.requiredMods = requiredMods;
        this.targetScreenClass = targetScreenClass;
        this.screenSizes = screenSizes != null ? screenSizes : Map.of();
        this.enabledConditions = enabledConditions != null ? enabledConditions : List.of();
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public IconData getIconData() { return iconData; }
    public ScreenOpenAction getScreenOpenAction() { return screenOpenAction; }
    public String getTooltipKey() { return tooltipKey; }
    public List<String> getScreenPatterns() { return screenPatterns; }
    public List<String> getRequiredMods() { return requiredMods; }
    public Optional<String> getTargetScreenClass() { return Optional.ofNullable(targetScreenClass); }
    public Map<String, ScreenSizeConfig> getScreenSizes() { return screenSizes; }
    public List<EnabledCondition> getEnabledConditions() { return enabledConditions; }

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
        private final String customAction;
        private final boolean closeScreenFirst;

        private ScreenOpenAction(ActionType type, String keyBinding, ResourceLocation itemToUse, String customAction, boolean closeScreenFirst) {
            this.type = type;
            this.keyBinding = keyBinding;
            this.itemToUse = itemToUse;
            this.customAction = customAction;
            this.closeScreenFirst = closeScreenFirst;
        }

        public static ScreenOpenAction keyPress(String keyBinding, boolean closeScreenFirst) {
            return new ScreenOpenAction(ActionType.KEY_PRESS, keyBinding, null, null, closeScreenFirst);
        }

        public static ScreenOpenAction rightClickItem(ResourceLocation itemToUse) {
            return new ScreenOpenAction(ActionType.RIGHT_CLICK_ITEM, null, itemToUse, null, false);
        }

        public static ScreenOpenAction custom(String customAction) {
            return new ScreenOpenAction(ActionType.CUSTOM, null, null, customAction, false);
        }

        public static ScreenOpenAction openScreen(String screenClassName) {
            return new ScreenOpenAction(ActionType.OPEN_SCREEN, null, null, screenClassName, false);
        }

        public static ScreenOpenAction apiCall(String callName) {
            return new ScreenOpenAction(ActionType.API_CALL, null, null, callName, false);
        }

        public Optional<String> getApiCallName() { return Optional.ofNullable(customAction); }

        public ActionType getType() { return type; }
        public boolean isCloseScreenFirst() { return closeScreenFirst; }
        public Optional<String> getKeyBinding() { return Optional.ofNullable(keyBinding); }
        public Optional<ResourceLocation> getItemToUse() { return Optional.ofNullable(itemToUse); }
        public Optional<String> getCustomAction() { return Optional.ofNullable(customAction); }
        public Optional<String> getScreenClassName() { return Optional.ofNullable(customAction); }

        public enum ActionType {
            KEY_PRESS,
            RIGHT_CLICK_ITEM,
            CUSTOM,
            OPEN_SCREEN,
            API_CALL
        }
    }

    public static class EnabledCondition {
        public enum ConditionType {
            ITEM_IN_INVENTORY,
            ITEM_IN_HOTBAR,
            ITEM_IN_CURIO
        }

        private final ConditionType type;
        private final String itemPattern;
        private final String curioSlot;

        public EnabledCondition(ConditionType type, String itemPattern, String curioSlot) {
            this.type = type;
            this.itemPattern = itemPattern;
            this.curioSlot = curioSlot;
        }

        public ConditionType getType() { return type; }
        public String getItemPattern() { return itemPattern; }
        public Optional<String> getCurioSlot() { return Optional.ofNullable(curioSlot); }

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

        public ScreenSizeConfig(int width, int height, int priority) {
            this.width = width;
            this.height = height;
            this.priority = priority;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getPriority() { return priority; }
    }
}
