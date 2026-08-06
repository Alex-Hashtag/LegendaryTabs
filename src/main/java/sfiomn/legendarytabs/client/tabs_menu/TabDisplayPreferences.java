package sfiomn.legendarytabs.client.tabs_menu;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds each tab's client-local display override (see TabDisplayOverride), keyed by
 * TabBase.getId(). Purely an in-memory structure for now - no load/save yet, so nothing set
 * here survives a restart. Persistence will likely need its own small JSON file in the config
 * directory (config/legendarytabs/), since this is dynamic per-tab-id data rather than the
 * fixed set of keys ForgeConfigSpec is built around.
 */
public class TabDisplayPreferences {

    private static final Map<String, TabDisplayOverride> OVERRIDES = new HashMap<>();

    private TabDisplayPreferences() {
    }

    public static Optional<TabDisplayOverride> get(String tabId) {
        return Optional.ofNullable(OVERRIDES.get(tabId));
    }

    public static void set(String tabId, TabDisplayOverride override) {
        OVERRIDES.put(tabId, override);
    }

    public static void clear(String tabId) {
        OVERRIDES.remove(tabId);
    }

    public static Map<String, TabDisplayOverride> getAll() {
        return Map.copyOf(OVERRIDES);
    }
}
