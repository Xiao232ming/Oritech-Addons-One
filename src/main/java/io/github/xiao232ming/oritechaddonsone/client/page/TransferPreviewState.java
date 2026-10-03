package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.block.base.entity.MultiblockMachineEntity;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.ScreenProvider;

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
 * while the machine behind the plugin is replaced or the plugin is picked up and put down again. It is dropped when
 * the screen closes ({@link #clear()}, called by {@code ExtensionAddonScreen#removed()}), so a built model never
 * outlives the GUI it was built for.
 */
public final class TransferPreviewState {

    /** The built preview and its interaction state, one per plugin position that had a GUI open. */
    private static final Map<BlockPos, Preview> PREVIEWS = new ConcurrentHashMap<>();

    private TransferPreviewState() {
    }

    /**
     * The preview of the plugin at {@code pluginPos}, built or rebuilt while it does not show the machine
     * {@code machinePos} yet, or {@code null} while that machine cannot be resolved on this client - its chunk is
     * not loaded, or the plugin serves nothing.
     *
     * @param previewKey position the built model is kept under, i.e. the position of the block the open menu belongs
     *                   to, so a preview never outlives the panel it was built for
     * @param machinePos the machine the plugin serves, as the menu of the open screen reports it - the position the
     *                   server resolved and sent along, because a client cannot resolve it itself (see the class
     *                   comment)
     * @param pluginPos  position of the <b>placed</b> plugin whose block belongs into the model, or {@code null}
     *                   while the page is shown inside an Extension Addon that merely stores one (see
     *                   {@code TransferPreviewAddonPage#pluginBlockPos})
     * @param x          left edge of the preview panel, in absolute screen space
     * @param y          top edge of the preview panel, in absolute screen space
     * @param width      width of the preview panel
     * @param height     height of the preview panel
     */
    @Nullable
    public static Preview preview(BlockPos previewKey, @Nullable BlockPos machinePos, @Nullable BlockPos pluginPos, int x,
            int y, int width, int height) {
        if (machinePos == null) return null;

        var level = Minecraft.getInstance().level;
        if (level == null || !level.isLoaded(machinePos)) return null;

        var existing = PREVIEWS.get(previewKey);
        if (existing != null && existing.machinePos().equals(machinePos) && existing.pluginPos().equals(pluginPos)) {
            // the panel can move (window resize) without the model changing
            existing.widget().setPosition(x, y);
            existing.widget().setSize(width, height);
            return existing;
        }

        var built = build(level, machinePos, pluginPos, x, y, width, height);
        if (built == null) {
            PREVIEWS.remove(previewKey);
            return null;
        }

        PREVIEWS.put(previewKey, built);
        return built;
    }

    /**
     * Builds the model of one machine: the machine itself at the model's origin, its connected addons around it and
     * an indicator in every open addon slot - the same picture Oritech's own addon overlay draws (see
     * {@code OritechMachineScreenMixin}) - plus, while the page belongs to a placed plugin, the plugin block itself,
     * so the player sees which face of the machine that plugin occupies.
     * <p>
     * The offsets are the machine's own relative positions, rotated into the machine's facing exactly like Oritech
     * does it, so a machine that faces a wall shows its addons where they really are.
     */
    @Nullable
    private static Preview build(Level level, BlockPos machinePos, @Nullable BlockPos pluginPos, int x, int y,
            int width, int height) {
        var machineState = level.getBlockState(machinePos);
        if (machineState.isAir()) return null;

        var machineEntity = level.getBlockEntity(machinePos);
        var widget = new FacePreviewWidget(x, y, width, height);
        widget.withSurface(OritechSurface.PANEL_DARK);
        widget.withPadding(Insets.of(4));
        widget.addBlock(machineState, machineEntity, new Vec3i(0, 0, 0));

        addAddons(level, widget, machinePos, machineState, machineEntity);

        if (pluginPos != null && level.isLoaded(pluginPos)) {
            widget.addBlock(level.getBlockState(pluginPos), level.getBlockEntity(pluginPos),
                    relativePos(machinePos, machineState, machineEntity, pluginPos));
        }

        return new Preview(machinePos, pluginPos, widget);
    }

    /** Adds every connected addon of the machine and an indicator for every open addon slot. */
    private static void addAddons(Level level, FacePreviewWidget widget, BlockPos machinePos, BlockState machineState,
            @Nullable BlockEntity machineEntity) {
        if (!(machineEntity instanceof MachineAddonController controller)) return;

        for (var addonPos : controller.getConnectedAddons()) {
            if (!level.isLoaded(addonPos)) continue;
            widget.addBlock(level.getBlockState(addonPos), level.getBlockEntity(addonPos),
                    relativePos(machinePos, machineState, machineEntity, addonPos));
        }

        for (var openPos : controller.getOpenAddonSlots()) {
            widget.addBlock(BlockContent.ADDON_INDICATOR.get().defaultBlockState(), null,
                    relativePos(machinePos, machineState, machineEntity, openPos));
        }
    }

    /**
     * Where one neighbouring block sits relative to the machine in the model, or its plain offset while the machine
     * has no facing to rotate by. Oritech's own {@code worldToRelativePos} does the rotation; the facing comes from
     * the machine's screen data, which every machine of Oritech provides.
     */
    private static Vec3i relativePos(BlockPos machinePos, BlockState machineState, @Nullable BlockEntity machineEntity,
            BlockPos otherPos) {
        var facing = facingOf(machineState, machineEntity);
        if (facing == null) return otherPos.subtract(machinePos);

        return MultiblockMachineEntity.worldToRelativePos(machinePos, otherPos, facing);
    }

    /** Facing of a machine, or {@code null} while its state carries no facing property at all. */
    @Nullable
    private static Direction facingOf(BlockState state, @Nullable BlockEntity entity) {
        Property<Direction> property = entity instanceof ScreenProvider screen ? screen.getBlockFacingProperty() : null;
        if (property == null || !state.hasProperty(property)) return null;

        return state.getValue(property);
    }

    /** Drops the model of one plugin, called when its GUI closes. */
    public static void clear(BlockPos pluginPos) {
        PREVIEWS.remove(pluginPos);
    }

    /** Drops every model, called when a GUI closes. */
    public static void clear() {
        PREVIEWS.clear();
    }

    /**
     * One built preview: the machine it shows, the plugin block that was drawn into it and the widget and
     * interaction state the page owns.
     * <p>
     * The class is mutable on purpose - the page rotates the model and selects faces while the player drags and
     * clicks, and both are state of one open GUI, not of one frame.
     */
    public static final class Preview {

        /** Degrees the model turns per pixel of drag: a quarter turn over a moderately sized panel. */
        private static final float DRAG_SPEED = 1.2F;

        private final BlockPos machinePos;
        /**
         * The placed plugin that belongs into the model, or {@code null} while the page is shown for a plugin that is
         * merely <b>stored</b> in an Extension Addon. It takes part in the identity of the preview, because the same
         * machine is drawn differently with and without that block.
         */
        @Nullable
        private final BlockPos pluginPos;
        private final FacePreviewWidget widget;
        private float pitch;
        private float yaw;
        @Nullable
        private Direction selected;

        private Preview(BlockPos machinePos, @Nullable BlockPos pluginPos, FacePreviewWidget widget) {
            this.machinePos = machinePos;
            this.pluginPos = pluginPos;
            this.widget = widget;
            this.pitch = widget.pitch();
            this.yaw = widget.yaw();
        }

        /** The machine this model shows. */
        public BlockPos machinePos() {
            return machinePos;
        }

        /** The placed plugin drawn into the model, or {@code null} while the page belongs to a stored one. */
        @Nullable
        public BlockPos pluginPos() {
            return pluginPos;
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

        /** Wraps a yaw into {@code [0, 360)} like the widget's own rotation does. */
        private static float wrapDegrees(float degrees) {
            float wrapped = degrees % 360.0F;
            return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
        }
    }
}
