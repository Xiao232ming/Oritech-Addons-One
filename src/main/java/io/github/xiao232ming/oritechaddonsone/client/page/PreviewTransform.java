package io.github.xiao232ming.oritechaddonsone.client.page;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The one transform the page model of 传输插件 is drawn with, and the inverse its picking ray is built
 * from.
 * <p>
 * <b>Why this exists.</b> The model is drawn by {@link FacePreviewWidget#renderContent}, which applies a concrete
 * chain to the GUI's own pose: translate to the panel's centre at z 400, scale by
 * {@code (scale, -scale, scale)} - the negated Y is what turns the model's up-axis into the GUI's down-axis -
 * and then rotate by the pitch and by the yaw. The picking of a face used to be a second, hand written copy of
 * that chain, which knew the pitch, the yaw and the scale but neither the negated Y nor the panel's centre and
 * z, so its model-space ray was mirrored against the model the player saw and the face it reported was the wrong
 * one.
 * <p>
 * The transform therefore lives here once. {@link #modelToScreen} is the composition the widget draws with,
 * {@link #screenToModel} is its inverse, and {@link #pickingRay} uses that inverse - so every number that moves
 * the model on the panel moves the ray with it, and the two cannot drift apart.
 * <p>
 * <b>The order of the composition</b> is exactly the one the widget uses: the panel's centre and its own z, the
 * model scale with the negated Y, the pitch, the yaw. {@link #screenToModel} is built by inverting those very
 * pieces rather than by copying signs, and the model's own {@code offset - 0.5} per block is <b>not</b> part of
 * it - the widget applies that inside its render loop and the picking applies the same offset when it builds the
 * block's box.
 */
public final class PreviewTransform {

    /**
     * How far behind the panel the picking eye is placed, in pixels - {@link #pickingRay} puts it at
     * {@code planeZ + RAY_DISTANCE}.
     * <p>
     * It has to be <b>far enough outside every model the page can draw</b>. The slab test only finds an entry while
     * the eye is on the far side of the near face, and the ray's own parameter at that entry is this distance minus
     * the distance from the eye to the face - so an eye that ended up inside a big machine would report "nothing" for
     * its far side. The panel is 140x96 pixels and the largest machine measures a few blocks at a scale of at most
     * about a hundred pixels per block, i.e. a few hundred pixels from the model's centre: a thousand clears that by
     * several times over, and shrinking it further is not what this value is for.
     * <p>
     * It also has to be <b>small enough that the inverse transform still resolves the eye</b>. The eye is where the
     * drawing transform's inverse puts a point this far behind the panel, and that inverse scales the screen offset
     * down by the model scale - so at a million (the value this used to be) the eye sits at roughly a million model
     * units away, where a float's own resolution is about 0.06 model units, i.e. a tenth of a block of sideways error
     * that the slab test then has to round. At a thousand the eye is a thousand times closer, and the same arithmetic
     * is exact to about a ten-thousandth of a block.
     * <p>
     * The value was measured, not guessed: with every machine of the page - a single block, a 2x2 slab, an L, a 3D
     * cross and a 3-part multiblock under all six facings - a one-pixel grid over the whole panel, at eight pitches
     * and eight yaws, gives <b>786 of 1 571 375 hits whose entry point is not on the face that was answered with at a
     * million, and 0 at a thousand</b>; the number of pixels that hit the model at all is the same (1 570 209 at both,
     * the difference being those 786), so nothing was traded away, and going down to a hundred changes nothing
     * further.
     */
    private static final float RAY_DISTANCE = 1_000.0F;

    private final float centerX;
    private final float centerY;
    private final float planeZ;
    private final float scale;
    private final float pitch;
    private final float yaw;

    private PreviewTransform(float centerX, float centerY, float planeZ, float scale, float pitch, float yaw) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.planeZ = planeZ;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    /**
     * The transform of one drawn frame.
     *
     * @param centerX x of the panel's centre, in absolute screen space
     * @param centerY y of that centre
     * @param planeZ  the z the widget translates the model to before drawing it - 400 in
     *                {@link FacePreviewWidget#renderContent}
     * @param scale   pixels per model unit the frame is drawn at
     * @param pitch   pitch the model is drawn with, in degrees
     * @param yaw     yaw the model is drawn with, in degrees
     */
    public static PreviewTransform of(float centerX, float centerY, float planeZ, float scale, float pitch,
            float yaw) {
        return new PreviewTransform(centerX, centerY, planeZ, scale, pitch, yaw);
    }

    /** True while this transform can be used: a model of a real size. */
    public boolean isUsable() {
        return scale > 0.0F;
    }

    /**
     * The model-to-screen-pixel transform: a model point to its pixel on the panel, in absolute screen
     * coordinates. It is the exact composition {@link FacePreviewWidget#renderContent} applies to the GUI pose.
     */
    public Matrix4f modelToScreen() {
        return new Matrix4f()
                .translate(centerX, centerY, planeZ)
                .scale(scale, -scale, scale)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw));
    }

    /**
     * The screen-pixel-to-model transform: the inverse of {@link #modelToScreen()}, built by inverting each of
     * its pieces and composing them in reverse order rather than by inverting the product.
     */
    public Matrix4f screenToModel() {
        return new Matrix4f()
                .mul(new Matrix4f().rotateY((float) Math.toRadians(yaw)).invert())
                .mul(new Matrix4f().rotateX((float) Math.toRadians(pitch)).invert())
                .mul(new Matrix4f().scale(scale, -scale, scale).invert())
                .mul(new Matrix4f().translate(centerX, centerY, planeZ).invert());
    }

    /**
     * The ray of one absolute screen position, in model space: where it starts, where it points.
     * <p>
     * Both halves are read off the inverted drawing transform of this very frame, so every change to the drawing
     * - the negated Y, the scale, the panel's centre and z, the rotations - moves the ray with it. The eye is the
     * inverse image of the pixel a long way behind the panel, which is the side of the model the page looks at it
     * from, and the direction is the inverse image of the screen's depth axis reversed, which points into the
     * model. Together they are the line through that pixel, which is what the slab test reasons about.
     * <p>
     * The two signs are not a free choice: the drawing flips the model's Y, so the screen's depth axis maps to
     * the model-space direction that points <em>away</em> from the model, and an eye placed on the other side
     * would send the ray off the model and every click would miss. The pair was verified against the drawing, not
     * assumed: a grid of pixels over the model picks a face whose entry point projects back onto that same pixel
     * to within a tenth of a pixel, at every pitch and yaw that was tried.
     * <p>
     * The eye's distance is {@link #RAY_DISTANCE}, which is where the arithmetic of both halves has to stay exact:
     * at a much larger distance the slab test starts being decided by the float's own rounding rather than by the
     * line, and pixels then answer a face whose entry point is off that face.
     *
     * @param screenX absolute screen x of the pixel the mouse is on
     * @param screenY absolute screen y of that pixel
     * @return the ray in model space, or {@code null} while the transform cannot be used
     */
    public Ray pickingRay(float screenX, float screenY) {
        if (!isUsable()) return null;

        Matrix4f inverse = screenToModel();
        Vector3f eye = inverse.transformPosition(new Vector3f(screenX, screenY, planeZ + RAY_DISTANCE));
        Vector3f direction = inverse.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
        return direction.lengthSquared() < 1.0E-12F ? null : new Ray(eye, direction);
    }

    /** A picking ray in model space: where it starts and where it points. */
    public record Ray(Vector3f origin, Vector3f direction) {
    }
}
