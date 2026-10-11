package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Server side index of the wireless extension addons per machine.
 * <p>
 * A wireless dock is not attached to the machine, so the machine cannot find it by scanning its addon
 * slots. The dock therefore registers itself here while it is linked and loaded, and every dock that
 * wants the machine to recompute asks the registry which docks belong to that machine. That way all
 * docks of a machine can apply their plugins in one go - if each dock applied on its own, every new
 * {@code initAddons()} would wipe the contributions of the others.
 * <p>
 * The index is intentionally runtime only: a dock re-registers itself from its saved link as soon as its
 * chunk is loaded again, which keeps it free of world save format concerns.
 */
public final class WirelessLinks {

    private static final Map<Level, Map<BlockPos, Set<BlockPos>>> LINKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WirelessLinks() {
    }

    /** Registers a dock for a machine (idempotent). */
    public static void register(Level level, BlockPos machine, BlockPos dock) {
        if (level == null || level.isClientSide() || machine == null || dock == null) return;

        var perLevel = LINKS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>());
        var docks = perLevel.computeIfAbsent(machine.immutable(), ignored -> ConcurrentHashMap.newKeySet());
        var added = docks.add(dock.immutable());
        OritechAddonsOne.LOGGER.debug("[diag] register: machine {} -> dock {} (new={}, docks={})",
                machine, dock, added, docks.size());
    }

    /** Removes a dock from a machine (idempotent). */
    public static void unregister(Level level, BlockPos machine, BlockPos dock) {
        if (level == null || machine == null || dock == null) return;

        var perLevel = LINKS.get(level);
        if (perLevel == null) return;

        var docks = perLevel.get(machine);
        if (docks == null) return;

        docks.remove(dock);
        if (docks.isEmpty()) perLevel.remove(machine);
    }

    /**
     * Docks currently registered for a machine, as an immutable snapshot. The caller is expected to
     * verify that each dock is still loaded and still linked to that machine.
     */
    public static Set<BlockPos> docksOf(Level level, BlockPos machine) {
        if (level == null || machine == null) return Set.of();

        var perLevel = LINKS.get(level);
        if (perLevel == null) return Set.of();

        var docks = perLevel.get(machine);
        OritechAddonsOne.LOGGER.debug("[diag] docksOf: machine {} -> {} dock(s), {} machine(s) indexed",
                machine, docks == null ? 0 : docks.size(), perLevel.size());
        return docks == null ? Set.of() : Set.copyOf(docks);
    }

    /**
     * Whether a dock has already announced itself for a machine. A linked dock that is not registered
     * yet is loaded but not contributing, which is exactly the state a machine must not mistake for
     * "this addon is gone".
     */
    public static boolean isRegistered(Level level, BlockPos machine, BlockPos dock) {
        if (level == null || machine == null || dock == null) return false;

        var perLevel = LINKS.get(level);
        if (perLevel == null) return false;

        var docks = perLevel.get(machine);
        return docks != null && docks.contains(dock);
    }

    /**
     * Docks a machine should treat as its own, taken from the machine's addon list instead of this index.
     * <p>
     * This is the fallback for the moment the index is still cold - after a world load, or while a dock's
     * chunk is not loaded and the dock therefore never announced itself. The machine's addon list survives
     * a save and names those docks, so reading the link back from the loaded dock block entities recovers
     * them without any extra bookkeeping. Docks whose chunk is not loaded cannot be read here; they are
     * covered by {@link AddonEnergyGuard}, which keeps the stored energy while their contribution is
     * missing.
     */
    public static Set<BlockPos> docksFromAddonList(Level level, BlockPos machine,
                                                   Collection<BlockPos> connectedAddons) {
        if (level == null || machine == null || connectedAddons == null) return Set.of();

        var found = new HashSet<BlockPos>();
        for (var pos : connectedAddons) {
            if (pos == null || pos.equals(machine)) continue;
            if (!(level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity dock)) continue;
            if (!dock.isLinkedTo(machine)) continue;

            found.add(pos.immutable());
            // The dock is loaded and linked again, so the index can be rebuilt from it.
            register(level, machine, pos);
        }
        return found;
    }
}
