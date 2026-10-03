package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.block.entity.addons.AddonBlockEntity;

import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;

/**
 * The item inventory one <b>face of Oritech's machine extender</b> offers to the outside world while a placed
 * transfer plugin hangs on that extender - the pipe side of {@link TransferAddonBlockEntity}.
 * <p>
 * The gating, the machine inventory, the slot roles and the handler identity all live in
 * {@link FacePluginStorage}; this class is the one question that is specific to an extender: <b>which plugin
 * configures a face of it</b>.
 * <p>
 * <b>The plugin configures the whole extender, not one face.</b> The transfer page is opened on the plugin, but
 * what it sets is a mode per face of the extender ({@link TransferFaceModes}), so a face is a connection when
 * <em>that</em> face has a mode - never because the plugin happens to hang on it. The face the plugin occupies
 * is therefore never a connection: a pipe cannot stand there, the mode is refused for it (see
 * {@link TransferAddonBlockEntity#setTransferConfig}) and the plugin's own faces answer empty (see
 * {@link TransferAddonBlockEntity#getItemLookup}).
 * <p>
 * <b>The machine is the one the extender was claimed by</b>, read from the extender's own controller position,
 * and nothing is reached while no machine claimed it, the extender was broken, or its chunk is unloaded -
 * see {@link FacePluginStorage} for how that fails safe.
 */
public final class ExtenderFaceStorage {

    private ExtenderFaceStorage() {
    }

    /**
     * The handler of one face of the extender at {@code extender}, or {@code null} while that face is not an
     * item connection at all. That is the case while the extender is asked without a side - a machine extender
     * has no inventory of its own, only faces a plugin configured - and while the queried face has no mode,
     * which includes every face of an extender no transfer plugin hangs on. {@code null} is also what the
     * capability provider answers with, so a pipe sees the extender as "nothing here" until a plugin really
     * offers something on that face.
     * <p>
     * Only extender faces can answer this way: a plugin on an extender offers its own faces an empty inventory
     * (see {@code TransferAddonBlockEntity#getItemLookup}), so the machine is reachable at exactly one place,
     * and the same items cannot be found at two.
     */
    @Nullable
    public static FacePluginStorage handlerAt(AddonBlockEntity extender, @Nullable Direction face) {
        return FacePluginStorage.handlerAt(extender, face, ExtenderFaceStorage::transferMode,
                ExtenderFaceStorage::machinePos, ExtenderHandler::new);
    }

    /**
     * The machine the extender is attached to, or {@code null} while no machine claimed it. Oritech writes the
     * position of the machine that claimed the extender into the extender's own block entity, and that machine's
     * controller position is the machine - as long as it is not the extender's own position, which is the
     * "claimed by nobody" value.
     */
    @Nullable
    private static BlockPos machinePos(BlockEntity host) {
        if (!(host instanceof AddonBlockEntity extender)) return null;

        var machinePos = extender.getControllerPos();
        return machinePos == null || machinePos.equals(extender.getBlockPos()) ? null : machinePos;
    }

    /**
     * The mode the given face of the extender transfers with, or {@link TransferMode#NONE} while it transfers
     * nothing.
     * <p>
     * The mode is the <b>plugin's</b>, not the face's own: a placed transfer plugin owns one mode per face of
     * the extender it hangs on (the transfer page configures the extender through it), so this asks the plugin
     * on the extender - whichever face it hangs on - what it was told about {@code face}. That is the whole
     * point of the lookup: the plugin occupies one of the six faces, and the connections are the other five, so
     * a face can only ever be a connection while a plugin elsewhere on the same extender is configured for it.
     * <p>
     * More than one plugin may hang on the same extender. Each of them carries its own modes, and only the
     * plugin a mode was set on knows it, so a face counts as configured when <em>any</em> of them configures it
     * - scanning every plugin and not only the first one is what keeps such an extender working.
     * <p>
     * The face a plugin hangs on is skipped for <em>that</em> plugin: the plugin block occupies it, so no pipe
     * or hopper can ever be there and a mode an older version stored on it must not turn the extender into a
     * connection that leads into the plugin block itself. With several plugins on one extender every plugin
     * therefore still answers for all faces but its own.
     * <p>
     * Only {@link TransferAddonBlockEntity} is asked. The preview plugin of this mod
     * ({@code TransferPreviewAddonBlockEntity}) also hangs on extenders, but it serves the machine through its
     * own faces and never claims the extender's, so an extender carrying only a preview plugin keeps answering
     * "no inventory" - exactly as it did before that plugin existed.
     */
    private static TransferMode transferMode(BlockEntity host, Direction face) {
        var level = host.getLevel();
        if (level == null || level.isClientSide()) return TransferMode.NONE;

        for (var side : Direction.values()) {
            var plugin = pluginAt(host, level, side);
            if (plugin == null) continue;

            var pluginFace = TransferAddonBlock.attachedFace(plugin.getBlockState());
            if (face == pluginFace) continue;
            if (!plugin.canTransferItems()) continue;

            var mode = plugin.transferModes().modeOf(face);
            if (mode != TransferMode.NONE) return mode;
        }

        return TransferMode.NONE;
    }

    /**
     * The transfer plugin hanging on the given face of the extender, i.e. the neighbour whose attachment points
     * back at this very extender, or {@code null} while there is none.
     * <p>
     * The plugin is looked up through the world on every call and not remembered: a plugin can be broken or
     * placed at any time, and NeoForge's own invalidation (see {@code TransferAddonBlockEntity}) is what makes a
     * pipe ask again - the answer itself has to be correct whenever it is asked.
     * <p>
     * {@code side} runs from the extender to the neighbour and {@code attachedFace} is the extender's own face
     * the plugin hangs on, so the two are the same direction - taking its opposite here asked for the far face
     * and never matched, which left every face of every extender answering "no inventory".
     */
    @Nullable
    private static TransferAddonBlockEntity pluginAt(BlockEntity extender, Level level, Direction side) {
        var pluginPos = extender.getBlockPos().relative(side);
        if (!level.isLoaded(pluginPos)) return null;
        if (!(level.getBlockEntity(pluginPos) instanceof TransferAddonBlockEntity plugin)) return null;

        return TransferAddonBlock.attachedFace(plugin.getBlockState()) == side ? plugin : null;
    }

    /**
     * The concrete handler of this placement: a {@link FacePluginStorage} whose two answers are the static
     * methods above. It exists as a class only so the handler cache can create exactly one object per host
     * position and face - the base class constructs its handlers itself.
     */
    private static final class ExtenderHandler extends FacePluginStorage {

        private ExtenderHandler(BlockEntity host, Direction face, FaceLookup lookup, MachineLookup machine) {
            super(host, face, lookup, machine);
        }
    }
}
