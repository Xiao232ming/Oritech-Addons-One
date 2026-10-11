package io.github.xiao232ming.oritechaddonsone.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.platform.AddonsPlatform;

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
 * <li>chunks another mod keeps loaded, which is asked of the loader through the platform SPI
 * ({@link AddonsPlatform#isForceLoadedByMods}) - on NeoForge that reads its force load API, which
 * {@code TicketController#forceChunk} uses (FTB Chunks force-loads its chunks exactly like that).</li>
 * </ul>
 * <p>
 * Everything else that loads a chunk is deliberately <em>not</em> counted: a player standing nearby adds
 * {@code TicketType.PLAYER} tickets, and every plain chunk lookup - this mod's own link handling included -
 * adds the short lived {@code TicketType.UNKNOWN} ticket through {@code ServerChunkCache}. Neither of them
 * appears in the three sources above, so a chunk that is only loaded for one of those reasons answers
 * "false".
 * <p>
 * The mod-forced part is the one source common code cannot see by itself, so it goes through the platform SPI:
 * NeoForge merges its own block and entity ticket trackers there, while vanilla (and therefore Fabric) has a
 * single force loaded set - <b>on Fabric this check can therefore not tell a mod's force load apart from
 * {@code /forceload}</b>, which is acceptable for the badge this feeds. A mod that adds its own force load
 * ticket directly through {@code ServerChunkCache#addRegionTicket} is invisible to the NeoForge side as well:
 * the raw ticket list of {@code DistanceManager} is private on 1.21.1.
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
                || AddonsPlatform.INSTANCE.isForceLoadedByMods(serverLevel, chunk.toLong());
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
}
