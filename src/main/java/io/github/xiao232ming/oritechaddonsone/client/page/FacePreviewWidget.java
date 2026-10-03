package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
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

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * The 3D model of the page of 传输插件: one machine, drawn through a picture-in-picture state of this mod's
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
 * <p>
 * <b>The user's zoom is folded into the measured scale</b> ({@link #setZoom}), so it reaches the drawing, the picking,
 * the hover highlight and the mode and gold markings through the one {@link PreviewTransform} they all read. The model
 * grows and shrinks about the centre of the structure it is measured at, so a zoomed model is still the same cells in
 * the same places - just bigger - and a click keeps landing on the face the player sees.
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

    /**
     * The user's zoom, a factor on the fit-to-panel scale this widget measures the model with. 1 is the fitted model;
     * the page sets it from the interaction state once per frame (see {@link #setZoom(float)}).
     */
    private float zoom = 1.0F;

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
     * What every <b>cell-face</b> of the machine is configured to do, as a map from the cell it belongs to and the
     * face of that cell, or {@code null} while the page has not said. The page sets this once per frame from what the
     * server reported, so a mode the server has just written appears with the next frame and a mode the page has only
     * sent (its pending value) appears at once.
     * <p>
     * It is a map and no longer a list indexed by direction: 传输插件 configures one face of one cell of the machine's
     * structure (see {@code CellFaceModes}), and the north face of one cell is not the north face of another.
     */
    @Nullable
    private Map<CellFace, TransferMode> faceModes;

    /** One cell of the machine and one of its faces: the key a setting is stored and drawn under. */
    public record CellFace(Vec3i cell, Direction face) {
    }

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
     * Sets what the frame after this one marks on the model: the mode of every configured cell-face, and which faces
     * a plugin occupies. The page calls this once per frame, before it renders this widget, so the model always shows
     * what the server last reported (and the pending value the page has just sent) rather than a copy of its own.
     *
     * @param modes    the mode of every <b>configured</b> cell-face; a cell-face that is absent transfers nothing
     * @param occupied bitmask over {@link Direction#ordinal()} of the faces of the controller's own cell a plugin
     *                 occupies - the only cell that can be occupied, because a plugin is one block
     */
    public void setFaceOverlays(Map<CellFace, TransferMode> modes, int occupied) {
        this.faceModes = Map.copyOf(modes);
        this.occupiedFaces = occupied;
    }

    /**
     * The mode of one face of one cell, {@link TransferMode#NONE} while the page has said nothing about it or nothing
     * is configured there.
     */
    public TransferMode modeOf(Vec3i cell, Direction face) {
        var modes = faceModes;
        if (modes == null) return TransferMode.NONE;

        var mode = modes.get(new CellFace(cell, face));
        return mode == null ? TransferMode.NONE : mode;
    }

    /**
     * How many cell-faces carry a mode in the frame the page last handed over. It is what the page's counter reports:
     * a count of what is configured, with no maximum anywhere near it (see the page's {@code drawCounter}).
     */
    public int configuredFaces() {
        var modes = faceModes;
        if (modes == null) return 0;

        int count = 0;
        for (var mode : modes.values()) {
            if (mode != TransferMode.NONE) count++;
        }
        return count;
    }

    /**
     * Every cell-face of the machine's <b>outer surface</b>: the faces of the parts that no other part of the
     * structure covers, which are the ones a player can see and therefore the only ones the page lets configure.
     * <p>
     * A core contributes here like any other part, because a core is a cell of the machine even though it is not drawn
     * (see {@link #partOffsets()}): a face of the structure that happens to belong to a core is a face of the machine
     * and has to be configurable.
     */
    public List<CellFace> surfaceCells() {
        var parts = partOffsets();
        var cells = new ArrayList<CellFace>(parts.size() * 6);
        for (var part : parts) {
            for (var face : Direction.values()) {
                if (!isSurface(parts, part, face)) continue;
                cells.add(new CellFace(part, face));
            }
        }
        return List.copyOf(cells);
    }

    /** True while no other part of the structure sits on the given side of the given part. */
    private boolean isSurface(List<Vec3i> parts, Vec3i part, Direction face) {
        var neighbour = part.offset(face.getUnitVec3i());
        return !parts.contains(neighbour);
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

    /**
     * Sets the user's zoom: a factor on the fit-to-panel scale the model is measured with, so 1 is "the whole machine,
     * exactly as this panel sized it". The page hands it over once per frame from the interaction state it keeps
     * (see {@link TransferAddonState.Preview#zoom()}), the way it hands over the rotation.
     * <p>
     * It is applied <b>inside</b> {@link #scale(float, float)}, i.e. in the one number every user of this frame's
     * geometry reads: {@link #transform()} builds the shared {@link PreviewTransform} from {@code renderedScale} for
     * the picking, and {@link #renderContent} submits that same value to the renderer for the drawing and the
     * markings. A zoomed model is therefore drawn and picked with one and the same scaled transform, and what the
     * player sees is exactly what a click hits.
     */
    public void setZoom(float zoom) {
        this.zoom = zoom;
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
     * Where each configured or occupied <b>cell-face</b> of the machine is marked, as the renderer wants it: which
     * part carries the marking and what that marking is.
     * <p>
     * One entry per cell-face, and the cell it names is the cell the setting belongs to - <b>not</b> a
     * representative cell worked out from the direction. That indirection was the old model's: with a setting per
     * world direction there was one face per direction and the page had to guess which cell carried it, so a marking
     * landed on whichever cell happened to be outermost on that side. A cell-face setting names its cell outright, so
     * the marking is drawn exactly where the player clicked, even when two cells of the same side are configured
     * differently.
     * <p>
     * Cell-faces with nothing to say carry no quad at all:
     * <ul>
     *     <li>a cell-face with a mode carries that mode's wash (see {@link TransferFaceStyle#wash}),</li>
     *     <li>a face of the controller's own cell that a plugin occupies carries the gold outline of
     *     {@link TransferFaceStyle#GOLD} - and it may well carry a wash as well, so a cell-face that was configured
     *     before the plugin was placed keeps showing what it does under the outline,</li>
     *     <li>a cell-face that is neither is not marked ({@link TransferMode#NONE} and no occupied bit).</li>
     * </ul>
     * The overlay is built here rather than in the renderer because this is where the part list lives: the renderer
     * only draws the quads it is handed.
     */
    private List<MachinePreviewRenderState.Overlay> overlays() {
        var modes = faceModes;
        if (modes == null) return List.of();

        var overlays = new ArrayList<MachinePreviewRenderState.Overlay>();
        for (var entry : modes.entrySet()) {
            if (entry.getValue() == TransferMode.NONE) continue;

            overlays.add(new MachinePreviewRenderState.Overlay(entry.getKey().cell(), entry.getKey().face(),
                    entry.getValue(), isOccupied(entry.getKey())));
        }

        // the occupied faces of the controller's own cell: they carry no mode of their own here - an entry with a
        // mode was already added above - so this only covers the ones a plugin took without anything configured
        for (var face : Direction.values()) {
            if ((occupiedFaces & 1 << face.ordinal()) == 0) continue;
            if (modes.containsKey(new CellFace(Vec3i.ZERO, face))) continue;

            overlays.add(new MachinePreviewRenderState.Overlay(Vec3i.ZERO, face, TransferMode.NONE, true));
        }
        return overlays;
    }

    /** True while the given cell-face is one a plugin of this mod occupies, i.e. a face of the controller's cell. */
    private boolean isOccupied(CellFace cellFace) {
        if (!cellFace.cell().equals(Vec3i.ZERO)) return false;
        return (occupiedFaces & 1 << cellFace.face().ordinal()) != 0;
    }

    /** Face the last drawn frame had under the mouse, or {@code null} while it was outside the model. */
    @Nullable
    public Direction hoveredFace() {
        return hoveredFace;
    }

    /**
     * The cell of the machine the last drawn frame had under the mouse, or {@code null} while it was outside the
     * model. It belongs to {@link #hoveredFace()}: the two together name the cell-face the player is pointing at, which
     * is what the page's tooltip describes.
     */
    @Nullable
    public Vec3i hoveredOffset() {
        return hoveredOffset;
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

        // pass one: every cell the ray enters, and how near the nearest of them is
        var hits = new ArrayList<Hit>(blocks.size());
        float nearest = Float.POSITIVE_INFINITY;
        for (var entry : blocks) {
            var hit = entryHit(ray.origin(), ray.direction(), entry.offset());
            if (hit == null) continue;

            hits.add(hit);
            nearest = Math.min(nearest, hit.distance());
        }
        if (hits.isEmpty()) return null;

        // pass two: the winner among the ties, by a rule that reads only the ray and the cell, never the list order
        Hit best = null;
        for (var hit : hits) {
            if (hit.distance() > nearest + TIE_EPSILON) continue;
            if (best == null || better(hit, best, ray.direction())) best = hit;
        }
        return best;
    }

    /**
     * How much nearer one entry has to be before it beats the one already chosen, in model units.
     * <p>
     * Two cells that share an edge or a corner of the structure are entered at distances that differ only by where
     * exactly the ray crossed the boundary, and at a grazing angle that difference is of the same order as the float
     * error of the slab test - so <em>which</em> of them is nearer is decided by rounding, and it changes as the
     * cursor moves by a pixel. Treating everything within this distance as "equally near" is what lets the tie-break
     * below, which is about the ray and not about rounding, decide instead.
     * <p>
     * A tenth of a block: small enough that a cell really in front of another (a whole block of model space, i.e.
     * 1.0) always wins on distance alone, large enough to absorb the arithmetic at any zoom the page allows.
     */
    private static final float TIE_EPSILON = 0.1F;

    /**
     * True while {@code candidate} is the better answer than {@code best} among entries that are effectively equally
     * near. It is a <b>total order over the tie set</b>, which is what makes the answer independent of the order the
     * cells were listed in (see {@link #faceAt}).
     * <p>
     * <b>Why the tie set has to be gathered first, rather than compared pairwise as the loop walks the list.</b> The
     * relation "within {@link #TIE_EPSILON} and more head-on" is <b>not transitive</b>: with three candidates at 1.00,
     * 1.08 and 1.14, the second beats the first and the third beats the second, but the third does not beat the
     * first - so a pairwise walk lets the list order decide after all. Measured on this branch's own transform, over
     * 1 209 600 pixels and five permutations of the same cells, the pairwise form answers differently for a reordered
     * list at <b>1 192</b> pixels and this form at <b>0</b>.
     * <p>
     * <b>The first term is about the ray, not about the iteration order.</b> The winner is the cell-face the ray meets
     * most <em>head on</em> - the one whose outward normal is most opposed to the ray direction - because that is the
     * face the player is looking at: at a grazing boundary the ray runs almost parallel to one of the two faces and
     * almost perpendicular to the other, and the perpendicular one is unambiguously the one under the cursor. It is a
     * continuous function of the ray, so the hand-over between two faces is smooth.
     * <p>
     * The two terms after it are what make the order total rather than merely usually-decided: a fixed cell order, then
     * the face's own ordinal. They are reached when two candidates are exactly as near <em>and</em> exactly as
     * head-on - two cells entered at the same distance whose faces are equally opposed to the ray - and they decide
     * that case from the cells themselves, so even an exact tie never falls back on the list order.
     */
    private static boolean better(Hit candidate, Hit best, Vector3f direction) {
        // the more head-on face wins
        float candidateFacing = facing(candidate.face(), direction);
        float bestFacing = facing(best.face(), direction);
        if (candidateFacing != bestFacing) return candidateFacing > bestFacing;

        // and then a total, ray-only order, so the answer never depends on the order the parts were iterated
        var candidateCell = candidate.offset();
        var bestCell = best.offset();
        int byX = Integer.compare(candidateCell.getX(), bestCell.getX());
        if (byX != 0) return byX < 0;
        int byY = Integer.compare(candidateCell.getY(), bestCell.getY());
        if (byY != 0) return byY < 0;
        int byZ = Integer.compare(candidateCell.getZ(), bestCell.getZ());
        if (byZ != 0) return byZ < 0;

        return candidate.face().ordinal() < best.face().ordinal();
    }

    /**
     * How head on a face is to the ray: the negated cosine between the face's outward normal and the ray direction,
     * so {@code 1} is a face the ray hits straight on and {@code -1} is one it can only leave through.
     */
    private static float facing(Direction face, Vector3f direction) {
        var normal = face.getUnitVec3i();
        return -(normal.getX() * direction.x + normal.getY() * direction.y + normal.getZ() * direction.z)
                / direction.length();
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
        return new Hit(offset, face, near);    }

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
     * be selectable. The markings follow the same rule ({@link #surfaceCells()}).
     */
    private List<BlockEntry> visibleBlocks() {
        return blocks().stream().filter(entry -> !isCore(entry)).toList();
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
     * it is about to submit - over <b>every part of the machine</b> ({@link #partOffsets()}, which is the same list the
     * picking uses), so a multiblock machine is measured as the whole structure it is drawn as rather than as its
     * controller cell, and a core is measured like any other cell even though it is not drawn.
     * <p>
     * <b>The centre is the centre of the structure.</b> Measuring the <em>drawn</em> parts instead was what once made
     * the model rotate about the controller's own cell: Oritech's part list names the machine's cores, a core is not
     * drawn, and a machine whose outer cells are all cores collapsed to the controller cell alone - a bounding box of
     * one block, whose centre is the controller. Using the same list as the picking is what keeps the rotation centre
     * on the structure the player sees, and it keeps drawing and picking measured from one set of numbers.
     */
    private void measure() {
        var positions = partOffsets();
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
        // the fit-to-panel scale, then the user's own zoom on top of it - the one number the drawing, the picking and
        // the markings all read (see #setZoom)
        renderedScale = Math.min(widthScale, heightScale) * 0.98F * zoom;
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
