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
     * Recomputes the unreadable addons of a machine. Has to run at the start of an addon scan, while
     * {@code getConnectedAddons()} still holds the addons the machine knew before that scan.
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
     * Collects the positions of {@code positions} whose chunk is not loaded, i.e. the addons whose
     * plugins cannot be part of the addon data the machine is about to compute.
     */
    private static void collect(Level level, BlockPos machine, Collection<BlockPos> positions,
                                Set<BlockPos> into) {
        if (positions == null) return;

        for (var pos : positions) {
            if (pos == null || pos.equals(machine)) continue;
            // A loaded chunk without a block entity means the addon is gone, so it is not kept.
            if (level.getBlockEntity(pos) == null && !level.isLoaded(pos)) {
                into.add(pos.immutable());
            }
        }
    }

    /**
     * True while the machine's addon data is missing the contribution of an addon that cannot be read
     * right now. Besides the remembered positions this also checks the machine's own addon list, because
     * the first energy container update after a world load runs before the machine's addon scan.
     */
    public static boolean isGuarded(MachineAddonController controller) {
        var level = controller.getWorldForAddon();
        if (!(level instanceof ServerLevel)) return false;

        var machine = controller.getPosForAddon();
        var perLevel = UNRESOLVED.get(level);
        if (perLevel != null && perLevel.containsKey(machine)) return true;

        for (var pos : controller.getConnectedAddons()) {
            if (pos == null || pos.equals(machine)) continue;
            if (level.getBlockEntity(pos) == null) return true;
        }
        return false;
    }

    /** Remembers the stored energy of the container before it is recalculated. */
    public static void capture(MachineAddonController controller) {
        CAPTURED_ENERGY.remove();
        if (!isGuarded(controller)) return;

        var storage = controller.getStorageForAddon();
        if (storage == null) return;

        CAPTURED_ENERGY.set(new Capture(controller, storage.energy));
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

        if (storage.energy < capture.energy()) {
            OritechAddonsOne.LOGGER.debug(
                    "[diag] keeping {} energy at {} that the addon scan would have clamped to {}",
                    capture.energy(), controller.getPosForAddon(), storage.energy);
            storage.energy = capture.energy();
        }
    }
}
