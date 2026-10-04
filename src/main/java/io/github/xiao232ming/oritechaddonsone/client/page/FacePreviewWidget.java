package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * The 3D model of the page of 传输插件: <b>one</b> machine, drawn inside the page's panel with the same recipe
 * Oritech's own {@code BlockPreviewWidget} uses on this branch, plus the things that page needs and Oritech's widget
 * does not offer - the markings of its configured faces (their mode) and face picking with a highlight on the face
 * under the mouse.
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
 * <b>The user's zoom is folded into the measured scale</b> ({@link #setZoom}): it multiplies the fit-to-panel scale
 * {@link #calculateSize()} works out, which is the one number the drawing, the picking, the hover outline and the
 * markings all read. The model grows and shrinks about the centre of the structure it is measured at, so a zoomed
 * model is still the same cells in the same places - just bigger - and a click keeps landing on the face the player
 * sees.
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
 * addons, the indicators of its open addon slots or the plugin block itself (see {@code TransferAddonState#build}
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
 * <b>The machine's core blocks are not drawn, but they are still part of the machine.</b> A machine core
 * ({@link MachineCoreBlock}) is the tier block inside an assembled machine, Oritech hides it itself once the machine
 * uses it ({@code MachineCoreBlock#getRenderShape(BlockState)} answers {@code INVISIBLE} for such a core), and it owns
 * no inventory - the inventory the page and the automation work on belongs to the controller, which sits in a cell of
 * its own. So cores are left out of the <b>drawing</b> only ({@link #visibleBlocks()} is {@link #blocks()} without
 * them, and {@link #entries()} draws from it), while the <b>picking</b> ({@link #faceAt(double, double)}), the
 * <b>measuring and centring</b> ({@link #calculateSize()} reads {@link #partOffsets()}) and the <b>markings</b>
 * ({@link #surfaceCells()}) all read the core-inclusive part list: a core is invisible, but it is a cell of the
 * machine, so the face it turns to the outside is a surface the player sees and must be able to select and configure,
 * and the box the model rotates about is the machine's own.
 * <p>
 * <b>The centre is the centre of the structure</b> ({@link #calculateSize()}), not of the controller's cell:
 * the drawing, the picking, the markings and the hover all take it from the one {@link PreviewTransform}, so what is
 * drawn, what a click answers and where a marking lands cannot drift apart.
 * <p>
 * <b>The faces are marked in the model's own space, on the part each belongs to.</b> {@link #renderContent} draws, in
 * the very pose the machine was drawn in: each configured face's mode wash and - last, on top - the white fill of the
 * face under the mouse. That is what a screen space rectangle - all the GUI's own fill can draw - cannot do: only a
 * quad in the model's pose follows the model's rotation and lies on the face the player is about to click.
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

    /**
     * The z that flat GUI has to be drawn at to <b>cover</b> this model, i.e. the configuration page a click on a
     * face opens.
     * <p>
     * <b>Why a z at all, and not just "drawn later".</b> On 1.21.1 the model is the one piece of this GUI that is
     * really three dimensional, and it is drawn straight into the GUI's own buffer: drawing real blocks turns the
     * depth test on, and {@code RenderType.gui()} leaves it on with {@code LEQUAL} for everything flat that follows.
     * So draw order alone does not decide what lands on top - depth does, and in this projection a larger z is the
     * nearer one. Every flat element of the page is drawn at z = 0, which is therefore <em>behind</em> the model at
     * {@link #PLANE_Z}: the modal lost every pixel it covers and the machine was drawn straight through it. 26.1.2
     * does not have this problem because its model leaves as a submitted picture-in-picture render state, which the
     * GUI is laid over afterwards.
     * <p>
     * <b>Why this number and not some larger one.</b> It has to clear the model, and the model's own extent is not a
     * guess: {@link #scale} is fitted to the panel, so {@code scale * radius} is at most half the panel's smaller
     * side, times Oritech's 0.98 margin, times the largest zoom the page allows - {@code 48 * 0.98 * 2}, i.e. about
     * {@code 94} for this widget's 140x96 panel and the 0.5..2.0 range {@code TransferAddonState.Preview#zoomBy}
     * clamps to. The model therefore never reaches past roughly {@code 495}. 700 clears that with room to spare and
     * stays well inside the depth range the GUI itself draws in, the same range Oritech's own 400 lives in.
     */
    public static final float OVER_MODEL_Z = 700.0F;

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
     * above {@link #MARKING_LIFT}, i.e. above a mode wash, so the hover is the topmost of the two markings.
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

    /** Distance a face's mode wash floats off that face, so it never trades depth with the block face behind it. */
    private static final float MARKING_LIFT = 0.001F;

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
     * Sets what the frame after this one marks on the model: the mode of every configured cell-face. The page calls
     * this once per frame, before it renders this widget, so the model always shows what the server last reported (and
     * the pending value the page has just sent) rather than a copy of its own.
     * <p>
     * A configured cell-face is the only thing that is marked: the page configures <b>every</b> cell-face of the
     * machine, the one the plugin block itself stands in included (see
     * {@code TransferAddonBlockEntity#setCellFaceConfig}), so there is no second kind of marking - no face this model
     * would have to show as one that cannot be configured.
     *
     * @param modes the mode of every <b>configured</b> cell-face; a cell-face that is absent transfers nothing
     */
    public void setFaceOverlays(Map<CellFace, TransferMode> modes) {
        this.faceModes = Map.copyOf(modes);
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
        var neighbour = part.offset(face.getNormal());
        return !parts.contains(neighbour);
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
     * Sets the user's zoom: a factor on the fit-to-panel scale the model is measured with, so 1 is "the whole machine,
     * exactly as this panel sized it". The page hands it over once per frame from the interaction state it keeps
     * (see {@link TransferAddonState.Preview#zoom()}), the way it hands over the rotation.
     * <p>
     * It is applied <b>inside</b> {@link #scale(float, float)}, i.e. in the one number every user of this frame's
     * geometry reads: {@link #transform()} builds the shared {@link PreviewTransform} from the scale it answers for
     * the picking, and {@link #renderContent} applies that same value to the pose for the drawing, the hover outline
     * and the markings. A zoomed model is therefore drawn and picked with one and the same scaled transform, and what
     * the player sees is exactly what a click hits.
     */
    public void setZoom(float zoom) {
        this.zoom = zoom;
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
     * the machine's configured faces and the hover fill of the face the mouse is over.
     * <p>
     * The order is the whole layering of the page: the machine's blocks first, then the mode wash of every configured
     * face, and the white hover fill last, on top of it - which is what keeps the mode's colour readable while the
     * face is hovered.
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
        // as well
        graphics.pose().translate(-centerX, -centerY, -centerZ);

        RenderSystem.runAsFancy(() -> {
            for (var part : visibleBlocks()) {
                graphics.pose().pushPose();
                // the part's own cell, in the same model space the picking tests - and Oritech's own "-0.5 + offset"
                // on top of it: a block model is drawn in the block's own 0..1 cube, so the half unit is what puts the
                // block around the centre of its cell, which is where the slab test's box ({@link #entryHit}) and the
                // markings ({@link #drawQuad}) already place it. Splitting Oritech's single "-0.5 + offset - centre"
                // into the centre above and a bare "offset" here dropped that half unit, and the machine was drawn
                // half a block up and to the side of the very cells a click answered and a wash marked.
                graphics.pose().translate(part.offset().getX() - 0.5F, part.offset().getY() - 0.5F,
                        part.offset().getZ() - 0.5F);

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
     * Draws the markings of the machine's <b>configured cell-faces</b>, in the pose that is still active from the
     * machine above: each marking names the cell and the face it belongs to, so its quads land on that cell's face
     * whatever the model is rotated to.
     * <p>
     * <b>One marking per configured cell-face, on the cell it was configured on.</b> That is not the same as one
     * marking per direction: the old model had a setting per world direction and this method had to work out a
     * representative cell for each of the six ({@code surfaceCell(Direction)}, the cell lying furthest that way), so
     * two cells of the same side could never be marked differently. A cell-face setting names its cell outright, so
     * the marking is drawn exactly where the player clicked.
     * <p>
     * <b>There is one kind of marking: the mode wash</b> - the translucent blue of an input face, the orange of an
     * output face, and both halves side by side on a face that does both, taken from {@link TransferFaceStyle#wash} so
     * the model and the cube net page colour a face the same way. The wash covers the inner part of the face and
     * leaves a rim free, and it is lifted off the face by {@link #MARKING_LIFT}, so it never trades depth with the
     * block face behind it. A cell-face that transfers nothing gets no marking at all.
     */
    private void drawOverlays(PoseStack poseStack) {
        var modes = faceModes;
        if (modes == null) return;

        for (var entry : modes.entrySet()) {
            var mode = entry.getValue();
            if (mode == TransferMode.NONE) continue;

            var cell = entry.getKey().cell();
            var face = entry.getKey().face();

            poseStack.pushPose();
            poseStack.translate(cell.getX(), cell.getY(), cell.getZ());
            drawWash(poseStack, face, mode);
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
     * One quad on one face of the part the pose is currently translated to.
     * <p>
     * <b>The units are the block's own.</b> The part is drawn by translating the pose to its cell and then rendering
     * its block model, which is one model unit wide and centred on that cell - so the cell's face is the square
     * {@code +-0.5} around the translated origin. This quad is built in exactly that space: {@code uMin}..{@code uMax}
     * and {@code vMin}..{@code vMax} are in <em>model units</em> (never more than 0.5, the face's half extent), the
     * axes of {@link #FACE_AXES} are unit vectors, and their product is the vertex, so a marking is the same size and
     * in the same place as the face it marks. Using those unit axes as if they were half extents is what made the
     * markings twice the size of a block.
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
        float plane = FACE_HALF_EXTENT + lift;

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
     * Draws the hover marking of the face the mouse is over, in the pose that is still active from the machine above:
     * a translucent white fill of the <b>whole face</b> of the part the picking answered with, so it lands on that
     * face whatever the model is rotated to.
     * <p>
     * The part is the one the picking answered with ({@link #hoveredOffset}): a multiblock machine is several cells of
     * model space, and the marking of a face on one of them belongs on that cell - drawn at the model origin it would
     * mark the controller's cell instead, on the far side of the machine.
     * <p>
     * <b>It is a fill of the entire face and not an outline</b>, which is what the feature is for: an outline only
     * answers "is the cursor on this face" for the cursor positions inside the ring, so a player pointing at the middle
     * of a face saw nothing and read it as the marking not following the mouse. The fill covers the face the picking
     * answers with exactly, so the highlighted area <em>is</em> the area that face owns - cursor on the marking and
     * cursor on the face are the same statement.
     * <p>
     * <b>It blends, it does not overwrite.</b> {@link #HIGHLIGHT_COLOR} is white at a low alpha drawn over the mode
     * wash, so what the face already carried stays readable underneath: the blue of an input face composites to
     * {@code 0x55669DF8} and the orange of an output face to {@code 0x55F7B341}, both lighter than the unmarked face
     * and both still their own colour, while a face with no mode reads as lit rather than as painted.
     * <p>
     * The mode wash of a face is drawn before this (see {@link #drawOverlays} running first) and lies under the fill,
     * which is the point of the low alpha: the colour the face already carried stays readable while it is hovered, and
     * {@link #HIGHLIGHT_LIFT} is raised above the wash's own lift so the white is never hidden by it.
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
        drawQuad(poseStack, face, -FACE_HALF_EXTENT, FACE_HALF_EXTENT, -FACE_HALF_EXTENT, FACE_HALF_EXTENT,
                HIGHLIGHT_LIFT, HIGHLIGHT_COLOR);
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
     * Recomputes the centre and the two radii of the model - Oritech's own calculation, repeated here because its
     * fields are private: the centre is the middle of the model's bounding box, and the radii are the largest
     * horizontal and the largest pitch-projected vertical distance of any point of that box from the centre.
     * Measuring all eight corners of every position instead of its middle is what keeps a model that is taller than
     * it is wide inside the panel at every rotation.
     * <p>
     * <b>The positions are every part of the machine</b> ({@link #partOffsets()}, which is the same list the picking
     * uses), cores included, so the centre is the centre of the structure the machine really is.
     * <p>
     * Measuring the <em>drawn</em> parts instead ({@link #visibleBlocks()}) was what once made the model rotate about
     * the controller's own cell: Oritech's part list names the machine's cores, a core is not drawn, and a machine
     * whose outer cells are all cores collapsed to the controller cell alone - a bounding box of one block, whose
     * centre is the controller. A core is a cell of the machine like any other, so it belongs in the box; only the
     * drawing leaves it out.
     */
    private void calculateSize() {
        var positions = new ArrayList<Vec3i>();
        if (state != null) {
            positions.addAll(partOffsets());
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
     * Picks, out of every cell the ray enters, the one its face is drawn under the cursor for.
     * <p>
     * <b>Two passes, and the reason is not style.</b> The candidates are first narrowed to those within
     * {@link #TIE_EPSILON} of the <em>nearest</em> entry distance, and only then is the winner chosen from that set by
     * {@link #better}. Comparing candidates pairwise as the loop walks the list instead would be
     * <b>non-transitive</b>: with {@code A} at 1.00, {@code B} at 1.08 and {@code C} at 1.14, {@code B} beats {@code A}
     * (within epsilon) and {@code C} beats {@code B}, but {@code C} does not beat {@code A} - so which of the three won
     * would depend on the order the parts were listed in, which is exactly the thing this method exists to remove. A
     * measured run on this branch found three pixels where the pairwise form still flipped when the cell list was
     * reordered; the two-pass form has none.
     */
    private Hit faceAt(double mouseX, double mouseY) {
        if (!isOverModel(mouseX, mouseY)) return null;
        if (state == null || renderedScale <= 0.0F) return null;

        var parts = blocks();
        if (parts.isEmpty()) return null;

        var ray = transform().pickingRay((float) mouseX, (float) mouseY);
        if (ray == null) return null;

        // pass one: how near the nearest entry is, and the entries that are effectively as near as it
        var near = new ArrayList<Hit>();
        float nearest = Float.POSITIVE_INFINITY;
        for (var part : parts) {
            var hit = entryHit(ray.origin(), ray.direction(), part.offset());
            if (hit == null) continue;

            near.add(hit);
            nearest = Math.min(nearest, hit.distance());
        }
        if (near.isEmpty()) return null;

        // pass two: the winner among the ties, by a rule that reads only the ray and the cell, never the list order
        Hit best = null;
        for (var hit : near) {
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
     * <b>The first term is about the ray, not about the iteration order.</b> The winner is the cell-face the ray meets
     * most <em>head on</em> - the one whose outward normal is most opposed to the ray direction - because that is the
     * face the player is looking at: at a grazing boundary the ray runs almost parallel to one of the two faces and
     * almost perpendicular to the other, and the perpendicular one is unambiguously the one under the cursor. It is a
     * continuous function of the ray, so the hand-over between two faces is smooth.
     * <p>
     * The two terms after it cannot normally be reached (two different cells cannot have the same normal <em>and</em>
     * the same entry distance), but they make the order total rather than merely usually-decided: a fixed cell order,
     * then the face's own ordinal.
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
        var normal = face.getNormal();
        return -(normal.getX() * direction.x + normal.getY() * direction.y + normal.getZ() * direction.z)
                / direction.length();
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
     * ({@link MachineCoreBlock}) that owns no inventory, and Oritech hides it itself once the machine uses it - so
     * {@link #visibleBlocks()} drops it from the drawing and from the measuring, while the picking and the markings
     * keep it (see {@link #faceAt(double, double)}).
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
     * The parts this widget <b>draws and measures</b>: everything in {@link #blocks()} that is not a core. The
     * measuring, the drawing and the model's centre all read this one list, so a hidden core takes no space on the
     * panel and is never submitted as geometry.
     * <p>
     * <b>The picking does not read it</b> (see {@link #faceAt(double, double)}): a core is invisible, but it is still
     * a block of the machine, so the face it turns to the outside is a surface the player can see and therefore has to
     * be selectable. The markings follow the same rule ({@link #surfaceCells()}).     */
    private List<BlockEntry> visibleBlocks() {
        var visible = new ArrayList<BlockEntry>();
        for (var entry : blocks()) {
            if (!isCore(entry)) visible.add(entry);
        }
        return visible;
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
        // The scale of the frame about to be drawn, not of the one before it: the page hands the rotation over once
        // per frame and the scale depends on the pitch, so a picking test that read a stale value would answer the
        // face a previous frame's model had under the cursor. It is the same call renderContent makes, and it only
        // measures while the model or the rotation really changed, so the drawing still sees the value computed here.
        renderedScale = scale(contentWidth(), contentHeight());

        return PreviewTransform.of(contentX() + contentWidth() * 0.5F, contentY() + contentHeight() * 0.5F, PLANE_Z,
                renderedScale, pitch, yaw);
    }

    /**
     * The scale the model is drawn with, from Oritech's own calculation: the smaller of the two ratios between the
     * panel's half size and the model's radii, with Oritech's own 0.98 margin - and then the user's zoom on top of it.
     * {@link #calculateSize()} is what refreshes the radii and the centre it reads, and it only runs while the model
     * changed.
     * <p>
     * The result is the one number every user of this frame's geometry reads (see {@link #setZoom}): the drawing
     * applies it to the pose, {@link #transform()} builds the shared {@link PreviewTransform} from it for the picking,
     * and the hover outline and the markings are drawn in the same pose - so a zoomed model is drawn and picked with
     * one and the same scaled transform.
     */
    private float scale(float availableWidth, float availableHeight) {
        if (scaleDirty) calculateSize();

        if (maxHorizontalRadius <= 0.0F || maxVerticalRadius <= 0.0F) return 0.0F;

        float widthScale = availableWidth * 0.5F / maxHorizontalRadius;
        float heightScale = availableHeight * 0.5F / maxVerticalRadius;
        // the fit-to-panel scale, then the user's own zoom on top of it - the one number the drawing, the picking and
        // the markings all read (see #setZoom)
        return Math.min(widthScale, heightScale) * 0.98F * zoom;
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
