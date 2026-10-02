package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import rearth.oritech.block.entity.addons.AddonBlockEntity;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.AnchorAddonBlock;
import io.github.xiao232ming.oritechaddonsone.wireless.AnchorForceLoad;

/**
 * Block entity of the chunk anchor plugin that is <b>placed as a block</b> next to a machine.
 * <p>
 * Everything else is Oritech's ordinary {@link AddonBlockEntity}: the block is a regular
 * {@code MachineAddonBlock}, so the machine's addon scan finds it, claims it and writes its own position
 * into this block entity like it does for any other plugin. The scan reports that position through
 * {@link #setControllerPos(BlockPos)}, which is the moment this anchor learns which chunk it has to keep
 * loaded; the force load itself lives in {@link AnchorForceLoad}.
 * <p>
 * This class only exists so the anchor has a block entity of its own type ({@code plugin_addon} lists all
 * three plugin blocks) and so the anchor can be told apart from the warehouse and tank addons, which use
 * the very same block entity type. It adds no state of its own: which chunk is force loaded follows from
 * the world, and {@link AnchorForceLoad} reconciles it every few ticks, so nothing here has to be saved.
 */
public class AnchorAddonBlockEntity extends AddonBlockEntity implements BlockEntityTicker<BlockEntity> {

    public AnchorAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.PLUGIN_ADDON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ the claimed machine

    /**
     * Position of the machine that claimed this anchor, or {@code null} while none did.
     * <p>
     * Oritech's addon scan calls {@link #setControllerPos(BlockPos)} with the machine's position, and the
     * block entity stores it as an offset from its own position. Before the first scan (and after the
     * machine is gone) the offset is zero, i.e. the position is this block itself - which means "no
     * machine".
     * <p>
     * The anchor does not have to be attached to the position it stands on: Oritech looks for addons on all
     * six faces of the machine plus the ones an extender addon opens up, and the machine is what writes the
     * position here. So this is exactly "the machine this plugin is connected to", wherever it stands.
     */
    @Nullable
    public BlockPos claimedMachinePos() {
        var controller = getControllerPos();
        return controller == null || controller.equals(worldPosition) ? null : controller;
    }

    // ------------------------------------------------------------------ keeping the force load right

    /**
     * Called by the machine's addon scan on every recomputation - including the ones triggered by placing
     * another addon, breaking a neighbour or loading the world - so the force load is updated right away
     * instead of at the next reconcile. Placing the anchor is covered as well: the scan of the machine it
     * was placed next to runs on the next tick and ends up here.
     */
    @Override
    public void setControllerPos(BlockPos pos) {
        super.setControllerPos(pos);
        AnchorForceLoad.register(this);
    }

    /**
     * Announces the saved anchor as soon as its chunk is loaded again.
     * <p>
     * The forced chunks are runtime state (see {@link AnchorForceLoad}), so after a server start nothing is
     * force loaded until a source announces itself. This hook is that announcement for the placed form, and
     * it is also what repairs the state after a crash: an anchor whose machine is gone reports "no machine"
     * and its old force load is released instead of carried over.
     * <p>
     * {@code onLoad} runs after the saved data was read (the controller offset is part of it), so the
     * machine really is known here.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel)) return;
        AnchorForceLoad.register(this);
    }

    /**
     * Releases the force load when this block is removed.
     * <p>
     * {@code setRemoved} is also called while a chunk is unloaded, in which case the block is still there
     * and nothing may be released - the anchor is going to announce itself again when the chunk comes back.
     * Only a block that really left the world releases its chunk here, so the release is immediate instead
     * of "at the next reconcile".
     */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel
                && level.isLoaded(worldPosition)
                && !(level.getBlockState(worldPosition).getBlock() instanceof AnchorAddonBlock)) {
            AnchorForceLoad.release(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------ ticking

    /**
     * Server ticker of the anchor block. The authoritative re-check happens in
     * {@link AnchorForceLoad#onServerTick}, which covers every anchor at once and also notices anchors
     * whose chunk stopped ticking; this one only makes the very first tick after a placement or a world
     * load immediate, so the chunk is loaded before a player could walk away.
     */
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, BlockEntity anchor) {
        if (anchor instanceof AnchorAddonBlockEntity self) {
            AnchorForceLoad.register(self);
        }
    }
}
