package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import io.github.xiao232ming.oritechaddonsone.client.page.AddonPageContext;
import io.github.xiao232ming.oritechaddonsone.client.page.AddonTabStrip;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * Screen of the Extension Addons and of the Wireless Extension Docks.
 * <p>
 * The panel is drawn procedurally instead of using a background texture, because the amount of plugin
 * slots is configurable (1-72): the layout is derived from the menu and extra rows are added as needed.
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
    private final AddonTabStrip tabs = new AddonTabStrip();

    /** Where the panel currently is; rebuilt in {@link #init()} because the screen can be resized. */
    private AddonPageContext context;

    public ExtensionAddonScreen(ExtensionAddonMenu menu, Inventory inventory, Component title) {
        // the panel plus the tab strip on its right edge: the whole GUI is centred, a click on a tab counts
        // as a click inside the GUI, and no tab can leave the window
        super(menu, inventory, title, ExtensionAddonLayout.TOTAL_WIDTH, menu.layout().imageHeight());
        this.layout = menu.layout();
        this.inventoryLabelY = this.imageHeight - ExtensionAddonLayout.LABEL_OFFSET;
    }

    @Override
    protected void init() {
        super.init();
        // the panel origin is only known once super.init() has centred the screen
        this.context = new AddonPageContext(this.menu, this.layout, this.leftPos, this.topPos);
        this.tabs.layout(this.leftPos, this.topPos, this.width);
    }

    /**
     * Adds the tooltip of the hovered tab.
     * <p>
     * On 26.1.2 tooltips are collected while the frame is extracted and shown afterwards, so this runs
     * after {@code super.extractRenderState} - which collects the hovered item tooltip - exactly like
     * Oritech's own widget screens do it. The tab strip is outside every slot, so only one of the two
     * tooltips can ever be hovered at a time.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.tabs.renderTooltip(graphics, this.font, mouseX, mouseY);
    }

    /**
     * Tabs are handled before the slot logic, and a click on a tab is consumed by the strip: vanilla
     * treats a click next to the panel as a click outside the GUI, which would start a quick craft or
     * throw the carried item into the world. A click that is not on a tab goes to the visible page first
     * and then to the slots, unchanged.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.tabs.mouseClicked(event.x(), event.y(), event.button())) return true;

        var relativeX = event.x() - this.leftPos;
        var relativeY = event.y() - this.topPos;
        if (this.context != null && this.tabs.selectedPage().mouseClicked(this.context, relativeX, relativeY, event.button())) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Adds the link line of the wireless addons: which machine this dock is linked to and where it
     * stands. It is centred right below the panel, so it cannot overlap any other text. The wired addons simply show that they are not
     * linked, so both variants keep the same layout.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        var text = linkText().getString();
        // centred under the panel body (not under the panel plus the tab strip), as it was before
        var x = (ExtensionAddonLayout.WIDTH - this.font.width(text)) / 2;
        // drawn under the panel, so it can never overlap the title or the inventory label; the String
        // overload is used because it takes the drop shadow flag (the Component one always adds one)
        graphics.text(this.font, text, x, this.imageHeight + 4,
                this.menu.linkedMachine() == null ? AddonPanelStyle.UNLINKED_COLOR : AddonPanelStyle.LINK_COLOR, false);
    }

    /** Text of the link line, either the linked machine with its coordinates or "not linked". */
    private Component linkText() {
        var machine = this.menu.linkedMachine();
        if (machine == null) {
            return Component.translatable("gui.oritechaddonsone.wireless.unlinked");
        }

        var nameKey = this.menu.linkedMachineNameKey();
        var name = nameKey == null
                ? Component.translatable("gui.oritechaddonsone.wireless.machine")
                : Component.translatable(nameKey);
        return Component.translatable("gui.oritechaddonsone.wireless.linked", name,
                machine.getX(), machine.getY(), machine.getZ());
    }

    /**
     * Draws the panel and its chrome: the panel body, the page that is currently selected, the player
     * inventory frames and the tab strip.
     * <p>
     * The panel body keeps its original width ({@link ExtensionAddonLayout#WIDTH}); the tab strip is
     * painted over its right border, which is what makes the selected tab look connected to the panel.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

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
