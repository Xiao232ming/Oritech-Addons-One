package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * What the transfer preview page hands to the renderer: the machine's block states with their offsets, plus the face
 * the mouse is currently over and the part of the machine that face belongs to.
 * <p>
 * Oritech's own preview state ({@code BlockPreviewRenderState}) carries block states only - its renderer draws those
 * and nothing else - so the hover highlight needs a state of this mod's own, registered as its own
 * {@code PictureInPictureRenderer} (see {@link MachinePreviewPipRenderer}). The geometry fields below copy Oritech's
 * state one for one and are filled from the very numbers {@link FacePreviewWidget} submits its render with, so the
 * highlight is drawn in the model's own pose rather than in a pose of its own.
 *
 * @param entries    the blocks this state draws, one per drawn part of the machine, in the same offset space the
 *                   picking uses (see {@link FacePreviewWidget#partOffsets()}) - the model has to be drawn at the
 *                   offsets it is picked at, or a click and the face under it would point at different cells
 * @param overlays   the markings of the machine's faces: for every face that is configured or occupied, the part that
 *                   carries it and what it is (see {@link Overlay}), drawn in the model's own pose over the blocks
 * @param face       the face of the machine the mouse is over, i.e. the face to highlight, or {@code null} while the
 *                   mouse is not on the model
 * @param offset     the part of the machine that face belongs to, in model space, or {@code null} while no face is
 *                   highlighted. A multiblock machine is several blocks of world, and the highlight belongs on the
 *                   part the mouse is really over rather than on the core block, so the renderer translates it there
 *                   before it draws the quad (see {@link MachinePreviewPipRenderer})
 * @param rotationX  pitch of the model
 * @param rotationY  yaw of the model, the one value the renderer really draws with (see {@link FacePreviewWidget})
 * @param centerX    x of the model's centre, in model space
 * @param centerY    y of the model's centre, in model space
 * @param centerZ    z of the model's centre, in model space
 * @param partialTick tick fraction of the frame, for the machine's block entity renderer
 * @param x0         left edge of the preview panel, in GUI space
 * @param y0         top edge of the preview panel, in GUI space
 * @param x1         right edge of the preview panel, in GUI space
 * @param y1         bottom edge of the preview panel, in GUI space
 * @param scale      pixels per model unit the model is drawn at, in the panel's own GUI pixels (the picture-in-picture
 *                   pipeline multiplies it by the GUI scale itself, so it must not be pre-multiplied here)
 * @param pose       the GUI pose the panel is drawn in
 * @param scissorArea the screen area the panel is clipped to, or {@code null} while nothing clips it
 * @param bounds     the area this state can draw to, i.e. panel and clip intersected
 */
public record MachinePreviewRenderState(
        List<Entry> entries,
        List<Overlay> overlays,
        @Nullable Direction face,
        @Nullable Vec3i offset,
        float rotationX,
        float rotationY,
        float centerX,
        float centerY,
        float centerZ,
        float partialTick,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        Matrix3x2f pose,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    /**
     * Builds the state of one frame, working the bounds out the way Oritech's own preview state does, so the GUI
     * renderer culls the panel exactly as it culls Oritech's.
     */
    public static MachinePreviewRenderState of(List<Entry> entries, List<Overlay> overlays, @Nullable Direction face,
            @Nullable Vec3i offset, float rotationX, float rotationY, float centerX, float centerY, float centerZ,
            float partialTick, int x0, int y0, int x1, int y1, float scale, Matrix3x2f pose,
            @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle panel = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        ScreenRectangle bounds = scissorArea != null ? scissorArea.intersection(panel) : panel;

        return new MachinePreviewRenderState(List.copyOf(entries), List.copyOf(overlays), face, offset, rotationX,
                rotationY, centerX, centerY, centerZ, partialTick, x0, y0, x1, y1, scale, new Matrix3x2f(pose),
                scissorArea, bounds);
    }

    /** One block of the model: its state, its block entity and where it sits in model space. */
    public record Entry(BlockState state, @Nullable BlockEntity entity, Vec3i offset) {
    }

    /**
     * One marking of one face of the machine: the part that carries it, which of its faces it is, the mode that face
     * is configured with and whether a plugin of this mod occupies it.
     * <p>
     * The renderer turns this into geometry - a wash in the mode's colours ({@link TransferFaceStyle#wash}) and, for
     * an occupied face, the gold outline of {@link TransferFaceStyle#GOLD}. A face that is both keeps both, so an
     * outline can be read over a wash; a face with neither is not in this list at all.
     */
    public record Overlay(Vec3i offset, Direction face, TransferMode mode, boolean occupied) {
    }
}
