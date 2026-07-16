package sfiomn.legendarytabs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.server.ServerLifecycleHooks;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.api.tabs_menu.TabData;
import sfiomn.legendarytabs.network.LegendaryTabsNetwork;
import sfiomn.legendarytabs.network.SyncTabsPacket;

import java.util.HashMap;
import java.util.Map;

public class TabDataLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static TabDataLoader INSTANCE;
    
    private final Map<String, TabData> loadedTabs = new HashMap<>();
    private Map<ResourceLocation, String> rawJson = new HashMap<>();

    public TabDataLoader() {
        super(GSON, "tabs");
        INSTANCE = this;
        LegendaryTabs.LOGGER.info("TabDataLoader constructor called");
    }

    public static TabDataLoader getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> preparations, ResourceManager resourceManager, ProfilerFiller profiler) {
        LegendaryTabs.LOGGER.info("TabDataLoader.apply() called with {} preparations", preparations.size());
        loadedTabs.clear();
        rawJson = new HashMap<>(preparations.size());

        for (Map.Entry<ResourceLocation, JsonElement> entry : preparations.entrySet()) {
            ResourceLocation location = entry.getKey();
            JsonObject json = entry.getValue().getAsJsonObject();
            rawJson.put(location, GSON.toJson(entry.getValue()));

            try {
                TabData tabData = parseTabData(location, json);
                loadedTabs.put(tabData.getId(), tabData);
                LegendaryTabs.LOGGER.info("Loaded tab data: {}", tabData.getId());
            } catch (Exception e) {
                LegendaryTabs.LOGGER.error("Failed to parse tab data from {}", location, e);
            }
        }

        LegendaryTabs.LOGGER.info("Loaded {} tab configurations", loadedTabs.size());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !rawJson.isEmpty()) {
            LegendaryTabsNetwork.syncTabsToAll(new SyncTabsPacket(rawJson));
        }
    }

    public void loadFromStrings(Map<ResourceLocation, String> tabsJson) {
        loadedTabs.clear();
        rawJson = new HashMap<>(tabsJson);
        for (Map.Entry<ResourceLocation, String> entry : tabsJson.entrySet()) {
            try {
                JsonObject json = GSON.fromJson(entry.getValue(), JsonObject.class);
                TabData tabData = parseTabData(entry.getKey(), json);
                loadedTabs.put(tabData.getId(), tabData);
                LegendaryTabs.LOGGER.info("Loaded synced tab data: {}", tabData.getId());
            } catch (Exception e) {
                LegendaryTabs.LOGGER.error("Failed to parse synced tab data from {}", entry.getKey(), e);
            }
        }
        LegendaryTabs.LOGGER.info("Loaded {} synced tab configurations", loadedTabs.size());
    }

    public Map<ResourceLocation, String> getRawJson() {
        return new HashMap<>(rawJson);
    }

    public TabData parseTabData(ResourceLocation location, JsonObject json) {
        String id = json.get("id").getAsString();
        boolean enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : true;
        
        // Parse icon data
        JsonObject iconJson = json.getAsJsonObject("icon");
        TabData.IconData iconData = parseIconData(iconJson);
        
        // Parse screen open action
        JsonObject actionJson = json.getAsJsonObject("screen_open_action");
        TabData.ScreenOpenAction screenOpenAction = parseScreenOpenAction(actionJson);
        
        // Parse tooltip
        String tooltipKey = json.get("tooltip").getAsString();
        
        // Parse required mods
        var requiredMods = new java.util.ArrayList<String>();
        if (json.has("required_mods")) {
            json.getAsJsonArray("required_mods").forEach(element -> 
                requiredMods.add(element.getAsString()));
        }
        
        // Parse screen patterns
        var screenPatterns = new java.util.ArrayList<String>();
        if (json.has("screen_patterns")) {
            json.getAsJsonArray("screen_patterns").forEach(element -> 
                screenPatterns.add(element.getAsString()));
        }
        
        // Parse target screen class (optional)
        String targetScreenClass = null;
        if (json.has("target_screen_class")) {
            targetScreenClass = json.get("target_screen_class").getAsString();
        }
        
        // Parse screen sizes (optional)
        var screenSizes = new java.util.HashMap<String, TabData.ScreenSizeConfig>();
        if (json.has("screen_sizes")) {
            JsonObject sizesJson = json.getAsJsonObject("screen_sizes");
            for (String screenClass : sizesJson.keySet()) {
                JsonObject sizeConfig = sizesJson.getAsJsonObject(screenClass);
                int width = sizeConfig.has("width") ? sizeConfig.get("width").getAsInt() : 176;
                int height = sizeConfig.has("height") ? sizeConfig.get("height").getAsInt() : 166;
                int priority = sizeConfig.has("priority") ? sizeConfig.get("priority").getAsInt() : 60;
                screenSizes.put(screenClass, new TabData.ScreenSizeConfig(width, height, priority));
            }
        }
        
        // Parse enabled_conditions (optional)
        var enabledConditions = new java.util.ArrayList<TabData.EnabledCondition>();
        if (json.has("enabled_conditions")) {
            json.getAsJsonArray("enabled_conditions").forEach(element -> {
                JsonObject condJson = element.getAsJsonObject();
                enabledConditions.add(parseEnabledCondition(condJson));
            });
        }

        return new TabData(id, enabled, iconData, screenOpenAction, tooltipKey, screenPatterns, requiredMods, targetScreenClass, screenSizes, enabledConditions);
    }
    
    private TabData.IconData parseIconData(JsonObject iconJson) {

        String type = iconJson.get("type").getAsString();
        
        switch (type.toLowerCase()) {
            case "texture" -> {
                ResourceLocation texture = new ResourceLocation(iconJson.get("texture").getAsString());
                int u = iconJson.has("u") ? iconJson.get("u").getAsInt() : 0;
                int v = iconJson.has("v") ? iconJson.get("v").getAsInt() : 0;
                return TabData.IconData.texture(texture, u, v);
            }
            case "item" -> {
                ResourceLocation itemId = new ResourceLocation(iconJson.get("item").getAsString());
                return TabData.IconData.item(itemId);
            }
            default -> throw new IllegalArgumentException("Unknown icon type: " + type);
        }
    }
    
    private TabData.ScreenOpenAction parseScreenOpenAction(JsonObject actionJson) {
        String type = actionJson.get("type").getAsString();
        
        switch (type.toLowerCase()) {
            case "key_press" -> {
                String keyBinding = actionJson.get("key_binding").getAsString();
                boolean closeScreenFirst = actionJson.has("close_screen_first") && actionJson.get("close_screen_first").getAsBoolean();
                return TabData.ScreenOpenAction.keyPress(keyBinding, closeScreenFirst);
            }
            case "right_click_item" -> {
                ResourceLocation itemId = new ResourceLocation(actionJson.get("item").getAsString());
                return TabData.ScreenOpenAction.rightClickItem(itemId);
            }
            case "custom" -> {
                String customAction = actionJson.get("action").getAsString();
                return TabData.ScreenOpenAction.custom(customAction);
            }
            case "open_screen" -> {
                String screenClass = actionJson.get("screen_class").getAsString();
                return TabData.ScreenOpenAction.openScreen(screenClass);
            }
            case "api_call" -> {
                String callName = actionJson.get("call").getAsString();
                return TabData.ScreenOpenAction.apiCall(callName);
            }
            case "reflection" -> {
                String className = actionJson.get("class_name").getAsString();
                String methodName = actionJson.get("method_name").getAsString();
                boolean isStatic = actionJson.has("static") && actionJson.get("static").getAsBoolean();
                boolean closeScreenFirst = actionJson.has("close_screen_first") && actionJson.get("close_screen_first").getAsBoolean();
                return TabData.ScreenOpenAction.reflection(className, methodName, isStatic, closeScreenFirst);
            }
            case "command" -> {
                String command = actionJson.get("command").getAsString();
                boolean closeScreenFirst = actionJson.has("close_screen_first") && actionJson.get("close_screen_first").getAsBoolean();
                return TabData.ScreenOpenAction.command(command, closeScreenFirst);
            }
            default -> throw new IllegalArgumentException("Unknown action type: " + type);
        }
    }
    
    private TabData.EnabledCondition parseEnabledCondition(JsonObject condJson) {
        String type = condJson.get("type").getAsString();
        String item = condJson.has("item") ? condJson.get("item").getAsString() : null;
        String curioSlot = condJson.has("curio_slot") ? condJson.get("curio_slot").getAsString() : null;
        TabData.EnabledCondition.ConditionType condType = switch (type.toLowerCase()) {
            case "item_in_inventory" -> TabData.EnabledCondition.ConditionType.ITEM_IN_INVENTORY;
            case "item_in_hotbar" -> TabData.EnabledCondition.ConditionType.ITEM_IN_HOTBAR;
            case "item_in_curio" -> TabData.EnabledCondition.ConditionType.ITEM_IN_CURIO;
            case "or" -> TabData.EnabledCondition.ConditionType.OR;
            default -> throw new IllegalArgumentException("Unknown condition type: " + type);
        };

        if (condType == TabData.EnabledCondition.ConditionType.OR) {
            var subConditions = new java.util.ArrayList<TabData.EnabledCondition>();
            if (condJson.has("conditions")) {
                for (JsonElement element : condJson.getAsJsonArray("conditions")) {
                    subConditions.add(parseEnabledCondition(element.getAsJsonObject()));
                }
            }
            return new TabData.EnabledCondition(condType, null, null, subConditions);
        }

        return new TabData.EnabledCondition(condType, item, curioSlot);
    }

    public Map<String, TabData> getLoadedTabs() {
        return new HashMap<>(loadedTabs);
    }
    
    public TabData getTabData(String id) {
        return loadedTabs.get(id);
    }
    
    public void manuallyLoadBuiltInTabs() {
        LegendaryTabs.LOGGER.info("Manually loading built-in tab data as fallback");
        
        try {
            // Get all resources in the tabs directory
            var tabsDir = TabDataLoader.class.getResource("/data/legendarytabs/tabs/");
            
            if (tabsDir != null) {
                LegendaryTabs.LOGGER.info("Found tabs directory, scanning for JSON files");
                
                // Use ClassLoader to list all files in the directory
                var uri = tabsDir.toURI();
                java.nio.file.Path tabsPath;
                
                if (uri.getScheme().equals("jar")) {
                    // Running from JAR - need to use FileSystem
                    try (java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(uri, java.util.Collections.emptyMap())) {
                        tabsPath = fs.getPath("/data/legendarytabs/tabs/");
                        loadTabsFromDirectory(tabsPath);
                    }
                } else {
                    // Running from IDE/filesystem
                    tabsPath = java.nio.file.Paths.get(uri);
                    loadTabsFromDirectory(tabsPath);
                }
            } else {
                LegendaryTabs.LOGGER.warn("Could not find tabs directory in resources");
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.error("Failed to manually load built-in tabs", e);
        }
    }
    
    private void loadTabsFromDirectory(java.nio.file.Path tabsPath) throws Exception {
        try (var stream = java.nio.file.Files.list(tabsPath)) {
            stream.filter(path -> path.toString().endsWith(".json"))
                  .forEach(path -> {
                      try {
                          String fileName = path.getFileName().toString();
                          String tabId = fileName.substring(0, fileName.length() - 5); // Remove .json
                          
                          LegendaryTabs.LOGGER.info("Found tab file: {}", fileName);
                          
                          String jsonContent = java.nio.file.Files.readString(path);
                          JsonObject json = GSON.fromJson(jsonContent, JsonObject.class);
                          
                          TabData tabData = parseTabData(new ResourceLocation("legendarytabs", tabId), json);
                          loadedTabs.put(tabData.getId(), tabData);
                          
                          LegendaryTabs.LOGGER.info("Manually loaded built-in tab: {}", tabData.getId());
                      } catch (Exception e) {
                          LegendaryTabs.LOGGER.error("Failed to load tab from file: {}", path, e);
                      }
                  });
        }
    }
}
