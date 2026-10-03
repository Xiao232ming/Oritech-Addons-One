package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * How a transfer mode is drawn and named, in the one place both transfer pages read it.
 * <p>
 * The cube net page ({@link TransferAddonPage}) and the 3D preview page ({@link TransferPreviewAddonPage}) show
 * the same thing - what one face of the machine does with its items - and the colours and words for it are part
 * of the feature, not of a page: a player who learned on the net that blue means "input" has to see blue on the
 * model as well. The mode colours, the plate list, the mode names and the "occupied face" treatment therefore
 * live here once, and a page only decides <em>where</em> it paints them.
 * <p>
 * The colours follow the ones Oritech uses for its own item arrows, so a face configured here reads like a face
 * of Oritech's own inventory proxy: blue while the machine <b>takes</b> items in, orange while it <b>gives</b>
 * them out, and the two halves side by side while it does both.
 */
public final class TransferFaceStyle {

    /** Translucent blue of a face that takes items in. */
    public static final int INPUT_FILL = 0x553B82F6;
    public static final int INPUT_EDGE = 0xFF2563EB;
    public static final int INPUT_EDGE_DARK = 0xFF1D4ED8;
    /** Translucent orange of a face that gives items out. */
    public static final int OUTPUT_FILL = 0x55F59E0B;
    public static final int OUTPUT_EDGE = 0xFFD97706;
    public static final int OUTPUT_EDGE_DARK = 0xFFB45309;

    /**
     * Gold of the border around a face a plugin of this mod occupies. Bright enough to read over the blue and the
     * orange of the mode washes it is drawn on top of.
     */
    public static final int GOLD = 0xFFFFD24A;

    /** Size of the square drawn around a face, and of the plates that offer the modes. */
    public static final int FACE_SIZE = 18;
    /** The modes the pickers offer, in the order their plates are laid out. */
    public static final List<TransferMode> MODES = List.of(TransferMode.INPUT, TransferMode.OUTPUT, TransferMode.BOTH);

    private TransferFaceStyle() {
    }

    /**
     * The wash of one face by its mode: blue while it takes items in, orange while it gives them out, and half blue
     * half orange - the input half on the left, the output half on the right - while it does both. A face that
     * transfers nothing carries no wash at all.
     */
    @Nullable
    public static AddonFaceNet.Wash wash(TransferMode mode) {
        return switch (mode) {
            case INPUT -> (graphics, x, y, size) -> {
                graphics.fill(x, y, x + size, y + size, INPUT_FILL);
                graphics.fill(x, y, x + size, y + 1, INPUT_EDGE);
                graphics.fill(x, y + size - 1, x + size, y + size, INPUT_EDGE_DARK);
                graphics.fill(x, y, x + 1, y + size, INPUT_EDGE);
                graphics.fill(x + size - 1, y, x + size, y + size, INPUT_EDGE_DARK);
            };
            case OUTPUT -> (graphics, x, y, size) -> {
                graphics.fill(x, y, x + size, y + size, OUTPUT_FILL);
                graphics.fill(x, y, x + size, y + 1, OUTPUT_EDGE);
                graphics.fill(x, y + size - 1, x + size, y + size, OUTPUT_EDGE_DARK);
                graphics.fill(x, y, x + 1, y + size, OUTPUT_EDGE);
                graphics.fill(x + size - 1, y, x + size, y + size, OUTPUT_EDGE_DARK);
            };
            case BOTH -> (graphics, x, y, size) -> {
                // Half blue, half orange: the input half on the left, the output half on the right. The two halves
                // meet directly - no line is drawn between them, the colours are the whole marker.
                int half = size / 2;
                graphics.fill(x, y, x + half, y + size, INPUT_FILL);
                graphics.fill(x + half, y, x + size, y + size, OUTPUT_FILL);
                graphics.fill(x, y, x + size, y + 1, INPUT_EDGE);
                graphics.fill(x, y + size - 1, x + size, y + size, OUTPUT_EDGE_DARK);
                graphics.fill(x, y, x + 1, y + size, INPUT_EDGE);
                graphics.fill(x + size - 1, y, x + size, y + size, OUTPUT_EDGE_DARK);
            };
            case NONE -> null;
        };
    }

    /**
     * Gold border around one face: the face of the machine a plugin of this mod occupies, so no pipe or hopper can
     * be there and no mode on it could describe a connection. The server refuses such a face as well (see
     * {@code TransferPreviewAddonBlockEntity#setTransferConfig}).
     */
    public static void drawGoldBorder(GuiGraphicsExtractor graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + 1, GOLD);
        graphics.fill(x, y + size - 1, x + size, y + size, GOLD);
        graphics.fill(x, y, x + 1, y + size, GOLD);
        graphics.fill(x + size - 1, y, x + size, y + size, GOLD);
    }

    /** Language key of a mode name, e.g. {@code gui.oritechaddonsone.transfer.mode.input}. */
    public static String modeKey(TransferMode mode) {
        return "gui.oritechaddonsone.transfer.mode." + mode.name().toLowerCase(Locale.ROOT);
    }
}
