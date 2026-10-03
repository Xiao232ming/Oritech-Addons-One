package io.github.xiao232ming.oritechaddonsone.client.page;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * Client side state of the Extension Transfer page: which face's configuration page is open and which
 * settings it just applied.
 * <p>
 * Everything here is presentation: what a face really does is stored on the block entity and applied by the
 * server (see {@code TransferFaceModes}). The page needs nothing from the server - the three modes are always
 * known - so unlike the Item Proxy page's picker this state has no layout cache.
 */
public final class ExtensionTransferPickerState {

    /** The addon and face whose configuration page is currently open, or {@code null}. */
    @Nullable
    private static BlockPos openPos;
    @Nullable
    private static Direction openFace;

    /** The mode and automation flag the open page has set, in one piece so they cannot drift apart. */
    public record Pending(TransferMode mode, boolean automation) {
    }

    /**
     * Settings the open page has applied, until the menu's own value catches up.
     * <p>
     * They travel to the server, which writes them into the block entity, and they come back through that
     * face's container data slot - so they are one tick old when {@code ExtensionAddonMenu#transferMode} first
     * answers, and a plate or a checkbox would react three frames after the click, which reads as a flicker.
     * The page therefore draws these first and falls back to the menu, and they are dropped with the page they
     * belong to.
     */
    @Nullable
    private static Pending pending;

    private ExtensionTransferPickerState() {
    }

    /** Opens the configuration page of one face. */
    public static void open(BlockPos pos, Direction face) {
        openPos = pos;
        openFace = face;
        pending = null;
    }

    /** Closes the page (a click outside it, or the screen going away). */
    public static void close() {
        openPos = null;
        openFace = null;
        pending = null;
    }

    /**
     * Remembers what the open page has just set, so its plate and its checkbox appear in the same frame as
     * the click. Only the page calls this, and {@link #close()} drops it again.
     */
    public static void select(TransferMode mode, boolean automation) {
        pending = new Pending(mode, automation);
    }

    /** What the open page set a moment ago, or {@code null} while it has set nothing yet. */
    @Nullable
    public static Pending pending() {
        return pending;
    }

    /** True while the configuration page of exactly this face is open. */
    public static boolean isOpen(BlockPos pos, Direction face) {
        return openPos != null && openPos.equals(pos) && openFace == face;
    }

    /**
     * True while any face's configuration page of this block is open, i.e. while the Extension Transfer page
     * covers the panel with it. The screen asks this to hide the player's own inventory slots.
     */
    public static boolean isOpen(BlockPos pos) {
        return openPos != null && openPos.equals(pos);
    }

    /** Drops everything this page remembered, called when the GUI closes. */
    public static void clear() {
        close();
    }
}
