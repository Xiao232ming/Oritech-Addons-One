package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.AnchorAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.entity.AnchorAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/**
 * Keeps one chunk loaded per chunk anchor plugin: the chunk that contains the machine the anchor is
 * connected to.
 * <p>
 * The anchor works in two forms and both end up here:
 * <ul>
 *     <li><b>placed as a block</b> next to a machine - the machine's addon scan claims the block (it is an
 *     ordinary Oritech {@code MachineAddonBlock}) and writes its own position into the block entity, see
 *     {@link AnchorAddonBlockEntity};</li>
 *     <li><b>inserted into the reserved slot</b> of one of this mod's addons or wireless docks - the
 *     anchor item then names the machine that addon is connected to, see
 *     {@link ExtensionAddonBlockEntity#connectedMachinePos()}.</li>
 * </ul>
 * Both forms report the machine position through {@link #machinePosOf}, so the force load itself is one
 * single code path.
 *
 * <h2>Which API, and why</h2>
 * The chunk is forced with {@link ServerLevel#setChunkForced(int, int, boolean)} - the very call vanilla's
 * {@code /forceload} command makes. It is the least invasive option: it needs no ticket type, it is fully
 * reversible ({@code setChunkForced(..., false)} takes the force load away again), and it is exactly what
 * {@link ForceLoadedChunks} already reports, so the wireless page's badge turns green for a machine an
 * anchor is attached to. NeoForge's ticket API ({@code TicketController#forceChunk}) would work as well but
 * brings its own ticket type and owner bookkeeping for no benefit here.
 *
 * <h2>Never releasing somebody else's force load</h2>
 * {@code setChunkForced} is not reference counted, so calling it with {@code false} would also drop a
 * chunk the player kept loaded with {@code /forceload}. Every chunk this class keeps loaded therefore has
 * a {@link Receipt}, and the receipt records whether the chunk was <em>already</em> forced when we arrived
 * ({@link Receipt#forcedByUs}). A receipt whose chunk was already forced is simply dropped on release and
 * {@code setChunkForced} is never called for it. Nothing else in the mod calls {@code setChunkForced}.
 *
 * <h2>No leaks</h2>
 * The receipts are deliberately <b>not</b> saved: a force load that is not backed by a live anchor is
 * worthless, so the server drops every one of them on restart and every anchor re-adds its own as soon as
 * its chunk ticks. Nothing can survive a restart half-released, and no bookkeeping can go stale. While the
 * server runs the receipts are reconciled every 5 ticks - not only from the sources themselves, so a block
 * entity that was destroyed or whose chunk was unloaded is noticed too:
 * <ul>
 *     <li>anchor block removed, machine broken, machine replaced: the anchor block entity is gone or no
 *     longer claimed, see {@link #hasAnchorBlock};</li>
 *     <li>anchor item taken out of the reserved slot, addon broken, dock unlinked: see
 *     {@link #hasAnchorInReservedSlot};</li>
 *     <li>the source's own chunk was unloaded (nobody looks at it any more) or its whole level is gone:
 *     the receipt is released.</li>
 * </ul>
 * Every lookup goes through {@link #reconcile} and is answered with "release" when anything is missing, so
 * a missing block entity, an unloaded chunk or a machine that is not a machine can never crash a tick - the
 * worst case is that a chunk stops being force loaded.
 */
@EventBusSubscriber(modid = OritechAddonsOne.MODID)
public final class AnchorForceLoad {

    /** How often the receipts are re-checked, in server ticks. */
    private static final int RECONCILE_INTERVAL = 5;

    /**
     * One force load this mod holds. {@code forcedByUs} is false while the chunk was already forced by
     * somebody else when the anchor arrived - such a receipt is dropped without calling
     * {@code setChunkForced(..., false)}, so the other force load survives.
     */
    private static final class Receipt {
        /** Position of the anchor block, or of the addon / dock that holds the anchor item. */
        private final BlockPos source;
        /** Chunk of the connected machine: what is kept loaded. */
        private final long chunk;
        /** Source is a placed anchor block (true) or an addon / dock with the anchor in its reserved slot. */
        private final boolean placed;
        /** True while this mod added the force load (false when it was already there). */
        private boolean forcedByUs;

        private Receipt(BlockPos source, long chunk, boolean placed, boolean forcedByUs) {
            this.source = source.immutable();
            this.chunk = chunk;
            this.placed = placed;
            this.forcedByUs = forcedByUs;
        }
    }

    /** Receipts per level, keyed weakly so an unloaded level does not stay in memory. */
    private static final Map<ServerLevel, Map<Long, List<Receipt>>> RECEIPTS = new WeakHashMap<>();

    private AnchorForceLoad() {
    }

    // ------------------------------------------------------------------ sources announce themselves

    /**
     * Re-resolves the force load of the given source position. Called when the anchor or the container it
     * is inserted into changes, so the effect is there immediately instead of at the next reconcile - the
     * regular re-check in {@link #onServerTick} makes the call optional, never wrong.
     */
    public static void register(AnchorAddonBlockEntity anchor) {
        refresh(anchor);
    }

    /** See {@link #register(AnchorAddonBlockEntity)} - same thing for the reserved slot of an addon. */
    public static void register(ExtensionAddonBlockEntity addon) {
        refresh(addon);
    }

    /**
     * Drops the force load of the given source without looking at the blocks again. Used while a block
     * entity is removed, so the chunk is released at once rather than at the next reconcile.
     */
    public static void release(ServerLevel level, BlockPos source) {
        var dropped = remove(level, source);
        if (dropped > 0) {
            OritechAddonsOne.LOGGER.debug("[diag] anchor: {} receipt(s) dropped for {}", dropped, source);
        }
    }

    /**
     * Removes every receipt of one source and releases the chunks this mod had forced for it.
     * <p>
     * The lists are replaced by fresh copies instead of being modified in place: the two callers
     * ({@link #release} while a block entity is removed and {@link #reconcile} on the tick) delete entries
     * from lists they are iterating themselves, and a copy keeps that independent of where the removal is
     * triggered from - a chunk that is unloaded while it is being reconciled included.
     */
    private static int remove(ServerLevel level, BlockPos source) {
        var perLevel = RECEIPTS.get(level);
        if (perLevel == null) return 0;

        var chunks = new ArrayList<Long>();
        var dropped = 0;

        for (var entry : perLevel.entrySet()) {
            var receipts = entry.getValue();
            if (receipts == null) continue;

            var touched = false;
            var kept = new ArrayList<Receipt>(receipts.size());

            for (var receipt : receipts) {
                if (!receipt.source.equals(source)) {
                    kept.add(receipt);
                    continue;
                }

                touched = true;
                dropped++;
                if (!receipt.forcedByUs) continue;

                if (level.getForcedChunks().contains(receipt.chunk)) {
                    level.setChunkForced(ChunkPos.getX(receipt.chunk), ChunkPos.getZ(receipt.chunk), false);
                    OritechAddonsOne.LOGGER.debug("[diag] anchor: released chunk {} of {}",
                            new ChunkPos(receipt.chunk), source);
                }
            }

            if (!touched) continue;
            if (kept.isEmpty()) {
                chunks.add(entry.getKey());
            } else {
                entry.setValue(kept);
            }
        }

        for (var chunk : chunks) {
            perLevel.remove(chunk);
        }
        return dropped;
    }

    // ------------------------------------------------------------------ reconciliation

    /**
     * Re-checks every force load five times a second and reports or releases it.
     * <p>
     * Runs at the end of the server tick, i.e. outside the chunk tick, so the block states and block
     * entities it reads are the finished ones of this tick.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % RECONCILE_INTERVAL != 0) return;

        for (var level : event.getServer().getAllLevels()) {
            reconcile(level);
        }
    }

    /** Releases everything of a level that stopped being wanted, then reports the rest. */
    @SuppressWarnings("deprecation")
    private static void reconcile(ServerLevel level) {
        var perLevel = RECEIPTS.get(level);
        if (perLevel == null || perLevel.isEmpty()) return;

        var chunks = new ArrayList<Long>();

        for (var entry : perLevel.entrySet()) {
            var chunk = entry.getKey();
            // a snapshot: the source can be released from inside the loop (a chunk that is unloaded while
            // this runs), which replaces the list of that chunk
            var receipts = new ArrayList<>(entry.getValue());

            for (var receipt : receipts) {
                // Both cases look up the source, so a source whose chunk is not loaded any more (or whose
                // level was unloaded) answers "nothing to do here" and the force load goes away. The
                // machine's own chunk is not required to be loaded: forcing a chunk loads it, so requiring
                // that would make the feature unable to start.
                var wanted = receipt.placed
                        ? hasAnchorBlock(level, receipt.source)
                        : hasAnchorInReservedSlot(level, receipt.source, chunk);

                if (!wanted) {
                    if (receipt.forcedByUs && level.getForcedChunks().contains(chunk)) {
                        level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
                        OritechAddonsOne.LOGGER.debug("[diag] anchor: released chunk {} of {} ({})",
                                new ChunkPos(chunk), receipt.source, receipt.placed ? "block" : "reserved slot");
                    }
                    // the stored list is not the snapshot, so this only touches what is still registered
                    var stored = perLevel.get(chunk);
                    if (stored != null) stored.remove(receipt);
                    continue;
                }

                if (!level.getForcedChunks().contains(chunk)) {
                    level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                    OritechAddonsOne.LOGGER.debug("[diag] anchor: forced chunk {} for {} ({})",
                            new ChunkPos(chunk), receipt.source, receipt.placed ? "block" : "reserved slot");
                }
                receipt.forcedByUs = true;
            }

            if (perLevel.containsKey(chunk) && perLevel.get(chunk).isEmpty()) {
                chunks.add(chunk);
            }
        }

        for (var chunk : chunks) {
            perLevel.remove(chunk);
        }
    }

    /**
     * Adds (or renews) the force load of one source. Only used when a source announces itself; the
     * periodic reconcile above is what keeps it honest.
     */
    @SuppressWarnings("deprecation")
    private static void refresh(@Nullable AddonBlockEntity addon) {
        if (addon == null || !(addon.getLevel() instanceof ServerLevel level)) return;

        var source = addon.getBlockPos();
        var machine = machinePosOf(addon);

        if (machine == null || !level.isLoaded(source)) {
            // The slot was emptied, the addon is not connected (any more) or the source is unloaded: drop
            // whatever this source held. This is also the path that answers a removed anchor item.
            release(level, source);
            return;
        }

        var chunk = new ChunkPos(machine).toLong();
        var existing = find(level, source, chunk);
        var alreadyForced = level.getForcedChunks().contains(chunk);

        // Re-force whenever the chunk is not kept loaded, even if a receipt of this very source is still
        // around: the force load can be gone while the anchor stayed (somebody removed it with /forceload,
        // or a fresh level state is being populated after a restart).
        if (existing != null) {
            if (!alreadyForced) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                existing.forcedByUs = true;
                OritechAddonsOne.LOGGER.debug("[diag] anchor: re-forced chunk {} for {}", new ChunkPos(chunk), source);
            }
            return;
        }

        // the old target is gone (the machine was relinked or replaced), so it goes first
        release(level, source);

        var receipt = new Receipt(source, chunk, addon instanceof AnchorAddonBlockEntity, !alreadyForced);
        RECEIPTS.computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(chunk, key -> new ArrayList<>())
                .add(receipt);

        if (!alreadyForced) {
            level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
            OritechAddonsOne.LOGGER.debug("[diag] anchor: forced chunk {} for {} ({})", new ChunkPos(chunk), source,
                    receipt.placed ? "block" : "reserved slot");
        }
    }

    /**
     * Position of the machine a source works on, or {@code null} while it works on none.
     * <ul>
     *     <li>a placed anchor is claimed by the machine's addon scan, which sets the controller position
     *     and the {@code addon_used} flag, and that machine has to exist;</li>
     *     <li>an addon or dock answers {@link ExtensionAddonBlockEntity#connectedMachinePos()} - the
     *     machine it is attached to or linked to - while its reserved slot holds the anchor item.</li>
     * </ul>
     * Never throws: anything unexpected answers {@code null}, i.e. "release".
     */
    @Nullable
    private static BlockPos machinePosOf(AddonBlockEntity addon) {
        try {
            if (addon instanceof AnchorAddonBlockEntity anchor) {
                return anchor.claimedMachinePos();
            }
            if (addon instanceof ExtensionAddonBlockEntity container && holdsAnchor(container)) {
                return container.connectedMachinePos();
            }
            return null;
        } catch (Throwable failure) {
            OritechAddonsOne.LOGGER.debug("[diag] anchor: could not resolve the machine of {} in {}", addon, failure);
            return null;
        }
    }

    /** True while the reserved slot of the addon holds a chunk anchor plugin. */
    private static boolean holdsAnchor(ExtensionAddonBlockEntity addon) {
        return addon.holdsAnchor();
    }

    // ------------------------------------------------------------------ the two conditions

    /**
     * True while a live anchor block stands at that position and a machine still claims it.
     * <p>
     * The anchor is claimed when the machine's addon scan marked it used ({@code addon_used}, a synced
     * block state) and there is a machine at the controller position. A removed block, an unloaded chunk
     * and a broken machine all fail one of the two.
     */
    private static boolean hasAnchorBlock(ServerLevel level, BlockPos source) {
        if (!level.isLoaded(source)) return false;
        // The anchor is claimed when the machine's addon scan marked it used (addon_used, a synced block
        // state) and there is a machine at the controller position. A removed block, an unloaded chunk and
        // a broken machine all fail one of the two.
        if (!(level.getBlockState(source).getBlock() instanceof AnchorAddonBlock)) return false;

        return level.getBlockEntity(source) instanceof AnchorAddonBlockEntity anchor
                && machineOf(anchor) != null;
    }

    /**
     * True while the addon or dock at that position still holds the anchor item and is connected to a
     * machine.
     * <p>
     * The reported chunk is only trusted while it is the machine's chunk: a relinked dock or a machine
     * that was replaced resolves to a different chunk, which is then treated as "not this source any more"
     * and released. The next {@link #refresh} adds the new one.
     */
    private static boolean hasAnchorInReservedSlot(ServerLevel level, BlockPos source, long chunk) {
        if (!level.isLoaded(source)) return false;

        if (!(level.getBlockEntity(source) instanceof ExtensionAddonBlockEntity addon)) return false;
        if (!holdsAnchor(addon)) return false;

        var machine = addon.connectedMachinePos();
        return machine != null && new ChunkPos(machine).toLong() == chunk;
    }

    /**
     * The machine that claims the given anchor, or {@code null}.
     * <p>
     * Two things have to hold: the machine's scan must have marked the anchor as used ({@code addon_used},
     * a synced block state, cleared again when the machine drops the addon), and a machine must be at the
     * controller position the scan wrote. The machine's chunk is not required to be loaded - forcing a
     * chunk loads it, so requiring that would keep the feature from ever starting - but when it is loaded
     * it really has to be a machine, otherwise the anchor is a leftover of a machine that was broken or
     * replaced.
     */
    @Nullable
    private static BlockPos machineOf(AnchorAddonBlockEntity anchor) {
        var state = anchor.getBlockState();
        if (!state.hasProperty(MachineAddonBlock.ADDON_USED) || !state.getValue(MachineAddonBlock.ADDON_USED)) {
            return null;
        }

        var machine = anchor.claimedMachinePos();
        if (machine == null) return null;

        var level = anchor.getLevel();
        if (level != null && level.isLoaded(machine)
                && !(level.getBlockEntity(machine) instanceof MachineAddonController)) {
            return null;
        }
        return machine;
    }

    // ------------------------------------------------------------------ bookkeeping

    /** The receipt of that source for that chunk, or {@code null}. */
    @Nullable
    private static Receipt find(ServerLevel level, BlockPos source, long chunk) {
        var perLevel = RECEIPTS.get(level);
        if (perLevel == null) return null;

        for (var receipt : perLevel.getOrDefault(chunk, List.<Receipt>of())) {
            if (receipt.source.equals(source)) return receipt;
        }
        return null;
    }
}
