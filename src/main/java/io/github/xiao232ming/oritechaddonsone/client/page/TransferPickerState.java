package io.github.xiao232ming.oritechaddonsone.client.page;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * Client side state of the Extension Transfer page: which face's mode picker is open and which mode it just
 * set.
 * <p>
 * Everything here is presentation: what a face really does is stored on the block entity and applied by the
 * server (see {@code TransferFaceModes}). The picker needs nothing from the server - the three modes are
 * always known - so unlike the Item Proxy page's picker this state has no layout cache.
 */
public final class TransferPickerState {

    /** The addon and face whose mode picker is currently open, or {@code null}. */
    @Nullable
    private static BlockPos openPos;
    @Nullable
    private static Direction openFace;

    /**
     * Mode the open picker has set, until the menu's own value catches up.
     * <p>
     * The mode travels to the server, which writes it into the block entity, and it comes back through that
     * face's container data slot - so it is one tick old when {@code ExtensionAddonMenu#transferMode} first
     * answers, and the plate of the chosen mode would light up three frames after the click, which reads as a
     * flicker. The page therefore draws this one first and falls back to the menu, and it is dropped with the
     * picker it belongs to.
     */
    @Nullable
    private static TransferMode pendingMode;

    private TransferPickerState() {
    }

    /** Opens the mode picker of one face. */
    public static void open(BlockPos pos, Direction face) {
        openPos = pos;
        openFace = face;
        pendingMode = null;
    }

    /** Closes the picker (a click outside it, or the screen going away). */
    public static void close() {
        openPos = null;
        openFace = null;
        pendingMode = null;
    }

    /**
     * Remembers the mode the open picker has just set, so its plate appears in the same frame as the click.
     * Only the picker calls this, and {@link #close()} drops it again.
     */
    public static void select(TransferMode mode) {
        pendingMode = mode;
    }

    /** The mode the open picker set a moment ago, or {@code null} while it has set nothing yet. */
    @Nullable
    public static TransferMode pendingMode() {
        return pendingMode;
    }

    /** True while the picker of exactly this face is open. */
    public static boolean isOpen(BlockPos pos, Direction face) {
        return openPos != null && openPos.equals(pos) && openFace == face;
    }

    /**
     * True while any face's picker of this block is open, i.e. while the Extension Transfer page covers the
     * panel with its mode picker. The screen asks this to hide the player's own inventory slots.
     */
    public static boolean isOpen(BlockPos pos) {
        return openPos != null && openPos.equals(pos);
    }

    /** Drops everything this page remembered, called when the GUI closes. */
    public static void clear() {
        close();
    }
}
