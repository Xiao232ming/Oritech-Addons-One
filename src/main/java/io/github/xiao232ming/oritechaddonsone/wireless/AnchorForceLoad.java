package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import io.github.xiao232ming.oritechaddonsone.block.ChunkAnchorAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.entity.ChunkAnchorAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/**
 * Keeps one chunk loaded per chunk anchor plugin: <b>the chunk the anchor itself stands in</b>, i.e. the
 * chunk of the anchor block, or of the addon / dock that holds the anchor item. The machine the anchor is
 * connected to only decides whether the anchor is active; it no longer decides which chunk is kept loaded.
 * <p>
 * The anchor works in two forms and both end up here:
 * <ul>
 *     <li><b>placed as a block</b> next to a machine - the machine's addon scan claims the block (it is an
 *     ordinary Oritech {@code MachineAddonBlock}) and writes its own position into the block entity, see
 *     {@link ChunkAnchorAddonBlockEntity};</li>
 *     <li><b>inserted into the reserved slot</b> of one of this mod's addons or wireless docks - the
 *     source is the addon / dock itself, so its chunk stays loaded even when the machine it serves is in
 *     another chunk and unloaded, see
 *     {@link ExtensionAddonBlockEntity#connectedMachinePos()} for what makes it active.</li>
 * </ul>
 * Both forms answer the "is this anchor active" question through {@link #machinePosOf}, while the forced
 * chunk is always the source's own.
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
 * <h2>Surviving a restart</h2>
 * The receipts themselves are saved per level ({@link AnchorReceiptsData}), because vanilla persists the
 * force load ticket as well: without the saved receipts a restart would make the mod read its own leftover
 * ticket as "somebody else forced this chunk", and it would never release that chunk again. On load every
 * saved receipt is restored and its chunk re-forced; the regular reconcile then drops the ones whose anchor
 * is really gone. While the server runs the receipts are re-checked every 5 ticks - not only from the
 * sources themselves, so a block entity that was destroyed or whose chunk was unloaded is noticed too:
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
        /** Chunk of the source itself: what is kept loaded. */
        private final long chunk;
        /** Source is a placed anchor block (true) or an addon / dock with the anchor in its reserved slot. */
        private final boolean placed;
        /** True while this mod added the force load (false when it was already there). */
        private boolean forcedByUs;

        /** True while the receipt came from the save file and has not been validated yet. */
        private boolean restored;

        private Receipt(BlockPos source, long chunk, boolean placed, boolean forcedByUs) {
            this.source = source.immutable();
            this.chunk = chunk;
            this.placed = placed;
            this.forcedByUs = forcedByUs;
        }
    }

    /** Receipts per level, keyed weakly so an unloaded level does not stay in memory. */
    private static final Map<ServerLevel, Map<Long, List<Receipt>>> RECEIPTS = new WeakHashMap<>();

    /** Levels whose saved receipts were already merged into {@link #RECEIPTS}. */
    private static final Set<ServerLevel> RESTORED = Collections.newSetFromMap(new WeakHashMap<>());

    /** Levels whose receipts changed and still have to be written back to {@link AnchorReceiptsData}. */
    private static final Set<ServerLevel> DIRTY = Collections.newSetFromMap(new WeakHashMap<>());

    private AnchorForceLoad() {
    }

    // ------------------------------------------------------------------ sources announce themselves

    /**
     * Re-resolves the force load of the given source position. Called when the anchor or the container it
     * is inserted into changes, so the effect is there immediately instead of at the next reconcile - the
     * regular re-check in {@link #onServerTick} makes the call optional, never wrong.
     */
    public static void register(ChunkAnchorAddonBlockEntity anchor) {
        refresh(anchor);
    }

    /** See {@link #register(ChunkAnchorAddonBlockEntity)} - same thing for the reserved slot of an addon. */
    public static void register(ExtensionAddonBlockEntity addon) {
        refresh(addon);
    }

    /**
     * Drops the force load of the given source without looking at the blocks again. Used while a block
     * entity is removed, so the chunk is released at once rather than at the next reconcile.
     */
    public static void release(ServerLevel level, BlockPos source) {
        ensureRestored(level);
        var dropped = remove(level, source);
        if (dropped > 0) {
            markDirty(level);
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

                if (level.getForceLoadedChunks().contains(receipt.chunk)) {
                    level.setChunkForced(ChunkPos.getX(receipt.chunk), ChunkPos.getZ(receipt.chunk), false);
                    OritechAddonsOne.LOGGER.debug("[diag] anchor: released chunk {} of {}",
                            ChunkPos.unpack(receipt.chunk), source);
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
        var reconcileNow = event.getServer().getTickCount() % RECONCILE_INTERVAL == 0;

        for (var level : event.getServer().getAllLevels()) {
            ensureRestored(level);
            if (reconcileNow) reconcile(level);
            flush(level);
        }
    }

    /** Releases everything of a level that stopped being wanted, then reports the rest. */
    /** Releases everything of a level that stopped being wanted, then reports the rest. */
    @SuppressWarnings("deprecation")
    private static void reconcile(ServerLevel level) {
        var perLevel = RECEIPTS.get(level);
        if (perLevel == null || perLevel.isEmpty()) return;

        var chunks = new ArrayList<Long>();

        // A snapshot of the map as well: forcing a chunk below can load it, which lets an anchor announce
        // itself and add to (or remove from) this very map from inside the loop.
        for (var entry : new ArrayList<>(perLevel.entrySet())) {
            var chunk = entry.getKey();
            // a snapshot: the source can be released from inside the loop (a chunk that is unloaded while
            // this runs), which replaces the list of that chunk
            var receipts = new ArrayList<>(entry.getValue());

            for (var receipt : receipts) {
                if (receipt.restored) {
                    // A receipt restored from the save file: make sure its chunk is forced before judging
                    // it, otherwise the source cannot load and a valid receipt would be dropped.
                    if (!level.getForceLoadedChunks().contains(chunk)) {
                        level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                        receipt.forcedByUs = true;
                        markDirty(level);
                    }
                    receipt.restored = false;
                    continue;
                }

                // Both cases look up the source, so a source whose chunk is not loaded any more (or whose
                // level was unloaded) answers "nothing to do here" and the force load goes away.
                var wanted = receipt.placed
                        ? hasAnchorBlock(level, receipt.source)
                        : hasAnchorInReservedSlot(level, receipt.source, chunk);

                if (!wanted) {
                    if (receipt.forcedByUs && level.getForceLoadedChunks().contains(chunk)) {
                        level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
                        OritechAddonsOne.LOGGER.debug("[diag] anchor: released chunk {} of {} ({})",
                                ChunkPos.unpack(chunk), receipt.source, receipt.placed ? "block" : "reserved slot");
                    }
                    // the stored list is not the snapshot, so this only touches what is still registered
                    var stored = perLevel.get(chunk);
                    if (stored != null) stored.remove(receipt);
                    markDirty(level);
                    continue;
                }

                if (!level.getForceLoadedChunks().contains(chunk)) {
                    level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                    markDirty(level);
                    OritechAddonsOne.LOGGER.debug("[diag] anchor: forced chunk {} for {} ({})",
                            ChunkPos.unpack(chunk), receipt.source, receipt.placed ? "block" : "reserved slot");
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
     * <p>
     * The forced chunk is the source's own chunk, not the machine's: a dock in a far away chunk has to stay
     * loaded for its plugins to keep reaching the machine, and an anchor is generally meant to keep the
     * place it was built in alive. A source that is no longer connected to a machine still loads nothing.
     */
    @SuppressWarnings("deprecation")
    private static void refresh(@Nullable AddonBlockEntity addon) {
        if (addon == null || !(addon.getLevel() instanceof ServerLevel level)) return;

        ensureRestored(level);

        var source = addon.getBlockPos();

        if (machinePosOf(addon) == null || !level.isLoaded(source)) {
            // The slot was emptied, the addon is not connected (any more) or the source is unloaded: drop
            // whatever this source held. This is also the path that answers a removed anchor item.
            release(level, source);
            return;
        }

        var chunk = ChunkPos.pack(source);
        var existing = find(level, source, chunk);
        var alreadyForced = level.getForceLoadedChunks().contains(chunk);

        // Re-force whenever the chunk is not kept loaded, even if a receipt of this very source is still
        // around: the force load can be gone while the anchor stayed (somebody removed it with /forceload,
        // or a fresh level state is being populated after a restart).
        if (existing != null) {
            if (!alreadyForced) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                existing.forcedByUs = true;
                markDirty(level);
                OritechAddonsOne.LOGGER.debug("[diag] anchor: re-forced chunk {} for {}", ChunkPos.unpack(chunk), source);
            }
            return;
        }

        // the old target is gone (the machine was relinked or replaced), so it goes first
        release(level, source);

        var receipt = new Receipt(source, chunk, addon instanceof ChunkAnchorAddonBlockEntity, !alreadyForced);
        RECEIPTS.computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(chunk, key -> new ArrayList<>())
                .add(receipt);
        markDirty(level);

        if (!alreadyForced) {
            level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
            OritechAddonsOne.LOGGER.debug("[diag] anchor: forced chunk {} for {} ({})", ChunkPos.unpack(chunk), source,
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
            if (addon instanceof ChunkAnchorAddonBlockEntity anchor) {
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
        if (!(level.getBlockState(source).getBlock() instanceof ChunkAnchorAddonBlock)) return false;

        return level.getBlockEntity(source) instanceof ChunkAnchorAddonBlockEntity anchor
                && machineOf(anchor) != null;
    }

    /**
     * True while the addon or dock at that position still holds the anchor item and is connected to a
     * machine.
     * <p>
     * The receipt's chunk is the source's own (see {@link #refresh}); a receipt carrying another chunk is a
     * leftover and is treated as "not this source any more". Being connected is still required: an
     * unlinked dock, or an addon whose machine is gone, turns the anchor off even though the chunk it
     * stands in has not changed.
     */
    private static boolean hasAnchorInReservedSlot(ServerLevel level, BlockPos source, long chunk) {
        if (!level.isLoaded(source)) return false;

        if (!(level.getBlockEntity(source) instanceof ExtensionAddonBlockEntity addon)) return false;
        if (!holdsAnchor(addon)) return false;
        if (ChunkPos.pack(source) != chunk) return false;

        return addon.connectedMachinePos() != null;
    }

    /**
     * The machine that claims the given anchor, or {@code null}.
     * <p>
     * Two things have to hold: the machine's scan must have marked the anchor as used ({@code addon_used},
     * a synced block state, cleared again when the machine drops the addon), and a machine must be at the
     * controller position the scan wrote. The machine's chunk does not have to be loaded - it is not the
     * chunk this feature keeps loaded - but when it is loaded it really has to be a machine, otherwise the
     * anchor is a leftover of a machine that was broken or replaced.
     */
    @Nullable
    private static BlockPos machineOf(ChunkAnchorAddonBlockEntity anchor) {
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

    // ------------------------------------------------------------------ saved receipts

    /**
     * Merges the level's saved receipts into {@link #RECEIPTS} once per server run.
     * <p>
     * The chunk is not forced here: every restored receipt is marked {@link Receipt#restored}, and the next
     * reconcile forces its chunk before validating it - so an anchor whose chunk is simply not loaded yet is
     * not mistaken for a removed one, while a receipt whose anchor is really gone is dropped one tick later.
     */
    private static void ensureRestored(ServerLevel level) {
        if (!RESTORED.add(level)) return;

        var data = level.getDataStorage().computeIfAbsent(AnchorReceiptsData.TYPE);
        for (var entry : data.entries()) {
            var existing = find(level, entry.source(), entry.chunk());
            if (existing != null) {
                // an anchor announced itself before the save was read: the saved ownership wins
                existing.forcedByUs |= entry.forcedByUs();
                continue;
            }

            var receipt = new Receipt(entry.source(), entry.chunk(), entry.placed(), entry.forcedByUs());
            receipt.restored = true;
            RECEIPTS.computeIfAbsent(level, key -> new HashMap<>())
                    .computeIfAbsent(entry.chunk(), key -> new ArrayList<>())
                    .add(receipt);
        }
    }

    /** Marks a level's receipts as changed, so {@link #flush} writes them back on this tick. */
    private static void markDirty(ServerLevel level) {
        DIRTY.add(level);
    }

    /** Writes the level's receipts back to {@link AnchorReceiptsData} if they changed since the last flush. */
    private static void flush(ServerLevel level) {
        if (!DIRTY.remove(level)) return;

        var entries = new ArrayList<AnchorReceiptsData.Entry>();
        var perLevel = RECEIPTS.get(level);
        if (perLevel != null) {
            for (var receipts : perLevel.values()) {
                for (var receipt : receipts) {
                    entries.add(new AnchorReceiptsData.Entry(receipt.source, receipt.chunk, receipt.placed,
                            receipt.forcedByUs));
                }
            }
        }

        level.getDataStorage().computeIfAbsent(AnchorReceiptsData.TYPE).setEntries(entries);
    }
}
