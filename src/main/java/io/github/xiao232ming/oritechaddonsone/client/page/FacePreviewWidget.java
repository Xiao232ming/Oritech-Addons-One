package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import rearth.oritech.api.screen.widgets.BlockPreviewWidget;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

/**
 * The 3D model of the transfer preview page: one machine, drawn through a picture-in-picture state of this mod's own
 * so that a translucent highlight can be drawn on the face the mouse is over (see {@link MachinePreviewPipRenderer}).
 * <p>
 * It replaces the drawing half of Oritech's {@link BlockPreviewWidget} for three reasons, all of them because the
 * widget's model lives in private fields of that class:
 * <ul>
 * <li>the page's model is <b>one</b> machine and never a list of blocks, so the widget keeps one state and entity
 * instead of Oritech's block list,</li>
 * <li>the hover highlight needs the face the mouse is over to be drawn <em>in</em> the model's render pass, which means
 * submitting this mod's state rather than Oritech's,</li>
 * <li>and it needs that face plus the block it belongs to, which Oritech's widget never keeps.</li>
 * </ul>
 * The picking is the part that stays: {@link BlockPreviewWidget#findBlockAt(double, double)} answers which
 * <em>block</em> of the model a point is over, but it throws the winning axis of its ray/slab test away, so a page
 * could only ever tell "the machine" from "not the machine". This widget keeps that axis: the face the ray entered the
 * block through is the axis whose {@code min(first, second)} produced the near hit, and half a unit further along the
 * ray that axis is outside the block again - which turns "which face is it" into a comparison of the entry point
 * against the box the ray test already built, instead of a rounding argument. The axis order of model space is the one
 * Oritech's own renderer uses - x runs east/west, y up/down and z south/north - which is what makes the answer a
 * {@link Direction} of the world the model is of.
 * <p>
 * <b>It is asked in absolute screen coordinates.</b> A page of this mod draws inside {@code extractBackground} of an
 * {@code AbstractContainerScreen} and maps the panel onto the screen itself, so it renders the widget at the pixel
 * position it wants on screen - while Oritech's own widget screens translate the pose by their GUI origin and
 * therefore ask the widget in GUI relative space. The two must not be mixed, which is why {@link #pickFace(double,
 * double)} and {@link #hoveredFace()} work off the very coordinates the page drew with and why this class overrides
 * the widget's own hit test: Oritech's {@code isMouseOver} compares a GUI relative mouse against the widget's
 * absolute frame, so on a two hundred pixel wide panel it can only ever be true left of the panel and would report
 * a hovered face for a mouse the page never drew a model under.
 * <p>
 * The centre and the scale the picking needs are the very numbers this widget submits its frame with, so "what the
 * player sees" and "what the player clicks" stay the same thing, and everything Oritech folds into those numbers is
 * inherited - the extra blocks of a multiblock machine's core, for one.
 */
public final class FacePreviewWidget extends BlockPreviewWidget {

    /** Distance the picking ray starts at; Oritech's own value, far enough to be outside any model. */
    private static final float RAY_DISTANCE = 1000000.0F;

    /**
     * The pitch and yaw this widget is drawn with. The stock widget's own fields are private, so the widget keeps its
     * own copy through {@link #withRotation}: it is the value of the last call, which is the value the render uses.
     */
    private float pitch;
    private float yaw;

    /** The machine this widget draws, or {@code null} while it has none to draw. */
    @Nullable
    private BlockState state;
    /** The machine's block entity, or {@code null} while its block has none. */
    @Nullable
    private BlockEntity entity;

    /** Centre of the model in the model's own space, as the last drawn frame used it. */
    private final Vector3f center = new Vector3f();

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

    /** The 3D preview of one machine: 140x110 pixels, the size the page's panel reserves for it. */
    public FacePreviewWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.pitch = 30.0F;
        this.yaw = 225.0F;
    }

    /**
     * Rotates the model and remembers the rotation, which the picking and the highlight test then use - the page calls
     * this once per frame with the drag it accumulated.
     */
    @Override
    public BlockPreviewWidget withRotation(float xRotation, float yRotation) {
        this.pitch = xRotation;
        this.yaw = yRotation;
        return super.withRotation(xRotation, yRotation);
    }

    /** Pitch of this model, as the page last set it. */
    public float pitch() {
        return pitch;
    }

    /** Yaw of this model, as the page last set it. */
    public float yaw() {
        return yaw;
    }

    /** Sets the one machine this widget draws; the model is measured again from it. */
    public void setMachine(BlockState state, @Nullable BlockEntity entity) {
        this.state = state;
        this.entity = entity;
    }

    /**
     * Remembers which of the model's faces the mouse is over, then draws the model - the page's face marker is read
     * from the remembered face, and the model submits that same face to be highlighted (see
     * {@link MachinePreviewPipRenderer}).
     * <p>
     * The hover is worked out <b>before</b> the model is drawn, from the rotation and the scale of the previous frame:
     * the page sets the rotation and then renders in one call, so the previous frame's scale is this frame's as well,
     * and taking the hover first means the model of this very frame carries the highlight the mouse just moved to
     * instead of the one it was on a frame ago.
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var hovered = faceAt(mouseX, mouseY);
        this.hoveredFace = hovered == null ? null : hovered.face();
        super.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * Submits the model of this frame: the machine plus, while the mouse is on it, the face to highlight.
     * <p>
     * It takes the place of Oritech's own content, which draws its private block list - a list this widget never fills
     * - and is therefore the one place the model's numbers are worked out. The rotation it submits is the one the page
     * set ({@link #withRotation}) and not the stock widget's own field, because the stock widget's automatic spin is
     * never used here: the player turns this model by dragging it.
     */
    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        BlockState machineState = state;
        if (machineState == null) {
            this.renderedScale = 0.0F;
            this.hoveredFace = null;
            return;
        }

        measure();
        if (renderedScale <= 0.0F) return;

        int cx = contentX();
        int cy = contentY();
        int width = contentWidth();
        int height = contentHeight();

        graphics.submitPictureInPictureRenderState(MachinePreviewRenderState.of(
                machineState, entity, hoveredFace, pitch, yaw, center.x, center.y, center.z, delta,
                cx, cy, cx + width, cy + height, renderedScale, graphics.pose(), graphics.peekScissorStack()));
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
     * its surface keeps, because that is where the model is really drawn and because Oritech's own picking test
     * ({@code isMouseOver} of the widget) measures the padded frame as well.
     * <p>
     * It is used instead of the widget's own hit test because that one is only correct for a caller in GUI relative
     * space - see the class comment - and because the page's own "is the click on the model at all" question has to
     * give the same answer as the picking below it.
     */
    public boolean isOverModel(double mouseX, double mouseY) {
        int padding = getPadding().left();
        return mouseX >= getX() + padding && mouseX < getX() + getWidth() - padding
                && mouseY >= getY() + getPadding().top() && mouseY < getY() + getHeight() - getPadding().bottom();
    }

    /**
     * The face of the model under the given absolute screen coordinates, or {@code null} while the mouse is not over
     * a block of it. The transform is the one of {@link BlockPreviewWidget#findBlockAt(double, double)}, only kept:
     * the closest block is picked with the same slab test, and the face is then read off the entry point.
     */
    @Nullable
    private Hit faceAt(double mouseX, double mouseY) {
        if (!isOverModel(mouseX, mouseY)) return null;

        var blocks = blocks();
        if (blocks.isEmpty() || renderedScale <= 0.0F) return null;

        float screenX = ((float) mouseX - (contentX() + contentWidth() * 0.5F)) / renderedScale;
        float screenY = -((float) mouseY - (contentY() + contentHeight() * 0.5F)) / renderedScale;
        var inverse = new Matrix3f()
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .invert();
        var origin = inverse.transform(new Vector3f(screenX, screenY, RAY_DISTANCE));
        var direction = inverse.transform(new Vector3f(0.0F, 0.0F, -1.0F));

        BlockEntry closest = null;
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

    /** The one block of this model, as the picking and the measuring both want it. */
    private List<BlockEntry> blocks() {
        BlockState machineState = state;
        if (machineState == null) return List.of();

        return List.of(new BlockEntry(machineState, entity, Vec3i.ZERO));
    }

    /**
     * Works out the centre and the scale of the model, exactly like the stock widget works them out for the frame it
     * is about to submit - including the core positions of a multiblock machine, because the renderer measures the
     * machine's whole model and a single cell would otherwise be measured too small here.
     */
    private void measure() {
        var positions = new ArrayList<Vec3i>();
        for (var entry : blocks()) {
            positions.addAll(previewPositions(entry));
        }
        if (positions.isEmpty()) {
            renderedScale = 0.0F;
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (var position : positions) {
            minX = Math.min(minX, position.getX() - 0.5F);
            minY = Math.min(minY, position.getY() - 0.5F);
            minZ = Math.min(minZ, position.getZ() - 0.5F);
            maxX = Math.max(maxX, position.getX() + 0.5F);
            maxY = Math.max(maxY, position.getY() + 0.5F);
            maxZ = Math.max(maxZ, position.getZ() + 0.5F);
        }

        center.set((minX + maxX) * 0.5F, (minY + maxY) * 0.5F, (minZ + maxZ) * 0.5F);

        float xSin = Math.abs((float) Math.sin(Math.toRadians(pitch)));
        float xCos = Math.abs((float) Math.cos(Math.toRadians(pitch)));
        float horizontalRadius = 0.0F;
        float verticalRadius = 0.0F;
        for (var position : positions) {
            float horizontal = (float) Math.hypot(Math.abs(position.getX() - center.x) + 0.5F,
                    Math.abs(position.getZ() - center.z) + 0.5F);
            float vertical = Math.abs(position.getY() - center.y) + 0.5F;
            horizontalRadius = Math.max(horizontalRadius, horizontal);
            verticalRadius = Math.max(verticalRadius, vertical * xCos + horizontal * xSin);
        }
        if (horizontalRadius <= 0.0F || verticalRadius <= 0.0F) {
            renderedScale = 0.0F;
            return;
        }

        float widthScale = contentWidth() * 0.5F / horizontalRadius;
        float heightScale = contentHeight() * 0.5F / verticalRadius;
        renderedScale = Math.min(widthScale, heightScale) * 0.98F;
    }

    /**
     * The positions one model block really covers: its own cell, plus - exactly like the stock widget - the core
     * positions of a multiblock machine, rotated into the offset's own frame. Without this a machine whose model is
     * made of several blocks would be measured too small here while the renderer measures it correctly.
     */
    private static List<Vec3i> previewPositions(BlockEntry entry) {
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
     * Distance from the ray origin to the box of one entry, along the ray, or {@code -1} while the ray misses it -
     * Oritech's own slab test, repeated here because it is private and because this class needs the winning axis as
     * well.
     */
    private float entryDistance(Vector3f origin, Vector3f direction, Vec3i offset) {
        var minimums = new float[] {
                offset.getX() - center.x - 0.5F,
                offset.getY() - center.y - 0.5F,
                offset.getZ() - center.z - 0.5F};
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
     * The face the ray entered the box at {@code offset} through, read from the entry point: half a unit further
     * along the ray that axis has left the block again, and the direction the ray points tells which of the two sides
     * - the entry point sits on the opposite one.
     */
    @Nullable
    private Direction entryFace(Vector3f origin, Vector3f direction, float distance, Vec3i offset) {
        var minimums = new float[] {
                offset.getX() - center.x - 0.5F,
                offset.getY() - center.y - 0.5F,
                offset.getZ() - center.z - 0.5F};
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
