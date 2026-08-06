package sfiomn.legendarytabs.client.tabs_menu;

/**
 * A per-tab, client-local override for where that tab's button is displayed, on top of the
 * position TabsMenu would otherwise compute for it. This is a player preference, not part of a
 * tab's definition - it never comes from legendarytabs:tabs/*.json and is never synced to or
 * from the server, unlike TabData. Structure only for now: nothing constructs, persists, loads,
 * or reads one of these yet. Wiring it into TabButton's actual positioning, and giving players
 * a way to set it (a config screen, drag-to-reposition, etc.), is future work.
 */
public class TabDisplayOverride {
    private int offsetX;
    private int offsetY;

    public TabDisplayOverride() {
        this(0, 0);
    }

    public TabDisplayOverride(int offsetX, int offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(int offsetX) {
        this.offsetX = offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(int offsetY) {
        this.offsetY = offsetY;
    }
}
