package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.Collection;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Answers whether a chunk is <em>kept</em> loaded, i.e. whether something loads it on purpose instead of it
 * merely being loaded at the moment.
 * <p>
 * Only three things count on this version:
 * <ul>
 * <li>vanilla's force loaded chunks - the {@code /forceload} command and everything else that goes through
 * {@link ServerLevel#setChunkForced(int, int, boolean)}, read from the level's own list;</li>
 * <li>the spawn area, which the server keeps loaded with a {@code TicketType.START} region ticket whose
 * radius is the {@code spawnChunkRadius} gamerule plus one;</li>
 * <li>chunks another mod keeps loaded through NeoForge's force load API
 * ({@link ForcedChunkManager}, which {@code TicketController#forceChunk} uses - FTB Chunks force-loads its
 * chunks exactly like that).</li>
 * </ul>
 * <p>
 * Everything else that loads a chunk is deliberately <em>not</em> counted: a player standing nearby adds
 * {@code TicketType.PLAYER} tickets, and every plain chunk lookup - this mod's own link handling included -
 * adds the short lived {@code TicketType.UNKNOWN} ticket through {@code ServerChunkCache}. Neither of them
 * appears in the three sources above, so a chunk that is only loaded for one of those reasons answers
 * "false".
 * <p>
 * All three sources are public vanilla/NeoForge API; no mixin or reflection is needed on this version. The
 * one thing that cannot be seen here is a mod that adds its own force load ticket directly through
 * {@code ServerChunkCache#addRegionTicket} instead of NeoForge's {@link ForcedChunkManager}: the raw ticket
 * list of {@code DistanceManager} is private on 1.21.1, so such a ticket is invisible to this check.
 */
public final class ForceLoadedChunks {

    private ForceLoadedChunks() {
    }

    /**
     * True while the chunk that contains {@code pos} is kept loaded by the server or by another mod, see
     * the class comment for what counts.
     * <p>
     * Meant to be called on the server; on the client (and for an unknown position) the answer is always
     * false. Never throws: an unexpected failure of one of the lookups falls back to "not kept loaded", so
     * a broken check can neither crash the server tick that polls the badge nor turn the badge green.
     */
    public static boolean isForceLoaded(@Nullable Level level, @Nullable BlockPos pos) {
        try {
            return isForceLoadedUnchecked(level, pos);
        } catch (Throwable failure) {
            OritechAddonsOne.LOGGER.debug("[diag] force-load check failed for {} in {}", pos, level, failure);
            return false;
        }
    }

    private static boolean isForceLoadedUnchecked(@Nullable Level level, @Nullable BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        // A chunk that is not loaded at all cannot be kept loaded either.
        if (!serverLevel.isLoaded(pos)) return false;

        var chunk = new ChunkPos(pos);
        return serverLevel.getForcedChunks().contains(chunk.toLong())
                || isSpawnArea(serverLevel, chunk)
                || isModForced(serverLevel, chunk);
    }

    /**
     * True for the chunks around the world spawn, which the server keeps loaded itself.
     * {@code ServerLevel#setDefaultSpawnPos} adds a {@code TicketType.START} region ticket with the distance
     * {@code spawnChunkRadius + 1} - and adds nothing at all while the gamerule is 0, because then vanilla
     * does not keep the spawn area loaded.
     */
    private static boolean isSpawnArea(ServerLevel level, ChunkPos chunk) {
        var reach = level.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) + 1;
        if (reach < 2) return false;

        return new ChunkPos(level.getSharedSpawnPos()).getChessboardDistance(chunk) <= reach;
    }

    /**
     * True while a mod keeps that chunk loaded through NeoForge's force load API. The tracked chunks are the
     * only place NeoForge exposes them per chunk, and they cover both the ticking and the non-ticking
     * variant, so FTB Chunks style force loads are found here no matter which of the two they used.
     */
    private static boolean isModForced(ServerLevel level, ChunkPos chunk) {
        var forced = level.getDataStorage().get(ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
        if (forced == null) return false;

        var key = chunk.toLong();
        return holds(forced.getBlockForcedChunks(), key) || holds(forced.getEntityForcedChunks(), key);
    }

    /** True while one of the given owners keeps that chunk loaded, ticking or not. */
    private static <T extends Comparable<? super T>> boolean holds(ForcedChunkManager.TicketTracker<T> tracker, long chunk) {
        return holds(tracker.getChunks().values(), chunk) || holds(tracker.getTickingChunks().values(), chunk);
    }

    private static boolean holds(Collection<LongSet> tracked, long chunk) {
        for (var chunks : tracked) {
            if (chunks.contains(chunk)) return true;
        }
        return false;
    }
}
