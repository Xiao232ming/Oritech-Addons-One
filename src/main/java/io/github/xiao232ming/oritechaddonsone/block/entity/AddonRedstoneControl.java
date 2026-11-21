package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity.RedstoneControllable;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity.RedstoneMode;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinks;

/**
 * The redstone behaviour of an Extension Addon that stores a control unit plugin.
 * <p>
 * A stored control unit has no block entity of its own, so the addon that holds it keeps its configuration -
 * the mode it works in and the machine slot {@code OUTPUT_SLOT} reports - the same two values Oritech's
 * control unit block entity keeps, and forwards the signal of its own face and of the machine it is attached
 * to. This class owns that state and the forwarding; the block entity delegates to it, which keeps the GUI,
 * the menu, the networking and the block's hooks working against the same small API as before.
 * <p>
 * It also owns the lifetime of the machine's redstone state: a machine that was switched off by this addon
 * is handed back when the control unit is taken out or the block is mined, but only while no other addon
 * (wired or wireless) or a directly attached control unit still owns it.
 */
final class AddonRedstoneControl {

    /** The addon this state belongs to. */
    private final ExtensionAddonBlockEntity owner;

    private RedstoneMode mode = RedstoneMode.INPUT_CONTROL;
    private int monitoredSlot;
    private int currentOutput;

    /** Last signal forwarded to the machine, so a tick only forwards an edge. */
    private boolean lastSignal;

    /** True while the machine was told a control unit drives it, so it can be handed back later. */
    private boolean applied;

    AddonRedstoneControl(ExtensionAddonBlockEntity owner) {
        this.owner = owner;
    }

    RedstoneMode mode() {
        return mode;
    }

    int monitoredSlot() {
        return monitoredSlot;
    }

    /** Comparator output this addon currently reports. */
    int output() {
        return currentOutput;
    }

    /**
     * True while the ticker has redstone work to do: a stored control unit, or a machine this addon
     * switched off earlier and still has to hand back.
     */
    boolean needsTick() {
        return applied || owner.hasRedstonePlugin();
    }

    /**
     * Writes the configuration the Redstone Control page edits. The page calls this through
     * {@code RedstoneNetworking}, so the server is what stores it; the values are saved with the block.
     */
    void configure(RedstoneMode mode, int slot) {
        var previousMode = this.mode;
        this.mode = mode;
        this.monitoredSlot = Math.max(0, slot);

        // Switching away from input control hands the machine back instead of leaving it disabled: Oritech's
        // control unit only drives the machine in INPUT_CONTROL, and the mode page can be used while a
        // signal is on. The release is the same path removal uses.
        if (previousMode == RedstoneMode.INPUT_CONTROL && mode != RedstoneMode.INPUT_CONTROL && applied) {
            apply(false);
        }

        owner.setChanged();
    }

    /**
     * Polled every server tick by the owning block. Cheap while there is nothing to do: without a stored
     * control unit (and without a state this addon set earlier) it returns immediately.
     */
    void tick() {
        var hasPlugin = owner.hasRedstonePlugin();
        if (!hasPlugin) {
            currentOutput = 0;
            if (!applied) return;

            lastSignal = powered();
            apply(lastSignal);
            return;
        }

        // Output modes are polled every tick: the machine's energy, slot fill, progress and active state
        // change on their own, and the comparator output has to follow them. Input control only reacts to
        // a redstone edge, exactly like Oritech's own control unit.
        if (mode != RedstoneMode.INPUT_CONTROL) {
            updateOutput();
            return;
        }

        updateOutput();

        var powered = powered();
        if (powered == lastSignal && applied) return;

        lastSignal = powered;
        apply(powered);
    }

    /**
     * Reads the current signal and forwards it, used at the end of every addon scan: that covers placement,
     * world loads and plugin changes, because every addon scan ends up there.
     */
    void refreshSignal() {
        lastSignal = powered();
        apply(lastSignal);
    }

    /**
     * Forwards the redstone signal at the owning block to the connected machine, exactly like Oritech's own
     * control unit plugin does from its {@code neighborChanged} hook: the machine implements
     * {@link RedstoneControllable} and turns itself off while it is powered.
     * <p>
     * Only input control is forwarded. The control unit's mode (input control / comparator output) is stored
     * here because the stored plugin has no block entity of its own, so a stored control unit always behaves
     * like the default mode {@code INPUT_CONTROL}.
     */
    void apply(boolean powered) {
        var level = owner.getLevel();
        if (level == null || level.isClientSide()) return;
        if (!(level.getBlockEntity(owner.getControllerPos()) instanceof MachineAddonController controller)) return;
        if (!(controller instanceof RedstoneControllable controllable)) return;

        if (owner.hasRedstonePlugin() && mode == RedstoneMode.INPUT_CONTROL) {
            applied = true;
            controllable.onRedstoneEvent(powered);
            return;
        }

        // Without a stored control unit this block never disabled the machine, so it must not hand it back
        // either: the machine re-scans every addon whenever one is added, removed or changed, and an addon
        // without a control unit would otherwise cancel the redstone control that another addon (the one
        // holding the control unit) had set up.
        if (!applied) return;

        release(controller, controllable);
    }

    /**
     * Called right before the owning block is removed: if this addon was the one that disabled the connected
     * machine, the machine has to be released, otherwise it would stay switched off forever.
     */
    void releaseOnRemoval() {
        if (!applied) return;

        var level = owner.getLevel();
        if (level == null || level.isClientSide()) return;
        if (!(level.getBlockEntity(owner.getControllerPos()) instanceof MachineAddonController controller)) return;
        if (!(controller instanceof RedstoneControllable controllable)) return;

        release(controller, controllable);
    }

    void save(ValueOutput output) {
        // the stored control unit's configuration, the same two values Oritech's control unit block entity saves
        output.putInt("redstoneMode", mode.ordinal());
        output.putInt("redstoneSlot", monitoredSlot);
    }

    void load(ValueInput input) {
        var modeOrdinal = input.getIntOr("redstoneMode", RedstoneMode.INPUT_CONTROL.ordinal());
        var modes = RedstoneMode.values();
        mode = modes[Math.max(0, Math.min(modes.length - 1, modeOrdinal))];
        monitoredSlot = Math.max(0, input.getIntOr("redstoneSlot", 0));
    }

    /** Recomputes {@link #currentOutput} for the configured output mode. */
    private void updateOutput() {
        var previous = currentOutput;
        var level = owner.getLevel();

        if (!owner.hasRedstonePlugin() || mode == RedstoneMode.INPUT_CONTROL) {
            currentOutput = 0;
        } else if (level == null
                || !(level.getBlockEntity(owner.getControllerPos()) instanceof RedstoneControllable controllable)) {
            currentOutput = 0;
        } else {
            currentOutput = switch (mode) {
                case OUTPUT_POWER -> controllable.getComparatorEnergyAmount();
                case OUTPUT_SLOT -> controllable.getComparatorSlotAmount(monitoredSlot);
                case OUTPUT_PROGRESS -> controllable.getComparatorProgress();
                case OUTPUT_ACTIVE -> controllable.getComparatorActiveState();
                case INPUT_CONTROL -> 0;
            };
        }

        if (currentOutput != previous) owner.setChanged();
    }

    /** Redstone signal that has to be honoured: at this block, or at the machine it is attached to. */
    private boolean powered() {
        var level = owner.getLevel();
        if (level == null) return false;
        return level.hasNeighborSignal(owner.getBlockPos()) || level.hasNeighborSignal(owner.getControllerPos());
    }

    private void release(MachineAddonController controller, RedstoneControllable controllable) {
        applied = false;

        // Another Extension Addon still holds a control unit: that one owns the redstone state now, so
        // handing the machine back here would switch it on behind its back.
        if (hasOtherControlUnit(controller)) return;

        // A control unit that is attached to the machine directly owns the state, so leave it untouched.
        if (hasAttachedRedstoneAddon(controller)) return;
        controllable.onRedstoneEvent(false);
    }

    /** True while another Extension Addon attached to the same machine holds a control unit. */
    private boolean hasOtherControlUnit(MachineAddonController controller) {
        var level = owner.getLevel();

        for (var pos : controller.getConnectedAddons()) {
            if (pos.equals(owner.getBlockPos())) continue;
            if (level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity other && other.hasRedstonePlugin()) {
                return true;
            }
        }

        // Wireless extension addons linked to the same machine count as well.
        for (var pos : WirelessLinks.docksOf(level, owner.getControllerPos())) {
            if (level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity other
                    && other.hasRedstonePlugin()) {
                return true;
            }
        }
        return false;
    }

    /** True while a vanilla control unit plugin block is attached to the machine directly. */
    private boolean hasAttachedRedstoneAddon(MachineAddonController controller) {
        var level = owner.getLevel();
        for (var pos : controller.getConnectedAddons()) {
            if (level.getBlockEntity(pos) instanceof RedstoneAddonBlockEntity) return true;
        }
        return false;
    }
}
