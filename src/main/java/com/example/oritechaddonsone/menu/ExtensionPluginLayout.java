package com.example.oritechaddonsone.menu;

import com.example.oritechaddonsone.block.ExtensionPluginType;

/**
 * Geometry of the Extension Plugin GUI, derived from the (configurable) slot count.
 * <p>
 * Types I and II use a row major grid of nine slots per row. Type III uses a
 * {@linkplain #ofColumnMajor(int, int) column major} grid instead: one column per stat category and one
 * row per plugin tier, so a column always holds the same kind of plugin and the tiers increase
 * downwards. The panel is drawn procedurally, so any slot count between {@value #MIN_SLOTS} and
 * {@value #MAX_SLOTS} can be displayed without a prepared background texture.
 */
public record ExtensionPluginLayout(int slots, int columns, int rows, int firstSlotX, int firstSlotY,
                                    int playerRowsY, int hotbarY, int imageWidth, int imageHeight,
                                    boolean columnMajor) {

    public static final int MIN_SLOTS = 1;
    /** Internal storage size: 6 categories x up to 12 tiers for type III. */
    public static final int MAX_SLOTS = 72;
    public static final int SLOT_SIZE = 18;
    public static final int WIDTH = 176;
    /** Y of the player inventory label, matching vanilla container screens. */
    public static final int LABEL_OFFSET = 94;
    /** Columns of the type III grid, one per stat category. */
    public static final int TYPE_3_COLUMNS = 6;
    /** Highest number of tier rows the type III grid can show. */
    public static final int TYPE_3_MAX_ROWS = MAX_SLOTS / TYPE_3_COLUMNS;

    /** Layout used by types I and II: rows of nine slots. */
    public static ExtensionPluginLayout of(int requestedSlots) {
        var slots = Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, requestedSlots));
        var columns = Math.min(9, slots);
        var rows = (slots + 8) / 9;

        // Partial rows stay centred in the panel.
        var firstSlotX = 8 + (9 - columns) * SLOT_SIZE / 2;
        var firstSlotY = 18;
        var playerRowsY = firstSlotY + rows * SLOT_SIZE + 12;
        var hotbarY = playerRowsY + 3 * SLOT_SIZE + 4;
        var imageHeight = 114 + rows * SLOT_SIZE;

        return new ExtensionPluginLayout(slots, columns, rows, firstSlotX, firstSlotY,
                playerRowsY, hotbarY, WIDTH, imageHeight, false);
    }

    /**
     * Layout used by type III: {@code columns} columns (one per stat category) with the slots filled
     * column by column, i.e. slot {@code index} sits at column {@code index / rows} and row
     * {@code index % rows}.
     */
    public static ExtensionPluginLayout ofColumnMajor(int requestedSlots, int columns) {
        var cols = Math.max(1, Math.min(TYPE_3_COLUMNS, columns));
        var rows = Math.max(1, Math.min(TYPE_3_MAX_ROWS, (requestedSlots + cols - 1) / cols));
        var slots = Math.min(Math.max(MIN_SLOTS, requestedSlots), cols * rows);

        var firstSlotX = 8 + (9 - cols) * SLOT_SIZE / 2;
        var firstSlotY = 18;
        var playerRowsY = firstSlotY + rows * SLOT_SIZE + 12;
        var hotbarY = playerRowsY + 3 * SLOT_SIZE + 4;
        var imageHeight = 114 + rows * SLOT_SIZE;

        return new ExtensionPluginLayout(slots, cols, rows, firstSlotX, firstSlotY,
                playerRowsY, hotbarY, WIDTH, imageHeight, true);
    }

    /** Picks the grid shape that matches the plugin type. */
    public static ExtensionPluginLayout forType(ExtensionPluginType type, int slots) {
        return type == ExtensionPluginType.TYPE_3
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
