package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import io.github.xiao232ming.oritechaddonsone.client.page.AddonPage;
import io.github.xiao232ming.oritechaddonsone.client.page.AddonPageContext;
import io.github.xiao232ming.oritechaddonsone.client.page.AddonPageRegistry;
import io.github.xiao232ming.oritechaddonsone.client.page.AddonTabStrip;
import io.github.xiao232ming.oritechaddonsone.client.page.ProxyPickerState;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * Screen of the Extension Addons and of the Wireless Extension Docks.
 * <p>
 * The panel is drawn procedurally instead of using a background texture, because the amount of plugin
 * slots is configurable (1-36): the layout is derived from the menu and extra rows are added as needed.
 * <p>
 * The panel carries a {@linkplain AddonTabStrip tab strip} on its right edge, one tab per registered
 * {@link io.github.xiao232ming.oritechaddonsone.client.page.AddonPage}. The strip is part of the GUI width
 * ({@link ExtensionAddonLayout#TOTAL_WIDTH}) so the panel and its tabs stay centred together; the panel
 * body itself is sized by the layout (vanilla width plus the layout's free strip, and tall enough for the
 * Item Proxy page's net). Only the page that is drawn inside it changes when a tab is clicked, and the
 * page list itself is refreshed while the GUI is open when the block's contents change. Pages are a
 * presentation detail: the menu slots, their positions and the sync are the same as before, and the tab
 * strip holds no state that the server ever sees.
 * <p>
 * This class lives in a client only package and is only referenced from
 * {@link OritechAddonsOneClient}, so it is never loaded on a dedicated server.
 */
public class ExtensionAddonScreen extends AbstractContainerScreen<ExtensionAddonMenu> {

    private final ExtensionAddonLayout layout;
    private final AddonTabStrip tabs;

    /** Where the panel currently is; rebuilt in {@link #init()} because the screen can be resized. */
    private AddonPageContext context;

    public ExtensionAddonScreen(ExtensionAddonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        // the pages depend on the block this menu belongs to (wired addon or wireless dock)
        this.tabs = new AddonTabStrip(AddonPageRegistry.pages(menu));
        // the panel plus the tab strip on its right edge: the whole GUI is centred, a click on a tab counts
        // as a click inside the GUI, and no tab can leave the window
        this.imageWidth = ExtensionAddonLayout.TOTAL_WIDTH;
        this.imageHeight = layout.imageHeight();
        // The label belongs to the player inventory, so it is anchored to the first inventory row (the
        // vanilla twelve pixel gap) instead of to the panel's bottom border, which follows the page.
        this.inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void init() {
        super.init();
        // the panel origin is only known once super.init() has centred the screen
        this.context = new AddonPageContext(this.menu, this.layout, this.leftPos, this.topPos);
        this.tabs.layout(this.leftPos, this.topPos, this.width);
        this.tabs.setSelectionListener(this::syncVisiblePage);
        this.syncVisiblePage();
    }

    /**
     * Tells the menu which page the screen shows.
     * <p>
     * A page owns its slots: the plugin slots are the plugin page's and the reserved single item slot the
     * wireless page's, so exactly one group is active - the Item Proxy page owns no menu slot at all, so
     * neither group is. Vanilla asks a slot for its activity before it draws it and before it hands a
     * click to it, which is why this single flag per group is what keeps the pages apart: no plugin item
     * can appear on the proxy page's net and no reserved item on the plugin grid. That is a client side
     * display and clicking decision - the server never sees it and the slots exist either way.
     */
    private void syncVisiblePage() {
        var page = this.tabs.selectedPage();
        this.menu.setPluginPageActive(page == AddonPageRegistry.pluginPage());
        this.menu.setWirelessPageActive(page == AddonPageRegistry.wirelessPage());
        // leaving the Item Proxy page closes whatever picker was open on it, so coming back starts fresh
        if (page != AddonPageRegistry.proxyPage()) {
            ProxyPickerState.close();
        }
        // The Item Proxy page's configuration panel is an opaque modal step over the whole panel, and the
        // player inventory is drawn after the page - so the inventory is hidden while that panel is open,
        // which is what makes the panel read as a full page like Oritech's own inventory proxy screen. The
        // slot positions, their ids and everything the server sees are untouched.
        this.menu.setPlayerSlotsActive(!ProxyPickerState.isOpen(this.menu.position()));
    }

    /**
     * Rebuilds the strip whenever the pages the menu offers changed, so the Item Proxy tab appears as soon
     * as an inventory proxy addon is put in and disappears as soon as the last one is taken out - without
     * reopening the GUI.
     * <p>
     * The page list is a function of the block's contents ({@link AddonPageRegistry#pages}), and the
     * contents are menu slots, which the client sees as soon as the server syncs them; the cheap check
     * runs once per container tick. It never runs on the server: the menu has no idea which page is open.
     * {@link AddonTabStrip#setPages} keeps the selection while the selected page survives and falls back
     * to the plugin page - the first page - when it does not.
     */
    @Override
    protected void containerTick() {
        super.containerTick();

        var pages = AddonPageRegistry.pages(this.menu);
        if (this.tabs.shows(pages)) return;

        this.tabs.setPages(pages);
        // the rebuilt tabs are at (0, 0) until they are placed at the panel again
        this.tabs.layout(this.leftPos, this.topPos, this.width);
    }

    /**
     * Forgets the slot layouts the Item Proxy page asked the server for. They describe one machine, so
     * keeping them past the GUI would only leak memory (and show a stale machine after a relink).
     */
    @Override
    public void removed() {
        super.removed();
        ProxyPickerState.clear();
    }

    /**
     * Draws the hovered item tooltip, the tooltip of the hovered tab and the tooltip of whatever the
     * visible page put under the mouse.
     * <p>
     * On 1.21.1 {@link AbstractContainerScreen#render} does <b>not</b> call {@code renderTooltip}
     * itself: every concrete container screen is expected to do it at the end of its own
     * {@code render} (see vanilla {@code ContainerScreen}, {@code HopperScreen}, ...). Overriding
     * only {@code renderBg} therefore left this GUI without any item tooltip.
     * <p>
     * The tooltips are drawn after {@code super.render}, so they also end up on top of the type III
     * hint veils (those are part of the background). The tab strip is outside every slot, so only one
     * of the two tooltips can ever be hovered at a time.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.tabs.renderTooltip(graphics, this.font, mouseX, mouseY);
        renderPageTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Draws the panel's title, and the player inventory's label only while the inventory is really shown.
     * <p>
     * Vanilla's {@link AbstractContainerScreen#renderLabels} always draws both labels and it runs after the
     * background, so the inventory label ("物品栏") used to stay visible in the middle of the Item Proxy
     * page's configuration panel - a light grey box floating next to Oritech's page, over an inventory the
     * modal step hides anyway. The title stays: it belongs to the panel itself.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.playerSlotsActive()) {
            super.renderLabels(graphics, mouseX, mouseY);
            return;
        }
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
    }

    /**
     * Draws the tooltip a page offers for its own controls, unless the mouse is over an item - an item's
     * own tooltip is the more interesting one, and a page only has to explain a control that holds no item
     * of its own. No page uses this today (the wireless page's reserved slot shows no text at all), but the
     * hook is what such a control would be explained with.
     */
    private void renderPageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {        if (this.context == null) return;
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) return;

        var lines = this.tabs.selectedPage().tooltipAt(this.context, mouseX - this.leftPos, mouseY - this.topPos);
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    /**
     * Tabs are handled before the slot logic, and a click on a tab is consumed by the strip: vanilla
     * treats a click next to the panel as a click outside the GUI, which would start a quick craft or
     * throw the carried item into the world. A click that is not on a tab goes to the visible page first
     * and then to the slots, unchanged.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tabs.mouseClicked(mouseX, mouseY, button)) return true;

        var relativeX = mouseX - this.leftPos;
        var relativeY = mouseY - this.topPos;
        if (this.context != null && this.tabs.selectedPage().mouseClicked(this.context, relativeX, relativeY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Draws the panel and its chrome: the panel body, the one selected page, the player inventory frames
     * and the tab strip.
     * <p>
     * The page gate: exactly one page is drawn per frame, and it is the one the strip selected
     * ({@code this.tabs.selectedPage()}), so no page's text, counter, frame or modal overlay can leak into
     * another page. The other half of the gate is the slot activity
     * ({@link #syncVisiblePage}): vanilla draws a slot's item only while the slot is active, and only the
     * selected page's slots are active, so no page shows another page's items either.
     * <p>
     * This runs before the slots themselves are rendered, so the dim plugin hints of type III end up
     * behind any plugin that is actually inserted.
     * <p>
     * The panel body is {@link ExtensionAddonLayout#WIDTH} wide; its bottom border is drawn at the height
     * the visible page asks for ({@link AddonPage#drawnHeight}), not at the menu's own height. The menu
     * keeps {@code layout.imageHeight()} - that is the geometry the server and the client derive the slot
     * coordinates from - but the plugin page and the wireless page only need the player inventory, so
     * their panel ends right below it instead of showing the band the Item Proxy page reserves for its net
     * and counter. The GUI stays centred on the menu's height either way, so switching a tab moves
     * nothing. The tab strip is painted over its right border, which is what makes the selected tab look
     * connected to the panel.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The page can open or close its configuration panel on a click, and that changes whether the
        // player's inventory is drawn at all; syncing here (once per frame, and idempotent) keeps the menu's
        // slot activity correct without the page having to reach into the menu.
        syncVisiblePage();

        int xo = this.leftPos;
        int yo = this.topPos;
        int right = xo + ExtensionAddonLayout.WIDTH;

        var page = this.tabs.selectedPage();
        int panelHeight = page.drawnHeight(this.layout);

        // classic container panel with a light top/left and dark bottom/right bevel
        graphics.fill(xo, yo, right, yo + panelHeight, AddonPanelStyle.PANEL);
        graphics.fill(xo, yo, right, yo + 2, AddonPanelStyle.PANEL_LIGHT);
        graphics.fill(xo, yo, xo + 2, yo + panelHeight, AddonPanelStyle.PANEL_LIGHT);
        graphics.fill(xo, yo + panelHeight - 2, right, yo + panelHeight, AddonPanelStyle.PANEL_DARK);
        graphics.fill(right - 2, yo, right, yo + panelHeight, AddonPanelStyle.PANEL_DARK);

        // the content of the selected page only (the plugin page draws the plugin slots and the type III
        // hints, the wireless page its info text and the Item Proxy page the net, counter and picker).
        // The page draws in panel space while the mouse arrives in screen space, so the origin is
        // subtracted exactly once, here - every hit test of a page then measures what it drew.
        if (this.context != null) {
            page.render(this.context, graphics, partialTick, mouseX - xo, mouseY - yo);
        }

        // player inventory (3 rows of 9) and the hotbar - these frames came from the background
        // texture before, so they have to be drawn here as well. They sit at the menu's coordinates,
        // which every page's drawn height covers. While the Item Proxy page's configuration panel is
        // open they are skipped together with their items and their label: that panel is an opaque modal
        // step over the whole panel, and these frames are painted after it, so they used to show up as a
        // grid of empty slots on top of Oritech's configuration page.
        if (this.menu.playerSlotsActive()) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    AddonPanelStyle.drawSlot(graphics, xo + 7 + column * 18, yo + layout.playerRowsY() + row * 18 - 1);
                }
            }
            for (int column = 0; column < 9; column++) {
                AddonPanelStyle.drawSlot(graphics, xo + 7 + column * 18, yo + layout.hotbarY() - 1);
            }
        }

        // the tab strip on the right edge, painted last so the selected tab covers the panel border
        this.tabs.render(graphics, mouseX, mouseY, partialTick);
    }
}
