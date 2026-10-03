package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.TransferPreviewAddonBlock;

/**
 * The item inventory one <b>face of this mod's preview plugin</b> offers to the outside world while that plugin is
 * hung directly on an Oritech machine - the pipe side of {@link TransferPreviewAddonBlockEntity}.
 * <p>
 * The gating, the machine inventory, the slot roles and the handler identity all live in {@link FacePluginStorage};
 * this class is the one question that is specific to a plugin hung on a machine: <b>which plugin configures a face of
 * it</b>.
 * <p>
 * <b>A machine host is the plugin block itself.</b> Unlike the extender placement - where the plugin configures the
 * six faces of a separate host - a plugin hung directly on a machine has no separate host whose faces could be
 * offered: the machine's own faces are Oritech's business (Oritech already exposes its inventory there) and the
 * plugin's own faces are the only place this plugin can add a connection. So the face a pipe stands on is a face of
 * the <b>plugin</b>, and the mode that gates it is the mode of that same face. Nothing has to be translated: a pipe
 * west of the plugin asks with {@link Direction#WEST}, and that is the face of the plugin whose mode decides.
 * <p>
 * <b>The face the plugin hangs on stays refused</b>, exactly as in the extender placement: the machine block is in
 * it, so no pipe or hopper can ever be there and a mode on it could not describe a connection. The page marks that
 * face and the server refuses it (see {@link TransferPreviewAddonBlockEntity#setTransferConfig}).
 * <p>
 * Registration is the same shape as for the extender: the capability provider is registered for the block entity type
 * this plugin belongs to, so it answers for exactly one kind of block and only where that block really hangs on a
 * machine (see {@code OritechAddonsOne#registerCapabilities}).
 */
public final class MachinePluginStorage {

    private MachinePluginStorage() {
    }

    /**
     * The handler of one face of the plugin block at {@code plugin}, or {@code null} while that face is not an item
     * connection at all: no mode is configured on it, or the plugin does not really hang on a machine. {@code null} is
     * what the capability provider answers with, so a pipe sees the plugin as "nothing here" until the page configured
     * that face.
     */
    @Nullable
    public static FacePluginStorage handlerAt(BlockEntity plugin, @Nullable Direction face) {
        return FacePluginStorage.handlerAt(plugin, face, MachinePluginStorage::transferMode,
                MachinePluginStorage::machinePos, HostFaceStorage::new);
    }

    /**
     * The machine the plugin at {@code plugin} is attached to, or {@code null} while it is not attached to one, or
     * that machine's chunk is not loaded.
     * <p>
     * A plugin hung directly on a machine is claimed by it like any Oritech addon, so Oritech wrote the machine's
     * position into the plugin's controller position; the machine also has to be the very block the plugin hangs on
     * (see {@link TransferPreviewAddonBlock#attachedTowards}). Both are checked, so a plugin that merely stands
     * somewhere with a stale controller position cannot expose a machine through its faces.
     */
    @Nullable
    private static BlockPos machinePos(BlockEntity plugin) {
        if (!(plugin instanceof TransferPreviewAddonBlockEntity preview)) return null;

        // the plugin's own controller position, which Oritech's addon scan writes with the position of the machine
        // that claimed it; it is read instead of connectedMachinePos(), because that one resolves the machine behind
        // an extender - and this is the placement where the plugin hangs on the machine itself
        var controller = preview.getControllerPos();
        if (controller == null || controller.equals(plugin.getBlockPos())) return null;
        if (!preview.hangsOnMachine()) return null;

        return controller;
    }

    /**
     * The mode the given face of the plugin transfers with, or {@link TransferMode#NONE} while it transfers nothing.
     * The mode is the plugin's own, one per face of the plugin block, so this is a plain lookup once the attachment
     * has been established.
     */
    private static TransferMode transferMode(BlockEntity plugin, Direction face) {
        if (!(plugin instanceof TransferPreviewAddonBlockEntity preview)) return TransferMode.NONE;

        var level = plugin.getLevel();
        if (level == null || level.isClientSide()) return TransferMode.NONE;
        if (!preview.canTransferItems()) return TransferMode.NONE;

        return preview.transferModes().modeOf(face);
    }

    /**
     * The concrete handler of this placement: a {@link FacePluginStorage} whose two answers are the static methods
     * above. It exists as a class only so the handler cache can create exactly one object per host position and face -
     * the base class constructs its handlers itself.
     */
    static final class HostFaceStorage extends FacePluginStorage {

        HostFaceStorage(BlockEntity host, Direction face, FaceLookup lookup, MachineLookup machine) {
            super(host, face, lookup, machine);
        }
    }
}
