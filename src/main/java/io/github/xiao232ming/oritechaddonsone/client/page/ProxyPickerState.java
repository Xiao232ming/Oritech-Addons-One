package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * Client side state of the Item Proxy page: which face's slot picker is open, which slots the machine of an
 * addon offers, and where Oritech's own configuration page is placed inside our panel.
 * <p>
 * The slot layout is asked for from the server (see {@code ProxyNetworking.RequestPicker}), because the
 * client may not have the machine's chunk loaded - which is exactly the case the wireless dock exists for.
 * The layout is cached per addon and face until the GUI is closed; the page only shows it while the picker
 * is open.
 * <p>
 * Everything here is presentation: the binding that really makes a face proxy the machine inventory lives
 * on the block entity (see {@code ProxyFaceBindings}). The geometry lives here as well, next to the state,
 * so the drawing, the click handling and the tooltips of the page all measure the same rectangle.
 */
public final class ProxyPickerState {

    /** The addon and face whose picker is currently open, or {@code null}. */
    @Nullable
    private static BlockPos openPos;
    @Nullable
    private static Direction openFace;

    /** Slot layouts the server sent, keyed by addon position and face. */
    private static final Map<BlockPos, Map<Direction, List<int[]>>> LAYOUTS = new HashMap<>();

    private ProxyPickerState() {
    }

    /** Opens the picker of one face and clears whatever layout was cached for it. */
    public static void open(BlockPos pos, Direction face) {
        openPos = pos;
        openFace = face;
        var perFace = LAYOUTS.get(pos);
        if (perFace != null) perFace.remove(face);
    }

    /** Closes the picker (a click outside the picker, or the screen going away). */
    public static void close() {
        openPos = null;
        openFace = null;
    }

    /** True while the picker of exactly this face is open. */
    public static boolean isOpen(BlockPos pos, Direction face) {
        return Objects.equals(openPos, pos) && openFace == face;
    }

    /**
     * True while any face's picker of this block is open, i.e. while the Item Proxy page covers the panel
     * with Oritech's configuration page. The screen asks this to hide the player's own inventory slots,
     * which are drawn - and clickable - only while the panel is closed.
     */
    public static boolean isOpen(BlockPos pos) {
        return openPos != null && openPos.equals(pos);
    }

    /** Drops everything this page remembered, called when the GUI closes. */
    public static void clear() {
        close();
        LAYOUTS.clear();
    }

    /** Stores a layout the server sent. */
    public static void putLayout(BlockPos pos, Direction face, List<Integer> flat) {
        var slots = new ArrayList<int[]>(flat.size() / 3);
        for (int i = 0; i + 2 < flat.size(); i += 3) {
            slots.add(new int[]{flat.get(i), flat.get(i + 1), flat.get(i + 2)});
        }
        LAYOUTS.computeIfAbsent(pos.immutable(), key -> new HashMap<>()).put(face, List.copyOf(slots));
    }

    /**
     * The layout last sent for that face, or {@code null} while the server has not answered yet (the page
     * then shows "loading"/"no machine" instead of an empty grid).
     */
    @Nullable
    public static List<int[]> layout(BlockPos pos, Direction face) {
        var perFace = LAYOUTS.get(pos);
        return perFace == null ? null : perFace.get(face);
    }

    // ------------------------------------------------------------------ geometry of Oritech's page

    /**
     * Places Oritech's own 176x100 configuration page inside our panel, in <b>panel relative</b>
     * coordinates.
     * <p>
     * Oritech's screen is the same size as the page it reproduces, so the machine's GUI slots keep the
     * coordinates {@link rearth.oritech.util.ScreenProvider#getGuiSlots()} gives them and the prompt stays
     * at Oritech's own y. Our panel is
     * {@code ExtensionAddonLayout.WIDTH - ItemProxyAddonPage.PANEL_WIDTH = 24} pixels wider, so the page is
     * centred horizontally with a twelve pixel gutter on each side, and it is centred vertically in the
     * room between the title icon - Oritech draws the icon of a widget screen 27 pixels above the panel's
     * top edge, so ours is a header floating over the panel body - and the section that holds the drawn
     * panel and the player inventory.
     * <p>
     * The placement is clamped to the drawn panel, so the configuration page and its icon are always fully
     * inside our GUI, whatever the window, the slot count or the plugin rows: on a panel with three plugin
     * rows the panel is taller than the page needs and the page is simply centred, and on the two taller
     * pages it is pushed up until it fits. It is never resized or scaled, so it always lands in the window.
     */
    public static Placed place(AddonPageContext context, List<int[]> slots) {
        int innerX = Math.max(0, (context.panelWidth() - ItemProxyAddonPage.PANEL_WIDTH) / 2);

        // the header icon sits on top of the panel and inside the body, so the panel starts below it
        int top = ItemProxyAddonPage.ICON_SIZE + ExtensionAddonLayout.TOP_BAND;
        int room = context.panelHeight() - ItemProxyAddonPage.PANEL_HEIGHT - ItemProxyAddonPage.ICON_SIZE;
        int centred = top + Math.max(0, room - ExtensionAddonLayout.TOP_BAND) / 2;
        int lowest = context.panelHeight() - ItemProxyAddonPage.PANEL_HEIGHT - 2;
        int innerY = Math.max(top, Math.min(centred, lowest));

        int iconX = innerX + (ItemProxyAddonPage.PANEL_WIDTH - ItemProxyAddonPage.ICON_SIZE) / 2;
        int iconY = innerY - ItemProxyAddonPage.ICON_SIZE;

        return new Placed(innerX, innerY, iconX, iconY);
    }

    /** True while the given panel relative mouse position is on one of the machine's slot cells. */
    public static boolean isOverSlot(Placed placed, int[] slot, double mouseX, double mouseY) {
        double x = placed.innerX() + slot[1];
        double y = placed.innerY() + slot[2];
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    /**
     * Geometry of Oritech's configuration page inside our panel, all in panel relative coordinates: the
     * panel itself ({@code PANEL_WIDTH} x {@code PANEL_HEIGHT} at {@code innerX/innerY}) and the header icon
     * above it.
     */
    public record Placed(int innerX, int innerY, int iconX, int iconY) {
    }
}
