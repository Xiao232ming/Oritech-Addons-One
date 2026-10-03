package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * The 3D model of the transfer preview page: <b>one</b> machine, drawn inside the page's panel with the same recipe
 * Oritech's own {@code BlockPreviewWidget} uses on this branch, plus the things that page needs and Oritech's widget
 * does not offer - the markings of its faces (their mode, and the face a plugin occupies) and face picking with a
 * highlight on the face under the mouse.
 * <p>
 * <b>Why this is not Oritech's widget.</b> On 1.21.1 there is no picture in picture GUI rendering at all, so a widget
 * has to draw its blocks straight into the GUI's own pose stack - which is exactly what Oritech's
 * {@code BlockPreviewWidget#renderContent} does
 * ({@code rearth/oritech/api/screen/widgets/BlockPreviewWidget.java:124-165}): it pushes the pose, translates to the
 * panel's centre at z 400, scales by {@code (scale, -scale, scale)} - the negated Y is what turns the model's
 * up-axis into the GUI's down-axis - rotates by a <b>hard coded</b> {@code Axis.XP.rotationDegrees(30)} and by
 * {@code Axis.YP.rotationDegrees(225 + rotation)}, translates by the block's offset, and then renders the block with
 * {@code client.getBlockRenderer().renderSingleBlock(state, pose, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY)}
 * followed by the block entity renderer, all inside {@code RenderSystem.runAsFancy} (the very call at
 * {@code BlockPreviewWidget.java:145}). That recipe is what this class reproduces, on the same surface and with the
 * same light.
 * <p>
 * What Oritech's 1.21.1 widget cannot do is what the preview page is for: its {@code rotation} field is private with
 * no setter, its X rotation is a constant, it has no picking helper of any kind, and its centre and scale are private
 * as well. So this class keeps its own pitch and yaw (the defaults are Oritech's own 30 and 225 degrees), computes
 * the centre and the scale with Oritech's formula, and adds <b>face picking</b>.
 * <p>
 * <b>The picking is not a second copy of the drawing any more.</b> It used to be a hand written inverse of the
 * widget's own chain which left the negated Y, the panel's centre and its z out, so its model-space ray was mirrored
 * against the model the player saw. Both halves now come from one {@link PreviewTransform}: {@link #renderContent}
 * draws with that composition, {@link #faceAt} picks with its inverse, and the face is read off the axis whose slab
 * gave the near intersection - which is exact for a click on the middle of a face, where a comparison of the entry
 * point against the box edges would be a tie between all three axes.
 * <p>
 * <b>The model is only the machine, and it is all of the machine.</b> The page's model never contains the machine's
 * addons, the indicators of its open addon slots or the plugin block itself (see {@code TransferPreviewState#build}
 * for why). It does contain <b>every drawn part</b> of the machine: a multiblock machine is several blocks of world,
 * so one entry would make one cell of it clickable and draw one cell of it while the player looks at a whole
 * structure, and the surface the page asks for a face of would be unreachable. Oritech enumerates those parts as
 * offsets relative to the controller in the machine's own frame
 * ({@link MultiblockMachineController#getCorePositions()}), and the machine turns each into a cell of the world with
 * {@link Geometry#rotatePosition} and its facing - the very call the assembled machine makes when it looks for its
 * cores. {@link #partOffsets()} is that list, and it is what the measuring, the drawing, the picking and the markings
 * all loop over.
 * <p>
 * <b>Every drawn part is drawn at its own block state.</b> The controller is drawn at the model's origin, and each
 * other part is drawn with the block state standing at its own position in the client's level - that is the model of
 * that part, because a multiblock machine is a controller plus core blocks. The controller's state is the fallback for
 * a part whose position the client cannot read a state from (an unassembled or unloaded machine, where the model has to
 * fall back to the one block the page does know). Drawing every part is not a decoration of the picking: the two have
 * to be the same cells, or a click would be answered where nothing is drawn and the highlight of a face the player
 * really sees on a part would be painted on the wrong part.
 * <p>
 * <b>The machine's core blocks are not part of the model.</b> A machine core ({@link MachineCoreBlock}) is the tier
 * block inside an assembled machine, Oritech hides it itself once the machine uses it
 * ({@code MachineCoreBlock#getRenderShape(BlockState)} answers {@code INVISIBLE} for such a core), and it owns no
 * inventory - the inventory the page and the automation work on belongs to the controller, which sits in a cell of its
 * own. So {@link #visibleBlocks()} - the list the measuring, the drawing, the picking and the markings all read - is
 * {@link #blocks()} without the cores, and a core can neither be seen nor clicked.
 * <p>
 * <b>The centre is the centre of the drawn structure</b> ({@link #calculateSize()}), not of the controller's cell:
 * the drawing, the picking, the markings and the hover all take it from the one {@link PreviewTransform}, so what is
 * drawn, what a click answers and where a marking lands cannot drift apart.
 * <p>
 * <b>The faces are marked in the model's own space, on the part each belongs to.</b> {@link #renderContent} draws, in
 * the very pose the machine was drawn in: each configured face's mode wash, the gold outline of a face a plugin
 * occupies, and - last, on top - the white outline of the face under the mouse. That is what a screen space rectangle
 * - all the GUI's own fill can draw - cannot do: only a quad in the model's pose follows the model's rotation and lies
 * on the face the player is about to click.
 * <p>
 * <b>It is asked in absolute screen coordinates.</b> A page of this mod draws inside
 * {@code AbstractContainerScreen#renderBg}, which is called with the plain screen pose - unlike Oritech's own widget
 * screens, which translate the pose by their GUI origin and therefore ask their widgets in GUI relative space. The two
 * must not be mixed, which is why {@link #pickFace(double, double)} and {@link #hoveredFace()} work off the very
 * coordinates the page drew with. This class is its own widget rather than a subclass of Oritech's for that reason as
 * well: Oritech's {@code isMouseOver} compares against the widget's frame, which is only correct for a GUI relative
 * caller.
 */
public final class FacePreviewWidget extends UIComponent {

    /** Pitch and yaw the model is drawn with; Oritech's own defaults. */
    private static final float DEFAULT_PITCH = 30.0F;
    private static final float DEFAULT_YAW = 225.0F;

    /**
     * The z the model is translated to before it is drawn: Oritech's own 400, which keeps the model in front of the
     * GUI's own geometry. {@link PreviewTransform} has to undo it as well, which is why it is a named constant here
     * instead of a literal buried in the drawing code.
     */
    private static final float PLANE_Z = 400.0F;

    /** Translucent white of the hover outline: white, at the alpha Oritech uses for its own placement ghost. */
    private static final int HIGHLIGHT_COLOR = 0x55FFFFFF;

    /** Distance the hover outline floats off the face, so it never trades depth with the block face behind it. */
    private static final float HIGHLIGHT_LIFT = 0.002F;

    /** How thick the hover outline is, as a factor of the face's half extent. */
    private static final float HIGHLIGHT_OUTLINE_THICKNESS = 0.14F;

    /** How far a marking's edges stay inside the face, so it reads as a marking on the block, not as a lid. */
    private static final float HIGHLIGHT_INSET = 0.06F;

    /**
     * Distance a face's markings float off that face, and the step between two markings on the same face, so the wash
     * and the gold outline of one face never trade depth with each other.
     */
    private static final float MARKING_LIFT = 0.001F;
    private static final float MARKING_STEP = 0.001F;

    /** Thickness of the gold outline of an occupied face, as a factor of the face's half extent. */
    private static final float OUTLINE_THICKNESS = 0.1F;

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

    private float pitch = DEFAULT_PITCH;
    private float yaw = DEFAULT_YAW;

    /** The machine this widget draws, or {@code null} while it has none to draw. */
    @Nullable
    private BlockState state;
    /** The machine's block entity, or {@code null} while its block has none. */
    @Nullable
    private BlockEntity entity;

    /** True while the centre and the radii have to be recomputed; set by {@link #setMachine} and {@link #withRotation}. */
    private boolean scaleDirty = true;

    /** Centre of the model in the model's own space, and its radii; see Oritech's own calculation. */
    private float centerX;
    private float centerY;
    private float centerZ;
    private float maxHorizontalRadius;
    private float maxVerticalRadius;

    /** Scale the last drawn frame used, or {@code 0} while nothing has been drawn yet. */
    private float renderedScale;

    /** Face the last drawn frame had under the mouse, or {@code null} while it was outside the model. */
    @Nullable
    private Direction hoveredFace;

    /**
     * Part of the machine the last drawn frame had under the mouse, or {@code null} while it was outside the model.
     * It is the offset of the block whose face {@link #hoveredFace} names, which is what puts the highlight on the
     * part the mouse is really over instead of on the controller's cell of a multiblock machine.
     */
    @Nullable
    private Vec3i hoveredOffset;

    /** Block the last {@link #pickFace(double, double)} landed on, or {@code null} while there was none. */
    @Nullable
    private Vec3i pickedOffset;

    /** Face that click landed on, or {@code null} while it was not on a block. */
    @Nullable
    private Direction pickedFace;

    /**
     * What each face of the machine is configured to do, indexed by {@link Direction#ordinal()}, or {@code null}
     * while the page has not said. The page sets this once per frame from the menu, so a mode the server has just
     * written appears with the next frame and a mode the page has only sent (its pending value) appears at once.
     */
    @Nullable
    private List<TransferMode> faceModes;

    /**
     * Faces of the machine a plugin of this mod occupies, as the bitmask the page's menu reports. {@code 0} while
     * none is, which is the case for the extender placement by the rule the page and the server share.
     */
    private int occupiedFaces;

    /** The 3D preview of one machine: 140x96 pixels, the size the page's panel reserves for it. */
    public FacePreviewWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /** Sets the one machine this widget draws, at the model's origin; the model is measured again from it. */
    public void setMachine(BlockState state, @Nullable BlockEntity entity) {
        this.state = state;
        this.entity = entity;
        this.scaleDirty = true;
    }

    /**
     * Sets what the frame after this one marks on the model: the mode of every face, and which faces a plugin
     * occupies. The page calls this once per frame, before it renders this widget, so the model always shows what the
     * menu (and the pending value the page has just sent) says rather than a copy of its own.
     *
     * @param modes    the mode of every {@link Direction}, indexed by its ordinal
     * @param occupied bitmask over {@link Direction#ordinal()} of the faces a plugin occupies
     */
    public void setFaceOverlays(List<TransferMode> modes, int occupied) {
        this.faceModes = List.copyOf(modes);
        this.occupiedFaces = occupied;
    }

    /**
     * Rotates the model and remembers the rotation, which the next picking test and the next drawn markings then use
     * - the page calls this once per frame with the rotation the player dragged it into.
     */
    public FacePreviewWidget withRotation(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.scaleDirty = true;
        return this;
    }

    /** Pitch of this model, as the page last set it. */
    public float pitch() {
        return pitch;
    }

    /** Yaw of this model, as the page last set it. */
    public float yaw() {
        return yaw;
    }

    /**
     * Remembers which of the model's faces the mouse is over - and which part of the machine that face is on - then
     * draws the model. The page's face marker is read from the remembered face, and the model marks that same face in
     * the same frame (see {@link #renderContent}).
     * <p>
     * The hover is worked out <b>before</b> the model is drawn, from the rotation and the scale of the previous frame:
     * the page sets the rotation and then renders in one call, so the previous frame's scale is this frame's as well,
     * and taking the hover first means the model of this very frame carries the highlight the mouse just moved to
     * instead of the one it was on a frame ago.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var hovered = faceAt(mouseX, mouseY);
        this.hoveredFace = hovered == null ? null : hovered.face();
        this.hoveredOffset = hovered == null ? null : hovered.offset();
        super.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * Draws every drawn part of the machine inside the panel with Oritech's own recipe for this branch - see the class
     * comment for the exact transform and why the Y scale is negative - and then, in that same pose, the markings of
     * the machine's faces and the hover outline of the face the mouse is over.
     * <p>
     * The order is the whole layering of the page: the machine's blocks first, then the mode wash of every configured
     * face, then the gold outline of an occupied face (over that wash, which is what makes it readable on a coloured
     * face), and the white hover outline last, on top of both.
     * <p>
     * The transform it applies is the one {@link PreviewTransform} describes and
     * {@link PreviewTransform#modelToScreen()} builds: the widget never writes that chain out a second time, so the
     * drawing and the picking cannot drift apart.
     */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        BlockState machineState = state;
        if (machineState == null) {
            renderedScale = 0.0F;
            hoveredFace = null;
            hoveredOffset = null;
            return;
        }

        var client = Minecraft.getInstance();
        int cx = contentX();
        int cy = contentY();
        float cw = contentWidth();
        float ch = contentHeight();

        float scale = scale(cw, ch);
        renderedScale = scale;
        if (scale <= 0.0F) return;

        var bufferSource = client.renderBuffers().bufferSource();
        graphics.pose().pushPose();
        // the composition PreviewTransform#modelToScreen describes, applied to the pose the GUI already has
        graphics.pose().translate(cx + cw / 2.0F, cy + ch / 2.0F, PLANE_Z);
        graphics.pose().scale(scale, -scale, scale);
        graphics.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        graphics.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        // the model's origin is the centre of the drawn structure, which is where the picking and the markings put it
        // as well; Oritech's own "-0.5 + offset" cancels against it for a block at offset zero
        graphics.pose().translate(-centerX, -centerY, -centerZ);

        RenderSystem.runAsFancy(() -> {
            for (var part : visibleBlocks()) {
                graphics.pose().pushPose();
                // the part's own cell, in the same model space the picking tests
                graphics.pose().translate(part.offset().getX(), part.offset().getY(), part.offset().getZ());

                if (part.state().getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) {
                    client.getBlockRenderer().renderSingleBlock(part.state(), graphics.pose(), bufferSource,
                            0xF000F0, OverlayTexture.NO_OVERLAY);
                }

                if (part.entity() != null) {
                    BlockEntityRenderer<BlockEntity> entityRenderer =
                            client.getBlockEntityRenderDispatcher().getRenderer(part.entity());
                    if (entityRenderer != null) {
                        entityRenderer.render(part.entity(), delta, graphics.pose(), bufferSource, 0xF000F0,
                                OverlayTexture.NO_OVERLAY);
                    }
                }

                graphics.pose().popPose();
            }

            drawOverlays(graphics.pose());
            drawHighlight(graphics.pose());

            RenderSystem.setShaderLights(new Vector3f(-1.5F, -0.5F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F));
            bufferSource.endBatch();
            Lighting.setupFor3DItems();
        });
        graphics.pose().popPose();
    }

    /**
     * Draws the markings of the machine's faces, in the pose that is still active from the machine above: each marking
     * names the part that carries it (see {@link #surfaceCell(Direction)}), so its quads land on that part's face
     * whatever the model is rotated to.
     * <p>
     * Two kinds of marking, and a face can carry both:
     * <ul>
     *     <li>the <b>mode wash</b> - the translucent blue of an input face, the orange of an output face, and both
     *     halves side by side on a face that does both, taken from {@link TransferFaceStyle#wash} so the model and the
     *     cube net page colour a face the same way. The wash covers the inner part of the face and leaves a rim free
     *     for the outline.</li>
     *     <li>the <b>gold outline</b> of a face a plugin of this mod occupies, in {@link TransferFaceStyle#GOLD} -
     *     the same gold the cube net page draws around that face. It is an outline rather than a fill, so a wash
     *     underneath it stays visible and the two meanings can be read at once.</li>
     * </ul>
     * A face that is neither configured nor occupied gets no marking at all. Both kinds are lifted off the face by
     * {@link #MARKING_LIFT} / {@link #MARKING_STEP}, so they never trade depth with the block face or with each other.
     */
    private void drawOverlays(PoseStack poseStack) {
        var modes = faceModes;
        if (modes == null) return;

        for (var face : Direction.values()) {
            var mode = modes.size() > face.ordinal() ? modes.get(face.ordinal()) : TransferMode.NONE;
            boolean occupied = (occupiedFaces & 1 << face.ordinal()) != 0;
            if (mode == TransferMode.NONE && !occupied) continue;

            var offset = surfaceCell(face);
            if (offset == null) continue;

            poseStack.pushPose();
            poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
            drawWash(poseStack, face, mode);
            if (occupied) {
                drawOutline(poseStack, face, MARKING_LIFT + MARKING_STEP, OUTLINE_THICKNESS, TransferFaceStyle.GOLD);
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

        float inset = HIGHLIGHT_INSET;
        if (mode != TransferMode.BOTH) {
            drawQuad(poseStack, face, -1.0F + inset, 1.0F - inset, -1.0F + inset, 1.0F - inset, MARKING_LIFT,
                    mode == TransferMode.INPUT ? TransferFaceStyle.INPUT_FILL : TransferFaceStyle.OUTPUT_FILL);
            return;
        }

        // half blue, half orange: the input half on the left of the face's horizontal axis and the output half on the
        // right, which is the split the net's cell uses (TransferFaceStyle#wash)
        float near = -1.0F + inset;
        float far = 1.0F - inset;
        float middle = 0.0F;
        drawQuad(poseStack, face, near, middle, near, far, MARKING_LIFT, TransferFaceStyle.INPUT_FILL);
        drawQuad(poseStack, face, middle, far, near, far, MARKING_LIFT, TransferFaceStyle.OUTPUT_FILL);
    }

    /**
     * The outline of one face: a frame of {@code thickness} inside the face's own edge, built from the four strips
     * between the outer and the inner rectangle.
     */
    private void drawOutline(PoseStack poseStack, Direction face, float lift, float thickness, int color) {
        float outer = 1.0F;
        float inner = 1.0F - thickness;

        // the two strips across the face and the two down its sides; the corners are covered by both
        drawQuad(poseStack, face, -outer, outer, inner, outer, lift, color);
        drawQuad(poseStack, face, -outer, outer, -outer, -inner, lift, color);
        drawQuad(poseStack, face, -outer, -inner, -inner, inner, lift, color);
        drawQuad(poseStack, face, inner, outer, -inner, inner, lift, color);
    }

    /**
     * One quad on one face of the part the pose is currently translated to: the rectangle of the face's own
     * coordinates between {@code uMin}..{@code uMax} and {@code vMin}..{@code vMax}, each in units of the face's half
     * extent, lifted off the face by {@code lift} along its outward normal.
     * <p>
     * It is drawn with {@code RenderType#debugQuads()}, the one render type this branch offers that takes plain vertex
     * colours: its format is {@code POSITION_COLOR}, so a vertex needs nothing but a position and a colour, and it
     * carries the translucent blend. It is deliberately the quad type and not {@code debugFilledBox()}, which is the
     * same thing with back face culling on: the model is drawn through a negated Y scale, so which way round a quad
     * ends up on screen is a property of that transform rather than of this code, and a marking that disappears on
     * some faces would be worse than the culling it saves. The depth is handled by the lift alone - a marking really
     * sits in front of the face it marks rather than relying on a render type's own depth offset.
     * <p>
     * The vertices go into the buffer source the machine was drawn with, so the {@code endBatch} in
     * {@link #renderContent} flushes them in that same frame, pose and order.
     */
    private void drawQuad(PoseStack poseStack, Direction face, float uMin, float uMax, float vMin, float vMax,
            float lift, int color) {
        var pose = poseStack.last();
        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.debugQuads());
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
     * Draws the outline of the hovered face, in the pose that is still active from the machine above: the outline is
     * built in model space on the face's own edge of the part the mouse is over, so it lands on that face whatever the
     * model is rotated to.
     * <p>
     * The part is the one the picking answered with ({@link #hoveredOffset}): a multiblock machine is several cells of
     * model space, and the outline of a face on one of them belongs on that cell - drawn at the model origin it would
     * mark the controller's cell instead, on the far side of the machine.
     * <p>
     * It is an <b>outline and not a wash</b>, and it is drawn last: the face under the mouse may be coloured by its
     * mode or ringed in gold, and a translucent white fill over either of those would only muddy the meaning it
     * carries. The white edge sits on top of both and leaves the colour in the middle readable.
     * <p>
     * Nothing is drawn while no face is hovered, i.e. while the mouse is not on the model - the page's picking keeps
     * working unchanged, because this only ever adds geometry to the frame and never touches the mouse.
     */
    private void drawHighlight(PoseStack poseStack) {
        Direction face = hoveredFace;
        Vec3i offset = hoveredOffset;
        if (face == null || offset == null) return;

        poseStack.pushPose();
        poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
        drawOutline(poseStack, face, HIGHLIGHT_LIFT, HIGHLIGHT_OUTLINE_THICKNESS, HIGHLIGHT_COLOR);
        poseStack.popPose();
    }

    /** One axis of {@link #FACE_AXES} for a face. */
    private static float[] basis(Direction face, int axis) {
        return FACE_AXES[face.ordinal()][axis];
    }

    /** Face the last drawn frame had under the mouse, or {@code null} while it was outside the model. */
    @Nullable
    public Direction hoveredFace() {
        return hoveredFace;
    }

    /**
     * The face a click at these absolute screen coordinates selected, or {@code null} while the click did not land on
     * the model. The answer is remembered as {@link #pickedFace()}, which the page then colours and configures.
     */
    @Nullable
    public Direction pickFace(double mouseX, double mouseY) {
        this.pickedOffset = null;
        this.pickedFace = null;

        var hit = faceAt(mouseX, mouseY);
        if (hit == null) return null;

        this.pickedOffset = hit.offset();
        this.pickedFace = hit.face();
        return this.pickedFace;
    }

    /** Face the last {@link #pickFace(double, double)} landed on, or {@code null} while there was none. */
    @Nullable
    public Direction pickedFace() {
        return pickedFace;
    }

    /** Offset of the block the last {@link #pickFace(double, double)} landed on, or {@code null}. */
    @Nullable
    public Vec3i pickedOffset() {
        return pickedOffset;
    }

    /**
     * True while the given absolute screen position is over the <b>model</b> - the widget's frame without the padding
     * its surface keeps, because that is where the model is really drawn. It is used instead of
     * {@code UIComponent#isMouseOver} for readability only: the two agree for an absolute caller, which this widget
     * has by construction (see the class comment).
     */
    public boolean isOverModel(double mouseX, double mouseY) {
        int padding = getPadding().left();
        return mouseX >= getX() + padding && mouseX < getX() + getWidth() - padding
                && mouseY >= getY() + getPadding().top() && mouseY < getY() + getHeight() - getPadding().bottom();
    }

    /**
     * The scale the model is drawn with, from Oritech's own calculation: the smaller of the two ratios between the
     * panel's half size and the model's radii, with Oritech's own 0.98 margin. {@link #calculateSize()} is what
     * refreshes the radii and the centre it reads, and it only runs while the model changed.
     */
    private float scale(float availableWidth, float availableHeight) {
        if (scaleDirty) calculateSize();

        if (maxHorizontalRadius <= 0.0F || maxVerticalRadius <= 0.0F) return 0.0F;

        float widthScale = availableWidth * 0.5F / maxHorizontalRadius;
        float heightScale = availableHeight * 0.5F / maxVerticalRadius;
        return Math.min(widthScale, heightScale) * 0.98F;
    }

    /**
     * Recomputes the centre and the two radii of the model - Oritech's own calculation, repeated here because its
     * fields are private: the centre is the middle of the model's bounding box, and the radii are the largest
     * horizontal and the largest pitch-projected vertical distance of any point of that box from the centre.
     * Measuring all eight corners of every position instead of its middle is what keeps a model that is taller than
     * it is wide inside the panel at every rotation.
     * <p>
     * The positions are <b>every drawn part</b> of the machine ({@link #visibleBlocks()}), so the centre is the centre
     * of the structure the player sees rather than of the controller's cell. A core contributes nothing: it is neither
     * drawn nor clickable, and letting it into the bounding box would only push the model off centre.
     */
    private void calculateSize() {
        var positions = new ArrayList<Vec3i>();
        if (state != null) {
            for (var part : visibleBlocks()) positions.add(part.offset());
        }

        if (positions.isEmpty()) {
            maxHorizontalRadius = 0.0F;
            maxVerticalRadius = 0.0F;
            centerX = 0.0F;
            centerY = 0.0F;
            centerZ = 0.0F;
            scaleDirty = false;
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (var offset : positions) {
            minX = Math.min(minX, offset.getX() - 0.5F);
            maxX = Math.max(maxX, offset.getX() + 0.5F);
            minY = Math.min(minY, offset.getY() - 0.5F);
            maxY = Math.max(maxY, offset.getY() + 0.5F);
            minZ = Math.min(minZ, offset.getZ() - 0.5F);
            maxZ = Math.max(maxZ, offset.getZ() + 0.5F);
        }

        centerX = (minX + maxX) * 0.5F;
        centerY = (minY + maxY) * 0.5F;
        centerZ = (minZ + maxZ) * 0.5F;

        float xSin = Math.abs((float) Math.sin(Math.toRadians(pitch)));
        float xCos = Math.abs((float) Math.cos(Math.toRadians(pitch)));
        float horizontalRadius = 0.0F;
        float verticalRadius = 0.0F;

        for (var offset : positions) {
            float[] xValues = {offset.getX() - 0.5F, offset.getX() + 0.5F};
            float[] yValues = {offset.getY() - 0.5F, offset.getY() + 0.5F};
            float[] zValues = {offset.getZ() - 0.5F, offset.getZ() + 0.5F};

            for (float x : xValues) {
                for (float y : yValues) {
                    for (float z : zValues) {
                        float centeredX = x - centerX;
                        float centeredY = y - centerY;
                        float centeredZ = z - centerZ;
                        float horizontalDistance = (float) Math.hypot(centeredX, centeredZ);
                        horizontalRadius = Math.max(horizontalRadius, horizontalDistance);
                        verticalRadius = Math.max(verticalRadius,
                                Math.abs(centeredY) * xCos + horizontalDistance * xSin);
                    }
                }
            }
        }

        maxHorizontalRadius = horizontalRadius;
        maxVerticalRadius = verticalRadius;
        scaleDirty = false;
    }

    /**
     * The parts of the machine this widget draws and picks, in model space: the controller's own cell plus, for a
     * multiblock machine, one offset per part of its assembled structure.
     * <p>
     * Oritech enumerates those parts as offsets <b>relative to the controller</b> in the machine's own frame
     * ({@link MultiblockMachineController#getCorePositions()}), and turns them into cells of the world with
     * {@link Geometry#rotatePosition} and the machine's facing - the very call the assembled machine itself makes when
     * it looks for its cores. Every machine's part list is verified to avoid the controller's own cell (the origin):
     * not one of them names {@code (0, 0, 0)}, so the controller - the block whose inventory the page addresses - is
     * always a cell of its own and never hidden with a core.
     * <p>
     * The model is drawn in world orientation (the widget only rotates it for the viewer, see
     * {@link PreviewTransform}), so an offset rotated that way is a world offset from the controller and the face the
     * picking reads off it is a world direction of the machine.
     * <p>
     * A position Oritech lists twice, or one that falls onto the controller's own cell, contributes one entry: the
     * entries are cells of one solid machine, and a repeated cell would only let the ray answer the same face twice.
     */
    private List<Vec3i> partOffsets() {
        var offsets = new ArrayList<Vec3i>();
        offsets.add(Vec3i.ZERO);

        if (entity instanceof MultiblockMachineController multiblock) {
            Direction facing = multiblock.getFacingForMultiblock();
            for (Vec3i relativeOffset : multiblock.getCorePositions()) {
                var offset = Geometry.rotatePosition(relativeOffset, facing);
                if (!offsets.contains(offset)) offsets.add(offset);
            }
        }
        return offsets;
    }

    /**
     * The parts as the drawing wants them: one entry per part, each with the block state standing at its own position
     * in the client's level, so a multiblock machine really looks like the structure the player assembled. Only the
     * controller at the model's origin carries the block entity, because a block entity renderer belongs to the one
     * position it was extracted from.
     * <p>
     * <b>A part that is a machine core is marked as such</b> ({@link #isCore}). It is a tier block
     * ({@link MachineCoreBlock}) that owns no inventory, and Oritech hides it itself once the machine uses it - so it
     * is dropped from everything the page draws and picks by {@link #visibleBlocks()}.
     */
    private List<BlockEntry> blocks() {
        BlockState machineState = state;
        if (machineState == null) return List.of();

        var level = entity == null ? null : entity.getLevel();
        var machinePos = entity == null ? null : entity.getBlockPos();
        var entries = new ArrayList<BlockEntry>();

        for (var offset : partOffsets()) {
            var partState = machineState;
            if (level != null && machinePos != null && !offset.equals(Vec3i.ZERO)) {
                var partPos = machinePos.offset(offset);
                if (level.isLoaded(partPos)) {
                    var worldState = level.getBlockState(partPos);
                    if (!worldState.isAir()) partState = worldState;
                }
            }

            entries.add(new BlockEntry(partState, offset.equals(Vec3i.ZERO) ? entity : null, offset,
                    partState.getBlock() instanceof MachineCoreBlock));
        }
        return entries;
    }

    /**
     * True while a part is a core block of the machine and therefore earns no model, no pick and no space on the
     * panel.
     * <p>
     * It is Oritech's own rule rather than a new one: a machine core is invisible once the machine uses it -
     * {@code MachineCoreBlock#getRenderShape(BlockState)} answers {@link RenderShape#INVISIBLE} for such a core - and
     * the part list of a machine is the list of cores it counts on
     * ({@link MultiblockMachineController#getCorePositions()}), which only ever holds cores that machine claimed. The
     * core owns no inventory either: the inventory the page and the automation work on belongs to the controller,
     * which sits in its own cell (see {@link #partOffsets()}).
     */
    private static boolean isCore(BlockEntry entry) {
        return entry.core();
    }

    /**
     * The parts this widget <b>draws and picks</b>: everything in {@link #blocks()} that is not a core. The measuring,
     * the drawing, the picking and the markings all read this one list, so the model, the clicks and the markings
     * cannot disagree about which cells the machine has.
     */
    private List<BlockEntry> visibleBlocks() {
        var visible = new ArrayList<BlockEntry>();
        for (var entry : blocks()) {
            if (!isCore(entry)) visible.add(entry);
        }
        return visible;
    }

    /**
     * The cell whose face on the given side is on the machine's outer surface: the cell of {@link #visibleBlocks()}
     * that lies furthest along that direction. That is where the face the page colours and marks lives, because the
     * machine is a solid block of cells and the furthest cell along a direction is the one whose face on that side
     * nothing else covers.
     * <p>
     * Ties - several cells equally far along the direction - are broken towards the middle of the structure, so a
     * marking sits in the middle of that side rather than in a corner it picked for no reason.
     */
    @Nullable
    private Vec3i surfaceCell(Direction face) {
        var parts = visibleBlocks();
        if (parts.isEmpty()) return null;

        int axis = face.getAxis() == Direction.Axis.X ? 0 : face.getAxis() == Direction.Axis.Y ? 1 : 2;
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        // the middle of the structure on that axis, for the tie break
        float middle = 0.0F;
        for (var part : parts) {
            middle += component(part.offset(), axis);
        }
        middle /= parts.size();

        BlockEntry best = null;
        float bestDistance = Float.NEGATIVE_INFINITY;
        float bestOffMiddle = Float.POSITIVE_INFINITY;
        for (var part : parts) {
            float value = component(part.offset(), axis);
            float distance = positive ? value : -value;
            float offMiddle = Math.abs(value - middle);
            if (distance > bestDistance || (distance == bestDistance && offMiddle < bestOffMiddle)) {
                best = part;
                bestDistance = distance;
                bestOffMiddle = offMiddle;
            }
        }
        return best == null ? null : best.offset();
    }

    /** One coordinate of a cell offset. */
    private static float component(Vec3i offset, int axis) {
        return switch (axis) {
            case 0 -> offset.getX();
            case 1 -> offset.getY();
            default -> offset.getZ();
        };
    }

    /**
     * The transform of the frame this widget is about to draw - the same numbers
     * {@link #renderContent(GuiGraphics, int, int, float)} applies to the pose, built through the one class that
     * describes them ({@link PreviewTransform}), so the picking and the drawing cannot disagree.
     */
    private PreviewTransform transform() {
        return PreviewTransform.of(contentX() + contentWidth() * 0.5F, contentY() + contentHeight() * 0.5F, PLANE_Z,
                renderedScale, pitch, yaw);
    }

    /**
     * The face of the model under the given absolute screen coordinates, or {@code null} while the mouse is not over
     * the machine.
     * <p>
     * The ray is {@link PreviewTransform#pickingRay} of this frame's own drawing transform - the very composition
     * {@link #renderContent} applies to the pose - so the ray of a pixel and the model drawn at that pixel cannot
     * disagree. Every drawn part is then tested with the usual slab test and the closest entry wins, and the face is
     * read off the axis whose slab gave the near intersection: for an axis aligned box that axis <em>is</em> the entry
     * face, and reading it off the entry point instead would make a click on the middle of a face a three way tie of
     * its edges. A core is not tested at all, so it can neither be clicked nor answer for a face behind it.
     */
    @Nullable
    private Hit faceAt(double mouseX, double mouseY) {
        if (!isOverModel(mouseX, mouseY)) return null;
        if (state == null || renderedScale <= 0.0F) return null;

        var ray = transform().pickingRay((float) mouseX, (float) mouseY);
        if (ray == null) return null;

        Hit closest = null;
        for (var part : visibleBlocks()) {
            var hit = entryHit(ray.origin(), ray.direction(), part.offset());
            if (hit != null && (closest == null || hit.distance() < closest.distance())) closest = hit;
        }
        return closest;
    }

    /**
     * The hit of the ray with one block's box, or {@code null} while it misses it: the face the ray entered through
     * and how far away that was. The box is the block's own cube around {@code -center}, i.e. the volume the widget
     * draws that part into.
     * <p>
     * This is Oritech's own slab test, repeated here because it is private and because this class needs the winning
     * axis as well: for an axis aligned box the axis whose slab gives the latest near intersection is the face the ray
     * was still outside of when it reached the box, which is the definition of the entry face.
     */
    @Nullable
    private Hit entryHit(Vector3f origin, Vector3f direction, Vec3i offset) {
        var minimums = new float[] {
                offset.getX() - centerX - 0.5F,
                offset.getY() - centerY - 0.5F,
                offset.getZ() - centerZ - 0.5F};
        var directions = new float[] {direction.x, direction.y, direction.z};

        float near = 0.0F;
        float far = Float.POSITIVE_INFINITY;
        int nearAxis = -1;
        for (int axis = 0; axis < 3; axis++) {
            float originOnAxis = componentGet(origin, axis);
            if (Math.abs(directions[axis]) < 1.0E-6F) {
                if (originOnAxis < minimums[axis] || originOnAxis > minimums[axis] + 1.0F) return null;
                continue;
            }

            float first = (minimums[axis] - originOnAxis) / directions[axis];
            float second = (minimums[axis] + 1.0F - originOnAxis) / directions[axis];
            float axisNear = Math.min(first, second);
            if (axisNear > near) {
                near = axisNear;
                nearAxis = axis;
            }
            far = Math.min(far, Math.max(first, second));
            if (far < near) return null;
        }
        if (nearAxis < 0) return null;

        // the face is the side of that axis the ray came from, which its sign names
        float sign = directions[nearAxis];
        var face = switch (nearAxis) {
            case 0 -> sign > 0.0F ? Direction.WEST : Direction.EAST;
            case 1 -> sign > 0.0F ? Direction.DOWN : Direction.UP;
            default -> sign > 0.0F ? Direction.NORTH : Direction.SOUTH;
        };
        return new Hit(offset, face, near);
    }

    /** Component of a vector on one axis; the array form the slab test uses. */
    private static float componentGet(Vector3f vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    /** One part of the model: the state drawn at its cell, its block entity, where that cell sits, and its kind. */
    private record BlockEntry(BlockState state, @Nullable BlockEntity entity, Vec3i offset, boolean core) {
    }

    /** One picked part of the model, the face the ray entered it through and how far away that was. */
    private record Hit(Vec3i offset, Direction face, float distance) {
    }
}
