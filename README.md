# Legendary Tabs

A Forge mod for Minecraft 1.20.1 that shows a shared tab bar of buttons above container-style
screens, letting a player jump between the inventory, mod skill screens, backpacks, quest books,
maps, etc. without closing and reopening each one separately.

Almost everything about a tab is defined in JSON, not hardcoded in Java. This document describes
that JSON schema.

## Where tabs live

Each tab is one JSON file under `data/legendarytabs/tabs/*.json`, loaded as a datapack resource
(so it reloads on `/reload` and syncs from server to client on join). The filename doesn't matter
to the loader - only the `id` field inside does - but by convention it matches the tab's `id`.

```json
{
  "id": "example_tab",
  "enabled": true,
  "icon": { "type": "texture", "texture": "legendarytabs:textures/gui/example.png" },
  "screen_open_action": { "type": "key_press", "key_binding": "key.examplemod.open" },
  "tooltip": "tooltip.legendarytabs.tab.example_tab.description",
  "required_mods": ["examplemod"]
}
```

`id`, `icon`, `screen_open_action`, and `tooltip` are required. Everything else is optional.

## Top-level fields

- `id` (string, required) - unique identifier for this tab.
- `enabled` (boolean, default `true`) - a hard on/off switch, independent of `required_mods`
  or `enabled_conditions` below.
- `icon` - see [Icon](#icon).
- `screen_open_action` - see [Screen open action](#screen-open-action).
- `tooltip` (string, required) - a translation key, shown when hovering the tab's button.
- `required_mods` (array of strings, default `[]`) - mod ids that must all be loaded for this
  tab to ever appear. Checked with Forge's `ModList`.
- `target_screen_class` (string, optional) - the fully-qualified class name of the screen this
  tab opens. Used to grey out the tab's own button while already on that screen
  (`isCurrentlyUsed`), and, if `screen_sizes` doesn't already cover it, seeds that screen for the
  tab bar with a default 176x166 size.
- `screen_sizes` (object, optional) - see [Screen sizes](#screen-sizes). This is where a tab
  actually registers its own screen with the tab bar system.
- `enabled_conditions` (array, default `[]`) - see [Enabled conditions](#enabled-conditions).
  Runtime checks (on top of `enabled` and `required_mods`) re-evaluated every time the tab bar
  is built.
- `show_tabs_on_screen` (boolean, default `true`) - when `false`, the screen(s) this tab
  registers via `screen_sizes`/`target_screen_class` never get a tab bar at all - not even this
  tab's own button. For screens with dense custom UIs (a full skill tree, a jobs menu) where the
  tab bar would just overlap content. See [pufferfish_skills.json](src/main/resources/data/legendarytabs/tabs/pufferfish_skills.json)
  for an example.

## How tabs find their way onto screens

There's no per-tab screen allow-list to configure. The default behaviour is unconditional: every
screen that any tab registers (via `screen_sizes` or `target_screen_class`) gets a tab bar
showing every other currently-enabled tab too, in a fixed order (by `priority`, then
alphabetically by `id`). `show_tabs_on_screen: false` is the only way to opt a screen out of that.

## Icon

```json
"icon": {
  "type": "texture",
  "texture": "legendarytabs:textures/gui/example.png",
  "u": 0,
  "v": 0
}
```

- `type: "texture"` - an 18x18 region of a texture file. `u`/`v` (both default `0`) are the
  top-left pixel of that region.
- `type: "item"` - renders an actual item as the icon instead of a texture.
  ```json
  "icon": { "type": "item", "item": "minecraft:diamond_sword" }
  ```

## Screen open action

What happens when the tab's button is clicked.

```json
"screen_open_action": {
  "type": "key_press",
  "key_binding": "key.examplemod.open_screen",
  "close_screen_first": false
}
```

`close_screen_first` (boolean, default `false`) is accepted by `key_press`, `reflection`, and
`command` - it closes the current screen and waits a tick before firing, which some mods need to
respond to a simulated key press or command at all.

- `key_press` - simulates a real key press for an existing `KeyMapping`.
  ```json
  { "type": "key_press", "key_binding": "key.examplemod.open_screen" }
  ```
- `right_click_item` - simulates right-clicking a held/worn item.
  ```json
  { "type": "right_click_item", "item": "examplemod:example_item" }
  ```
- `open_screen` - directly constructs and opens a `Screen` by class name.
  ```json
  {
    "type": "open_screen",
    "screen_class": "net.minecraft.client.gui.screens.advancements.AdvancementsScreen",
    "constructor_args": ["client_advancements"]
  }
  ```
  `constructor_args` (array of strings, default `[]`) is only needed if the screen doesn't have a
  no-arg constructor. Each entry names an engine value to pass, in order, to whichever
  constructor on the class matches the argument count. Currently defined: `player`, `minecraft`,
  `client_advancements`. Omit `constructor_args` entirely for a plain `new Screen()`.
- `reflection` - calls a method (static or instance) on a class via reflection.
  ```json
  {
    "type": "reflection",
    "class_name": "examplemod.client.ui.UIManager",
    "method_name": "openScreen",
    "static": false
  }
  ```
  For instance calls, the instance is resolved via a public static `INSTANCE` field on the class
  if one exists, otherwise via a no-arg constructor.
- `command` - sends a chat command to the server.
  ```json
  { "type": "command", "command": "examplemod open" }
  ```

## Screen sizes

Keyed by the fully-qualified class name of the screen. This is what actually registers a screen
with the shared tab bar - `target_screen_class` alone only affects the "am I currently on this
screen" check.

```json
"screen_sizes": {
  "examplemod.client.gui.ExampleScreen": {
    "width": 176,
    "height": 166,
    "priority": 50,
    "button_skin": "legendarytabs:textures/gui/buttons_example.png",
    "icon_offset_x": 0,
    "icon_offset_y": -2
  }
}
```

- `width`/`height` (int, default `176`/`166`) - used to center the tab bar above the screen:
  `(realScreenWidth - width) / 2` and `(realScreenHeight - height) / 2`. Should match the actual
  on-screen size of the dialog/panel this screen draws, not the window size, unless the screen is
  genuinely fullscreen.
- `priority` (int, default `60` here, `50` elsewhere - see note below) - lower sorts first
  (left-most) in the tab bar. This is the *only* priority that matters; see the note below about
  why per-screen overrides are ignored for ordering.
- `button_skin` (string, optional) - replaces the shared button background sheet
  (`legendarytabs:textures/gui/buttons.png`) for **every tab's button while this screen is open**,
  not just this tab's own. The tab bar is shared UI chrome, so its skin is a property of the
  screen being viewed. Must be a 64x64 sheet laid out like the default: normal state at (0,0),
  hover/pressed state at (27,0), each 26x22.
- `icon_offset_x`/`icon_offset_y` (int, default `0`) - added to the default 4px icon inset, for
  **every tab's icon while this screen is open**. Useful when a custom `button_skin`'s icon slot
  doesn't sit in the same spot as the default sheet's.
- `variables`/`width_formula`/`height_formula` - see [Formulas](#formulas), for sizes that depend
  on runtime state (an item's NBT, a mod's live config) rather than a fixed number.

Note on `priority`: a tab's position relative to its peers is resolved once from its *own*
`screen_sizes` entry (the first one, if it has more than one) and reused for every screen it
appears on, specifically so the same tab doesn't end up in a different spot in the bar depending
on which screen you're looking at. Setting different `priority` values per screen for the same
tab won't do anything useful.

## Formulas

`width`/`height` can instead be computed at render time from named variables:

```json
"screen_sizes": {
  "examplemod.client.gui.ExampleScreen": {
    "priority": 50,
    "variables": {
      "rows": { "source": "builtin", "id": "backpacked_rows" },
      "wide": { "source": "formula", "expr": "rows > 4" }
    },
    "width_formula": "wide ? 220 : 176",
    "height_formula": "114 + rows * 18"
  }
}
```

Each variable has a `source`:

- `constant` - a fixed number. `{ "source": "constant", "value": 9 }`
- `builtin` - a named engine value (see [BuiltinTabVariables.java](src/main/java/sfiomn/legendarytabs/api/tabs_menu/BuiltinTabVariables.java)
  for the current list: `backpacked_columns`, `backpacked_rows`, `backpacked_visible`,
  `travelers_tanks_visible`, `diet_group_count`). `{ "source": "builtin", "id": "diet_group_count" }`
- `item_nbt` - reads a numeric/boolean NBT path off an item connected to the player.
  ```json
  {
    "source": "item_nbt",
    "locator": "inventory:examplemod:backpack",
    "path": "Storage.SlotCount",
    "type": "int",
    "default": 27
  }
  ```
  `locator` is one of `main_hand`, `off_hand`, `inventory:<item_id_or_namespace:*>`,
  `curio:<item_id_or_namespace:*>`, or a mod-specific locator wired up in
  [ItemNbtResolver.java](src/main/java/sfiomn/legendarytabs/api/tabs_menu/ItemNbtResolver.java).
  `path` is dot-separated for nested compound tags. `type` is `int`, `double`, or `boolean`.
  `default` is used when the item or NBT path isn't found.
- `formula` - a nested expression, evaluated against variables already resolved above it
  (`variables` is order-sensitive - a `formula` can only reference variables declared earlier in
  the object).

Formula/expression syntax supports `+ - * / %`, comparisons (`< <= > >= == !=`), boolean logic
(`&& || !`), a ternary (`cond ? a : b`), parentheses, and the functions `min(a,b)`, `max(a,b)`,
`floor(x)`, `ceil(x)`, `round(x)`, `abs(x)`. Booleans are `1`/`0`; any nonzero value is truthy.

## Enabled conditions

Extra runtime checks, evaluated in addition to `enabled` and `required_mods`, every time the tab
bar is built (so these can change without a reload - e.g. picking an item up or putting it down).

```json
"enabled_conditions": [
  {
    "type": "or",
    "conditions": [
      { "type": "item_in_inventory", "item": "examplemod:example_item" },
      { "type": "item_in_curio", "item": "examplemod:*", "curio_slot": "back" }
    ]
  }
]
```

All top-level entries must pass (implicit AND). Types:

- `item_in_inventory` - `item` (an id, or `namespace:*` for any item in that namespace) is
  somewhere in the player's inventory.
- `item_in_hotbar` - same, but only the hotbar (slots 0-8).
- `item_in_curio` - same, but in a Curios slot. `curio_slot` (optional) restricts to one slot id.
- `or` - passes if any of its nested `conditions` passes.

## Related config

A few things are deliberately *not* per-tab JSON, but a player-facing client config instead (see
[Config.java](src/main/java/sfiomn/legendarytabs/config/Config.java),
`config/legendarytabs/legendarytabs-client.toml`):

- `Tabs Menu Display X/Y Offset` - a global pixel nudge applied to the whole tab bar.
- `Include Opened Screen Tab` - whether the inventory tab also appears on `InventoryScreen`
  itself.
- `Inventory Tab Enabled` - whether the inventory tab appears at all.
- `Verbose Logging Enabled` - detailed tab loading/sizing/input-simulation logs. Off by default;
  turn on when troubleshooting a tab that isn't registering or rendering as expected.
