package io.github.xiao232ming.oritechaddonsone.menu;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;

/**
 * Geometry of the Extension Addon GUI, derived from the (configurable) slot count.
 * <p>
 * Types I and II use a row major grid of nine slots per row. Type III uses a
 * {@linkplain #ofColumnMajor(int, int) column major} grid instead: one column per stat category and one
 * row per plugin tier, so a column always holds the same kind of plugin and the tiers increase
 * downwards. The panel is drawn procedurally, so any slot count between {@value #MIN_SLOTS} and
 * {@value #MAX_SLOTS} can be displayed without a prepared background texture.
 * <p>
 * The right edge of the panel also carries the {@linkplain #TAB_WIDTH tab strip} of the page framework.
 * It is a client side presentation detail - the menu slots are the same on both sides - but its size is
 * part of the GUI, so it is defined here together with the panel and used by the screen.
 */
public record ExtensionAddonLayout(int slots, int columns, int rows, int firstSlotX, int firstSlotY,
                                    int playerRowsY, int hotbarY, int imageWidth, int imageHeight,
                                    boolean columnMajor) {

    public static final int MIN_SLOTS = 1;
    public static final int SLOT_SIZE = 18;
    public static final int WIDTH = 176;
    /** Y of the player inventory label, matching vanilla container screens. */
    public static final int LABEL_OFFSET = 94;
    /**
     * Columns of the type III grid, one per stat category - derived from the enum so a new category gets
     * its own column without touching the geometry here. Eight columns still fit the vanilla panel width:
     * the first slot starts at {@code 8 + (9 - 8) * 18 / 2 = 17} and the grid ends at {@code 17 + 8 * 18 =
     * 161}, inside {@link #WIDTH}.
     */
    public static final int TYPE_3_COLUMNS = ExtensionAddonType.StatCategory.values().length;
    /** Internal storage size: one column per stat category, up to 12 tiers each. */
    public static final int MAX_SLOTS = 12 * TYPE_3_COLUMNS;
    /** Highest number of tier rows the type III grid can show. */
    public static final int TYPE_3_MAX_ROWS = MAX_SLOTS / TYPE_3_COLUMNS;

    // ------------------------------------------------------------------ wireless page

    /**
     * X of the reserved single item slot of the wireless page, in GUI space.
     * <p>
     * It is the top right cell of a full nine column grid: level with the first plugin row and as far
     * right as a slot frame may go, because the tab strip starts {@value #TAB_OVERLAP} pixels further
     * right (the frame ends at {@code RESERVED_SLOT_X + 17 = 169}, the strip at {@code 176 - 4 = 172}).
     * A narrower grid is centred, so the slot then stands next to it in the free part of the panel.
     */
    public static final int RESERVED_SLOT_X = 8 + 8 * SLOT_SIZE;

    /**
     * Y of the reserved single item slot: level with the first plugin row
     * ({@code firstSlotY} of every layout is {@code 18}).
     */
    public static final int RESERVED_SLOT_Y = 18;

    // ------------------------------------------------------------------ tab strip

    /**
     * Width of one tab of the strip that sits on the right edge of the panel. The tabs are drawn by the
     * screen, but their geometry lives here as well: the strip is part of the GUI, so the panel and the
     * {@linkplain #TOTAL_WIDTH whole screen} have to be sized from the same numbers the menu uses.
     */
    public static final int TAB_WIDTH = 28;
    /** Height of one tab. */
    public static final int TAB_HEIGHT = 24;
    /** Vertical gap between two tabs. */
    public static final int TAB_GAP = 2;
    /** Y of the first tab, level with the panel's title label. */
    public static final int TAB_FIRST_Y = 6;
    /**
     * How far a tab reaches into the panel. The tab is drawn over the panel's right border, which is what
     * makes the selected tab look connected to the panel; the overlap stays clear of the slot grid (the
     * rightmost slot frame ends at {@code 8 + 9 * 18 - 1 = 169}, i.e. three pixels left of it).
     */
    public static final int TAB_OVERLAP = 4;
    /** Width a tab adds to the right of the panel. */
    public static final int TAB_STRIP_WIDTH = TAB_WIDTH - TAB_OVERLAP;
    /** Size of a tab icon (vanilla 16x16 block/item texture). */
    public static final int TAB_ICON_SIZE = 16;
    /**
     * Width of the whole GUI: the panel plus the tab strip on its right edge. The screen uses it as its
     * {@code imageWidth}, so the panel and its tabs stay centred together, a click on a tab counts as a
     * click inside the GUI and no tab can leave the window (the strip is part of the centred GUI).
     */
    public static final int TOTAL_WIDTH = WIDTH + TAB_STRIP_WIDTH;

    /** X of a tab in GUI space: {@value #TAB_OVERLAP} pixels inside the panel's right border. */
    public static int tabX() {
        return WIDTH - TAB_OVERLAP;
    }

    /** Y of the tab with the given index in GUI space. */
    public static int tabY(int index) {
        return TAB_FIRST_Y + index * (TAB_HEIGHT + TAB_GAP);
    }

    /** X of a tab icon in GUI space: centred in the part of the tab that sticks out of the panel. */
    public static int tabIconX() {
        return WIDTH + (TAB_STRIP_WIDTH - TAB_ICON_SIZE) / 2;
    }

    /** Y of the tab icon with the given index in GUI space: centred in its tab. */
    public static int tabIconY(int index) {
        return tabY(index) + (TAB_HEIGHT - TAB_ICON_SIZE) / 2;
    }

    /** Layout used by types I and II: rows of nine slots. */
    public static ExtensionAddonLayout of(int requestedSlots) {
        var slots = Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, requestedSlots));
        var columns = Math.min(9, slots);
        var rows = (slots + 8) / 9;

        // Partial rows stay centred in the panel.
        var firstSlotX = 8 + (9 - columns) * SLOT_SIZE / 2;
        var firstSlotY = 18;
        var playerRowsY = firstSlotY + rows * SLOT_SIZE + 12;
        var hotbarY = playerRowsY + 3 * SLOT_SIZE + 4;
        var imageHeight = 114 + rows * SLOT_SIZE;

        return new ExtensionAddonLayout(slots, columns, rows, firstSlotX, firstSlotY,
                playerRowsY, hotbarY, WIDTH, imageHeight, false);
    }

    /**
     * Layout used by type III: {@code columns} columns (one per stat category) with the slots filled
     * column by column, i.e. slot {@code index} sits at column {@code index / rows} and row
     * {@code index % rows}.
     */
    public static ExtensionAddonLayout ofColumnMajor(int requestedSlots, int columns) {
        var cols = Math.max(1, Math.min(TYPE_3_COLUMNS, columns));
        var rows = Math.max(1, Math.min(TYPE_3_MAX_ROWS, (requestedSlots + cols - 1) / cols));
        var slots = Math.min(Math.max(MIN_SLOTS, requestedSlots), cols * rows);

        var firstSlotX = 8 + (9 - cols) * SLOT_SIZE / 2;
        var firstSlotY = 18;
        var playerRowsY = firstSlotY + rows * SLOT_SIZE + 12;
        var hotbarY = playerRowsY + 3 * SLOT_SIZE + 4;
        var imageHeight = 114 + rows * SLOT_SIZE;

        return new ExtensionAddonLayout(slots, cols, rows, firstSlotX, firstSlotY,
                playerRowsY, hotbarY, WIDTH, imageHeight, true);
    }

    /** Picks the grid shape that matches the plugin type. */
    public static ExtensionAddonLayout forType(ExtensionAddonType type, int slots) {
        return type == ExtensionAddonType.TYPE_3
                ? ofColumnMajor(slots, TYPE_3_COLUMNS)
                : of(slots);
    }

    /** X of the plugin slot with the given index (in GUI space). */
    public int slotX(int index) {
        return firstSlotX + (columnMajor ? index / rows : index % columns) * SLOT_SIZE;
    }

    /** Y of the plugin slot with the given index (in GUI space). */
    public int slotY(int index) {
        return firstSlotY + (columnMajor ? index % rows : index / columns) * SLOT_SIZE;
    }
}
