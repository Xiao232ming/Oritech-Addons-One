package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * The 3D model of the transfer preview page: the machine this plugin serves, drawn inside the page's panel with the
 * same recipe Oritech's own {@code BlockPreviewWidget} uses on this branch, plus the two things that page needs and
 * Oritech's widget does not offer.
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

    /** The blocks of the model, in the order they were added. */
    private final List<Entry> blocks = new ArrayList<>();

    private float pitch = DEFAULT_PITCH;
    private float yaw = DEFAULT_YAW;

    /** True while the centre and the radii have to be recomputed; set by {@link #addBlock} and {@link #withRotation}. */
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

    /** Adds one block of the model, exactly like Oritech's widget does. */
    public void addBlock(BlockState state, @Nullable BlockEntity entity, Vec3i offset) {
        blocks.add(new Entry(state, entity, offset));
        scaleDirty = true;
    }

    /** The blocks of the model, in the order they were added. */
    public List<Entry> blocks() {
        return List.copyOf(blocks);
    }

    /**
     * Rotates the model and remembers the rotation, which the next picking test then uses - the page calls this once
     * per frame with the rotation the player dragged it into.
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
     * Draws the model and remembers which of its faces the mouse is over - the page's face marker is read from there,
     * and the picking uses the scale and the rotation this very frame was drawn with.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        var hovered = faceAt(mouseX, mouseY);
        this.hoveredFace = hovered == null ? null : hovered.face();
    }

    /**
     * Draws every block of the model inside the panel, with Oritech's own recipe for this branch - see the class
     * comment for the exact transform and why the Y scale is negative. The blocks are drawn in the order they were
     * added, each in its own pushed pose, so no block can move another one.
     */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (blocks.isEmpty()) {
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
        for (var entry : blocks) {
            graphics.pose().pushPose();
            graphics.pose().translate(cx + cw / 2.0F, cy + ch / 2.0F, 400.0F);
            graphics.pose().scale(scale, -scale, scale);
            graphics.pose().mulPose(Axis.XP.rotationDegrees(pitch));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(yaw));
            graphics.pose().translate(-0.5F + entry.offset().getX() - centerX,
                    -0.5F + entry.offset().getY() - centerY,
                    -0.5F + entry.offset().getZ() - centerZ);

            RenderSystem.runAsFancy(() -> {
                if (entry.state().getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) {
                    client.getBlockRenderer().renderSingleBlock(entry.state(), graphics.pose(), bufferSource,
                            0xF000F0, OverlayTexture.NO_OVERLAY);
                }

                if (entry.entity() != null) {
                    BlockEntityRenderer<BlockEntity> entityRenderer =
                            client.getBlockEntityRenderDispatcher().getRenderer(entry.entity());
                    if (entityRenderer != null) {
                        entityRenderer.render(entry.entity(), delta, graphics.pose(), bufferSource, 0xF000F0,
                                OverlayTexture.NO_OVERLAY);
                    }
                }

                RenderSystem.setShaderLights(new Vector3f(-1.5F, -0.5F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F));
                bufferSource.endBatch();
                Lighting.setupFor3DItems();
            });
            graphics.pose().popPose();
        }
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
     * corners of every block instead of its middle is what keeps a model that is taller than it is wide (a multiblock
     * machine with addons around it) inside the panel at every rotation.
     */
    private void calculateSize() {
        if (blocks.isEmpty()) {
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

        for (var entry : blocks) {
            for (var offset : previewPositions(entry)) {
                minX = Math.min(minX, offset.getX() - 0.5F);
                maxX = Math.max(maxX, offset.getX() + 0.5F);
                minY = Math.min(minY, offset.getY() - 0.5F);
                maxY = Math.max(maxY, offset.getY() + 0.5F);
                minZ = Math.min(minZ, offset.getZ() - 0.5F);
                maxZ = Math.max(maxZ, offset.getZ() + 0.5F);
            }
        }

        centerX = (minX + maxX) * 0.5F;
        centerY = (minY + maxY) * 0.5F;
        centerZ = (minZ + maxZ) * 0.5F;

        float xSin = Math.abs((float) Math.sin(Math.toRadians(pitch)));
        float xCos = Math.abs((float) Math.cos(Math.toRadians(pitch)));
        float horizontalRadius = 0.0F;
        float verticalRadius = 0.0F;

        for (var entry : blocks) {
            for (var offset : previewPositions(entry)) {
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
        }

        maxHorizontalRadius = horizontalRadius;
        maxVerticalRadius = verticalRadius;
        scaleDirty = false;
    }

    /**
     * The positions one model block really covers: its own cell, plus - exactly like Oritech's widget - the core
     * positions of a multiblock machine, rotated into the offset's own frame. Without this a machine whose model is
     * made of several blocks would be measured too small here while the renderer measures it correctly.
     */
    private static List<Vec3i> previewPositions(Entry entry) {
        var positions = new ArrayList<Vec3i>();
        positions.add(entry.offset());

        if (entry.entity() instanceof MultiblockMachineController multiblock) {
            Direction facing = multiblock.getFacingForMultiblock();
            for (Vec3i relativeOffset : multiblock.getCorePositions()) {
                positions.add(Geometry.rotatePosition(relativeOffset, facing).offset(entry.offset()));
            }
        }
        return positions;
    }

    /**
     * The face of the model under the given absolute screen coordinates, or {@code null} while the mouse is not over a
     * block of it.
     * <p>
     * The transform is the exact inverse of the one {@link #renderContent} draws with: the mouse is turned back into
     * the model's rotated space (undoing the Y flip the render applies), a ray is started far in front of the model
     * and pointed along the GUI's -Z, that ray is rotated back into the model's own space, and the closest block along
     * it is picked with the usual slab test.
     */
    @Nullable
    private Hit faceAt(double mouseX, double mouseY) {
        if (!isOverModel(mouseX, mouseY)) return null;
        if (blocks.isEmpty() || renderedScale <= 0.0F) return null;

        float screenX = ((float) mouseX - (contentX() + contentWidth() * 0.5F)) / renderedScale;
        float screenY = -((float) mouseY - (contentY() + contentHeight() * 0.5F)) / renderedScale;
        var inverse = new Matrix3f()
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .invert();
        var origin = inverse.transform(new Vector3f(screenX, screenY, RAY_DISTANCE));
        var direction = inverse.transform(new Vector3f(0.0F, 0.0F, -1.0F));

        Entry closest = null;
        float closestDistance = Float.POSITIVE_INFINITY;
        for (var entry : blocks) {
            float distance = entryDistance(origin, direction, entry.offset());
            if (distance >= 0.0F && distance < closestDistance) {
                closestDistance = distance;
                closest = entry;
            }
        }
        if (closest == null) return null;

        var face = entryFace(origin, direction, closestDistance, closest.offset());
        return face == null ? null : new Hit(closest.offset(), face);
    }

    /**
     * Distance from the ray origin to the box of one entry, along the ray, or {@code -1} while the ray misses it. The
     * box is the block's own cube around {@code offset - center}, i.e. the volume the renderer draws that block into.
     */
    private float entryDistance(Vector3f origin, Vector3f direction, Vec3i offset) {
        var minimums = new float[] {
                offset.getX() - centerX - 0.5F,
                offset.getY() - centerY - 0.5F,
                offset.getZ() - centerZ - 0.5F};
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
     * The face the ray entered the box at {@code offset} through, read from the entry point: half a unit further along
     * the ray that axis has left the block again, and the direction the ray points tells which of the two sides - the
     * entry point sits on the opposite one.
     */
    @Nullable
    private Direction entryFace(Vector3f origin, Vector3f direction, float distance, Vec3i offset) {
        var minimums = new float[] {
                offset.getX() - centerX - 0.5F,
                offset.getY() - centerY - 0.5F,
                offset.getZ() - centerZ - 0.5F};
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

    /** One block of the model: the state to draw, its optional block entity and where it sits. */
    public record Entry(BlockState state, @Nullable BlockEntity entity, Vec3i offset) {
    }

    /** One picked block of the model and the face the ray entered it through. */
    private record Hit(Vec3i offset, Direction face) {
    }
}
