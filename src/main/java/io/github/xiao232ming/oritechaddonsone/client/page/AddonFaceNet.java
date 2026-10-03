package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.client.FaceTextures;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * The unfolded cube net the item pages draw: where each of the block's six faces sits inside the panel and
 * how it is painted.
 * <p>
 * Both the Item Proxy page and the Extension Transfer page show the same net - the block's own per-face
 * textures (see {@link FaceTextures}) unfolded around the face the player looks at - and differ only in what
 * they paint on top of a face, which is the {@link Wash}. Keeping the geometry in one place is what makes
 * "what the player sees is what the player clicks" true for both pages: the drawing, the hit test and the
 * hover test all read the very same panel relative rectangles.
 * <p>
 * The frame is built around the block's interface face ({@link FaceTextures#front()}) instead of rotating the
 * drawing, so the net looks the same whatever direction the block was placed in while every cell still shows
 * the texture its own face really has.
 */
public final class AddonFaceNet {

    /**
     * Size of one face of the net, in pixels ({@link ExtensionAddonLayout#PROXY_FACE}). 18 keeps the whole
     * net inside the free part of the panel.
     */
    public static final int FACE = ExtensionAddonLayout.PROXY_FACE;
    /** Left edge of the net, in panel space, from the layout so panel and page cannot drift apart. */
    private static final int NET_X = ExtensionAddonLayout.PROXY_NET_X;
    /** Top edge of the net, in panel space, below the panel's title label. */
    private static final int NET_Y = ExtensionAddonLayout.PROXY_NET_Y;

    /** Cell of the face the player looks at: the interface face, in the middle of the cross. */
    private static final int[] CELL_FRONT = {1, 1};
    /** Cell of the face opposite it: the far end of the row. */
    private static final int[] CELL_BACK = {3, 1};
    private static final int[] CELL_RIGHT = {2, 1};
    private static final int[] CELL_LEFT = {0, 1};
    private static final int[] CELL_UP = {1, 0};
    private static final int[] CELL_DOWN = {1, 2};

    private AddonFaceNet() {
    }

    /**
     * Paints what a page has to say about one face - the green "this face proxies something" marker of the
     * Item Proxy page, the input/output colours of the Extension Transfer page - on top of that face's
     * texture and under its outline.
     */
    @FunctionalInterface
    public interface Wash {
        /** Paints the marker of one face into the cell at {@code x}/{@code y}, {@code size} pixels wide. */
        void paint(GuiGraphicsExtractor graphics, int x, int y, int size);
    }

    /**
     * Cell of every face inside the net, in face units, in the one frame both pages always draw: the addon's
     * interface face in the middle, the face opposite it at the right end of the row, and the four remaining
     * faces around them - the viewer's right and left next to the middle, the other two above and below it.
     */
    public static Map<Direction, int[]> cells(FaceTextures textures) {
        var front = textures.front();
        var right = rightOf(front);
        var up = upOf(front);

        var cells = new EnumMap<Direction, int[]>(Direction.class);
        cells.put(front, CELL_FRONT);
        cells.put(front.getOpposite(), CELL_BACK);
        cells.put(right, CELL_RIGHT);
        cells.put(right.getOpposite(), CELL_LEFT);
        cells.put(up, CELL_UP);
        cells.put(up.getOpposite(), CELL_DOWN);
        return cells;
    }

    /**
     * The direction to the viewer's right while they look at the given interface face from outside, i.e. the
     * face that goes into the cell right of the middle.
     */
    private static Direction rightOf(Direction front) {
        // A vertical interface face (a flat addon) has no inherent right; the four faces around it all carry
        // the side texture, so east is as good as any and keeps the frame deterministic.
        return front.getAxis().isVertical() ? Direction.EAST : front.getCounterClockWise();
    }

    /** The direction the viewer sees above the given interface face, i.e. the cell above the middle one. */
    private static Direction upOf(Direction front) {
        return switch (front) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    /**
     * Left edge of one face of the net, in panel space. This is the single definition of where a face is;
     * {@link #drawFace}, {@link #faceAt} and both pages' hit tests read it, so they cannot drift apart.
     */
    public static int localX(Map<Direction, int[]> cells, Direction face) {
        return NET_X + cells.get(face)[0] * FACE;
    }

    /** Top edge of one face of the net, in panel space; see {@link #localX}. */
    public static int localY(Map<Direction, int[]> cells, Direction face) {
        return NET_Y + cells.get(face)[1] * FACE;
    }

    /**
     * The face under the mouse, or {@code null} while it is not on the net. The mouse position is panel
     * relative, so this reads the very same rectangles {@link #drawFace} draws.
     */
    @Nullable
    public static Direction faceAt(Map<Direction, int[]> cells, double mouseX, double mouseY) {
        for (var face : Direction.values()) {
            double x = localX(cells, face);
            double y = localY(cells, face);
            if (mouseX >= x && mouseX < x + FACE && mouseY >= y && mouseY < y + FACE) return face;
        }
        return null;
    }

    /** Draws one face of the net: its texture, the page's {@link Wash} over it, and a 1px outline. */
    public static void drawFace(AddonPageContext context, GuiGraphicsExtractor graphics, Direction face,
            Map<Direction, int[]> cells, FaceTextures textures, @Nullable Wash wash) {
        int x = context.screenX(localX(cells, face));
        int y = context.screenY(localY(cells, face));

        var texture = textures.face(face);
        // The flat addon's model samples its side textures from the lower half of the 16x16 texture while the
        // net draws the full sprite, so those faces are drawn mirrored. A mirror is the same rectangle with
        // the V range swapped, which this blit overload - (x0, y0, x1, y1, u0, u1, v0, v1) - takes directly.
        // Passing the arguments of the 1.21.1 overload (0f, 0f, 1f, 1f) here asked for a zero area UV slice
        // and drew nothing at all, which is what left the four horizontal faces of a flat addon blank.
        if (texture.flipVertically()) {
            graphics.blit(texture.texture(), x, y, x + FACE, y + FACE, 0f, 1f, 1f, 0f);
        } else {
            graphics.blit(texture.texture(), x, y, x + FACE, y + FACE, 0f, 1f, 0f, 1f);
        }

        if (wash != null) wash.paint(graphics, x, y, FACE);

        // 1px outline so neighbouring faces of the net stay distinguishable
        graphics.fill(x, y, x + FACE, y + 1, AddonPanelStyle.SLOT_DARK);
        graphics.fill(x, y + FACE - 1, x + FACE, y + FACE, AddonPanelStyle.SLOT_DARK);
        graphics.fill(x, y, x + 1, y + FACE, AddonPanelStyle.SLOT_DARK);
        graphics.fill(x + FACE - 1, y, x + FACE, y + FACE, AddonPanelStyle.SLOT_DARK);
    }
}
