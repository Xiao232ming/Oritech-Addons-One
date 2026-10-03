package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.HashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.wireless.AddonEnergyGuard;
import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinks;

/**
 * Block entity of the wireless extension addons.
 * <p>
 * It is the same plugin container as the wired {@link ExtensionAddonBlockEntity}, with one difference:
 * it is not attached to a machine, so the machine never scans it. Instead it keeps the position of a
 * machine that was selected with Oritech's target designator and reports that position as its controller.
 * All the inherited logic (stat merging, special behaviour forwarding, redstone control, energy input
 * for acceptor plugins) therefore points at the linked machine.
 * <p>
 * Because the machine does not know about this block, the dock drives the exchange: whenever its contents
 * change - and every second as a safety net, since the machine recomputes its addons on many events the
 * dock cannot observe - it lets the machine recompute and then applies every wireless dock of that
 * machine. Applying all of them together is important: a single machine recompute resets the addon data,
 * so docks applying one after another would overwrite each other.
 * <p>
 * The dock has to be in a loaded chunk to work (this is also where the "wireless" part ends: the plugins
 * are still stored here, not in the machine).
 */
public class WirelessExtensionAddonBlockEntity extends ExtensionAddonBlockEntity {

    private static final String LINKED_MACHINE_TAG = "linked_machine";

    /** How often the dock re-applies itself, in ticks (the machine may recompute without telling us). */
    private static final int REFRESH_INTERVAL = 20;

    /** Guards {@link #applyAllDocks} against re-entering itself through the block updates it causes. */
    private static boolean applying;

    /** Machine this dock is linked to, or {@code null} while it is not linked. */
    private BlockPos linkedMachine;

    public WirelessExtensionAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.WIRELESS_EXTENSION_ADDON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ link

    /** The machine this dock is linked to, or {@code null}. */
    public BlockPos linkedMachine() {
        return linkedMachine;
    }

    public boolean isLinked() {
        return linkedMachine != null;
    }

    /**
     * Translation key of the linked machine's display name, used by the GUI. Returns null while the dock
     * is not linked or the machine is in an unloaded chunk (in that case the GUI shows the coordinates
     * only). See {@link #connectedMachineNameKey()} for why the name is resolved here and not on the
     * client.
     */
    @Nullable
    public String linkedMachineNameKey() {
        return connectedMachineNameKey();
    }

    /** The link is what this dock works on, so that is its connected machine. */
    @Override
    @Nullable
    public BlockPos connectedMachinePos() {
        return linkedMachine;
    }

    // The inherited isTargetChunkForceLoaded() answers for the link as well, because it follows
    // connectedMachinePos() - a dock that is not linked has no target and therefore nothing kept loaded.

    public boolean isLinkedTo(BlockPos machine) {
        return linkedMachine != null && linkedMachine.equals(machine);
    }

    /**
     * Links this dock to a machine. Linking again simply moves the link, which is also the only way to
     * unlink without breaking the block (as requested: no manual unlink action).
     */
    public void linkTo(BlockPos machine) {
        if (level == null || level.isClientSide() || machine == null) {
            OritechAddonsOne.LOGGER.debug("[diag] link: dock {} ignored (level={}, client={}, machine={})",
                    worldPosition, level, level != null && level.isClientSide(), machine);
            return;
        }
        OritechAddonsOne.LOGGER.debug("[diag] link: dock {} -> machine {} (was {})", worldPosition, machine, linkedMachine);

        var previous = linkedMachine;
        if (machine.equals(previous)) {
            // same machine: just make sure everything is in sync
            registerWithMachine();
            updateLinkedState();
            refreshMachine();
            return;
        }

        // hand the old machine back before moving the link
        releaseRedstoneOnRemoval();
        if (previous != null) {
            WirelessLinks.unregister(level, previous, worldPosition);
            releaseMachine(previous);
        }

        linkedMachine = machine.immutable();
        setChanged();

        registerWithMachine();
        updateLinkedState();
        refreshMachine();
    }

    /** Removes the link (used when the block is removed). */
    public void clearLink() {
        if (linkedMachine == null) return;

        releaseRedstoneOnRemoval();
        if (level != null) WirelessLinks.unregister(level, linkedMachine, worldPosition);
        releaseMachine(linkedMachine);
        linkedMachine = null;
        setChanged();
    }

    /**
     * Lets a machine this dock was linked to recompute its addons, which removes the stats of this dock
     * from it again, and tells its clients about the new values. Without this a machine would keep the
     * plugins of a dock that was moved or broken until something else makes it recompute.
     */
    private void releaseMachine(BlockPos machine) {
        forgetAppliedStats();
        if (machine == null || !(level instanceof ServerLevel serverLevel)) return;
        if (!(serverLevel.getBlockEntity(machine) instanceof MachineAddonController controller)) return;

        serverLevel.getServer().execute(() -> {
            controller.initAddons();
            if (controller instanceof NetworkedBlockEntity networked) {
                networked.sendUpdate(SyncType.GUI_OPEN);
            }
        });
    }

    private void registerWithMachine() {
        if (linkedMachine != null) WirelessLinks.register(level, linkedMachine, worldPosition);
    }

    // ------------------------------------------------------------------ driving the machine

    /**
     * Asks the linked machine to recompute its addons. The machine applies every wireless dock of that
     * machine during that recomputation itself (see {@code MachineAddonControllerMixin}), so nothing else
     * has to be done here. Runs on the server thread, because {@code initAddons} must not be called from
     * a foreign thread.
     */
    public void refreshMachine() {
        if (!(level instanceof ServerLevel serverLevel) || linkedMachine == null) {
            OritechAddonsOne.LOGGER.debug("[diag] refresh: dock {} skipped (level={}, link={})",
                    worldPosition, level, linkedMachine);
            return;
        }
        if (!(serverLevel.getBlockEntity(linkedMachine) instanceof MachineAddonController controller)) {
            OritechAddonsOne.LOGGER.debug("[diag] refresh: dock {} found no addon machine at {} (block={}, be={})",
                    worldPosition, linkedMachine, serverLevel.getBlockState(linkedMachine),
                    serverLevel.getBlockEntity(linkedMachine));
            return;
        }

        serverLevel.getServer().execute(controller::initAddons);
    }

    /**
     * Applies the plugins of every dock linked to that machine, on top of the data the machine just
     * computed for its own addons. Called from the machine's own addon scan (see
     * {@code MachineAddonControllerMixin}), i.e. before it recalculates its energy container.
     */
    public static void applyAllDocks(Level level, BlockPos machinePos) {
        // Applying the stats updates synced block states, which sends block updates that can make a
        // neighbouring machine scan its addons again. Running the merge once is enough, so nested calls
        // are dropped instead of recursing.
        if (applying) return;

        applying = true;
        try {
            // The runtime index only lists docks that have already announced themselves. Its cold state is
            // the normal one while the machine recomputes right after a world load, and a dock in an
            // unloaded chunk never announces itself at all - both would leave the machine with the default
            // capacity and let it clamp (i.e. delete) the energy the player stored with that dock's
            // plugins. The machine's own addon list names those docks and survives a save, so it is read
            // as a second source. A dock that is not loaded is skipped here; its energy is protected by
            // AddonEnergyGuard instead.
            var docks = new HashSet<BlockPos>(WirelessLinks.docksOf(level, machinePos));
            var controller = level.getBlockEntity(machinePos);
            if (controller instanceof MachineAddonController addonController) {
                docks.addAll(WirelessLinks.docksFromAddonList(level, machinePos,
                        addonController.getConnectedAddons()));
            }

            OritechAddonsOne.LOGGER.debug("[diag] applyAllDocks: {} -> {} dock(s)", machinePos, docks.size());
            // Everything inside this window runs with an addon data set that does not contain these docks
            // yet, and merging one writes block states and replays plugin behaviours - either can make the
            // machine recompute its addons and clamp its stored energy in the middle of the merge. The
            // guard keeps the energy while the merge is open.
            AddonEnergyGuard.beginDockMerge(level, machinePos);
            try {
                for (var dockPos : docks) {
                    if (level.getBlockEntity(dockPos) instanceof WirelessExtensionAddonBlockEntity dock
                            && dock.isLinkedTo(machinePos)) {
                        dock.applyCombinedStats();
                    } else {
                        OritechAddonsOne.LOGGER.debug("[diag] applyAllDocks: skipping {} (be={})", dockPos,
                                level.getBlockEntity(dockPos));
                    }
                }
            } finally {
                AddonEnergyGuard.endDockMerge(level, machinePos);
            }
        } finally {
            applying = false;
        }
    }

    /**
     * Server ticker of the wireless addons: redstone control plus a periodic re-register of the link.
     * <p>
     * The machine applies the docks itself whenever it recomputes its addons, and a dock whose contents
     * changed asks for such a recomputation, so this must not touch the machine's addon data or energy
     * container - only the link and the synced block states are refreshed here.
     */
    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        serverTickRedstone();
        // faces of the transfer page whose automation is on move their items here, exactly like on a wired
        // addon: the machine is the linked one, so nothing else is needed
        serverTickTransfer();

        if (linkedMachine == null) return;

        if (level.getGameTime() % REFRESH_INTERVAL == 0) {
            registerWithMachine();
            updateLinkedState();
        }
    }

    /**
     * Announces the link as soon as the dock is loaded again.
     * <p>
     * The link index is runtime only, so without this a dock is unknown until its next {@link #serverTick}
     * (up to a second later) - while the machine recomputes its addons right after a load, because
     * Oritech's {@code MachineControllerLifecycle.onLoad} defers an addon scan to the next server tick.
     * That scan would compute the machine's capacity (and clamp its stored energy to it) without this
     * dock's plugins, so the dock has to be part of it. Asking the machine to recompute covers the other
     * order, where the machine scanned its addons before this chunk was loaded.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide() || linkedMachine == null) return;

        registerWithMachine();
        refreshMachine();
    }

    // ------------------------------------------------------------------ inherited hooks

    /**
     * The machine this dock works on. Machines never scan this block, so nothing else ever writes this
     * position - it comes exclusively from the saved link.
     */
    @Override
    public BlockPos getControllerPos() {
        return linkedMachine == null ? worldPosition : linkedMachine;
    }

    /**
     * Ignored on purpose: a wireless dock is never claimed by a machine's addon scan. (The wired version
     * also ignores the addon splicer here, which cannot apply to a block that is never scanned.)
     */
    @Override
    public void setControllerPos(BlockPos pos) {
        // no-op
    }

    /**
     * The dock is the only block of this mod a transfer addon can be <b>placed</b> on to reach its faces: the
     * six neighbours are asked whether a transfer addon hangs on this dock (see
     * {@link TransferAddonBlock#isAttachedTo}). That is what the transfer page shows as a gold border, and
     * what makes such a plugin work without being put into one of the plugin slots.
     */
    @Override
    protected int scanAttachedTransferFaces() {
        if (level == null) return 0;

        var mask = 0;
        for (var face : Direction.values()) {
            var pluginPos = worldPosition.relative(face);
            if (!level.isLoaded(pluginPos)) continue;
            if (!TransferAddonBlock.isAttachedTo(level.getBlockState(pluginPos), face)) continue;

            mask |= 1 << face.ordinal();
        }
        return mask;
    }

    @Override
    protected boolean isOwnBlock() {
        return getBlockState().getBlock() instanceof WirelessExtensionAddonBlock;
    }

    /**
     * The wireless block is not an Oritech addon block, so it never carries the "used" state of a
     * machine slot. Being linked is what makes the energy input work.
     */
    @Override
    protected boolean isEnergyInputActive() {
        return hasAcceptorPlugin() && linkedMachine != null && getControllerEntity() instanceof MachineAddonController;
    }

    @Override
    protected BooleanProperty controlUnitProperty() {
        return WirelessExtensionAddonBlock.HAS_CONTROL_UNIT;
    }

    @Override
    protected void refreshController() {
        // the wired addon relies on the machine's scan calling setControllerPos; a wireless dock is not
        // part of that scan, so it drives the exchange itself
        refreshMachine();
    }

    /** Mirrors the link into the block state, which is what the textures switch on. */
    public void updateLinkedState() {
        if (level == null || level.isClientSide()) return;

        var state = getBlockState();
        if (!state.hasProperty(WirelessExtensionAddonBlock.LINKED)) return;

        var connected = linkedMachine != null && level.getBlockEntity(linkedMachine) instanceof MachineAddonController;
        if (state.getValue(WirelessExtensionAddonBlock.LINKED) != connected) {
            level.setBlock(worldPosition, state.setValue(WirelessExtensionAddonBlock.LINKED, connected),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.oritechaddonsone.wireless_" + pluginType().id());
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (linkedMachine != null) {
            output.store(LINKED_MACHINE_TAG, BlockPos.CODEC, linkedMachine);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        linkedMachine = input.read(LINKED_MACHINE_TAG, BlockPos.CODEC).orElse(null);
    }
}
