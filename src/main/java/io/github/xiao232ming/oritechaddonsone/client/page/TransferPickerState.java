package io.github.xiao232ming.oritechaddonsone.client.page;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * Client side state of the 3D page of 传输插件: which <b>face of which cell</b> of the model was clicked and is
 * being configured in the modal page, plus the settings that page has just applied.
 * <p>
 * It is the transfer page's counterpart of {@link ExtensionTransferPickerState}, which belongs to the cube net page and is
 * deliberately <b>not</b> shared with it: the two pages can be open on the same block at the same time - an
 * Extension Addon that holds both transfer plugins offers both tabs - and the screen closes the cube net page's
 * picker whenever that page is not the visible one, which would close this page's modal on the frame it opened.
 * A state of its own is what lets the two modals exist independently, while the shape of the state, the pending
 * value and the way the page reads them are the same.
 * <p>
 * <b>The selection is a cell and a face, not a direction.</b> The page configures one face of one cell of the
 * machine's structure (see {@code CellFaceModes}), so a direction alone would not say which of the machine's faces
 * the modal belongs to - "north" names as many faces as the structure has cells. The cell is the offset the model's
 * own picking reports, i.e. relative to the machine's controller block, which is the frame the server validates and
 * stores.
 * <p>
 * Everything here is presentation: what a cell-face really does is stored on the block entity and applied by the
 * server (see {@code CellFaceModes}), and the three modes are always known on this side, so this state needs
 * nothing from the server to draw a plate.
 */
public final class TransferPickerState {

    /** The block whose cell-face is being configured, or {@code null} while no configuration page is open. */
    @Nullable
    private static BlockPos openPos;
    /** The cell of that block's machine that is being configured, or {@code null} while none is. */
    @Nullable
    private static Vec3i openCell;
    /** The face of that cell that is being configured, or {@code null} while none is. */
    @Nullable
    private static Direction openFace;

    /** The mode and automation flag the open page has set, in one piece so they cannot drift apart. */
    public record Pending(TransferMode mode, boolean automation) {
    }

    /**
     * Settings the open page has applied, until the server's own answer catches up.
     * <p>
     * They travel to the server, which writes them into the block entity, and they come back in the whole map the
     * server sends after the change ({@code TransferNetworking.FaceModes}) - so they are one round trip old when
     * {@link TransferFaceState} first answers, and a plate or a checkbox would react three frames after the click,
     * which reads as a flicker. The page therefore draws these first and falls back to the synced map, and they are
     * dropped with the page they belong to.
     */
    @Nullable
    private static Pending pending;

    private TransferPickerState() {
    }

    /** Opens the configuration page of one face of one cell of the machine. */
    public static void open(BlockPos pos, Vec3i cell, Direction face) {
        openPos = pos;
        openCell = cell;
        openFace = face;
        pending = null;
    }

    /** Closes the page: the right mouse button, a click outside it, or the screen going away. */
    public static void close() {
        openPos = null;
        openCell = null;
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

    /** True while the configuration page of exactly this cell-face is open. */
    public static boolean isOpen(BlockPos pos, Vec3i cell, Direction face) {
        return openPos != null && openPos.equals(pos) && cell.equals(openCell) && openFace == face;
    }

    /** True while any cell-face's configuration page of this block is open, i.e. while the modal covers the page. */
    public static boolean isOpen(BlockPos pos) {
        return openPos != null && openPos.equals(pos);
    }

    /** The cell the open configuration page belongs to, or {@code null} while none is open. */
    @Nullable
    public static Vec3i openCell() {
        return openCell;
    }

    /**
     * The face the open configuration page belongs to, or {@code null} while none is open.
     * <p>
     * The face alone is enough for everything the modal itself does - the plates, the prompt and the automation row
     * are about the face, and which cell it belongs to is only what the page names in a packet.
     */
    @Nullable
    public static Direction openFace() {
        return openFace;
    }

    /** Drops everything, called when the GUI closes. */
    public static void clear() {
        close();
    }
}
