package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import rearth.oritech.api.screen.widgets.BlockPreviewWidget;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

/**
 * The 3D model of the transfer preview page: one machine, drawn through a picture-in-picture state of this mod's
 * own so that a translucent highlight can be drawn on the face the mouse is over (see
 * {@link MachinePreviewPipRenderer}).
 * <p>
 * It replaces the drawing half of Oritech's {@link BlockPreviewWidget} for three reasons, all of them because the
 * widget's model lives in private fields of that class:
 * <ul>
 * <li>the page's model is <b>one</b> machine and never a list of blocks, so the widget keeps one state and entity
 * instead of Oritech's block list,</li>
 * <li>the hover highlight needs the face the mouse is over to be drawn <em>in</em> the model's render pass, which
 * means submitting this mod's state rather than Oritech's,</li>
 * <li>and it needs that face plus the block it belongs to, which Oritech's widget never keeps.</li>
 * </ul>
 * <b>The picking is not a second copy of the drawing any more.</b> It used to be Oritech's own
 * {@code findBlockAt}, which knows the pitch, the yaw and the panel scale but neither the picture-in-picture
 * pipeline's vertical flip nor its viewport factor - so its model-space ray was mirrored against the model the
 * player saw and the face it reported was the wrong one: at the default pitch and yaw every pixel of the model
 * answered a face other than the one drawn there (a click on the upper right of a machine answered {@code DOWN}).
 * Both halves now come from one {@link PreviewTransform}: the renderer's state describes the transform the frame
 * is drawn with, and {@link #faceAt(double, double)} picks with its inverse, so a pixel and the model drawn at
 * that pixel cannot disagree.
 * <p>
 * <b>It is asked in absolute screen coordinates.</b> A page of this mod draws inside {@code extractBackground} of
 * an {@code AbstractContainerScreen} and maps the panel onto the screen itself, so it renders the widget at the
 * pixel position it wants on screen - while Oritech's own widget screens translate the pose by their GUI origin
 * and therefore ask the widget in GUI relative space. The two must not be mixed, which is why {@link
 * #pickFace(double, double)} and {@link #hoveredFace()} work off the very coordinates the page drew with and why
 * this class overrides the widget's own hit test: Oritech's {@code isMouseOver} compares a GUI relative mouse
 * against the widget's absolute frame, so on a two hundred pixel wide panel it can only ever be true left of the
 * panel and would report a hovered face for a mouse the page never drew a model under.
 */
public final class FacePreviewWidget extends BlockPreviewWidget {

    /**
     * The pitch and yaw this widget is drawn with. The stock widget's own fields are private, so the widget keeps
     * its own copy through {@link #withRotation}: it is the value of the last call, which is the value the render
     * uses.
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
     * Rotates the model and remembers the rotation, which the picking and the highlight test then use - the page
     * calls this once per frame with the drag it accumulated.
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
     * Remembers which of the model's faces the mouse is over, then draws the model - the page's face marker is
     * read from the remembered face, and the model submits that same face to be highlighted (see
     * {@link MachinePreviewPipRenderer}).
     * <p>
     * The hover is worked out <b>before</b> the model is drawn, from the rotation and the scale of the previous
     * frame: the page sets the rotation and then renders in one call, so the previous frame's scale is this
     * frame's as well, and taking the hover first means the model of this very frame carries the highlight the
     * mouse just moved to instead of the one it was on a frame ago.
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
     * It takes the place of Oritech's own content, which draws its private block list - a list this widget never
     * fills - and is therefore the one place the model's numbers are worked out. The rotation it submits is the
     * one the page set ({@link #withRotation}) and not the stock widget's own field, because the stock widget's
     * automatic spin is never used here: the player turns this model by dragging it.
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
     * The face a click at these absolute screen coordinates selected, or {@code null} while the click did not land
     * on the model. The answer is remembered as {@link #pickedFace()}, which the page then colours and configures.
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
     * True while the given absolute screen position is over the <b>model</b> - the widget's frame without the
     * padding its surface keeps, because that is where the model is really drawn and because Oritech's own picking
     * test ({@code isMouseOver} of the widget) measures the padded frame as well.
     * <p>
     * It is used instead of the widget's own hit test because that one is only correct for a caller in GUI
     * relative space - see the class comment - and because the page's own "is the click on the model at all"
     * question has to give the same answer as the picking below it.
     */
    public boolean isOverModel(double mouseX, double mouseY) {
        int padding = getPadding().left();
        return mouseX >= getX() + padding && mouseX < getX() + getWidth() - padding
                && mouseY >= getY() + getPadding().top() && mouseY < getY() + getHeight() - getPadding().bottom();
    }

    /**
     * The face of the model under the given absolute screen coordinates, or {@code null} while the mouse is not
     * over a block of it.
     * <p>
     * The ray is {@link PreviewTransform#pickingRay} of this frame's own drawing transform - the very composition
     * the renderer is handed - so the ray of a pixel and the model drawn at that pixel cannot disagree. Each block
     * is then tested with Oritech's slab test, and the face is read off the axis whose slab the ray entered
     * through: for an axis aligned box that axis <em>is</em> the entry face, and reading it off the entry point
     * instead would make a click on the middle of a face a three way tie of its edges.
     */
    @Nullable
    private Hit faceAt(double mouseX, double mouseY) {
        if (!isOverModel(mouseX, mouseY)) return null;

        var blocks = blocks();
        if (blocks.isEmpty() || renderedScale <= 0.0F) return null;

        var ray = transform().pickingRay((float) mouseX, (float) mouseY);
        if (ray == null) return null;

        Hit closest = null;
        for (var entry : blocks) {
            var hit = entryHit(ray.origin(), ray.direction(), entry.offset());
            if (hit != null && (closest == null || hit.distance() < closest.distance())) {
                closest = hit;
            }
        }
        return closest;
    }

    /**
     * The hit of the ray with one block's box, or {@code null} while it misses it: the distance to the near
     * intersection and the face the ray entered through.
     * <p>
     * This is Oritech's own slab test, repeated here because it is private and because this class needs the
     * winning axis as well: for an axis aligned box the axis whose slab gives the latest near intersection is the
     * face the ray was still outside of when it reached the box, which is the definition of the entry face. The
     * box is the model-space cell of the entry, which is what the renderer translates its own geometry into.
     */
    @Nullable
    private Hit entryHit(Vector3f origin, Vector3f direction, Vec3i offset) {
        var minimums = new float[] {
                offset.getX() - center.x - 0.5F,
                offset.getY() - center.y - 0.5F,
                offset.getZ() - center.z - 0.5F};
        var directions = new float[] {direction.x, direction.y, direction.z};

        float near = 0.0F;
        float far = Float.POSITIVE_INFINITY;
        int nearAxis = -1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0E-6F) {
                float originOnAxis = componentGet(origin, axis);
                if (originOnAxis < minimums[axis] || originOnAxis > minimums[axis] + 1.0F) return null;
                continue;
            }

            float originOnAxis = componentGet(origin, axis);
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

    /**
     * The transform of the frame this widget is about to draw - the same numbers
     * {@link #renderContent(GuiGraphicsExtractor, int, int, float)} submits, built through the one helper the
     * renderer's state is also described by (see {@link PreviewTransform}), so the picking and the drawing cannot
     * disagree.
     */
    private PreviewTransform transform() {
        return PreviewTransform.of(contentX() + contentWidth() * 0.5F, contentY() + contentHeight() * 0.5F,
                renderedScale, pitch, yaw, center.x, center.y, center.z);
    }

    /** The one block of this model, as the picking and the measuring both want it. */
    private List<BlockEntry> blocks() {
        BlockState machineState = state;
        if (machineState == null) return List.of();

        return List.of(new BlockEntry(machineState, entity, Vec3i.ZERO));
    }

    /**
     * Works out the centre and the scale of the model, exactly like the stock widget works them out for the frame
     * it is about to submit - including the core positions of a multiblock machine, because the renderer measures
     * the machine's whole model and a single cell would otherwise be measured too small here.
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

    /** Component of a vector on one axis; the array form the slab test uses. */
    private static float componentGet(Vector3f vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    /** One picked block of the model, the face the ray entered it through and how far away that was. */
    private record Hit(Vec3i offset, Direction face, float distance) {
    }
}
