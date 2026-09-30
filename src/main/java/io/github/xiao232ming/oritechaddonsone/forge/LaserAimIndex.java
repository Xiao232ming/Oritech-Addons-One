package io.github.xiao232ming.oritechaddonsone.forge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Server side index of the lasers that currently aim at an atomic forge.
 * <p>
 * A laser may sit up to its configured range away - 128 blocks by default - so the forge cannot find the
 * lasers that charge it by looking around itself, and the lasers' addon data is only synced when their own
 * GUI is opened, so a client cannot read it either. Every laser therefore reports its current target here
 * once in a while (see {@code LaserAimIndexMixin}), and the forge sums them up on the server side and syncs
 * the result to whoever opens its GUI.
 * <p>
 * The index is runtime only on purpose: a laser re-reports from its next tick, and entries are validated
 * against the laser's real target when they are read.
 */
public final class LaserAimIndex {

    private static final Map<Level, Map<BlockPos, Set<BlockPos>>> LASERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LaserAimIndex() {
    }

    /** Records that a laser aims at a forge (idempotent). */
    public static void register(Level level, BlockPos forge, BlockPos laser) {
        if (level == null || level.isClientSide() || forge == null || laser == null) return;

        LASERS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(forge.immutable(), ignored -> ConcurrentHashMap.newKeySet())
                .add(laser.immutable());
    }

    /** Forgets a laser, for a laser that stopped aiming at a forge or was removed. */
    public static Set<BlockPos> unregister(Level level, BlockPos laser) {
        if (level == null || laser == null) return Set.of();

        var forges = LASERS.get(level);
        if (forges == null) return Set.of();

        var affected = new java.util.HashSet<BlockPos>();
        for (var entry : forges.entrySet()) {
            if (entry.getValue().remove(laser)) affected.add(entry.getKey());
        }
        forges.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (forges.isEmpty()) LASERS.remove(level);
        return affected;
    }

    /** Lasers last seen aiming at that forge, as an immutable snapshot. */
    public static Set<BlockPos> lasersOf(Level level, BlockPos forge) {
        if (level == null || forge == null) return Set.of();

        var forges = LASERS.get(level);
        if (forges == null) return Set.of();

        var lasers = forges.get(forge);
        return lasers == null ? Set.of() : Set.copyOf(lasers);
    }
}
