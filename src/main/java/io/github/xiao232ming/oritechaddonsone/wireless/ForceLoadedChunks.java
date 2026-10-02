package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Answers whether a chunk is <em>kept</em> loaded, i.e. whether something loads it on purpose instead of it
 * merely being loaded at the moment.
 * <p>
 * Only these count on this version:
 * <ul>
 * <li>vanilla's force loaded chunks - the {@code /forceload} command and everything else that goes through
 * {@link ServerLevel#setChunkForced(int, int, boolean)}, read from the level's own list;</li>
 * <li>the spawn area, which the server keeps loaded with a temporary {@code TicketType.PLAYER_SPAWN} region
 * ticket while a player is joining (this version has no {@code spawnChunkRadius} gamerule any more);</li>
 * <li>a force load ticket on the chunk itself. This version keeps every ticket in the level's
 * {@link TicketStorage}, which is public, so a ticket another mod added for its own force load is visible
 * here: FTB Chunks force-loads its chunks through NeoForge's {@code TicketController}, i.e. with the
 * {@code neoforge:entity_with_natural_spawning} ticket type, and lands in this list like any other mod
 * ticket.</li>
 * </ul>
 * <p>
 * Everything else that loads a chunk is deliberately <em>not</em> counted: a player nearby adds the
 * {@code player_loading}/{@code player_simulation} tickets, and every plain chunk lookup - this mod's own
 * link handling included - adds the short lived {@code unknown} ticket through {@code ServerChunkCache}.
 * Both groups are excluded by type below, so a chunk that is only loaded for one of those reasons answers
 * "false".
 * <p>
 * Only public vanilla API is used; no mixin or reflection is needed on this version.
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

        var chunk = ChunkPos.containing(pos);
        if (serverLevel.getForceLoadedChunks().contains(chunk.pack())) return true;

        var tickets = serverLevel.getDataStorage().get(TicketStorage.TYPE);
        if (tickets == null) return false;

        return holdsForceLoadTicket(tickets.getTickets(chunk.pack())) || isSpawnArea(serverLevel, tickets, chunk);
    }

    /** True while one of the tickets on that chunk keeps it loaded without any player nearby. */
    private static boolean holdsForceLoadTicket(List<Ticket> tickets) {
        for (var ticket : tickets) {
            if (keepsLoadedWithoutPlayers(ticket.getType())) return true;
        }
        return false;
    }

    /**
     * True for the ticket types that keep a chunk loaded on their own. A mod's own force load ticket falls
     * into this group too: this version has a ticket type registry, so every mod ticket arrives here and
     * only vanilla's player and visitor tickets are excluded by identity.
     * <p>
     * That makes the check deliberately inclusive - a mod ticket that only loads a chunk for a moment (a
     * teleport in progress, for example) is counted as well. Missing a force load is the worse mistake for
     * this badge, because it is the "this machine stops working when you walk away" warning.
     */
    private static boolean keepsLoadedWithoutPlayers(TicketType type) {
        if (!type.doesLoad()) return false;

        return type != TicketType.PLAYER_LOADING
                && type != TicketType.PLAYER_SIMULATION
                && type != TicketType.PLAYER_SPAWN
                && type != TicketType.SPAWN_SEARCH
                && type != TicketType.UNKNOWN
                && type != TicketType.PORTAL
                && type != TicketType.ENDER_PEARL
                && type != TicketType.DRAGON;
    }

    /**
     * True for the chunks the server keeps loaded around the world spawn. This version dropped the
     * {@code spawnChunkRadius} gamerule and the permanent {@code start} ticket with it: the spawn area is
     * loaded by a {@code player_spawn} region ticket that {@code PrepareSpawnTask} adds and keeps alive
     * while a player is joining, three chunks around the spawn chunk - so the answer is only "yes" while
     * that ticket is there. The radius is read from the ticket's level instead of being hard coded.
     */
    private static boolean isSpawnArea(ServerLevel level, TicketStorage tickets, ChunkPos chunk) {
        var spawnChunk = ChunkPos.containing(level.getRespawnData().pos());
        for (var ticket : tickets.getTickets(spawnChunk.pack())) {
            if (ticket.getType() != TicketType.PLAYER_SPAWN) continue;

            var reach = ChunkLevel.byStatus(FullChunkStatus.FULL) - ticket.getTicketLevel();
            if (reach < 0) continue;
            if (Math.abs(spawnChunk.x() - chunk.x()) <= reach && Math.abs(spawnChunk.z() - chunk.z()) <= reach) {
                return true;
            }
        }
        return false;
    }
}
