package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;

/**
 * Client side state of the transfer preview page (传输插件): the 3D model of the machine this plugin serves, the
 * rotation the player dragged it into, and the face the player selected.
 * <p>
 * Everything here is presentation, exactly like {@link TransferPickerState}: what a face really does is stored on
 * the block entity, and every value the page shows comes from the menu's container data. Two things cannot come from
 * there, and both are read on this side: the model - a block state and a block entity for the renderer, taken from
 * the client's own level, which has the machine as soon as its chunk is loaded - and <b>which</b> machine that is,
 * which the server resolved and sent with the menu ({@code ExtensionAddonMenu#transferPreviewMachinePos()}), because
 * the offsets it is resolved from never reach a client.
 * <p>
 * The state is kept per menu position - i.e. per panel that was opened, whether that panel belongs to a placed plugin
 * or to an Extension Addon that stores one - and rebuilt when the machine it serves changes, because a GUI can be open
 * while the machine behind the plugin is replaced. It is dropped when the screen closes ({@link #clear(BlockPos)},
 * called by {@code ExtensionAddonScreen#removed()}), so a built model never outlives the GUI it was built for.
 */
public final class TransferPreviewState {

    /** The built preview and its interaction state, one per plugin position that had a GUI open. */
    private static final Map<BlockPos, Preview> PREVIEWS = new ConcurrentHashMap<>();

    private TransferPreviewState() {
    }

    /**
     * The preview of the plugin's machine at {@code machinePos}, built or rebuilt while it does not show that machine
     * yet, or {@code null} while the machine cannot be resolved on this client - its chunk is not loaded, or the
     * plugin serves nothing.
     *
     * @param previewKey position the built model is kept under, i.e. the position of the block the open menu belongs
     *                   to, so a preview never outlives the panel it was built for
     * @param machinePos the machine the plugin serves, as the menu of the open screen reports it - the position the
     *                   server resolved and sent along, because a client cannot resolve it itself (see the class
     *                   comment)
     * @param x          left edge of the preview panel, in absolute screen space
     * @param y          top edge of the preview panel, in absolute screen space
     * @param width      width of the preview panel
     * @param height     height of the preview panel
     */
    @Nullable
    public static Preview preview(BlockPos previewKey, @Nullable BlockPos machinePos, int x, int y, int width,
            int height) {
        if (machinePos == null) return null;

        var level = Minecraft.getInstance().level;
        if (level == null || !level.isLoaded(machinePos)) return null;

        var existing = PREVIEWS.get(previewKey);
        if (existing != null && existing.machinePos().equals(machinePos)) {
            // the panel can move (window resize) without the model changing
            existing.widget().setPosition(x, y);
            existing.widget().setSize(width, height);
            return existing;
        }

        var built = build(level, machinePos, x, y, width, height);
        if (built == null) {
            PREVIEWS.remove(previewKey);
            return null;
        }

        PREVIEWS.put(previewKey, built);
        return built;
    }

    /**
     * Builds the model of one machine: <b>only</b> the served machine itself, at the model's origin.
     * <p>
     * Its addons, the indicators of its open addon slots and the plugin block itself are deliberately left out, even
     * though Oritech's own addon overlay draws the first two next to the machine (see {@code
     * OritechMachineScreenMixin}): the model here is not a picture of the machine's surroundings, it is the surface the
     * page asks the player to pick a face on. Everything else would sit on faces of the machine and be picked instead
     * of them - and the plugin's own block would hide the very face it occupies, which is the one face the model has
     * to show as taken. Addons grow onto the machine over time, so leaving them out also keeps the faces in the same
     * place for as long as the panel is open.
     * <p>
     * A multiblock machine is complete with this one entry: the widget works the machine's own part list out from its
     * controller (see {@code FacePreviewWidget#partOffsets()}), so the parts of the structure are drawn and picked as
     * cells of the machine - each with the block state standing at its own position - rather than as blocks of their
     * own here.
     */
    @Nullable
    private static Preview build(Level level, BlockPos machinePos, int x, int y, int width, int height) {
        var machineState = level.getBlockState(machinePos);
        if (machineState.isAir()) return null;

        var machineEntity = level.getBlockEntity(machinePos);
        var widget = new FacePreviewWidget(x, y, width, height);
        widget.withSurface(OritechSurface.PANEL_DARK);
        widget.withPadding(Insets.of(4));
        widget.setMachine(machineState, machineEntity);

        return new Preview(machinePos, widget);
    }

    /** Drops the model of one plugin, called when its GUI closes. */
    public static void clear(BlockPos pluginPos) {
        PREVIEWS.remove(pluginPos);
    }

    /** Drops every model; only used when the whole client state has to go. */
    public static void clear() {
        PREVIEWS.clear();
    }

    /**
     * One built preview: the machine it shows, and the widget and interaction state the page owns.
     * <p>
     * The class is mutable on purpose - the page rotates the model and selects faces while the player drags and
     * clicks, and both are state of one open GUI, not of one frame.
     */
    public static final class Preview {

        /** Degrees the model turns per pixel of drag: a quarter turn over a moderately sized panel. */
        private static final float DRAG_SPEED = 1.2F;

        /**
         * Factor the user's zoom is multiplied by per scroll notch, and the range it is kept in.
         * <p>
         * The zoom is a factor on the model's own fit-to-panel scale, so 1 is "the whole machine, exactly as the page
         * sized it". A tenth of a notch per event would need twenty events to double the model, which is slow for a
         * mouse wheel that reports one notch at a time, so each notch multiplies by 1.15; the ends of the range are
         * where the model stops being readable: below half the fit the whole machine is a thumbnail in the middle of
         * the panel, and above twice it the outer cells leave the panel and the player can no longer see the face
         * they are about to click. The ends are inclusive and the value is clamped after every notch, so scrolling on
         * past an end changes nothing.
         */
        private static final float ZOOM_STEP = 1.15F;
        private static final float MIN_ZOOM = 0.5F;
        private static final float MAX_ZOOM = 2.0F;

        private final BlockPos machinePos;
        private final FacePreviewWidget widget;
        private float pitch;
        private float yaw;
        /** The user's zoom, a factor on the fit-to-panel scale. */
        private float zoom = 1.0F;
        @Nullable
        private Direction selected;

        private Preview(BlockPos machinePos, FacePreviewWidget widget) {
            this.machinePos = machinePos;
            this.widget = widget;
            this.pitch = widget.pitch();
            this.yaw = widget.yaw();
        }

        /** The machine this model shows. */
        public BlockPos machinePos() {
            return machinePos;
        }

        /** The widget that draws the model; the page ticks and renders it and picks faces on it. */
        public FacePreviewWidget widget() {
            return widget;
        }

        /** Pitch the model is rotated to. */
        public float pitch() {
            return pitch;
        }

        /** Yaw the model is rotated to. */
        public float yaw() {
            return yaw;
        }

        /** The user's zoom, a factor on the model's fit-to-panel scale (see {@link #zoomBy}). */
        public float zoom() {
            return zoom;
        }

        /**
         * Zooms the model by one scroll event, clamped to the range the model stays readable in (see
         * {@link #ZOOM_STEP}).
         * <p>
         * The zoom is a plain factor the page hands to the widget together with the rotation, so it reaches the one
         * {@link PreviewTransform} the drawing, the picking, the hover highlight and the mode and gold markings all
         * share - a zoomed model is drawn and picked with the same scaled transform, and what the player sees is what
         * a click hits.
         *
         * @param scrollY the event's vertical scroll, positive when the player scrolls up (zooming in)
         */
        public void zoomBy(double scrollY) {
            if (scrollY == 0.0) return;

            float factor = scrollY > 0.0 ? ZOOM_STEP : 1.0F / ZOOM_STEP;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        }

        /** The face the player selected, or {@code null} while none is. */
        @Nullable
        public Direction selected() {
            return selected;
        }

        /** Selects a face, or clears the selection with {@code null}. */
        public void select(@Nullable Direction face) {
            this.selected = face;
        }

        /**
         * Rotates the model by a drag, clamped the way a player expects a model to behave: the pitch stops at the
         * poles (looking at a face straight on is the interesting angle, upside down is not) and the yaw wraps.
         */
        public void drag(double dragX, double dragY) {
            pitch = clampPitch(pitch + (float) dragY * DRAG_SPEED);
            yaw = wrapDegrees(yaw + (float) dragX * DRAG_SPEED);
        }

        /** Keeps the pitch inside the range the model stays readable in. */
        private static float clampPitch(float degrees) {
            return Math.max(-89.0F, Math.min(89.0F, degrees));
        }

        /** Wraps a yaw into {@code [0, 360)} like Oritech's own rotation does. */
        private static float wrapDegrees(float degrees) {
            float wrapped = degrees % 360.0F;
            return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
        }
    }
}
