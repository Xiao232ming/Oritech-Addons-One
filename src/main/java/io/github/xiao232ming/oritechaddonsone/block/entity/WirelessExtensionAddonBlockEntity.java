package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
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
     * only).
     */
    @Nullable
    public String linkedMachineNameKey() {
        if (linkedMachine == null || level == null || !level.isLoaded(linkedMachine)) return null;
        var state = level.getBlockState(linkedMachine);
        return state.isAir() ? null : state.getBlock().getDescriptionId();
    }
    public boolean isLinkedTo(BlockPos machine) {
        return linkedMachine != null && linkedMachine.equals(machine);
    }

    /**
     * Links this dock to a machine. Linking again simply moves the link, which is also the only way to
     * unlink without breaking the block (as requested: no manual unlink action).
     */
    public void linkTo(BlockPos machine) {
        if (level == null || level.isClientSide() || machine == null) return;

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
        if (previous != null) WirelessLinks.unregister(level, previous, worldPosition);

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
        linkedMachine = null;
        setChanged();
    }

    private void registerWithMachine() {
        if (linkedMachine != null) WirelessLinks.register(level, linkedMachine, worldPosition);
    }

    // ------------------------------------------------------------------ driving the machine

    /**
     * Asks the linked machine to recompute its addons and applies every wireless dock of that machine
     * afterwards. Runs on the server thread, because {@code initAddons} must not be called from a
     * foreign thread.
     */
    public void refreshMachine() {
        if (!(level instanceof ServerLevel serverLevel) || linkedMachine == null) return;
        if (!(serverLevel.getBlockEntity(linkedMachine) instanceof MachineAddonController controller)) return;

        var machinePos = linkedMachine;
        serverLevel.getServer().execute(() -> {
            controller.initAddons();
            applyAllDocks(serverLevel, machinePos);
        });
    }

    /**
     * Applies the plugins of every dock linked to that machine. The machine just recomputed its addon
     * data, so this is the point where the wireless contributions have to be merged on top.
     */
    public static void applyAllDocks(Level level, BlockPos machinePos) {
        for (var dockPos : WirelessLinks.docksOf(level, machinePos)) {
            if (level.getBlockEntity(dockPos) instanceof WirelessExtensionAddonBlockEntity dock
                    && dock.isLinkedTo(machinePos)) {
                dock.applyCombinedStats();
            }
        }
    }

    /** Server ticker of the wireless addons: redstone control plus a periodic re-apply. */
    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        serverTickRedstone();

        if (linkedMachine == null) return;

        if (level.getGameTime() % REFRESH_INTERVAL == 0) {
            // re-register (covers world loads and removed entries) and let the machine recompute, so a
            // link survives addon changes on the machine side that we cannot observe
            registerWithMachine();
            updateLinkedState();
            refreshMachine();
        }
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
