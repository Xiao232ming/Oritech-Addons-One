package io.github.xiao232ming.oritechaddonsone.client.page;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.api.screen.widgets.TextureWidget;

import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * One tab of the strip on the right edge of the panel.
 * <p>
 * The tab is a Oritech {@link UIComponent}, so hit testing and the tooltip come from Oritech's widget API;
 * only the tab shape itself is painted with plain fills, because it has to match the bevel of the
 * procedural panel (Oritech's own surfaces are nine patches of its bedrock theme and would not fit). The
 * icon is drawn by Oritech's {@link TextureWidget}.
 * <p>
 * Coordinates are screen coordinates: the tab sits at the panel's right border and - for
 * {@value ExtensionAddonLayout#TAB_OVERLAP} pixels - inside it, which is what makes the selected tab look
 * like an opening in that border. That overlap is kept clear of every slot frame, so a click on a tab can
 * never be a click on a slot.
 */
public final class AddonTabWidget extends UIComponent {

    /** Vertical inset of an unselected tab: it is drawn shorter, so it reads as sitting behind the panel. */
    private static final int INSET = 2;

    /** Offset of the icon inside the tab (the icon is centred in the part that sticks out). */
    private static final int ICON_OFFSET_X = ExtensionAddonLayout.tabIconX() - ExtensionAddonLayout.tabX();
    private static final int ICON_OFFSET_Y = (ExtensionAddonLayout.TAB_HEIGHT - ExtensionAddonLayout.TAB_ICON_SIZE) / 2;

    private final AddonPage page;
    private final int index;
    private final Runnable onSelect;
    private final TextureWidget icon;

    /** X of the panel's right border, needed to paint the panel pixels back over an unselected tab. */
    private int panelRight;
    private boolean active;

    AddonTabWidget(AddonPage page, int index, Runnable onSelect) {
        super(0, 0, ExtensionAddonLayout.TAB_WIDTH, ExtensionAddonLayout.TAB_HEIGHT);
        this.page = page;
        this.index = index;
        this.onSelect = onSelect;
        this.icon = new TextureWidget(0, 0, ExtensionAddonLayout.TAB_ICON_SIZE, ExtensionAddonLayout.TAB_ICON_SIZE,
                page.icon(), ExtensionAddonLayout.TAB_ICON_SIZE, ExtensionAddonLayout.TAB_ICON_SIZE);
        this.withTooltip(page.tooltip());
    }

    /** The page this tab shows. */
    public AddonPage page() {
        return page;
    }

    /** Position of this tab in the strip. */
    public int getIndex() {
        return index;
    }

    /** True while this tab is the visible page. */
    public boolean isActive() {
        return active;
    }

    /** X of the panel's right border, so the tab knows which pixels of it to paint back. */
    void setPanelRight(int panelRight) {
        this.panelRight = panelRight;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    /** Moves the tab and its icon together. */
    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        this.icon.setPosition(x + ICON_OFFSET_X, y + ICON_OFFSET_Y);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        drawTab(graphics, isMouseOver(mouseX, mouseY));
        this.icon.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * Any click on a tab is consumed, so nothing of it reaches the slot logic behind the panel (vanilla
     * would treat a click next to the panel as a click outside the GUI and could start a quick craft or
     * throw the carried item). Only the left button switches the page.
     */
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            onSelect.run();
        }
        return true;
    }

    /**
     * Paints the tab shape.
     * <p>
     * The selected tab is filled in the panel colour and left open towards the panel; it is drawn over the
     * panel's right border, so tab and panel become one shape. An unselected tab is recessed (darker fill,
     * outline, 2px shorter) and the panel pixels over its overlap are painted back afterwards, so it ends
     * up behind the panel border like a classic inventory tab - the whole strip is therefore clearly
     * distinguishable and clickable without disturbing the panel.
     */
    private void drawTab(GuiGraphicsExtractor graphics, boolean hovered) {
        int left = this.x;
        int right = this.x + ExtensionAddonLayout.TAB_WIDTH;

        if (active) {
            int bottom = this.y + ExtensionAddonLayout.TAB_HEIGHT;
            graphics.fill(left, this.y, right, bottom, AddonPanelStyle.PANEL);
            graphics.fill(left, this.y, right, this.y + 1, AddonPanelStyle.PANEL_LIGHT);
            graphics.fill(left, bottom - 1, right, bottom, AddonPanelStyle.PANEL_DARK);
            graphics.fill(right - 1, this.y, right, bottom, AddonPanelStyle.PANEL_DARK);
            return;
        }

        int top = this.y + INSET;
        int bottom = this.y + ExtensionAddonLayout.TAB_HEIGHT - INSET;
        int fill = hovered ? AddonPanelStyle.TAB_FILL_HOVER : AddonPanelStyle.TAB_FILL;

        graphics.fill(left, top, right, bottom, AddonPanelStyle.SLOT_DARK);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fill);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, AddonPanelStyle.PANEL_LIGHT);
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, AddonPanelStyle.PANEL_LIGHT);

        if (panelRight > 0) {
            // paint the panel's own edge back over the overlap: interior plus the 2px dark border
            graphics.fill(left, top, panelRight - 2, bottom, AddonPanelStyle.PANEL);
            graphics.fill(panelRight - 2, top, panelRight, bottom, AddonPanelStyle.PANEL_DARK);
        }
    }
}
