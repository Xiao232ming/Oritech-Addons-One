package io.github.xiao232ming.oritechaddonsone.client.page;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * Where the panel of the Extension Addon GUI currently is, plus the geometry of its menu.
 * <p>
 * The menu positions its slots in panel space (starting at the panel's top left corner), while the screen
 * paints in screen space; this record is the one place where the two are combined, so a page can draw a
 * frame exactly where the menu put the slot that belongs into it.
 */
public record AddonPageContext(ExtensionAddonMenu menu, ExtensionAddonLayout layout, int left, int top) {

    /** X of the plugin slot with the given index, in screen space. */
    public int slotX(int index) {
        return left + layout.slotX(index);
    }

    /** Y of the plugin slot with the given index, in screen space. */
    public int slotY(int index) {
        return top + layout.slotY(index);
    }

    /** Width of the panel body (without the tab strip on its right edge). */
    public int panelWidth() {
        return ExtensionAddonLayout.WIDTH;
    }

    /** Height of the panel body. */
    public int panelHeight() {
        return layout.imageHeight();
    }
}
