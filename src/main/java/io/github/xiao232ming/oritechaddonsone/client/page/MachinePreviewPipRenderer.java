package io.github.xiao232ming.oritechaddonsone.client.page;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * Draws the transfer preview page's model: every drawn part of the machine, the markings of its configured and
 * occupied faces, and the outline of the face the mouse is over.
 * <p>
 * <b>Why this exists at all.</b> Oritech's own preview draws through a picture-in-picture state that carries block
 * states and nothing else ({@code BlockPreviewRenderState} / {@code BlockPreviewPipRenderer}), and the GUI API next to
 * it can only fill axis aligned rectangles - neither can put a quad <em>on a face of the model</em> in the model's own
 * pose. So the page submits a state of this mod's own ({@link MachinePreviewRenderState}) and this renderer draws both
 * the machine and its markings in the one pose. The block drawing below is Oritech's, repeated because the state being
 * drawn is not Oritech's.
 * <p>
 * The model is drawn the way Oritech draws it: the same pose (pitch, yaw and the centre of the model), the same
 * orthographic picture-in-picture target, the same item lighting, and entries translated to {@code offset - 0.5}. The
 * markings then need nothing of their own but the part they belong to: they are quads on a face's plane in model
 * space, so the pose that puts the part on screen puts them on that part's face too.
 * <p>
 * It is registered per state class, which is why this is one renderer and not an addition to Oritech's.
 */
@EventBusSubscriber(modid = OritechAddonsOne.MODID, value = Dist.CLIENT)
public class MachinePreviewPipRenderer extends PictureInPictureRenderer<MachinePreviewRenderState> {

    /** Context a block state is resolved into a model with; the stock preview uses its own, identical one. */
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    /** Light the preview is drawn with: the packed value Oritech's own preview uses, i.e. full light. */
    private static final int LIGHT = 15728880;

    /**
     * Translucent white of the hover marking: the whole face under the mouse, at an alpha low enough to lighten what
     * the face already carries rather than to cover it.
     * <p>
     * <b>The alpha is the compromise between "the marking is unmistakable" and "the mode colour survives".</b> The
     * mode washes are themselves translucent at {@code 0x55}, so the hover is composited over them: at {@code 0x38}
     * white, an input face's blue {@code 0x553B82F6} composites to {@code 0x55669DF8} and an output face's orange
     * {@code 0x55F59E0B} to {@code 0x55F7B341} - both still unmistakably their own hue, both clearly lighter than the
     * unmarked face, and the layer's own {@code 0x55} alpha still lets the machine's texture show through. A heavier
     * veil drains the colour (at {@code 0x55} the same blue comes out {@code 0x557CACF9}, visibly greyer and closer
     * to the orange's own brightness, so the two modes start to look alike); a lighter one stops reading over a bright
     * block texture.
     */
    private static final int HIGHLIGHT_COLOR = 0x38FFFFFF;

    /**
     * Distance the hover fill floats off the face, so it never trades depth with the block face behind it. It is
     * above {@link #MARKING_LIFT} + {@link #MARKING_STEP}, i.e. above a mode wash and above the gold outline of an
     * occupied face, so the hover is the topmost marking of the three.
     */
    private static final float HIGHLIGHT_LIFT = 0.004F;

    /**
     * The half extent of one cell's face, in model units: a block model is one unit wide and the drawing translates
     * the pose to the cell's centre, so its face is the square {@code +-0.5} around that origin. Every marking is
     * expressed in these units (see {@link #drawQuad}).
     */
    private static final float FACE_HALF_EXTENT = 0.5F;

    /** How far a wash's edges stay inside the face, in model units, so it reads as a marking and not as a lid. */
    private static final float WASH_INSET = 0.03F;

    /**
     * Distance a face's markings float off that face, and the step between two markings on the same face, so the wash
     * and the gold outline of one face never trade depth with each other.
     */
    private static final float MARKING_LIFT = 0.001F;
    private static final float MARKING_STEP = 0.001F;

    /** Thickness of the gold outline of an occupied face, in model units. */
    private static final float OUTLINE_THICKNESS = 0.05F;

    /**
     * One axis of the coordinate frame the markings and the hover outline are built in, per face: {@code [0]} is the
     * face's outward normal and {@code [1]} / {@code [2]} are the two axes that span the face. {@code [1]} is the
     * face's <b>horizontal</b> axis as seen from outside, which is the one a "both" wash is split across.
     * <p>
     * They are chosen so that {@code u} cross {@code v} is the normal, which is the winding the quads are drawn in.
     */
    private static final float[][][] FACE_AXES = {
            {{0.0F, -1.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}},       // DOWN
            {{0.0F, 1.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -1.0F}},      // UP
            {{0.0F, 0.0F, -1.0F}, {-1.0F, 0.0F, 0.0F}, {0.0F, 1.0F, 0.0F}},     // NORTH
            {{0.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, 1.0F, 0.0F}},       // SOUTH
            {{-1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, 1.0F, 0.0F}},      // WEST
            {{1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -1.0F}, {0.0F, 1.0F, 0.0F}}};     // EAST

    public MachinePreviewPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    /** Registers this renderer for the page's own state, on the mod's event bus. */
    @SubscribeEvent
    static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(MachinePreviewRenderState.class, MachinePreviewPipRenderer::new);
    }

    @Override
    public Class<MachinePreviewRenderState> getRenderStateClass() {
        return MachinePreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(MachinePreviewRenderState renderState, PoseStack poseStack) {
        var minecraft = Minecraft.getInstance();
        FeatureRenderDispatcher featureDispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage submitNodes = featureDispatcher.getSubmitNodeStorage();
        var modelState = new BlockModelRenderState();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);

        // the pose Oritech's preview renderer sets up: y down the picture-in-picture target is flipped, then the model
        // is rotated and moved so that its centre lands on the panel's centre.
        // <p>
        // <b>The z flip here is not a second one.</b> {@code PictureInPictureRenderer#prepare} scales the pose by
        // {@code -guiScale * renderState.scale()} in z, and that is the pipeline's own flip; this negates z again, so
        // on this branch the model is drawn mirrored front to back with respect to the pipeline's convention. It is
        // harmless for what the page does - the model is drawn with an orthographic projection whose depth only
        // decides what occludes what, and the markings are lifted off their faces rather than depth tested against
        // them - and it is <b>not</b> what makes a face and the cursor disagree: measured through the whole chain
        // (prepare's pose, the orthographic projection, the viewport and the blit), the pixel a model point lands on
        // is identical with and without this flip, to 0.0000 px at gui scales 1 to 4.
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.rotationX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotationY()));
        poseStack.translate(-renderState.centerX(), -renderState.centerY(), -renderState.centerZ());

        for (var entry : renderState.entries()) {
            poseStack.pushPose();
            Vec3i offset = entry.offset();
            poseStack.translate(offset.getX() - 0.5F, offset.getY() - 0.5F, offset.getZ() - 0.5F);

            minecraft.getBlockModelResolver().update(modelState, entry.state(), DISPLAY_CONTEXT);
            modelState.submit(poseStack, submitNodes, LIGHT, OverlayTexture.NO_OVERLAY, 0);
            if (entry.entity() != null) {
                BlockEntityRenderState entityState = minecraft.getBlockEntityRenderDispatcher()
                        .tryExtractRenderState(entry.entity(), renderState.partialTick(), null, null);
                if (entityState != null) {
                    CameraRenderState cameraState =
                            minecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
                    minecraft.getBlockEntityRenderDispatcher().submit(entityState, poseStack, submitNodes, cameraState);
                }
            }

            poseStack.popPose();
        }

        featureDispatcher.renderAllFeatures();

        // After the machine, and in this order, so the three markings read over each other the way they are meant to:
        // the mode wash lies under everything, the gold outline of an occupied face is drawn over that wash (that is
        // what makes it readable on a coloured face), and the white hover outline is drawn last, on top of both.
        drawOverlays(renderState, poseStack);
        drawHighlight(renderState, poseStack);
    }

    /**
     * Draws the markings of the machine's faces, in the pose that is still active from the model above: each overlay
     * names the part that carries it ({@link MachinePreviewRenderState.Overlay#offset()}), so its quad lands on that
     * part's face whatever the model is rotated to.
     * <p>
     * Two kinds of marking, and a face can carry both:
     * <ul>
     *     <li>the <b>mode wash</b> - the translucent blue of an input face, the orange of an output face, and both
     *     halves side by side on a face that does both, taken from {@link TransferFaceStyle#wash} so the model and the
     *     cube net page colour a face the same way. The wash covers the inner part of the face and leaves a rim free
     *     for the outline.</li>
     *     <li>the <b>gold outline</b> of a face a plugin of this mod occupies, in
     *     {@link TransferFaceStyle#GOLD} - the same gold the cube net page draws around that face. It is an outline
     *     rather than a fill, so a wash underneath it stays visible and the two meanings can be read at once.</li>
     * </ul>
     * Both are lifted off the face by {@link #MARKING_LIFT} / {@link #MARKING_STEP}, so they never trade depth with
     * the block face or with each other.
     */
    private void drawOverlays(MachinePreviewRenderState renderState, PoseStack poseStack) {
        for (var overlay : renderState.overlays()) {
            poseStack.pushPose();
            poseStack.translate(overlay.offset().getX(), overlay.offset().getY(), overlay.offset().getZ());
            drawWash(poseStack, overlay.face(), overlay.mode());
            if (overlay.occupied()) {
                drawOutline(poseStack, overlay.face(), MARKING_LIFT + MARKING_STEP, OUTLINE_THICKNESS,
                        TransferFaceStyle.GOLD);
            }
            poseStack.popPose();
        }
    }

    /**
     * The translucent wash of one face by its mode: the fill of {@link TransferFaceStyle#wash}'s colours, as geometry
     * on the face's inner area - the whole face for input or output, and the two halves side by side for both, split
     * across the face's own horizontal axis exactly like the net splits its cell.
     * <p>
     * A face that transfers nothing gets no wash.
     */
    private void drawWash(PoseStack poseStack, Direction face, TransferMode mode) {
        if (mode == TransferMode.NONE) return;

        float near = -FACE_HALF_EXTENT + WASH_INSET;
        float far = FACE_HALF_EXTENT - WASH_INSET;
        if (mode != TransferMode.BOTH) {
            drawQuad(poseStack, face, near, far, near, far, MARKING_LIFT,
                    mode == TransferMode.INPUT ? TransferFaceStyle.INPUT_FILL : TransferFaceStyle.OUTPUT_FILL);
            return;
        }

        // half blue, half orange: the input half on the left of the face's horizontal axis and the output half on the
        // right, which is the split the net's cell uses (TransferFaceStyle#wash)
        float middle = 0.0F;
        drawQuad(poseStack, face, near, middle, near, far, MARKING_LIFT, TransferFaceStyle.INPUT_FILL);
        drawQuad(poseStack, face, middle, far, near, far, MARKING_LIFT, TransferFaceStyle.OUTPUT_FILL);
    }

    /**
     * The outline of one face: a frame {@link #OUTLINE_THICKNESS} wide inside the face's own edge, built from the four
     * strips between the outer and the inner rectangle, all in model units of the cell the pose is translated to.
     */
    private void drawOutline(PoseStack poseStack, Direction face, float lift, float thickness, int color) {
        float outer = FACE_HALF_EXTENT;
        float inner = FACE_HALF_EXTENT - thickness;

        // the two strips across the face and the two down its sides; the corners are covered by both
        drawQuad(poseStack, face, -outer, outer, inner, outer, lift, color);
        drawQuad(poseStack, face, -outer, outer, -outer, -inner, lift, color);
        drawQuad(poseStack, face, -outer, -inner, -inner, inner, lift, color);
        drawQuad(poseStack, face, inner, outer, -inner, inner, lift, color);
    }

    /**
     * One quad on one face of the part the pose is currently translated to.
     * <p>
     * <b>The units are the block's own.</b> The part is drawn by translating the pose to its cell and then submitting
     * a block model, which is one model unit wide and centred on that cell - so the cell's face is the square
     * {@code +-0.5} around the translated origin. This quad is built in exactly that space: {@code uMin}..{@code uMax}
     * and {@code vMin}..{@code vMax} are in <em>model units</em> (never more than 0.5, the face's half extent), the
     * axes of {@link #FACE_AXES} are unit vectors, and their product is the vertex, so a marking is the same size and
     * in the same place as the face it marks. Using those unit axes as if they were half extents is what made the
     * markings twice the size of a block.
     * <p>
     * The quad is lifted off the face by {@code lift} along its outward normal so it never trades depth with the
     * block face, and its corners are wound counter clockwise seen from outside; the render type takes plain vertex
     * colours, so a vertex needs nothing but a position and a colour.
     */
    private void drawQuad(PoseStack poseStack, Direction face, float uMin, float uMax, float vMin, float vMax,
            float lift, int color) {
        var pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.debugQuads());
        float[] normal = basis(face, 0);
        float[] u = basis(face, 1);
        float[] v = basis(face, 2);
        float plane = 0.5F + lift;

        // counter clockwise seen from outside the face, which is the winding the quad's render type culls by
        float[][] corners = {
                {uMax, vMin},
                {uMax, vMax},
                {uMin, vMax},
                {uMin, vMin}};

        for (float[] corner : corners) {
            float x = normal[0] * plane + u[0] * corner[0] + v[0] * corner[1];
            float y = normal[1] * plane + u[1] * corner[0] + v[1] * corner[1];
            float z = normal[2] * plane + u[2] * corner[0] + v[2] * corner[1];
            consumer.addVertex(pose, x, y, z).setColor(color);
        }
    }

    /**
     * Draws the hover marking of the face the mouse is over, in the pose that is still active from the model above: a
     * translucent white fill of the <b>whole face</b> of the part the picking answered with, so it lands on that
     * face whatever the model is rotated to.
     * <p>
     * The part is the one the picking answered with ({@link MachinePreviewRenderState#offset()}): a multiblock machine
     * is several cells of model space, and the marking of a face on one of them belongs on that cell - drawn at the
     * model origin it would mark the controller's cell instead, on the far side of the machine.
     * <p>
     * <b>It is a fill of the entire face and not an outline</b>, which is what the feature is for: an outline only
     * answers "is the cursor on this face" for the cursor positions inside the ring, so a player pointing at the middle
     * of a face saw nothing and read it as the marking not following the mouse. The fill covers the face the picking
     * answers with exactly, so the highlighted area <em>is</em> the area that face owns - cursor on the marking and
     * cursor on the face are the same statement.
     * <p>
     * <b>It blends, it does not overwrite.</b> {@link #HIGHLIGHT_COLOR} is white at a low alpha, so what the face
     * already carried stays readable underneath: a mode wash keeps its hue - the blue of an input face composites to
     * {@code 0x55669DF8} and the orange of an output face to {@code 0x55F7B341}, both lighter than the unmarked face
     * and both still their own colour - and a face with no mode reads as lit rather than as painted.
     * <p>
     * The gold outline of an occupied face is drawn over the fill (see {@link #drawOverlays} running before this), so
     * the "a plugin stands here" reading survives being hovered, and the lift is raised above that outline's so the
     * white is never hidden by it.
     * <p>
     * Nothing is drawn while no face is hovered, i.e. while the mouse is not on the model - the page's picking keeps
     * working unchanged, because this only ever adds geometry to the frame and never touches the mouse.
     */
    private void drawHighlight(MachinePreviewRenderState renderState, PoseStack poseStack) {
        Direction face = renderState.face();
        Vec3i offset = renderState.offset();
        if (face == null || offset == null) return;

        poseStack.pushPose();
        poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
        drawQuad(poseStack, face, -FACE_HALF_EXTENT, FACE_HALF_EXTENT, -FACE_HALF_EXTENT, FACE_HALF_EXTENT,
                HIGHLIGHT_LIFT, HIGHLIGHT_COLOR);
        poseStack.popPose();
    }

    /** One axis of {@link #FACE_AXES} for a face. */
    private static float[] basis(Direction face, int axis) {
        return FACE_AXES[face.ordinal()][axis];
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "oritechaddonsone_machine_preview";
    }
}
