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
 * Item Proxy page right aligns its counter in and which gives the slot picker room) and it reserves the
 * {@linkplain #PROXY_CONTENT_BOTTOM room} of that page above the player inventory, inside which that
 * page's net is centred. Both numbers are defined here, so screen, menu and pages always agree on them.
 * <p>
 * That room is what makes one geometry serve every page: it pushes the player inventory down below the
 * net on a block with one or two plugin rows. The menu keeps the one {@code imageHeight} height
 * - the slot coordinates a client and the server derive from it have to agree - while a page may draw a
 * {@linkplain #pageHeight shorter panel} that ends just below its own content: an empty band is then
 * simply not part of the panel. The page framework asks each page for its
 * {@link io.github.xiao232ming.oritechaddonsone.client.page.AddonPage#drawnHeight(ExtensionAddonLayout)
 * drawn height}, so the screen paints the shorter border without touching a single slot. See {@link #pageHeight}.
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
    /** Width of the net: the four side faces in a row. */
    public static final int PROXY_NET_WIDTH = 4 * PROXY_FACE;
    /** Height of the net: top, front and bottom face. */
    public static final int PROXY_NET_HEIGHT = 3 * PROXY_FACE;
    /**
     * Left edge of the net, in panel space: the net is centred in the panel body, so it keeps the same
     * margin to the left and to the right border ({@code (WIDTH - PROXY_NET_WIDTH) / 2 = 64}) instead of
     * hugging the left one. The page draws the faces at {@code left + PROXY_NET_X} - the panel's own
     * origin, the same one the panel body and the player slots use - so the net can never leave the panel.
     */
    public static final int PROXY_NET_X = (WIDTH - PROXY_NET_WIDTH) / 2;
    /**
     * Bottom of the room the panel reserves above the player inventory for the Item Proxy page, i.e. the Y
     * the player inventory never starts above. It is a fixed 90 (five slot rows) and is not derived from
     * the net any more, because the net is centred inside it ({@link #PROXY_NET_Y}): deriving it as
     * {@code net + gap} would move the player inventory along every time the net moves.
     * <p>
     * This is the room the panel reserves for the Item Proxy page. Without it a block with one or two
     * plugin rows would place the player inventory over the net (at 48 and 66), so the inventory starts
     * at {@code max(firstSlotY + rows * SLOT_SIZE + 12, PROXY_CONTENT_BOTTOM)}: at 90 for one and two
     * rows and at its old value from three rows on - the panel only grows where the net really needs it.
     */
    public static final int PROXY_CONTENT_BOTTOM = 90;
    /**
     * Margin the net keeps inside that room, above and below it: half of what the room has left once the
     * net is subtracted ({@code (PROXY_CONTENT_BOTTOM - PROXY_NET_HEIGHT) / 2 = 18}), which is what
     * centres the net in the room instead of letting it hug the top edge.
     */
    public static final int PROXY_NET_MARGIN = (PROXY_CONTENT_BOTTOM - PROXY_NET_HEIGHT) / 2;
    /**
     * Top edge of the net, in panel space: {@link #PROXY_NET_MARGIN} below the top of the room, so the net
     * is centred in it (18 in the 90 pixel room, 10 pixels higher than the 28 it sat at). It stays clear
     * of the panel's title label - drawn at Y 6, eight pixels tall - and of the first player inventory
     * row, which starts at {@link #PROXY_CONTENT_BOTTOM}.
     */
    public static final int PROXY_NET_Y = PROXY_NET_MARGIN;
    /**
     * Height of the band a page needs below its own content, and at the least below the hotbar row: the
     * two pixels of the panel's dark bottom bevel plus four pixels of panel, so the drawn panel ends with
     * a normal looking border. No page reserves more than this: the Item Proxy page's counter used to sit
     * in a taller band under the hotbar and has moved to the panel's top right corner, so that band is
     * gone and the panel ends right under the player inventory.
     */
    public static final int PAGE_BOTTOM_BAND = 6;
    /**
     * Height the drawn panel does <em>not</em> need above the first content row: the panel's two pixel
     * light bevel plus the gap to a row of content. A page measures itself as {@code TOP_BAND + content}.
     */
    public static final int TOP_BAND = 2;
    /**
     * Vertical inset of the counter's text inside the panel's top edge: with the two pixel light bevel
     * that puts it at Y 6, level with the panel's title label and with the first tab of the strip.
     */
    public static final int COUNTER_INSET = 4;
    /**
     * Distance the counter keeps from the panel's right border. The tab strip reaches
     * {@link #TAB_OVERLAP} pixels into the panel, so eight pixels leave the whole text - and four pixels
     * of panel - left of it.
     */
    public static final int COUNTER_MARGIN = 8;

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

    /**
     * Height of the panel: the hotbar row plus the normal bottom border, i.e.
     * {@code pageHeight(hotbarY + SLOT_SIZE)}, the tallest a page ever needs. Nothing is drawn below the
     * player inventory any more - the Item Proxy page's counter moved to the top right corner - so the
     * menu reserves no extra band there.
     */
    private static int imageHeight(int hotbarY) {
        return hotbarY + SLOT_SIZE + TOP_BAND + PAGE_BOTTOM_BAND;
    }

    /**
     * Y of the Item Proxy page's counter, in panel space: the panel's top right corner, level with the
     * title label and with the first tab of the strip. It used to sit in the band below the hotbar row,
     * which is what made that page taller than the player inventory; moving it up there is what lets the
     * panel end right under the inventory.
     */
    public int counterY() {
        return TOP_BAND + COUNTER_INSET;
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

    // ------------------------------------------------------------------ drawn panel per page

    /**
     * Height the drawn panel of a page needs whose own content ends at {@code contentBottom}: the two
     * pixel light bevel plus that content, and at least the whole player inventory - the three inventory
     * rows and the hotbar - plus the bottom border. A page passes the bottom of its own content here, so
     * the dark bottom border lands just below that content instead of leaving an empty band below it,
     * which is the large empty area the plugin page used to show.
     * <p>
     * The menu keeps {@link #imageHeight() its own height} (which is
     * {@code pageHeight(imageHeight() - 2 - PAGE_BOTTOM_BAND)} for every layout), because the slot
     * coordinates of the client and of the server are derived from it; only the panel border and the
     * player inventory label follow the page. The Item Proxy page needs no height of its own any more:
     * its net and its counter sit at the top and the panel has to cover the player inventory either way,
     * so it draws the menu's own height.
     */
    public int pageHeight(int contentBottom) {
        return Math.max(TOP_BAND + contentBottom + PAGE_BOTTOM_BAND,
                hotbarY + SLOT_SIZE + PAGE_BOTTOM_BAND);
    }

    /**
     * Y of the player inventory label, matching vanilla container screens. The label belongs to the
     * player inventory, not to a page: it is anchored to the first inventory row (twelve pixels above
     * it, the vanilla gap), so it cannot move with the plugin grid or with a page's own content.
     */
    public int inventoryLabelY() {
        return playerRowsY - inventoryLabelGap();
    }

    /** Vanilla's gap between the first player inventory row and the label above it. */
    public static int inventoryLabelGap() {
        return 12;
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
