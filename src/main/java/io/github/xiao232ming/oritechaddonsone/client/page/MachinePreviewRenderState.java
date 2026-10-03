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

/**
 * What the transfer preview page hands to the renderer: the machine's own block plus the face the mouse is currently
 * over.
 * <p>
 * Oritech's own preview state ({@code BlockPreviewRenderState}) carries block states only - its renderer draws those
 * and nothing else - so the hover highlight needs a state of this mod's own, registered as its own
 * {@code PictureInPictureRenderer} (see {@link MachinePreviewPipRenderer}). The geometry fields below copy Oritech's
 * state one for one and are filled from the very numbers {@link FacePreviewWidget} submits its render with, so the
 * highlight is drawn in the model's own pose rather than in a pose of its own.
 *
 * @param block      the machine's block state, drawn at the model's origin
 * @param entity     its block entity, or {@code null} while the block has none - the renderer draws the block state
 *                   either way
 * @param face       the face of the machine the mouse is over, i.e. the face to highlight, or {@code null} while the
 *                   mouse is not on the model
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
 * @param scale      pixels per model unit the model is drawn at
 * @param pose       the GUI pose the panel is drawn in
 * @param scissorArea the screen area the panel is clipped to, or {@code null} while nothing clips it
 * @param bounds     the area this state can draw to, i.e. panel and clip intersected
 */
public record MachinePreviewRenderState(
        BlockState block,
        @Nullable BlockEntity entity,
        @Nullable Direction face,
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
    public static MachinePreviewRenderState of(BlockState block, @Nullable BlockEntity entity, @Nullable Direction face,
            float rotationX, float rotationY, float centerX, float centerY, float centerZ, float partialTick, int x0,
            int y0, int x1, int y1, float scale, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle panel = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        ScreenRectangle bounds = scissorArea != null ? scissorArea.intersection(panel) : panel;

        return new MachinePreviewRenderState(block, entity, face, rotationX, rotationY, centerX, centerY, centerZ,
                partialTick, x0, y0, x1, y1, scale, new Matrix3x2f(pose), scissorArea, bounds);
    }

    /**
     * The entries this state draws, in Oritech's own entry shape: just the machine, which is what the preview is for -
     * its addons and the plugin itself are deliberately not part of the model (see
     * {@code TransferPreviewState#build}).
     */
    public List<Entry> entries() {
        return List.of(new Entry(block, entity, Vec3i.ZERO));
    }

    /** One block of the model: its state, its block entity and where it sits in model space. */
    public record Entry(BlockState state, @Nullable BlockEntity entity, Vec3i offset) {
    }
}
