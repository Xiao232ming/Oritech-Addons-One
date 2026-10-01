package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Colours, depth layers and shared drawing primitives of the procedurally drawn Extension Addon GUI.
 * <p>
 * The panel has no background texture (the slot count is configurable), so everything is painted with
 * plain fills in the vanilla container style. The palette and the slot frame drawing live here instead of
 * in the screen, because the screen, the pages and the tab strip all draw with them.
 */
public final class AddonPanelStyle {

    /** Background of the panel body. */
    public static final int PANEL = 0xFFC6C6C6;
    /** Light bevel (top and left edge of the panel, inner highlight of slots and tabs). */
    public static final int PANEL_LIGHT = 0xFFFFFFFF;
    /** Dark bevel (bottom and right edge of the panel, outer shadow of slots and tabs). */
    public static final int PANEL_DARK = 0xFF555555;
    /** Fill of a recessed slot. */
    public static final int SLOT_FILL = 0xFF8B8B8B;
    /** Outline of a recessed slot or tab. */
    public static final int SLOT_DARK = 0xFF373737;

    /**
     * Fill of a tab that is not selected. Darker than the panel, so the tab reads as a background page
     * and the selected one (painted in {@link #PANEL}) clearly stands out.
     */
    public static final int TAB_FILL = SLOT_FILL;
    /** Fill of an unselected tab while the mouse hovers it. */
    public static final int TAB_FILL_HOVER = 0xFFAFAFAF;

    /** Dark veil drawn over the type III slot hints so they read as a dim background icon. */
    public static final int HINT_VEIL = 0x99000000;

    /** Colour of the link line under the panel (bright green, no drop shadow). */
    public static final int LINK_COLOR = 0xFF55FF55;
    /** Colour of that line while the addon is not linked to a machine. */
    public static final int UNLINKED_COLOR = 0xFFAAAAAA;

    private AddonPanelStyle() {
    }

    /**
     * Recessed 18x18 slot frame, drawn like vanilla container backgrounds do. It is painted 1px above and
     * left of the slot itself, so the 16x16 item never covers the frame.
     */
    public static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + 18, SLOT_DARK);
        graphics.fill(x, y + 17, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 17, y, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
    }
}
