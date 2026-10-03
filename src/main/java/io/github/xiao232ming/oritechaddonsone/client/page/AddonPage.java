package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One page of the Extension Addon GUI.
 * <p>
 * A page owns everything that is specific to it: the tab that represents it (id, icon, label and
 * tooltip) and the content it draws inside the panel. The screen draws the panel chrome (background,
 * bevel, player inventory frames, tab strip) and the menu owns the slots, so adding a page never
 * requires touching the layout or the menu.
 * <p>
 * The page is a display only concern: the plugin page draws the frames and hints of the plugin grid,
 * but the slots themselves exist in the menu regardless of the page that is visible, and switching pages
 * changes nothing on the server.
 */
public interface AddonPage {

    /** Stable id of this page, used for logs and lookups. */
    String id();

    /** Label of this page and of its tab (shown in the tab tooltip). */
    Component label();

    /** 16x16 GUI texture of the tab icon. */
    ResourceLocation icon();

    /** Tooltip lines of the tab: at least the label, more to explain what the page is for. */
    default List<Component> tooltip() {
        return List.of(label());
    }

    /**
     * Height of the panel this page wants to be drawn in, for the layout the GUI currently shows.
     * <p>
     * The menu owns one geometry for every page - the slot coordinates of the client and of the server
     * are derived from it and have to agree - but a page usually needs far less room than the tallest
     * one. This is what the screen paints the panel border with, so a page whose content ends well above
     * the player inventory does not show the empty band the Item Proxy page reserves for its net and
     * counter. The slot positions, the item sync and the interaction are untouched: only the bottom
     * border of the panel (and the label above the player inventory) follows the visible page.
     * <p>
     * The default is the full panel height every menu has, so a page that draws down to the layout's
     * bottom band needs no override; {@code ExtensionAddonLayout#pageHeight(int)} measures a page by the
     * bottom of its own content.
     */
    default int drawnHeight(io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout layout) {
        return layout.imageHeight();
    }

    /**
     * Draws the content of this page inside the panel. The context maps the panel's coordinate system
     * onto the screen, so a page draws at {@code context.left() + layout.slotX(slot)} like the panel
     * background does.
     *
     * @param mouseX mouse X of this frame, <b>panel relative</b> - the same coordinate system every other
     *               position on this page uses (the screen subtracts its own origin once), so a page can
     *               light up the control the mouse is over while it draws it. Pages without their own
     *               hover state ignore it.
     * @param mouseY mouse Y of this frame, panel relative; see {@code mouseX}
     */
    void render(AddonPageContext context, GuiGraphics graphics, float partialTick,
            double mouseX, double mouseY);

    /**
     * Called on a click inside the panel before the slot logic sees it, with mouse coordinates relative
     * to the panel's top left corner. Returns {@code true} to consume the click. A page without its own
     * controls keeps the default, so clicks reach the slots unchanged.
     */
    default boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Tooltip lines for this page's own controls at the given position (panel relative), or an empty list
     * while nothing of this page is hovered. The screen draws them after the item tooltip, so a page can
     * explain a control of its own - for example a slot that is still empty and therefore has no item
     * tooltip of its own.
     */
    default List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        return List.of();
    }
}
