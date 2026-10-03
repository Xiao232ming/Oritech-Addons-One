package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import rearth.oritech.api.screen.widgets.BlockPreviewWidget;
import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * The 3D model of the transfer preview page: one machine, drawn through a picture-in-picture state of this mod's
 * own so that a translucent highlight can be drawn on the face the mouse is over (see
 * {@link MachinePreviewPipRenderer}).
 * <p>
 * It replaces the drawing half of Oritech's {@link BlockPreviewWidget} for three reasons, all of them because the
 * widget's model lives in private fields of that class:
 * <ul>
 * <li>the page's model is <b>one</b> machine, so the widget keeps one state, one entity and the machine's own part
 * list instead of Oritech's block list (see {@link #partOffsets()}),</li>
 * <li>the hover highlight needs the face the mouse is over to be drawn <em>in</em> the model's render pass, which
 * means submitting this mod's state rather than Oritech's,</li>
 * <li>and it needs that face plus the block it belongs to, which Oritech's widget never keeps.</li>
 * </ul>
 * <b>Every part of a multiblock machine is a candidate.</b> The machine the page configures can be several blocks of
 * world, and its surface is the surface of all of them - so the picking tests one cell per part and the face it
 * reports is the world direction of the part it entered (see {@link #partOffsets()}). The page's per-face modes are
 * keyed on exactly that direction, which is why the answer is a direction and not a part index.
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

    /**
     * Part of the machine the last drawn frame had under the mouse, or {@code null} while it was outside the model.
     * It is the offset of the block whose face {@link #hoveredFace} names, which is what puts the highlight on the
     * part the mouse is really over instead of on the core block of a multiblock machine.
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

    /** The 3D preview of one machine: 140x110 pixels, the size the page's panel reserves for it. */
    public FacePreviewWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.pitch = 30.0F;
        this.yaw = 225.0F;
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
        this.hoveredOffset = hovered == null ? null : hovered.offset();
        super.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * Submits the model of this frame: every part of the machine, the markings of the faces (their mode's wash and
     * the gold outline of an occupied face) and, while the mouse is on it, the face to highlight and the part that
     * face is on.
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
            this.hoveredOffset = null;
            return;
        }

        measure();
        if (renderedScale <= 0.0F) return;

        int cx = contentX();
        int cy = contentY();
        int width = contentWidth();
        int height = contentHeight();

        graphics.submitPictureInPictureRenderState(MachinePreviewRenderState.of(
                entries(), overlays(), hoveredFace, hoveredOffset, pitch, yaw, center.x, center.y, center.z, delta,
                cx, cy, cx + width, cy + height, renderedScale, graphics.pose(), graphics.peekScissorStack()));
    }

    /** The model's entries in the render state's own shape: one per drawn part, i.e. never a core. */
    private List<MachinePreviewRenderState.Entry> entries() {
        return visibleBlocks().stream()
                .map(block -> new MachinePreviewRenderState.Entry(block.state(), block.entity(), block.offset()))
                .toList();
    }

    /**
     * Where each of the machine's six faces is marked, as the renderer wants it: which part carries the marking and
     * what that marking is.
     * <p>
     * One entry per direction, and each names the cell of {@link #surfaceCell(Direction)} - the part whose face on
     * that side is on the machine's outer surface, which is the one the player sees. Directions with nothing to say
     * are left out, so a face that is neither configured nor occupied carries no quad at all:
     * <ul>
     *     <li>a face with a mode carries that mode's wash (see {@link TransferFaceStyle#wash}),</li>
     *     <li>a face a plugin occupies carries the gold outline of {@link TransferFaceStyle#GOLD} - and it may well
     *     carry a wash as well, so a face that was configured before the plugin was placed keeps showing what it does
     *     under the outline,</li>
     *     <li>a face that is neither is not marked ({@link TransferMode#NONE} and no occupied bit).</li>
     * </ul>
     * The overlay is built here rather than in the renderer because this is where the part list lives: the renderer
     * only draws the quads it is handed.
     */
    private List<MachinePreviewRenderState.Overlay> overlays() {
        var modes = faceModes;
        if (modes == null) return List.of();

        var overlays = new ArrayList<MachinePreviewRenderState.Overlay>();
        for (var face : Direction.values()) {
            var mode = modes.size() > face.ordinal() ? modes.get(face.ordinal()) : TransferMode.NONE;
            boolean occupied = (occupiedFaces & 1 << face.ordinal()) != 0;
            if (mode == TransferMode.NONE && !occupied) continue;

            var offset = surfaceCell(face);
            if (offset == null) continue;

            overlays.add(new MachinePreviewRenderState.Overlay(offset, face, mode, occupied));
        }
        return overlays;
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
     * <p>
     * <b>The candidates are {@link #blocks()}, cores included</b>, and not the shorter list the drawing uses. A core
     * is not drawn, but it is a block of the machine all the same, so the face it turns to the outside is a face of
     * the machine's surface - a face the player sees (nothing is drawn over it) and therefore has to be able to select
     * and configure like any other. Leaving cores out here is what used to make the top of a machine whose outermost
     * cell is a core unclickable. The answer is a world {@link Direction} either way, so the page, its modes and its
     * automation do not care which cell of the machine the click landed on.
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

    /**
     * <b>Every part of the machine</b>, as the picking and the drawing both want them: the controller's own cell plus
     * one entry per part of a multiblock machine (see {@link #partOffsets()}), each with the block state standing at
     * its own position in the client's level.
     * <p>
     * The model of a multiblock machine is several blocks of world, so a single entry would make one cell of it
     * clickable and leave the rest of the machine - the surface the player really sees - unanswerable. One entry per
     * part is also what the stock widget means by its block list: its size, its picking and its drawing all loop over
     * that list, and Oritech's own addon overlay puts the machine's addons into it as one entry each.
     * <p>
     * <b>The machine's core blocks are marked as such</b> ({@link #isCore}). They are the tier blocks inside an
     * assembled machine ({@link MachineCoreBlock}), Oritech hides them itself once they are
     * {@linkplain MultiblockMachineController#isAssembled assembled} ({@code MachineCoreBlock#getRenderShape()} there
     * answers {@code INVISIBLE}), and they own no inventory - so the drawing, the picking and the measuring all skip
     * them (see {@link #isHiddenCore} and {@link #partOffsets()}), while the controller cell the page addresses is
     * never one of them: Oritech's own part lists never name their own controller (see the class comment of
     * {@link #partOffsets()}).
     * <p>
     * The controller's state is the fallback for a part whose position the client cannot read a state from (an
     * unassembled or unloaded machine, where the model has to fall back to the one block the page does know), and only
     * the controller at the model's origin carries the block entity, because a block entity render state belongs to
     * the one position it was extracted from.
     * <p>
     * Drawing every part is not a decoration of the picking: the two have to be the same cells. A model drawn at the
     * origin while the picking tests the whole structure would put clickable surface where nothing is drawn, and the
     * highlight of a face the player really sees on a part would be painted on the wrong part.
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
     * The model is drawn in world orientation (the renderer only rotates it for the viewer, see
     * {@link PreviewTransform}), so an offset rotated that way is a world offset from the controller and the face the
     * picking reads off it is a world direction of the machine.
     * <p>
     * A position Oritech lists twice, or one that falls onto the controller's own cell, contributes one entry: the
     * entries are cells of one solid machine, and a repeated cell would only let the ray answer the same face twice.
     */
    private List<Vec3i> partOffsets() {
        if (!(entity instanceof MultiblockMachineController multiblock)) return List.of(Vec3i.ZERO);

        Direction facing = multiblock.getFacingForMultiblock();
        var offsets = new ArrayList<Vec3i>();
        offsets.add(Vec3i.ZERO);

        for (Vec3i relativeOffset : multiblock.getCorePositions()) {
            var offset = Geometry.rotatePosition(relativeOffset, facing);
            if (!offsets.contains(offset)) offsets.add(offset);
        }
        return offsets;
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
     * The parts this widget <b>draws and measures</b>: everything in {@link #blocks()} that is not a core. The
     * measuring, the drawing and the model's centre all read this one list, so a hidden core takes no space on the
     * panel and is never submitted as geometry.
     * <p>
     * <b>The picking does not read it</b> (see {@link #faceAt(double, double)}): a core is invisible, but it is still
     * a block of the machine, so the face it turns to the outside is a surface the player can see and therefore has to
     * be selectable. The markings follow the same rule ({@link #surfaceCell(Direction)}).
     */
    private List<BlockEntry> visibleBlocks() {
        return blocks().stream().filter(entry -> !isCore(entry)).toList();
    }

    /**
     * The cell whose face on the given side is on the machine's outer surface: the cell of {@link #blocks()} - cores
     * included - that lies furthest along that direction. That is where the face the page colours and marks lives,
     * because the machine is a solid block of cells and the furthest cell along a direction is the one whose face on
     * that side nothing else covers.
     * <p>
     * A hidden core may be that cell: Oritech's cores sit inside an assembled machine but they are ordinary blocks of
     * it, so the outermost cell on some side can well be one - and then the marking belongs on its face, which is a
     * face the player sees and can configure even though the block itself is not drawn.
     * <p>
     * Ties - several cells equally far along the direction - are broken towards the middle of the structure, so a
     * marking sits in the middle of that side rather than in a corner it picked for no reason.
     */
    @Nullable
    private Vec3i surfaceCell(Direction face) {
        var parts = blocks();
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
     * Works out the centre and the scale of the model, exactly like the stock widget works them out for the frame
     * it is about to submit - over every part of the machine ({@link #partOffsets()}), so a multiblock machine is
     * measured as the whole structure it is drawn as rather than as its core cell.
     */
    private void measure() {
        var positions = new ArrayList<Vec3i>();
        for (var entry : visibleBlocks()) {
            positions.add(entry.offset());
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

    /**
     * One block of the model, as the picking, the measuring and the drawing all want it: its state, the block entity
     * that belongs to it (only the controller at the origin has one), where it sits in model space, and whether it is
     * a machine core.
     */
    private record BlockEntry(BlockState state, @Nullable BlockEntity entity, Vec3i offset, boolean core) {
    }
}
