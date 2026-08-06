package sfiomn.legendarytabs.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;
import sfiomn.legendarytabs.config.Config;

import static sfiomn.legendarytabs.api.tabs_menu.TabBase.TAB_HEIGHT;
import static sfiomn.legendarytabs.api.tabs_menu.TabBase.TAB_WIDTH;

public class NextTabsButton extends Button {
    public static final int LEFT_ARROW_TEX_X = 0;
    public static final int LEFT_ARROW_TEX_Y = 23;
    public static final int LEFT_ARROW_PRESSED_TEX_X = 26;
    public static final int LEFT_ARROW_PRESSED_TEX_Y = 23;
    public static final int RIGHT_ARROW_TEX_X = 13;
    public static final int RIGHT_ARROW_TEX_Y = 23;
    public static final int RIGHT_ARROW_PRESSED_TEX_X = 39;
    public static final int RIGHT_ARROW_PRESSED_TEX_Y = 23;
    public static final int NEXT_TABS_BUTTON_WIDTH = 12;
    public static final int NEXT_TABS_BUTTON_HEIGHT = 21;
    private static final int BUTTONS_TEXTURE_WIDTH = 64;
    private static final int BUTTONS_TEXTURE_HEIGHT = 64;
    public int tabPositionIndex;
    private final Screen screen;

    public NextTabsButton(int tabPositionIndex, int leftScreenPos, int topScreenPos, Screen screen, net.minecraft.client.gui.components.Button.OnPress press) {
        super(leftScreenPos + tabPositionIndex * (TAB_WIDTH + 1) + Config.Baked.tabsMenuOffsetX, topScreenPos - TAB_HEIGHT + Config.Baked.tabsMenuOffsetY, NEXT_TABS_BUTTON_WIDTH, NEXT_TABS_BUTTON_HEIGHT, Component.literal(""), press, DEFAULT_NARRATION);
        this.tabPositionIndex = tabPositionIndex;
        this.screen = screen;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partial) {
        int texX = RIGHT_ARROW_TEX_X;
        int texY = RIGHT_ARROW_TEX_Y;
        if (this.isMouseOver(mouseX, mouseY)) {
            texX = RIGHT_ARROW_PRESSED_TEX_X;
            texY = RIGHT_ARROW_PRESSED_TEX_Y;
        }

        // Same skin as the tab buttons on this screen - it's the screen's chrome, not this
        // widget's own, so it must match whatever legendarytabs:tabs/*.json declared for it.
        TabsMenu.ScreenInfo screenInfo = TabsMenu.getScreenInfo(TabsMenu.resolveScreenIdentity(this.screen));
        var buttonTexture = screenInfo != null && screenInfo.buttonSkin != null ? screenInfo.buttonSkin : TabBase.DEFAULT_BUTTONS_TEXTURE;

        gui.blit(buttonTexture, this.getX(), this.getY(), texX, texY, NEXT_TABS_BUTTON_WIDTH, NEXT_TABS_BUTTON_HEIGHT, BUTTONS_TEXTURE_WIDTH, BUTTONS_TEXTURE_HEIGHT);
    }

    public void updatePosition(int leftScreenPos, int topScreenPos) {
        setX(leftScreenPos + tabPositionIndex * (TAB_WIDTH + 1) + Config.Baked.tabsMenuOffsetX);
        setY(topScreenPos - TAB_HEIGHT + Config.Baked.tabsMenuOffsetY);
    }
}
