package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

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
 * ({@link ExtensionAddonLayout#TOTAL_WIDTH}) so the panel and its tabs stay centred together, but the
 * panel body itself keeps its original width and layout - only the page that is drawn inside it changes
 * when a tab is clicked. Pages are a presentation detail: the menu slots, their positions and the sync are
 * the same as before, and the tab strip holds no state that the server ever sees.
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
        this.inventoryLabelY = this.imageHeight - ExtensionAddonLayout.LABEL_OFFSET;
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
     * The pages own their slots: the plugin slots belong to the plugin page and the reserved single item
     * slot to the wireless page, so only the visible page's slots are active. That is a client side
     * display and clicking decision - the server never sees it and the slots exist either way - and it is
     * what keeps the plugin items from being drawn over the wireless page's info text.
     */
    private void syncVisiblePage() {
        this.menu.setWirelessPageActive(this.tabs.selectedPage() == AddonPageRegistry.wirelessPage());
        // leaving the Item Proxy page closes whatever picker was open on it, so coming back starts fresh
        if (this.tabs.selectedPage() != AddonPageRegistry.proxyPage()) {
            ProxyPickerState.close();
        }
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
     * Draws the tooltip a page offers for its own controls, unless the mouse is over an item - an item's
     * own tooltip is the more interesting one, and a page only has to explain a control that holds no item
     * of its own. No page uses this today (the wireless page's reserved slot shows no text at all), but the
     * hook is what such a control would be explained with.
     */
    private void renderPageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.context == null) return;
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
     * Draws the panel and its chrome: the panel body, the page that is currently selected, the player
     * inventory frames and the tab strip.
     * <p>
     * This runs before the slots themselves are rendered, so the dim plugin hints of type III end up
     * behind any plugin that is actually inserted.
     * <p>
     * The panel body keeps its original width ({@link ExtensionAddonLayout#WIDTH}); the tab strip is
     * painted over its right border, which is what makes the selected tab look connected to the panel.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int xo = this.leftPos;
        int yo = this.topPos;
        int right = xo + ExtensionAddonLayout.WIDTH;

        // classic container panel with a light top/left and dark bottom/right bevel
        graphics.fill(xo, yo, right, yo + this.imageHeight, AddonPanelStyle.PANEL);
        graphics.fill(xo, yo, right, yo + 2, AddonPanelStyle.PANEL_LIGHT);
        graphics.fill(xo, yo, xo + 2, yo + this.imageHeight, AddonPanelStyle.PANEL_LIGHT);
        graphics.fill(xo, yo + this.imageHeight - 2, right, yo + this.imageHeight, AddonPanelStyle.PANEL_DARK);
        graphics.fill(right - 2, yo, right, yo + this.imageHeight, AddonPanelStyle.PANEL_DARK);

        // the content of the visible page (the plugin page draws the plugin slots and the type III hints)
        if (this.context != null) {
            this.tabs.selectedPage().render(this.context, graphics, partialTick);
        }

        // player inventory (3 rows of 9) and the hotbar - these frames came from the background
        // texture before, so they have to be drawn here as well.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                AddonPanelStyle.drawSlot(graphics, xo + 7 + column * 18, yo + layout.playerRowsY() + row * 18 - 1);
            }
        }
        for (int column = 0; column < 9; column++) {
            AddonPanelStyle.drawSlot(graphics, xo + 7 + column * 18, yo + layout.hotbarY() - 1);
        }

        // the tab strip on the right edge, painted last so the selected tab covers the panel border
        this.tabs.render(graphics, mouseX, mouseY, partialTick);
    }
}
