package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The tab strip on the right edge of the panel: one tab per registered page.
 * <p>
 * The strip only holds client side state - which page is selected - and draws and hit tests its tabs. It
 * never touches the menu: every slot of every page already exists in {@code ExtensionAddonMenu}, so
 * switching a tab only changes what is drawn. The strip is also the one place that keeps the tabs inside
 * the window: the strip is part of the GUI width, so the tabs are inside the window for every window
 * Minecraft can produce, and the {@link #layout} clamp covers the theoretical case of a smaller one.
 */
public final class AddonTabStrip {

    private final List<AddonTabWidget> tabs;
    private int selected;

    /** Called after the selection changed, so the screen can follow it (e.g. enable the page's slots). */
    private Runnable selectionListener = () -> {
    };

    public AddonTabStrip(ExtensionAddonMenu menu) {
        this(AddonPageRegistry.pages(menu));
    }

    public AddonTabStrip(List<AddonPage> pages) {
        var widgets = new ArrayList<AddonTabWidget>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            final int tab = index;
            widgets.add(new AddonTabWidget(pages.get(index), index, () -> select(tab)));
        }
        this.tabs = List.copyOf(widgets);
        select(0);
    }

    /** All tabs, in tab order (the first one is the page a freshly opened GUI shows). */
    public List<AddonTabWidget> tabs() {
        return tabs;
    }

    /** Sets what runs after {@link #select(int)} changed the visible page. */
    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Places the strip on the right edge of the panel at the given panel origin.
     * <p>
     * The GUI (panel plus strip) is {@value ExtensionAddonLayout#TOTAL_WIDTH} pixels wide and the screen
     * uses that as its {@code imageWidth}, so the strip is inside the window whenever the window is at
     * least that wide - which every window Minecraft can produce is. Should a window ever be narrower, the
     * strip is shifted left instead of being clipped: the tabs stay visible and clickable, they just
     * overlap the panel a little further.
     */
    public void layout(int panelLeft, int panelTop, int screenWidth) {
        int x = panelLeft + ExtensionAddonLayout.tabX();
        int overflow = panelLeft + ExtensionAddonLayout.TOTAL_WIDTH - screenWidth;
        if (overflow > 0) {
            x -= overflow;
        }
        x = Math.max(0, x);

        int panelRight = panelLeft + ExtensionAddonLayout.WIDTH;
        for (var tab : tabs) {
            tab.setPosition(x, panelTop + ExtensionAddonLayout.tabY(tab.getIndex()));
            tab.setPanelRight(panelRight);
        }
    }

    /** The page that is currently shown. */
    public AddonPage selectedPage() {
        return tabs.get(selected).page();
    }

    /** Selects the page at the given index (clamped), which is what a click on a tab does. */
    public void select(int index) {
        this.selected = Math.max(0, Math.min(index, tabs.size() - 1));
        for (var tab : tabs) {
            tab.setActive(tab.getIndex() == this.selected);
        }
        this.selectionListener.run();
    }

    /** Draws all tabs in screen coordinates, in tab order. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (var tab : tabs) {
            tab.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Consumes a click on a tab, in screen coordinates. Returns {@code false} while the click is not on a
     * tab, so the screen can hand it to the page and then to the slot logic unchanged.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (var tab : tabs) {
            if (tab.isVisible() && tab.isMouseOver(mouseX, mouseY)) {
                return tab.handleClick(mouseX, mouseY, button);
            }
        }
        return false;
    }

    /** Draws the tooltip of the hovered tab, if any. */
    public void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (var tab : tabs) {
            if (tab.isVisible() && tab.isMouseOver(mouseX, mouseY) && tab.hasTooltip()) {
                graphics.renderComponentTooltip(font, tab.getTooltip(), mouseX, mouseY);
                return;
            }
        }
    }
}
