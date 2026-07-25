package sfiomn.legendarytabs.api.tabs_menu;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import sfiomn.legendarytabs.LegendaryTabs;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Simulates a keybind activation in a way every registration style can observe.
 *
 * Three mutually exclusive routes are used, never two at once (firing both the
 * real pipeline and a manual InputEvent is what causes mods to execute twice):
 *
 *   REAL      - a physical key exists, has no KeyModifier, and no screen is open.
 *               We drive Minecraft's own KeyboardHandler/MouseHandler, which is
 *               byte-for-byte what a hardware press does: KeyMapping.click/set,
 *               Forge's InputEvent.Key/MouseButton, screen routing, everything.
 *
 *   SYNTHETIC - a physical key exists but the binding carries a KeyModifier
 *               (Forge polls real GLFW state for those, so the real pipeline
 *               would silently drop it), or a screen is still open (vanilla
 *               keyPress routes to the screen and skips KeyMapping entirely).
 *               We mutate the KeyMapping instance and post the input event by hand.
 *
 *   UNBOUND   - key is InputConstants.UNKNOWN (-1). Every unbound KeyMapping in
 *               the game shares that same -1, and matches() reads only the
 *               instance's own field, so posting keyCode -1 would match all of
 *               them at once - not just the one pressed. Instead we temporarily
 *               write a private, unique negative key value onto this specific
 *               mapping, post the event with that, then restore -1 afterward.
 *               This covers mods that only listen for InputEvent.Key rather than
 *               polling consumeClick()/isDown() in a tick handler, without
 *               triggering every other unbound mapping in the process.
 */
public final class KeyPressSimulator {

    /** Ticks the press is held before release. 2 guarantees a full tick of visibility. */
    /**
     * Ticks the press is held before giving up if nothing ever opens. Handoff is
     * checked every tick and releases immediately once detected, so this value only
     * matters for the "nothing happened" fallback path - it doesn't add latency to
     * the common case. Kept generous since a deferred Minecraft.execute() task can
     * land a tick or more after the press.
     */
    private static final int HOLD_TICKS = 4;

    private static final Map<String, KeyMapping> LOOKUP_CACHE = new HashMap<>();

    private static Field allField;
    private static Field clickCountField;
    private static Field conflictContextField;
    private static Field keyModifierField;
    private static Field keyField;

    /**
     * Source of private, per-press key values used only for UNBOUND mappings.
     * Always negative and always below InputConstants.UNKNOWN's value (-1), so it
     * can never collide with a real GLFW code (always >= 0) or with the shared
     * "unbound" sentinel (-1) that every other unbound KeyMapping still carries.
     */
    private static final java.util.concurrent.atomic.AtomicInteger TEMP_KEY_SOURCE =
            new java.util.concurrent.atomic.AtomicInteger(-10_000);

    private KeyPressSimulator() {}

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    public static void press(String keyBindingName, boolean closeScreenFirst) {
        KeyMapping mapping = resolve(keyBindingName);
        if (mapping == null) {
            LegendaryTabs.LOGGER.warn("KeyMapping not found: {}", keyBindingName);
            return;
        }
        Scheduler.enqueue(mapping, closeScreenFirst);
    }

    // ------------------------------------------------------------------
    // Resolution - tolerate several naming conventions
    // ------------------------------------------------------------------

    private static KeyMapping resolve(String name) {
        if (name == null || name.isEmpty()) return null;
        if (LOOKUP_CACHE.containsKey(name)) return LOOKUP_CACHE.get(name);

        Map<String, KeyMapping> all = allMappings();
        KeyMapping found = null;

        if (all != null) {
            // 1. exact description / translation key, e.g. "key.jei.showRecipe"
            found = all.get(name);

            // 2. case-insensitive description match
            if (found == null) {
                for (Map.Entry<String, KeyMapping> e : all.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) { found = e.getValue(); break; }
                }
            }

            // 3. suffix match, e.g. "showRecipe" -> "key.jei.showRecipe"
            if (found == null) {
                String needle = "." + name.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, KeyMapping> e : all.entrySet()) {
                    if (e.getKey().toLowerCase(Locale.ROOT).endsWith(needle)) { found = e.getValue(); break; }
                }
            }

            // 4. rendered display name, e.g. "Show Recipe"
            if (found == null) {
                for (KeyMapping km : all.values()) {
                    try {
                        String display = net.minecraft.network.chat.Component
                                .translatable(km.getName()).getString();
                        if (display.equalsIgnoreCase(name)) { found = km; break; }
                    } catch (Exception ignored) {}
                }
            }
        }

        // 5. fall back to the options array, which catches mappings registered oddly
        if (found == null && Minecraft.getInstance().options != null) {
            for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
                if (km.getName().equalsIgnoreCase(name)) { found = km; break; }
            }
        }

        LOOKUP_CACHE.put(name, found);
        if (found != null) {
            LegendaryTabs.LOGGER.info("Resolved keybind '{}' -> {} (key={})",
                    name, found.getName(), found.getKey().getName());
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, KeyMapping> allMappings() {
        try {
            if (allField == null) {
                allField = findField("ALL", f -> {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                            || !Map.class.isAssignableFrom(f.getType())) return false;
                    // Distinguish from KeyMapping's other static Map field (CATEGORY_SORT_ORDER,
                    // Map<String, Integer>) by checking the value type parameter is KeyMapping.
                    java.lang.reflect.Type generic = f.getGenericType();
                    if (!(generic instanceof java.lang.reflect.ParameterizedType pt)) return false;
                    java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                    return args.length == 2 && args[1] == KeyMapping.class;
                });
            }
            if (allField == null) return null;
            return (Map<String, KeyMapping>) allField.get(null);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Cannot access KeyMapping.ALL", e);
            return null;
        }
    }

    /**
     * Runtime field names for vanilla classes vary by environment: the dev workspace
     * patches Minecraft to use Mojang's official names, but a production install loads
     * the SRG (obfuscated intermediate) named jar directly - so a hardcoded official
     * name like "clickCount" only resolves in dev. Try the official name first (works
     * in dev, and costs nothing extra if it happens to also work in prod), then fall
     * back to finding the one field on KeyMapping matching a distinguishing shape -
     * this requires no mapping data and works identically in every environment.
     */
    private static Field findField(String mojangName, java.util.function.Predicate<Field> shape) {
        try {
            Field f = KeyMapping.class.getDeclaredField(mojangName);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
            // fall through to shape-based search
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Unexpected error looking up KeyMapping.{}: {}", mojangName, e.getMessage());
        }
        for (Field f : KeyMapping.class.getDeclaredFields()) {
            if (shape.test(f)) {
                f.setAccessible(true);
                LegendaryTabs.LOGGER.debug("Resolved KeyMapping field '{}' by shape as '{}'", mojangName, f.getName());
                return f;
            }
        }
        LegendaryTabs.LOGGER.debug("Could not find KeyMapping field '{}' by name or shape", mojangName);
        return null;
    }

    // ------------------------------------------------------------------
    // Press / release
    // ------------------------------------------------------------------

    private enum Route { REAL, SYNTHETIC, UNBOUND }

    private static Route routeFor(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key == null || key == InputConstants.UNKNOWN) return Route.UNBOUND;

        // MouseHandler.onPress is private in 1.20.1, so the real pipeline is
        // unreachable for mouse bindings without an access transformer.
        // SYNTHETIC covers them fully via InputEvent.MouseButton + instance state.
        if (key.getType() == InputConstants.Type.MOUSE) return Route.SYNTHETIC;

        KeyModifier modifier = mapping.getKeyModifier();
        if (modifier != null && modifier != KeyModifier.NONE) return Route.SYNTHETIC;

        if (Minecraft.getInstance().screen != null) return Route.SYNTHETIC;

        return Route.REAL;
    }

    /** Saved binding state so SYNTHETIC/UNBOUND presses can be undone exactly. */
    private static final class PressState {
        Route route;
        InputConstants.Key key;
        IKeyConflictContext originalContext;
        KeyModifier originalModifier;
        InputConstants.Key originalKey;
        boolean contextOverridden;
        boolean modifierOverridden;
        boolean keyOverridden;
    }

    private static PressState doPress(KeyMapping mapping) {
        PressState state = new PressState();
        state.route = routeFor(mapping);
        state.key = mapping.getKey();

        switch (state.route) {
            case REAL -> dispatchReal(state.key, true);
            case SYNTHETIC, UNBOUND -> {
                // Forge's isDown() is gated on the conflict context being active and the
                // modifier being physically held. Neutralise both for the duration of the
                // press, writing the fields directly so the global KeyMappingLookup index
                // is never rebuilt or corrupted.
                state.originalContext = mapping.getKeyConflictContext();
                if (state.originalContext != null && !state.originalContext.isActive()) {
                    state.contextOverridden = writeConflictContext(mapping, KeyConflictContext.UNIVERSAL);
                }
                state.originalModifier = mapping.getKeyModifier();
                if (state.originalModifier != null && state.originalModifier != KeyModifier.NONE) {
                    state.modifierOverridden = writeKeyModifier(mapping, KeyModifier.NONE);
                }

                // consumeClick() reads clickCount, isDown() reads isDown. They are
                // independent - a press that sets only one is invisible to half of all mods.
                bumpClickCount(mapping);
                mapping.setDown(true);

                // For UNBOUND, the shared -1 sentinel is ambiguous: matches() reads only
                // this mapping's own key field, and EVERY unbound KeyMapping in the game
                // also carries -1, so an event posted with -1 matches all of them at once
                // - whichever mod's listener runs first wins, not necessarily the one the
                // player pressed. Give this mapping a private key value for the duration
                // of the press so the event can only ever match it.
                if (state.route == Route.UNBOUND) {
                    InputConstants.Key synthetic =
                            InputConstants.Type.KEYSYM.getOrCreate(TEMP_KEY_SOURCE.decrementAndGet());
                    state.originalKey = mapping.getKey();
                    state.keyOverridden = writeKeyField(mapping, synthetic);
                    if (state.keyOverridden) state.key = synthetic;
                }

                postInputEvent(state.key, mapping.getKeyModifier(), true);
            }
        }

        LegendaryTabs.LOGGER.info("Simulated press [{}] for '{}' (key={})",
                state.route, mapping.getName(),
                state.key == null ? "none" : state.key.getName());
        return state;
    }

    /**
     * @param quiet true if a screen already took over this press (ScreenCloak.handedOff).
     *              In that case we only clear our own overrides/state - we do NOT dispatch
     *              a real key-up or post a release InputEvent. That release would land on
     *              the screen that just opened, and any screen using the common "check my
     *              own open-keybind again to close" pattern (the same pattern vanilla's own
     *              inventory screen uses) would see it as a second toggle and immediately
     *              close itself - which is exactly the "opens then instantly closes" bug.
     *              A real physical tap never produces this, because its release lands on
     *              whatever had focus at release time, not on a screen that only just opened.
     */
    private static void doRelease(KeyMapping mapping, PressState state, boolean quiet) {
        switch (state.route) {
            case REAL -> {
                if (!quiet) dispatchReal(state.key, false);
            }
            case SYNTHETIC, UNBOUND -> {
                mapping.setDown(false);
                if (!quiet) postInputEvent(state.key, KeyModifier.NONE, false);
                if (state.keyOverridden) writeKeyField(mapping, state.originalKey);
                if (state.modifierOverridden) writeKeyModifier(mapping, state.originalModifier);
                if (state.contextOverridden) writeConflictContext(mapping, state.originalContext);
            }
        }
        LegendaryTabs.LOGGER.debug("Released simulated press for '{}' (quiet={})", mapping.getName(), quiet);
    }

    // ------------------------------------------------------------------
    // Route: REAL - drive Minecraft's own handlers
    // ------------------------------------------------------------------

    private static void dispatchReal(InputConstants.Key key, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        int action = down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;

        try {
            int scancode = key.getType() == InputConstants.Type.SCANCODE ? key.getValue() : 0;
            int keyCode = key.getType() == InputConstants.Type.SCANCODE
                    ? InputConstants.UNKNOWN.getValue() : key.getValue();
            mc.keyboardHandler.keyPress(window, keyCode, scancode, action, 0);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Real input dispatch failed for {}", key.getName(), e);
        }
    }

    // ------------------------------------------------------------------
    // Route: SYNTHETIC - hand-post the input event
    // ------------------------------------------------------------------

    private static void postInputEvent(InputConstants.Key key, KeyModifier modifier, boolean down) {
        int action = down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
        int mods = glfwModsFor(modifier);
        try {
            if (key.getType() == InputConstants.Type.MOUSE) {
                InputEvent.MouseButton.Pre pre =
                        new InputEvent.MouseButton.Pre(key.getValue(), action, mods);
                MinecraftForge.EVENT_BUS.post(pre);
                if (!pre.isCanceled()) {
                    MinecraftForge.EVENT_BUS.post(
                            new InputEvent.MouseButton.Post(key.getValue(), action, mods));
                }
            } else {
                int scancode = key.getType() == InputConstants.Type.SCANCODE ? key.getValue() : 0;
                int keyCode = key.getType() == InputConstants.Type.SCANCODE
                        ? InputConstants.UNKNOWN.getValue() : key.getValue();
                MinecraftForge.EVENT_BUS.post(new InputEvent.Key(keyCode, scancode, action, mods));
            }
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Failed to post input event for {}: {}",
                    key.getName(), e.getMessage());
        }
    }

    private static int glfwModsFor(KeyModifier modifier) {
        if (modifier == null) return 0;
        return switch (modifier) {
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case ALT -> GLFW.GLFW_MOD_ALT;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Reflection helpers
    // ------------------------------------------------------------------

    private static void bumpClickCount(KeyMapping mapping) {
        try {
            if (clickCountField == null) {
                // The only non-static int field on KeyMapping - unique by type.
                clickCountField = findField("clickCount", f ->
                        f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers()));
            }
            if (clickCountField == null) return;
            clickCountField.setInt(mapping, clickCountField.getInt(mapping) + 1);
        } catch (Exception e) {
            LegendaryTabs.LOGGER.warn("Failed to bump clickCount for {}: {}",
                    mapping.getName(), e.getMessage());
        }
    }

    private static boolean writeConflictContext(KeyMapping mapping, IKeyConflictContext ctx) {
        try {
            if (conflictContextField == null) {
                // Forge's own field (not a vanilla/SRG name), but resolve defensively all the same.
                conflictContextField = findField("keyConflictContext", f -> f.getType() == IKeyConflictContext.class);
            }
            if (conflictContextField == null) return false;
            conflictContextField.set(mapping, ctx);
            return true;
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Could not override conflict context for {}: {}",
                    mapping.getName(), e.getMessage());
            return false;
        }
    }

    private static boolean writeKeyModifier(KeyMapping mapping, KeyModifier modifier) {
        try {
            if (keyModifierField == null) {
                // KeyMapping also has a "keyModifierDefault" field of the same type - exclude it.
                keyModifierField = findField("keyModifier", f ->
                        f.getType() == KeyModifier.class && !f.getName().toLowerCase(Locale.ROOT).contains("default"));
            }
            if (keyModifierField == null) return false;
            keyModifierField.set(mapping, modifier);
            return true;
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Could not override key modifier for {}: {}",
                    mapping.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Writes KeyMapping's mutable "key" field directly (Mojang mapping name), never
     * going through setKeyModifierAndCode/click's static KeyMappingLookup - so this
     * never touches the shared registry other bindings are indexed under. If the
     * field name ever changes, fall back to the one InputConstants.Key-typed field
     * that isn't final (the "key" field; "defaultKey" is final and must be skipped).
     */
    private static boolean writeKeyField(KeyMapping mapping, InputConstants.Key value) {
        try {
            if (keyField == null) {
                keyField = findField("key", f -> f.getType() == InputConstants.Key.class
                        && !java.lang.reflect.Modifier.isFinal(f.getModifiers()));
            }
            if (keyField == null) return false;
            keyField.set(mapping, value);
            return true;
        } catch (Exception e) {
            LegendaryTabs.LOGGER.debug("Could not override key field for {}: {}",
                    mapping.getName(), e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Seamless screen handoff
    // ------------------------------------------------------------------

    /**
     * Hides the current screen from game logic without actually closing it.
     *
     * Minecraft.setScreen(null) is what produced the visible seam: it fires
     * removed(), resumes sounds, and calls mouseHandler.grabMouse(), so the world
     * renders ungated for a frame or two and the cursor is re-captured. When a mod
     * then opens its own screen, releaseMouse() runs again - a full grab/release
     * cycle, which snaps the camera if the player is moving the mouse.
     *
     * Instead we write Minecraft.screen directly at tick START and restore it at
     * tick END. Minecraft.runTick() drains the whole tick loop before rendering,
     * so nothing is ever drawn while the screen is hidden. Meanwhile game logic
     * (KeyConflictContext.IN_GAME, mods checking mc.screen == null, and
     * KeyboardHandler's no-screen branch) sees an empty screen and behaves exactly
     * as it would in-world.
     *
     * If a mod opens its screen at any point during the hold window, we detect it
     * (checked every tick, not just once - some mods open a tick or more late via a
     * deferred Minecraft.execute() task) and hand off immediately: no grabMouse, no
     * flash, just a swap. We then finish closing the old screen ourselves, since
     * setScreen() skipped its removed() call.
     */
    private static final class ScreenCloak {
        private Screen hidden;
        private boolean active;
        private boolean handedOff;

        boolean handedOff() {
            return handedOff;
        }

        void hide() {
            if (handedOff || active) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return;
            hidden = mc.screen;
            mc.screen = null;
            active = true;
        }

        /**
         * Checked every tick while cloaked, not just once. A mod's screen-open can
         * legitimately land a tick or more after the press - e.g. via a deferred
         * Minecraft.execute() task, which runs at the boundary between ticks, or via
         * the mod's own separately-scheduled tick handler. A single early check (the
         * previous design) would miss that, conclude "nothing opened," put the old
         * screen back, and then still fire a release later - into a screen the mod
         * went on to open anyway. That stray release is what looked like an instant
         * open-then-close: if the mod's listener doesn't filter press vs. release,
         * the release read as "pressed again" and closed what it had just opened.
         */
        void checkForHandoff() {
            if (!active) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return; // still nothing yet - keep waiting

            active = false;
            handedOff = true;
            finishClose(hidden);
            hidden = null;
            LegendaryTabs.LOGGER.debug("Seamless handoff to {}", mc.screen.getClass().getName());
        }

        /** Nothing opened in the entire hold window, so put the original screen back for real. */
        void giveUpAndRestore() {
            if (handedOff || !active) return;
            active = false;
            Minecraft.getInstance().screen = hidden;
            hidden = null;
        }

        /**
         * setScreen() only calls removed() on a non-null previous screen, and it was
         * null while cloaked - so the old screen never got torn down. Do it here, and
         * release its container to the server if it had one. Deliberately avoids
         * LocalPlayer.closeContainer(), which calls setScreen(null) internally and
         * would kill the screen the mod just opened.
         */
        private static void finishClose(Screen old) {
            if (old == null) return;
            Minecraft mc = Minecraft.getInstance();
            try {
                // Read the menu off the OLD screen itself, not mc.player.containerMenu - by
                // the time a handoff is detected, the server may have already opened the new
                // screen's menu and reassigned player.containerMenu to it. Reading the live
                // field here would grab the NEW menu and send a close packet for it, telling
                // the server to tear down the container the player is currently looking at
                // (every subsequent slot click then gets silently rejected as stale).
                AbstractContainerMenu menu =
                        (old instanceof AbstractContainerScreen<?> containerScreen)
                                ? containerScreen.getMenu() : null;

                old.removed();

                if (menu != null && mc.player != null && menu != mc.player.inventoryMenu) {
                    if (mc.player.connection != null) {
                        mc.player.connection.send(new ServerboundContainerClosePacket(menu.containerId));
                    }
                    if (mc.player.containerMenu == menu) {
                        mc.player.containerMenu = mc.player.inventoryMenu;
                    }
                }
            } catch (Exception e) {
                LegendaryTabs.LOGGER.warn("Deferred close of {} failed", old.getClass().getName(), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Scheduler - one permanent listener, no per-press registration leaks
    // ------------------------------------------------------------------

    private static final class Job {
        final KeyMapping mapping;
        final boolean closeScreenFirst;
        final ScreenCloak cloak = new ScreenCloak();
        boolean pressed;
        int heldTicks;
        PressState state;

        Job(KeyMapping mapping, boolean closeScreenFirst) {
            this.mapping = mapping;
            this.closeScreenFirst = closeScreenFirst;
        }
    }

    private static final class Scheduler {
        private static final Deque<Job> JOBS = new ArrayDeque<>();
        private static boolean registered = false;

        static void enqueue(KeyMapping mapping, boolean closeScreenFirst) {
            if (!registered) {
                MinecraftForge.EVENT_BUS.register(new Scheduler());
                registered = true;
            }
            JOBS.add(new Job(mapping, closeScreenFirst));
        }

        // Cloak and press at START/HIGHEST, before any mod tick handler runs.
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onTickStart(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            for (Job job : JOBS) {
                if (job.closeScreenFirst) job.cloak.hide();
                if (!job.pressed) {
                    try {
                        job.state = doPress(job.mapping);
                    } catch (Exception e) {
                        LegendaryTabs.LOGGER.warn("Press failed for {}", job.mapping.getName(), e);
                    }
                    job.pressed = true;
                }
            }
        }

        // Uncloak and release at END/LOWEST, after every handler has run and
        // still before the frame is drawn.
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onTickEnd(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            JOBS.removeIf(job -> {
                if (!job.pressed) return false;

                // Poll every tick, not just the first one - the mod's screen-open can
                // legitimately land a tick or more late (deferred execute() task, or
                // its own separately-scheduled tick handler).
                if (job.closeScreenFirst) job.cloak.checkForHandoff();
                boolean handedOff = job.closeScreenFirst && job.cloak.handedOff();

                // The instant a handoff is seen, release right away and quietly -
                // no reason to hold any longer, and every extra tick held is another
                // chance for a stray release to land inside the screen that just opened.
                if (handedOff) {
                    try {
                        if (job.state != null) doRelease(job.mapping, job.state, true);
                    } catch (Exception e) {
                        LegendaryTabs.LOGGER.warn("Release failed for {}", job.mapping.getName(), e);
                    }
                    return true;
                }

                if (++job.heldTicks < HOLD_TICKS) return false;

                // Held out the full window and nothing ever opened - release for real
                // and put the original screen back.
                if (job.closeScreenFirst) job.cloak.giveUpAndRestore();
                try {
                    if (job.state != null) doRelease(job.mapping, job.state, false);
                } catch (Exception e) {
                    LegendaryTabs.LOGGER.warn("Release failed for {}", job.mapping.getName(), e);
                }
                return true;
            });
        }
    }
}