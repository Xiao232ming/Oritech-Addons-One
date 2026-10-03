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

/**
 * Draws the transfer preview page's model: exactly the machine and one translucent white quad on the face the mouse is
 * over.
 * <p>
 * <b>Why this exists at all.</b> Oritech's own preview draws through a picture-in-picture state that carries block
 * states and nothing else ({@code BlockPreviewRenderState} / {@code BlockPreviewPipRenderer}), and the GUI API next to
 * it can only fill axis aligned rectangles - neither can put a quad <em>on a face of the model</em> in the model's own
 * pose. So the page submits a state of this mod's own ({@link MachinePreviewRenderState}) and this renderer draws both
 * the machine and the highlight in the one pose. The block drawing below is Oritech's, repeated because the state being
 * drawn is not Oritech's.
 * <p>
 * The model is drawn the way Oritech draws it: the same pose (pitch, yaw and the centre of the model), the same
 * orthographic picture-in-picture target, the same item lighting, and entries translated to {@code offset - 0.5}. The
 * highlight then needs nothing of its own: it is a quad on the hovered face's plane in model space, so the pose that
 * puts the block on screen puts it on that block's face too.
 * <p>
 * It is registered per state class, which is why this is one renderer and not an addition to Oritech's.
 */
@EventBusSubscriber(modid = OritechAddonsOne.MODID, value = Dist.CLIENT)
public class MachinePreviewPipRenderer extends PictureInPictureRenderer<MachinePreviewRenderState> {

    /** Context a block state is resolved into a model with; the stock preview uses its own, identical one. */
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    /** Light the preview is drawn with: the packed value Oritech's own preview uses, i.e. full light. */
    private static final int LIGHT = 15728880;

    /** Translucent white of the highlight: white, at the alpha Oritech uses for the block its own overlay points at. */
    private static final int HIGHLIGHT_COLOR = 0x55FFFFFF;

    /** Distance the highlight floats off the face, so it never trades depth with the block face behind it. */
    private static final float HIGHLIGHT_LIFT = 0.002F;

    /** How far the highlight's edges stay inside the face, so it reads as a marking on the block, not as a lid. */
    private static final float HIGHLIGHT_INSET = 0.06F;

    /**
     * The highlight quad's corners, as factors of the face's own half extents: counter clockwise seen from outside the
     * block, which is the winding the quad's render type culls by - the wrong way round it would be drawn on the inside
     * of the machine instead of on the face.
     */
    private static final float[][] HIGHLIGHT_CORNERS = {
            {1.0F, -1.0F},
            {1.0F, 1.0F},
            {-1.0F, 1.0F},
            {-1.0F, -1.0F}};

    /**
     * One axis of the coordinate frame the highlight's quad is built in, per face: {@code [0]} is the face's outward
     * normal and {@code [1]} / {@code [2]} are the two axes that span the face.
     * <p>
     * They are chosen so that {@code u} cross {@code v} is the normal - the frame {@link #HIGHLIGHT_CORNERS} is wound
     * in.
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
        // is rotated and moved so that its centre lands on the panel's centre
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

        // after the machine, so the quad blends over the block; its own render type lifts it off the face in view
        // space, which is what keeps it from z-fighting with the face it lies on
        drawHighlight(renderState, poseStack);
    }

    /**
     * Draws the translucent quad on the hovered face, in the pose that is still active from the model above: the quad
     * is built in model space around the face's own centre, so it lands on the face whatever the model is rotated to.
     * <p>
     * Nothing is drawn while no face is hovered, i.e. while the mouse is not on the model - the page's picking keeps
     * working unchanged, because this only ever adds geometry to the frame and never touches the mouse.
     */
    private void drawHighlight(MachinePreviewRenderState renderState, PoseStack poseStack) {
        Direction face = renderState.face();
        if (face == null) return;

        var pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.debugFilledBox());
        float[] normal = basis(face, 0);
        float[] u = basis(face, 1);
        float[] v = basis(face, 2);
        float centre = 0.5F + HIGHLIGHT_LIFT;
        float size = 0.5F - HIGHLIGHT_INSET;

        for (float[] corner : HIGHLIGHT_CORNERS) {
            float x = normal[0] * centre + (u[0] * corner[0] + v[0] * corner[1]) * size;
            float y = normal[1] * centre + (u[1] * corner[0] + v[1] * corner[1]) * size;
            float z = normal[2] * centre + (u[2] * corner[0] + v[2] * corner[1]) * size;
            consumer.addVertex(pose, x, y, z).setColor(HIGHLIGHT_COLOR);
        }
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
