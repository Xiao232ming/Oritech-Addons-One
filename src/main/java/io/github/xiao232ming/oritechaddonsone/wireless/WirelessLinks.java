package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
        perLevel.computeIfAbsent(machine.immutable(), ignored -> ConcurrentHashMap.newKeySet()).add(dock.immutable());
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
        return docks == null ? Set.of() : Set.copyOf(docks);
    }
}
