package io.github.xiao232ming.oritechaddonsone.client.page;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * Where the panel of the Extension Addon GUI currently is, plus the geometry of its menu.
 * <p>
 * The menu positions its slots in panel space (starting at the panel's top left corner), while the screen
 * paints in screen space; this record is the one place where the two are combined, so a page can draw a
 * frame exactly where the menu put the slot that belongs into it.
 * <p>
 * <b>Every</b> page coordinate is panel relative and has to be mapped through {@link #screenX(int)} /
 * {@link #screenY(int)} before it is drawn, and the same coordinate has to be used for the hit tests. That
 * single rule is what the Item Proxy page once broke: its layout rectangles were correct and inside the
 * panel, but the drawing used a different origin, so the pixels ended up left of the panel border even
 * though every panel space check passed. The screen space helpers are the only supported way to turn a
 * page coordinate into a pixel position.
 */
public record AddonPageContext(ExtensionAddonMenu menu, ExtensionAddonLayout layout, int left, int top) {

    /** X of the plugin slot with the given index, in screen space. */
    public int slotX(int index) {
        return screenX(layout.slotX(index));
    }

    /** Y of the plugin slot with the given index, in screen space. */
    public int slotY(int index) {
        return screenY(layout.slotY(index));
    }

    /**
     * Screen X of a panel relative coordinate. The panel body of the screen is drawn from
     * {@link #left()}, so this is the origin every page has to draw with.
     */
    public int screenX(int panelX) {
        return left + panelX;
    }

    /** Screen Y of a panel relative coordinate; see {@link #screenX(int)}. */
    public int screenY(int panelY) {
        return top + panelY;
    }

    /** Width of the panel body (without the tab strip on its right edge). */
    public int panelWidth() {
        return ExtensionAddonLayout.WIDTH;
    }

    /** Height of the panel body. */
    public int panelHeight() {
        return layout.imageHeight();
    }

    /** Right edge of the panel body, in screen space (exclusive). */
    public int panelRight() {
        return screenX(panelWidth());
    }

    /** Bottom edge of the panel body, in screen space (exclusive). */
    public int panelBottom() {
        return screenY(panelHeight());
    }
}
