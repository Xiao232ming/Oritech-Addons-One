package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

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
 * on the block entity (see {@code ProxyFaceBindings}). The geometry of the net and of the configuration page
 * lives in {@link AddonFaceNet} and {@link AddonPickerPanel}, next to the other pages that draw them, so the
 * drawing, the click handling and the tooltips all measure the same rectangles.
 */
public final class ProxyPickerState {

    /** The addon and face whose picker is currently open, or {@code null}. */
    @Nullable
    private static BlockPos openPos;
    @Nullable
    private static Direction openFace;

    /**
     * Slot the open picker has bound, until the menu's own binding catches up.
     * <p>
     * The binding travels to the server, which writes it into the block entity, and it comes back through
     * that face's container data slot - so it is one tick old when
     * {@code ExtensionAddonMenu#proxySlotOf} first answers, and the dark plate of the selected cell would
     * light up three frames after the click, which reads as a flicker. The page therefore draws this one
     * first and falls back to the menu, and it is dropped with the picker it belongs to.
     */
    @Nullable
    private static Integer pendingSlot;

    /** Slot layouts the server sent, keyed by addon position and face. */
    private static final Map<BlockPos, Map<Direction, List<int[]>>> LAYOUTS = new HashMap<>();

    private ProxyPickerState() {
    }

    /** Opens the picker of one face and clears whatever layout was cached for it. */
    public static void open(BlockPos pos, Direction face) {
        openPos = pos;
        openFace = face;
        pendingSlot = null;
        var perFace = LAYOUTS.get(pos);
        if (perFace != null) perFace.remove(face);
    }

    /** Closes the picker (a click outside the picker, or the screen going away). */
    public static void close() {
        openPos = null;
        openFace = null;
        pendingSlot = null;
    }

    /**
     * Remembers the slot the open picker has just bound, so the selected plate appears in the same frame
     * as the click. Only the picker calls this, and {@link #close()} drops it again.
     */
    public static void select(int slot) {
        pendingSlot = slot;
    }

    /** The slot the open picker bound a moment ago, or {@code null} while it has bound nothing yet. */
    @Nullable
    public static Integer pendingSlot() {
        return pendingSlot;
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

    /** The layout last sent for that face, or {@code null} while the server has not answered yet (the page
     * then shows "loading"/"no machine" instead of an empty grid).
     */
    @Nullable
    public static List<int[]> layout(BlockPos pos, Direction face) {
        var perFace = LAYOUTS.get(pos);
        return perFace == null ? null : perFace.get(face);
    }
}
