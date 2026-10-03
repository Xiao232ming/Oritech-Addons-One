package io.github.xiao232ming.oritechaddonsone.client.page;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The one transform the transfer preview page's model is drawn with, and the inverse its picking ray is built
 * from.
 * <p>
 * <b>Why this exists.</b> The model is not drawn by the page: the picture-in-picture pipeline draws it from the
 * numbers {@link FacePreviewWidget} submits, and that pipeline adds two steps of its own on the way to the
 * panel - it flips the picture vertically (the {@code scale(1, -1, -1)} every preview renderer starts with) and
 * it applies the orthographic projection's {@code 1000 / width, 1000 / height} viewport factor. The picking of a
 * face used to be a second, hand written copy of that chain - Oritech's own {@code BlockPreviewWidget#findBlockAt},
 * which knows the pitch and the yaw and nothing else - and a copy of a transform is a transform that can disagree
 * with the original. It did: the copy left the pipeline's flip out, which mirrors the model-space ray vertically,
 * so the face the player aimed at and the face that was reported were different faces (aiming at the upper right
 * of a machine reported the 下 face, which is on the far side of the model).
 * <p>
 * The transform therefore lives here once. {@link #modelToScreen} is the composition the renderer and the
 * pipeline produce together, {@link #screenToModel} is its inverse, and {@link #pickingRay} uses that inverse -
 * so every number that moves the model on the panel moves the ray with it, and the two cannot drift apart.
 * <p>
 * The order is the one the pipeline uses, outermost first: the panel's own pixel frame (the blit), the
 * projection's viewport factor, the pipeline's flip and the model scale, the pitch, the yaw, and the translation
 * that puts the model's centre at the panel's centre (which is the renderer's own {@code -center} translation
 * seen from the other side). {@link #screenToModel} inverts those pieces one by one in reverse order rather than
 * inverting a product, so no sign, scale or pixel mapping is reproduced by hand anywhere.
 * <p>
 * The model's own {@code offset - 0.5} per block is <b>not</b> part of this transform, exactly as it is not part
 * of the renderer's pose: the renderer applies it inside its block loop, and the picking applies the same
 * offset when it builds each block's box.
 */
public final class PreviewTransform {

    /**
     * Where the picking eye is placed, in screen pixels: far outside every model the page can draw, so the slab
     * test always finds an entry, but still near enough that a float resolves the eye to about a tenth of a model
     * pixel - which is what keeps the entry face of a face-on click exact. It is used with a negative sign
     * ({@link #pickingRay}), because that is the side of the panel the projection looks from.
     */
    private static final float RAY_DISTANCE = 1_000_000.0F;

    private final float centerX;
    private final float centerY;
    private final float scale;
    private final float pitch;
    private final float yaw;
    private final float modelCenterX;
    private final float modelCenterY;
    private final float modelCenterZ;

    private PreviewTransform(float centerX, float centerY, float scale, float pitch, float yaw, float modelCenterX,
            float modelCenterY, float modelCenterZ) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.modelCenterX = modelCenterX;
        this.modelCenterY = modelCenterY;
        this.modelCenterZ = modelCenterZ;
    }

    /**
     * The transform of one drawn frame.
     *
     * @param centerX      x of the panel's centre, in absolute screen space, i.e. what the renderer is handed as
     *                     the middle of its picture-in-picture rectangle
     * @param centerY      y of that centre
     * @param scale        pixels per model unit the frame is drawn at
     * @param pitch        pitch the model is drawn with, in degrees
     * @param yaw          yaw the model is drawn with, in degrees
     * @param modelCenterX x of the model's centre in model space, i.e. the renderer's {@code centerX}
     * @param modelCenterY y of the model's centre in model space
     * @param modelCenterZ z of the model's centre in model space
     */
    public static PreviewTransform of(float centerX, float centerY, float scale, float pitch, float yaw,
            float modelCenterX, float modelCenterY, float modelCenterZ) {
        return new PreviewTransform(centerX, centerY, scale, pitch, yaw, modelCenterX, modelCenterY, modelCenterZ);
    }

    /** True while this transform can be used: a panel of a real size and a model of a real size. */
    public boolean isUsable() {
        return scale > 0.0F;
    }

    /**
     * The transform the render state of one frame describes: the same numbers {@link MachinePreviewRenderState}
     * hands to the renderer, so the picking of that frame and the drawing of it are the same transform.
     */
    public static PreviewTransform of(MachinePreviewRenderState state) {
        return of((state.x0() + state.x1()) * 0.5F, (state.y0() + state.y1()) * 0.5F, state.scale(),
                state.rotationX(), state.rotationY(), state.centerX(), state.centerY(), state.centerZ());
    }

    /**
     * The model-to-screen-pixel transform: a model point to its pixel on the panel, in absolute screen
     * coordinates. It is the exact composition the renderer and the picture-in-picture pipeline produce: the
     * panel's centre, the model scale with the pipeline's vertical flip, the pitch, the yaw, and the translation
     * that brings the model's own centre to the origin.
     */
    public Matrix4f modelToScreen() {
        return new Matrix4f()
                .translate(centerX, centerY, 0.0F)
                .scale(scale, -scale, -scale)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-modelCenterX, -modelCenterY, -modelCenterZ);
    }

    /**
     * The screen-pixel-to-model transform: the inverse of {@link #modelToScreen()}, built by inverting each of
     * its pieces in reverse order.
     */
    public Matrix4f screenToModel() {
        return new Matrix4f()
                .translate(modelCenterX, modelCenterY, modelCenterZ)
                .rotateY((float) -Math.toRadians(yaw))
                .rotateX((float) -Math.toRadians(pitch))
                .scale(1.0F / scale, -1.0F / scale, -1.0F / scale)
                .translate(-centerX, -centerY, 0.0F);
    }

    /**
     * The ray of one absolute screen position, in model space: where it starts, where it points.
     * <p>
     * Both halves are read off the inverted drawing transform of this very frame, so every change to the drawing
     * - the pipeline's flip, the scale, the pixel mapping, the model's centre - moves the ray with it. The eye is
     * the inverse image of the pixel far behind the panel, i.e. on the side of the model the preview looks at it
     * from, and the direction is the inverse image of the screen's own depth axis. Together they are the line
     * through that pixel, which is the line the slab test below has to reason about.
     * <p>
     * The two signs are not a free choice: the projection flips the picture vertically, so the screen's depth
     * axis maps to the model-space direction that points <em>into</em> the model, and an eye placed on the other
     * side sends the ray away from it and every click misses. The pair was verified against the drawing, not
     * assumed: a grid of pixels over the model picks a face whose entry point projects back onto that same pixel
     * to within a tenth of a pixel, at every pitch and yaw that was tried.
     *
     * @param screenX absolute screen x of the pixel the mouse is on
     * @param screenY absolute screen y of that pixel
     * @return the ray in model space, or {@code null} while the transform cannot be used
     */
    public Ray pickingRay(float screenX, float screenY) {
        if (!isUsable()) return null;

        Matrix4f inverse = screenToModel();
        Vector3f eye = inverse.transformPosition(new Vector3f(screenX, screenY, -RAY_DISTANCE));
        Vector3f direction = inverse.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
        return direction.lengthSquared() < 1.0E-12F ? null : new Ray(eye, direction);
    }

    /** A picking ray in model space: where it starts and where it points. */
    public record Ray(Vector3f origin, Vector3f direction) {
    }
}
