package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import sfiomn.legendarytabs.LegendaryTabs;


public abstract class TabBase {
    public static final int TAB_HEIGHT = 22;
    public static final int TAB_WIDTH = 26;
    public static final int ICON_SIZE = 18;
    public static final int ICON_OFFSET_X = 4;
    public static final int ICON_OFFSET_Y = 4;
    
    private static final ResourceLocation BUTTONS_TEXTURE = new ResourceLocation(LegendaryTabs.MOD_ID, "textures/gui/buttons.png");
    // Texture coordinates for 26x22 button backgrounds in 64x64 texture
    private static final int BUTTON_BG_TEX_X = 0;
    private static final int BUTTON_BG_TEX_Y = 0;
    private static final int BUTTON_BG_PRESSED_TEX_X = 27;
    private static final int BUTTON_BG_PRESSED_TEX_Y = 0;
    
    // Texture dimensions - buttons.png is 64x64, we need to specify correct UV mapping
    private static final int BUTTONS_TEXTURE_WIDTH = 64;
    private static final int BUTTONS_TEXTURE_HEIGHT = 64;

    public TabBase() {
    }

    public abstract void openTargetScreen(Player player);

    public abstract boolean isEnabled(Player player);

    public abstract void initTabOnScreens();

    public void render(GuiGraphics gui, int x, int y, boolean hover) {
        int bgTexX = hover ? BUTTON_BG_PRESSED_TEX_X : BUTTON_BG_TEX_X;
        int bgTexY = hover ? BUTTON_BG_PRESSED_TEX_Y : BUTTON_BG_TEX_Y;
        
        // Specify texture dimensions for proper 64x64 texture handling
        gui.blit(BUTTONS_TEXTURE, x, y, bgTexX, bgTexY, TAB_WIDTH, TAB_HEIGHT, BUTTONS_TEXTURE_WIDTH, BUTTONS_TEXTURE_HEIGHT);
        
        // Abstraction layer: handle 18x18 textures directly
        ResourceLocation iconTexture = getIconTexture();
        int iconTexX = getIconTexX();
        int iconTexY = getIconTexY();
        
        // For icon textures, assume 18x18 unless coordinates suggest otherwise
        gui.blit(iconTexture, x + ICON_OFFSET_X, y + ICON_OFFSET_Y, iconTexX, iconTexY, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    public abstract boolean isCurrentlyUsed(Screen currentScreen);

    public abstract Component getTooltip();

    public abstract ResourceLocation getIconTexture();

    // Default implementation returns 0 for simple 18x18 textures
    public int getIconTexX() {
        return 0;
    }

    // Default implementation returns 0 for simple 18x18 textures  
    public int getIconTexY() {
        return 0;
    }
}
