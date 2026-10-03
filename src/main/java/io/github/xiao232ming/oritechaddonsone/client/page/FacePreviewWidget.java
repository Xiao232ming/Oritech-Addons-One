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
import org.joml.Matrix3f;
import org.joml.Vector3f;

import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

/**
 * The 3D model of the transfer preview page: <b>one</b> machine, drawn inside the page's panel with the same recipe
 * Oritech's own {@code BlockPreviewWidget} uses on this branch, plus the two things that page needs and Oritech's
 * widget does not offer - face picking and a highlight on the face under the mouse.
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
 * the centre and the scale with Oritech's formula, and adds <b>face picking</b>: the winning axis of the ray/slab
 * test against a block's cube is the face the ray entered through, and half a unit further along the ray that axis is
 * outside the block again - which turns "which face is it" into a comparison of the entry point against the box the
 * test already built, instead of a rounding argument. The axis order is the model's own: x runs east/west, y up/down
 * and z south/north, which is what makes the answer a {@link Direction} of the world the model is of.
 * <p>
 * <b>The model is only the machine.</b> The page's model never contains the machine's addons, the indicators of its
 * open addon slots or the plugin block itself, so this widget holds one state and entity rather than a list of blocks
 * (see {@code TransferPreviewState#build} for why). A multiblock machine is complete with that one entry: the core's
 * block model carries the machine's assembled structure, and {@link #previewPositions()} still measures the whole
 * structure through the machine's core positions, so it is sized like the multi-block model it is.
 * <p>
 * <b>The hovered face is marked in the model's own space.</b> The picking already answers which face the mouse is
 * over, so {@link #renderContent} draws one translucent white quad on that face right after the machine, inside the
 * very pose the machine was drawn in (see {@link #drawHighlight}). That is what a screen space rectangle - all the
 * GUI's own fill can draw - cannot do: only a quad in the model's pose follows the model's rotation and lies on the
 * face the player is about to click.
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

    /** Distance the picking ray starts at; far enough to be outside any model. */
    private static final float RAY_DISTANCE = 1000000.0F;

    /** Pitch and yaw the model is drawn with; Oritech's own defaults. */
    private static final float DEFAULT_PITCH = 30.0F;
    private static final float DEFAULT_YAW = 225.0F;

    /** Translucent white of the hover highlight: white, at the alpha Oritech uses for its own placement ghost. */
    private static final int HIGHLIGHT_COLOR = 0x55FFFFFF;

    /** Distance the highlight floats off the face, so it never trades depth with the block face behind it. */
    private static final float HIGHLIGHT_LIFT = 0.002F;

    /** How far the highlight's edges stay inside the face, so it reads as a marking on the block, not as a lid. */
    private static final float HIGHLIGHT_INSET = 0.06F;

    /**
     * The highlight quad's corners, as factors of the face's own half extents: counter clockwise seen from outside the
     * block, in the frame {@link #FACE_AXES} builds.
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
     * in - which puts the quad on the face's outside.
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

    /** Block the last {@link #pickFace(double, double)} landed on, or {@code null} while there was none. */
    @Nullable
    private Vec3i pickedOffset;

    /** Face that click landed on, or {@code null} while it was not on a block. */
    @Nullable
    private Direction pickedFace;

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
     * Rotates the model and remembers the rotation, which the next picking test and the next drawn highlight then use
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
     * Remembers which of the model's faces the mouse is over, then draws the model - the page's face marker is read
     * from the remembered face, and the model marks that same face in the same frame (see {@link #renderContent}).
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

        super.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * Draws the machine inside the panel with Oritech's own recipe for this branch - see the class comment for the
     * exact transform and why the Y scale is negative - and then the hover highlight on the face the mouse is over,
     * still inside that same pose.
     */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        BlockState machineState = state;
        if (machineState == null) {
            renderedScale = 0.0F;
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
        graphics.pose().translate(cx + cw / 2.0F, cy + ch / 2.0F, 400.0F);
        graphics.pose().scale(scale, -scale, scale);
        graphics.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        graphics.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        // the model's origin is the machine's block centre, which is where the picking and the highlight put it as
        // well; Oritech's own "-0.5 + offset" cancels against it for a block at offset zero
        graphics.pose().translate(-centerX, -centerY, -centerZ);

        RenderSystem.runAsFancy(() -> {
            if (machineState.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) {
                client.getBlockRenderer().renderSingleBlock(machineState, graphics.pose(), bufferSource,
                        0xF000F0, OverlayTexture.NO_OVERLAY);
            }

            if (entity != null) {
                BlockEntityRenderer<BlockEntity> entityRenderer =
                        client.getBlockEntityRenderDispatcher().getRenderer(entity);
                if (entityRenderer != null) {
                    entityRenderer.render(entity, delta, graphics.pose(), bufferSource, 0xF000F0,
                            OverlayTexture.NO_OVERLAY);
                }
            }

            // after the machine, so it blends over the block it lies on, and in the pose that is still active above,
            // so it lands on that block's face at every rotation
            drawHighlight(graphics.pose().last());

            RenderSystem.setShaderLights(new Vector3f(-1.5F, -0.5F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F));
            bufferSource.endBatch();
            Lighting.setupFor3DItems();
        });
        graphics.pose().popPose();
    }

    /**
     * Draws the translucent quad on the face under the mouse, in the model's own pose: the quad is built in model
     * space around the face's own centre, so it lands on the face whatever the model is rotated to, and it floats
     * {@link #HIGHLIGHT_LIFT} off that face so the face itself never wins the depth test.
     * <p>
     * It is drawn with {@code RenderType#debugQuads()}, the one render type this branch offers that takes plain vertex
     * colours: its format is {@code POSITION_COLOR}, so a vertex needs nothing but a position and a colour, and it
     * carries the translucent blend. It is deliberately the quad type and not {@code debugFilledBox()}, which is the
     * same thing with back face culling on: the model is drawn through a negated Y scale, so which way round a quad
     * ends up on screen is a property of that transform rather than of this code, and a highlight that disappears on
     * some faces would be worse than the culling it saves. The depth is handled by {@link #HIGHLIGHT_LIFT} alone - the
     * quad really sits in front of the face it marks rather than relying on a render type's own depth offset.
     * <p>
     * The vertices go into the buffer source the machine was drawn with, so the {@code endBatch} in
     * {@link #renderContent} flushes them in that same frame, pose and order: the machine first, the quad over it.
     * <p>
     * Nothing is drawn while no face is hovered, i.e. while the mouse is not on the model - the page's picking keeps
     * working unchanged, because this only ever adds geometry to the frame and never touches the mouse.
     */
    private void drawHighlight(PoseStack.Pose pose) {
        Direction face = hoveredFace;
        if (face == null) return;

        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.debugQuads());
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
     * Recomputes the model's centre and its two radii - Oritech's own calculation, repeated here because its fields
     * are private: the centre is the middle of the model's bounding box, and the radii are the largest horizontal and
     * the largest pitch-projected vertical distance of any point of that box from the centre. Measuring all eight
     * corners of every position instead of its middle is what keeps a model that is taller than it is wide (a
     * multiblock machine, whose parts are core positions of the one block drawn here) inside the panel at every
     * rotation.
     */
    private void calculateSize() {
        var positions = new ArrayList<Vec3i>();
        if (state != null) positions.addAll(previewPositions());

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
     * The positions the one model block really covers: its own cell at the model's origin, plus - exactly like
     * Oritech's widget - the core positions of a multiblock machine, rotated into the offset's own frame. Without this
     * a machine whose model is made of several blocks would be measured too small here while the renderer measures it
     * correctly.
     */
    private List<Vec3i> previewPositions() {
        var positions = new ArrayList<Vec3i>();
        positions.add(Vec3i.ZERO);

        if (entity instanceof MultiblockMachineController multiblock) {
            Direction facing = multiblock.getFacingForMultiblock();
            for (Vec3i relativeOffset : multiblock.getCorePositions()) {
                positions.add(Geometry.rotatePosition(relativeOffset, facing));
            }
        }
        return positions;
    }

    /**
     * The face of the model under the given absolute screen coordinates, or {@code null} while the mouse is not over
     * the machine.
     * <p>
     * The transform is the exact inverse of the one {@link #renderContent} draws with: the mouse is turned back into
     * the model's rotated space (undoing the Y flip the render applies), a ray is started far in front of the model
     * and pointed along the GUI's -Z, that ray is rotated back into the model's own space, and the machine's own box
     * along it is picked with the usual slab test.
     */
    @Nullable
    private Hit faceAt(double mouseX, double mouseY) {
        if (!isOverModel(mouseX, mouseY)) return null;
        if (state == null || renderedScale <= 0.0F) return null;

        float screenX = ((float) mouseX - (contentX() + contentWidth() * 0.5F)) / renderedScale;
        float screenY = -((float) mouseY - (contentY() + contentHeight() * 0.5F)) / renderedScale;
        var inverse = new Matrix3f()
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .invert();
        var origin = inverse.transform(new Vector3f(screenX, screenY, RAY_DISTANCE));
        var direction = inverse.transform(new Vector3f(0.0F, 0.0F, -1.0F));

        float distance = entryDistance(origin, direction);
        if (distance < 0.0F) return null;

        var face = entryFace(origin, direction, distance);
        return face == null ? null : new Hit(Vec3i.ZERO, face);
    }

    /**
     * Distance from the ray origin to the machine's box, along the ray, or {@code -1} while the ray misses it. The box
     * is the block's own cube around {@code -center}, i.e. the volume the renderer draws the machine into.
     */
    private float entryDistance(Vector3f origin, Vector3f direction) {
        var minimums = new float[] {-centerX - 0.5F, -centerY - 0.5F, -centerZ - 0.5F};
        var origins = new float[] {origin.x, origin.y, origin.z};
        var directions = new float[] {direction.x, direction.y, direction.z};

        float near = 0.0F;
        float far = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0E-6F) {
                if (origins[axis] < minimums[axis] || origins[axis] > minimums[axis] + 1.0F) return -1.0F;
                continue;
            }

            float first = (minimums[axis] - origins[axis]) / directions[axis];
            float second = (minimums[axis] + 1.0F - origins[axis]) / directions[axis];
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
            if (far < near) return -1.0F;
        }
        return near;
    }

    /**
     * The face the ray entered the machine's box through, read from the entry point: half a unit further along the
     * ray that axis has left the block again, and the direction the ray points tells which of the two sides - the
     * entry point sits on the opposite one.
     */
    @Nullable
    private Direction entryFace(Vector3f origin, Vector3f direction, float distance) {
        var minimums = new float[] {-centerX - 0.5F, -centerY - 0.5F, -centerZ - 0.5F};
        var point = new float[] {
                origin.x + direction.x * distance,
                origin.y + direction.y * distance,
                origin.z + direction.z * distance};

        for (int axis = 0; axis < 3; axis++) {
            float ahead = point[axis] + directionGet(direction, axis) * 0.5F;
            if (ahead >= minimums[axis] + 1.0F) {
                return switch (axis) {
                    case 0 -> direction.x > 0.0F ? Direction.WEST : Direction.EAST;
                    case 1 -> direction.y > 0.0F ? Direction.DOWN : Direction.UP;
                    default -> direction.z > 0.0F ? Direction.NORTH : Direction.SOUTH;
                };
            }
            if (ahead <= minimums[axis]) {
                return switch (axis) {
                    case 0 -> direction.x > 0.0F ? Direction.EAST : Direction.WEST;
                    case 1 -> direction.y > 0.0F ? Direction.UP : Direction.DOWN;
                    default -> direction.z > 0.0F ? Direction.SOUTH : Direction.NORTH;
                };
            }
        }
        return null;
    }

    /** Component of a direction on one axis; the array form the slab test uses. */
    private static float directionGet(Vector3f direction, int axis) {
        return switch (axis) {
            case 0 -> direction.x;
            case 1 -> direction.y;
            default -> direction.z;
        };
    }

    /** One picked block of the model and the face the ray entered it through. */
    private record Hit(Vec3i offset, Direction face) {
    }
}
