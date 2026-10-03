package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
    Identifier icon();

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
     * bottom band needs no override;
     * {@code io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout#pageHeight(int)} measures a
     * page by the bottom of its own content.
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
    void render(AddonPageContext context, GuiGraphicsExtractor graphics, float partialTick,
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
     * Called while a mouse button is held and moved over the panel, with the mouse coordinates relative to the
     * panel's top left corner and the movement since the last event. Returns {@code true} to consume the drag.
     * <p>
     * A page that does not draw a draggable control keeps the default and the drag reaches vanilla's slot logic
     * unchanged - exactly like {@link #mouseClicked}. A page that <em>does</em> use it has to remember that its own
     * drag started (in {@link #mouseClicked}, by hit testing its control) and forget it in
     * {@link #mouseReleased}: a drag is forwarded for the whole window, so a page that rotated on every drag
     * anywhere would steal the drag from a slot the player is moving items across.
     * <p>
     * The 3D page of 传输插件 is the one page that uses this today: it rotates its model while the player drags over
     * it. The screen's own page host is an {@code AbstractContainerScreen}, whose widgets know nothing about our
     * pages, so a page cannot be given a widget's own drag handling - this hook is what stands in for it.
     */
    default boolean mouseDragged(AddonPageContext context, double mouseX, double mouseY, double dragX,
            double dragY, int button) {
        return false;
    }

    /**
     * Called when a mouse button is released anywhere, with the mouse coordinates relative to the panel's top left
     * corner. It is the counterpart of {@link #mouseDragged} and exists so a page can end its own drag even when
     * the movement left the page's control - the draggable 3D preview does exactly that. The return value is
     * ignored: a release is always observed, whether or not the page consumed the drag before it.
     */
    default void mouseReleased(AddonPageContext context, double mouseX, double mouseY, int button) {
    }

    /**
     * Called when the mouse wheel moves while the pointer is over the panel, with the mouse coordinates relative to
     * the panel's top left corner. Returns {@code true} to consume the scroll.
     * <p>
     * It is the wheel's counterpart of {@link #mouseClicked} and {@link #mouseDragged}, and the same rule applies: a
     * page that does not use the wheel keeps the default and the scroll reaches vanilla unchanged. A page that does
     * use it has to hit test its own control itself, because the scroll is forwarded for the whole panel - a page that
     * zoomed on every scroll anywhere would steal the wheel from the slots a player scrolls over.
     * <p>
     * The 3D page of 传输插件 is the one page that uses this today: it zooms its model while the pointer is over the
     * model's own panel. The screen's page host is an {@code AbstractContainerScreen}, whose widgets know nothing
     * about our pages, so a page cannot be given a widget's own scroll handling - this hook stands in for it.
     *
     * @param scrollX horizontal scroll of this event
     * @param scrollY vertical scroll of this event, positive when the player scrolls up
     */
    default boolean mouseScrolled(AddonPageContext context, double mouseX, double mouseY, double scrollX,
            double scrollY) {
        return false;
    }

    /**
     * Tooltip lines for this page's own controls at the given position (panel relative), or an empty list
     * while nothing of this page is hovered. The screen hands them to the frame it extracts after the
     * item tooltip, so a page can explain a control of its own - for example a slot that is still empty
     * and therefore has no item tooltip of its own.
     */
    default List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        return List.of();
    }
}
