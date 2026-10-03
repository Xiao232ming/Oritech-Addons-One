package io.github.xiao232ming.oritechaddonsone.client.page;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * Client side state of the 3D preview page (传输插件): which face of the model was clicked and is being configured
 * in the modal page, plus the settings that page has just applied.
 * <p>
 * It is the preview page's counterpart of {@link TransferPickerState}, which belongs to the cube net page and is
 * deliberately <b>not</b> shared with it: the two pages can be open on the same block at the same time - an
 * Extension Addon that holds both transfer plugins offers both tabs - and the screen closes the cube net page's
 * picker whenever that page is not the visible one, which would close this page's modal on the frame it opened.
 * A state of its own is what lets the two modals exist independently, while the shape of the state, the pending
 * value and the way the page reads them are the same.
 * <p>
 * Everything here is presentation: what a face really does is stored on the block entity and applied by the
 * server (see {@code TransferFaceModes}), and the three modes are always known on this side, so this state needs
 * nothing from the server.
 */
public final class TransferPreviewPickerState {

    /** The block whose face is being configured, or {@code null} while no configuration page is open. */
    @Nullable
    private static BlockPos openPos;
    /** The face of that block that is being configured, or {@code null} while none is. */
    @Nullable
    private static Direction openFace;

    /** The mode and automation flag the open page has set, in one piece so they cannot drift apart. */
    public record Pending(TransferMode mode, boolean automation) {
    }

    /**
     * Settings the open page has applied, until the menu's own value catches up.
     * <p>
     * They travel to the server, which writes them into the block entity, and they come back through that face's
     * container data slot - so they are one tick old when {@code ExtensionAddonMenu#transferMode} first answers,
     * and a plate or a checkbox would react three frames after the click, which reads as a flicker. The page
     * therefore draws these first and falls back to the menu, and they are dropped with the page they belong to.
     */
    @Nullable
    private static Pending pending;

    private TransferPreviewPickerState() {
    }

    /** Opens the configuration page of one face. */
    public static void open(BlockPos pos, Direction face) {
        openPos = pos;
        openFace = face;
        pending = null;
    }

    /** Closes the page: the right mouse button, a click outside it, or the screen going away. */
    public static void close() {
        openPos = null;
        openFace = null;
        pending = null;
    }

    /**
     * Remembers what the open page has just set, so its plate and its checkbox appear in the same frame as the
     * click. Only the page calls this, and {@link #close()} drops it again.
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

    /** True while any face's configuration page of this block is open, i.e. while the modal covers the page. */
    public static boolean isOpen(BlockPos pos) {
        return openPos != null && openPos.equals(pos);
    }

    /** Drops everything, called when the GUI closes. */
    public static void clear() {
        close();
    }
}
