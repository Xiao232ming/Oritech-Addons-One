package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Keeps a machine from destroying stored energy while the contribution of one of its addons is missing.
 * <p>
 * Oritech recomputes a machine's addon data from the addons it can find around it and then recalculates
 * the energy container, which ends in {@code energy = min(energy, capacity)}. That is fine while an addon
 * is really gone, but it silently deletes player energy when an addon is merely <em>unreadable</em>: the
 * wireless extension addons are indexed at runtime only, so after a world load a machine recomputes its
 * addons (and its capacity, and therefore its stored energy) before its docks have announced themselves
 * again. A dock in an unloaded chunk - which is the point of a wireless dock - never announces itself in
 * time at all.
 * <p>
 * This guard remembers which addon positions of a machine cannot be read (their chunk is not loaded) and
 * stops the clamp from reducing the stored energy while that is the case. The energy is kept, not
 * duplicated: it stays exactly where it was, and once the addon is readable again the capacity is
 * recomputed with its contribution, so the container is consistent again. When an addon is really gone
 * (its chunk is loaded but there is no block entity) it is dropped from the guard, so the clamp behaves
 * like Oritech's again.
 * <p>
 * A dock whose chunk <em>is</em> loaded is recovered instead of merely guarded:
 * {@code WirelessLinks#docksFromAddonList} reads the link back from the machine's own addon list, which
 * survives a save, so the dock contributes again even while the runtime index is still cold. That is what
 * keeps the capacity (and with it the energy bar of an opened GUI) correct right after a world load, where
 * the machine recomputes its addons before any dock has announced itself.
 */
public final class AddonEnergyGuard {

    /**
     * Addon positions a machine could not read during its last addon scan, per level and machine. Only
     * positions in unloaded chunks are kept, because only those can still turn out to be an addon.
     */
    private static final Map<Level, Map<BlockPos, Set<BlockPos>>> UNRESOLVED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Stored energy of the container before {@code updateEnergyContainer} clamped it, for the call that is
     * currently running. The controller is part of it so that a capture that never reached its restore
     * (an exception in between) can never be applied to another machine's container.
     */
    private static final ThreadLocal<Capture> CAPTURED_ENERGY = new ThreadLocal<>();

    private record Capture(MachineAddonController controller, long energy) {
    }

    private AddonEnergyGuard() {
    }

    /**
     * Recomputes the addons of a machine that cannot contribute right now. Has to run at the start of an
     * addon scan, while {@code getConnectedAddons()} still holds the addons the machine knew before that
     * scan.
     */
    public static void refresh(MachineAddonController controller) {
        var level = controller.getWorldForAddon();
        if (!(level instanceof ServerLevel)) return;

        var machine = controller.getPosForAddon();
        var perLevel = UNRESOLVED.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>());
        var next = new HashSet<BlockPos>();

        collect(level, machine, perLevel.get(machine), next);
        collect(level, machine, controller.getConnectedAddons(), next);

        if (next.isEmpty()) {
            perLevel.remove(machine);
        } else {
            perLevel.put(machine.immutable(), Set.copyOf(next));
        }
    }

    /**
     * Collects the positions of {@code positions} that cannot contribute to the addon data the machine
     * is about to compute, i.e. the addons whose plugins are missing from it.
     * <p>
     * An addon that is really gone (its chunk is loaded and there is no block entity any more) is not
     * kept, so a removed addon lowers the capacity and clamps the stored energy exactly like Oritech
     * does. Only the two states in which the addon still exists somewhere - its chunk is not loaded, or
     * it is a dock that has not announced itself yet - keep the guard up.
     */
    private static void collect(Level level, BlockPos machine, Collection<BlockPos> positions,
                                Set<BlockPos> into) {
        if (positions == null) return;

        for (var pos : positions) {
            if (pos == null || pos.equals(machine)) continue;

            var be = level.getBlockEntity(pos);
            if (be == null) {
                // Unloaded chunk: the addon may well be there, so its contribution is still expected.
                if (!level.isLoaded(pos)) into.add(pos.immutable());
                continue;
            }

            if (be instanceof WirelessExtensionAddonBlockEntity dock
                    && dock.isLinkedTo(machine) && !WirelessLinks.isRegistered(level, machine, pos)) {
                // Loaded and linked, but it has not handed its plugins to the machine yet.
                into.add(pos.immutable());
            }
        }
    }

    /**
     * True while the machine's addon data is missing the contribution of an addon that cannot be read
     * right now. Besides the remembered positions this also checks the machine's own addon list, because
     * the first energy container update after a world load runs before the machine's addon scan.
     * <p>
     * A linked dock that is loaded but has not announced itself yet counts as missing too. That state is
     * the normal one right after a world load: the machine recomputes its addons before the dock's first
     * tick, so the scan resets the addon data to the machine's own addons and the container is clamped
     * against the much smaller default capacity. The dock is known through the machine's own addon list,
     * which survives a save, so this works for remote docks as well.
     */
    public static boolean isGuarded(MachineAddonController controller) {
        var level = controller.getWorldForAddon();
        if (!(level instanceof ServerLevel)) return false;

        var machine = controller.getPosForAddon();
        var perLevel = UNRESOLVED.get(level);
        if (perLevel != null && perLevel.containsKey(machine)) return true;

        return hasMissingAddon(level, machine, controller.getConnectedAddons());
    }

    /** Whether any of {@code positions} cannot contribute to the addon data the machine is computing. */
    private static boolean hasMissingAddon(Level level, BlockPos machine, Collection<BlockPos> positions) {
        if (positions == null) return false;

        for (var pos : positions) {
            if (pos == null || pos.equals(machine)) continue;
            if (!contributes(level, machine, pos)) return true;
        }
        return false;
    }

    /**
     * Whether the addon at {@code pos} can be part of the addon data the machine computes right now.
     * A plain addon is read straight from the world. A wireless dock only contributes once it has
     * announced itself for this machine, which happens when its chunk loads - after the machine has
     * already recomputed its addons. A dock that is linked elsewhere contributes nothing.
     * <p>
     * A missing block entity only counts as missing while its chunk is not loaded: a loaded chunk without
     * a block entity means the addon was really removed, and that has to keep clamping like Oritech.
     */
    private static boolean contributes(Level level, BlockPos machine, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return level.isLoaded(pos);

        if (be instanceof WirelessExtensionAddonBlockEntity dock) {
            return dock.isLinkedTo(machine) && WirelessLinks.isRegistered(level, machine, pos);
        }
        return true;
    }

    /** Remembers the stored energy of the container before it is recalculated. */
    public static void capture(MachineAddonController controller) {
        CAPTURED_ENERGY.remove();
        if (!isGuarded(controller)) return;

        var storage = controller.getStorageForAddon();
        if (storage == null) return;

        CAPTURED_ENERGY.set(new Capture(controller, storage.amount));
    }

    /**
     * Puts the stored energy back if the recalculation reduced it. A machine that cannot read all of its
     * addons has a too small capacity right now, and energy above it is still energy the player stored -
     * it must not be deleted because an addon is temporarily out of reach.
     */
    public static void restore(MachineAddonController controller) {
        var capture = CAPTURED_ENERGY.get();
        CAPTURED_ENERGY.remove();
        if (capture == null || capture.controller() != controller) return;

        var storage = controller.getStorageForAddon();
        if (storage == null) return;

        if (storage.amount < capture.energy()) {
            OritechAddonsOne.LOGGER.debug(
                    "[diag] keeping {} energy at {} that the addon scan would have clamped to {}",
                    capture.energy(), controller.getPosForAddon(), storage.amount);
            storage.amount = capture.energy();
        }
    }
}
