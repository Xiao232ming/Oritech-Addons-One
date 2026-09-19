package io.github.xiao232ming.oritechaddonsone.menu;

/**
 * Geometry of the Extension Addon GUI, derived from the (configurable) slot count.
 * <p>
 * Slots are laid out in rows of nine, the panel uses the vanilla container dimensions
 * ({@code 114 + rows * 18} px high), so any slot count between {@value #MIN_SLOTS} and
 * {@value #MAX_SLOTS} can be displayed without a prepared background texture.
 */
public record ExtensionAddonLayout(int slots, int columns, int rows, int firstSlotX, int firstSlotY,
                                    int playerRowsY, int hotbarY, int imageWidth, int imageHeight) {

    public static final int MIN_SLOTS = 1;
    /** Internal storage size: 8 rows of 9 slots, also the upper bound of the type I / II slot config. */
    public static final int MAX_SLOTS = 72;
    public static final int SLOT_SIZE = 18;
    public static final int WIDTH = 176;
    /** Y of the player inventory label, matching vanilla container screens. */
    public static final int LABEL_OFFSET = 94;

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
                playerRowsY, hotbarY, WIDTH, imageHeight);
    }

    /** X of the plugin slot with the given index (in GUI space). */
    public int slotX(int index) {
        return firstSlotX + (index % columns) * SLOT_SIZE;
    }

    /** Y of the plugin slot with the given index (in GUI space). */
    public int slotY(int index) {
        return firstSlotY + (index / columns) * SLOT_SIZE;
    }
}
