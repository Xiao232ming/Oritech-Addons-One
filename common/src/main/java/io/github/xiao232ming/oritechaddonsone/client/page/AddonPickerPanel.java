package io.github.xiao232ming.oritechaddonsone.client.page;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * Geometry of the modal configuration page both item pages open over the panel: Oritech's own inventory proxy
 * screen, a 176x100 bedrock panel with the block's item as a 28x28 title icon above it.
 * <p>
 * The Item Proxy page fills it with the machine's GUI slots and the Extension Transfer page with the three
 * transfer modes, but the page itself - its size, the prompt line Oritech puts at y 85, and the way it is
 * kept inside our panel - is the same, so it is defined once here.
 */
public final class AddonPickerPanel {

    /**
     * Size of Oritech's own inventory proxy screen, which its {@code InventoryProxyScreen} passes to
     * {@code OritechWidgetScreen} as {@code (176, 100)}. The configuration page is that panel, so anything
     * Oritech draws on it keeps the coordinates Oritech gives it.
     */
    public static final int WIDTH = 176;
    public static final int HEIGHT = 100;
    /**
     * Y of Oritech's prompt line inside that panel ({@code InventoryProxyScreen} uses {@code 85}, its
     * {@code LabelWidget} is {@code 176} wide and centred, ten pixels tall).
     */
    public static final int PROMPT_Y = 85;
    /**
     * Size of Oritech's title icon ({@code OritechWidgetScreen#addTitle()} builds a {@code 28x28} widget)
     * and the padding it keeps inside it, which centres the 16x16 item.
     */
    public static final int ICON_SIZE = 28;
    /** Padding of the item inside that icon widget. */
    public static final int ICON_PADDING = 3;

    private AddonPickerPanel() {
    }

    /**
     * Places the configuration page inside our panel, in <b>panel relative</b> coordinates.
     * <p>
     * Oritech's screen is the same size as the page it reproduces, so the machine's GUI slots keep the
     * coordinates {@code ScreenProvider#getGuiSlots()} gives them. Our panel is
     * {@code ExtensionAddonLayout.WIDTH - WIDTH = 24} pixels wider, so the page is centred horizontally with
     * a twelve pixel gutter on each side, and it is centred vertically in the room between the title icon -
     * Oritech draws the icon of a widget screen 27 pixels above the panel's top edge, so ours is a header
     * floating over the panel body - and the bottom border of our panel.
     * <p>
     * The placement is clamped to the drawn panel, so the configuration page and its icon are always fully
     * inside our GUI, whatever the window, the slot count or the plugin rows: on a panel with three plugin
     * rows the panel is taller than the page needs and the page is simply centred, and on the two taller
     * pages it is pushed up until it fits. It is never resized or scaled, so it always lands in the window.
     */
    public static Placed place(AddonPageContext context) {
        int innerX = Math.max(0, (context.panelWidth() - WIDTH) / 2);

        // the header icon sits on top of the panel and inside the body, so the panel starts below it
        int top = ICON_SIZE + ExtensionAddonLayout.TOP_BAND;
        int room = context.panelHeight() - HEIGHT - ICON_SIZE;
        int centred = top + Math.max(0, room - ExtensionAddonLayout.TOP_BAND) / 2;
        int lowest = context.panelHeight() - HEIGHT - 2;
        int innerY = Math.max(top, Math.min(centred, lowest));

        int iconX = innerX + (WIDTH - ICON_SIZE) / 2;
        int iconY = innerY - ICON_SIZE;

        return new Placed(innerX, innerY, iconX, iconY);
    }

    /** True while the given panel relative mouse position is on one of the machine's slot cells. */
    public static boolean isOverSlot(Placed placed, int[] slot, double mouseX, double mouseY) {
        double x = placed.innerX() + slot[1];
        double y = placed.innerY() + slot[2];
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    /**
     * Geometry of the configuration page inside our panel, all in panel relative coordinates: the panel
     * itself ({@code WIDTH} x {@code HEIGHT} at {@code innerX/innerY}) and the header icon above it.
     */
    public record Placed(int innerX, int innerY, int iconX, int iconY) {
    }
}
