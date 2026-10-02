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
 * The panel is sized for every page it can show, not only for the plugin grid: it is
 * {@value #RIGHT_GUTTER} pixels wider than vanilla's 176 columns (a free strip on the right, which the
 * Item Proxy page right aligns its counter in and which gives the slot picker room) and it keeps the
 * {@linkplain #PROXY_CONTENT_BOTTOM net} of that page clear of the player inventory, plus a
 * {@linkplain #BOTTOM_BAND band} below the hotbar for the counter. All three numbers are defined here,
 * so screen, menu and pages always agree on them.
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

    /**
     * Extra width of the panel body right of the vanilla 176 columns. The plugin grid, the reserved slot
     * and the player inventory keep the x positions they had at {@code WIDTH = 176}; this is a free strip
     * on their right, in front of the tab strip - the room the Item Proxy page's counter is right aligned
     * in and the room the slot picker uses, so neither has to be squeezed onto the inventory columns.
     */
    public static final int RIGHT_GUTTER = 24;
    /** Width of the panel body: the vanilla columns plus {@link #RIGHT_GUTTER} (200). */
    public static final int WIDTH = 176 + RIGHT_GUTTER;

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

    // ------------------------------------------------------------------ item proxy page

    /** Size of one face of the six face net of the Item Proxy page, in pixels. */
    public static final int PROXY_FACE = 18;
    /** Left edge of the net, in panel space. */
    public static final int PROXY_NET_X = 6;
    /**
     * Top edge of the net, in panel space: the first row below the panel's title label, so the vanilla
     * title and the net cannot overlap.
     */
    public static final int PROXY_NET_Y = 18;
    /** Width of the net: the four side faces in a row. */
    public static final int PROXY_NET_WIDTH = 4 * PROXY_FACE;
    /** Height of the net: top, front and bottom face. */
    public static final int PROXY_NET_HEIGHT = 3 * PROXY_FACE;
    /** Gap the panel keeps between the net and the first player inventory row. */
    public static final int CONTENT_GAP = 8;
    /**
     * Y the player inventory never starts above: the bottom of the net plus {@link #CONTENT_GAP} (80).
     * <p>
     * This is the room the panel reserves for the Item Proxy page. Without it a block with one or two
     * plugin rows would place the player inventory over the net (at 48 and 66), so the inventory starts
     * at {@code max(firstSlotY + rows * SLOT_SIZE + 12, PROXY_CONTENT_BOTTOM)}: at 80 for one and two
     * rows and at its old value from three rows on - the panel only grows where the net really needs it.
     */
    public static final int PROXY_CONTENT_BOTTOM = PROXY_NET_Y + PROXY_NET_HEIGHT + CONTENT_GAP;
    /**
     * Height of the free band the panel keeps below the hotbar row. The counter of the Item Proxy page
     * is drawn in it, which is what keeps the counter clear of the hotbar slots it used to sit on.
     */
    public static final int BOTTOM_BAND = 16;
    /** Vertical inset of the counter's text inside {@link #BOTTOM_BAND}. */
    public static final int COUNTER_INSET = 4;
    /**
     * Distance the counter keeps from the panel's right border. The tab strip reaches
     * {@link #TAB_OVERLAP} pixels into the panel, so eight pixels leave the whole text - and four pixels
     * of panel - left of it.
     */
    public static final int COUNTER_MARGIN = 8;

    /** Y of the player inventory label, matching vanilla container screens. */
    public static final int LABEL_OFFSET = 98;

    // ------------------------------------------------------------------ wireless page

    /**
     * X of the reserved single item slot of the wireless page, in GUI space.
     * <p>
     * It is the top right cell of a full nine column grid: level with the first plugin row and as far
     * right as a slot frame may go, because the tab strip starts {@value #TAB_OVERLAP} pixels further
     * right (the frame ends at {@code RESERVED_SLOT_X + 17 = 169}, the strip at {@code 200 - 4 = 196}).
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
     * rightmost slot frame ends at {@code 8 + 9 * 18 - 1 = 169}, i.e. {@link #RIGHT_GUTTER} + 3 pixels
     * left of it).
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
        var playerRowsY = playerRowsY(firstSlotY, rows);
        var hotbarY = hotbarY(playerRowsY);

        return new ExtensionAddonLayout(slots, columns, rows, firstSlotX, firstSlotY,
                playerRowsY, hotbarY, WIDTH, imageHeight(hotbarY), false);
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
        var playerRowsY = playerRowsY(firstSlotY, rows);
        var hotbarY = hotbarY(playerRowsY);

        return new ExtensionAddonLayout(slots, cols, rows, firstSlotX, firstSlotY,
                playerRowsY, hotbarY, WIDTH, imageHeight(hotbarY), true);
    }

    /** Picks the grid shape that matches the plugin type. */
    public static ExtensionAddonLayout forType(ExtensionAddonType type, int slots) {
        return type == ExtensionAddonType.TYPE_3
                ? ofColumnMajor(slots, TYPE_3_COLUMNS)
                : of(slots);
    }

    /**
     * Y of the first player inventory row: below the plugin grid, but never high enough to reach into the
     * Item Proxy page's net (see {@link #PROXY_CONTENT_BOTTOM}).
     */
    private static int playerRowsY(int firstSlotY, int rows) {
        return Math.max(firstSlotY + rows * SLOT_SIZE + 12, PROXY_CONTENT_BOTTOM);
    }

    /** Y of the hotbar row: the usual four pixels below the three player inventory rows. */
    private static int hotbarY(int playerRowsY) {
        return playerRowsY + 3 * SLOT_SIZE + 4;
    }

    /** Height of the panel: the hotbar row plus the band the Item Proxy page's counter sits in. */
    private static int imageHeight(int hotbarY) {
        return hotbarY + SLOT_SIZE + BOTTOM_BAND;
    }

    /**
     * Y of the Item Proxy page's counter, in panel space: in the free band below the hotbar row, so the
     * counter is off the inventory slots and off the tab strip (which only reaches
     * {@link #TAB_OVERLAP} pixels into the panel).
     */
    public int counterY() {
        return hotbarY + SLOT_SIZE + COUNTER_INSET;
    }

    /**
     * X the Item Proxy page's counter ends at, in panel space: right aligned in the
     * {@linkplain #RIGHT_GUTTER free strip} on the right of the panel and {@link #COUNTER_MARGIN} pixels
     * left of its border.
     */
    public int counterRight() {
        return WIDTH - COUNTER_MARGIN;
    }

    /**
     * First Y nothing may be drawn at: the top of the player inventory's slot frames (which start one
     * pixel above the slots themselves). The Item Proxy page clamps its slot picker to this, so the
     * machine inventory preview can never cover the player's own slots.
     */
    public int contentBottom() {
        return playerRowsY - 2;
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
