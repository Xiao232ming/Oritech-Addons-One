package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * The item inventory one face of a block a <b>placed transfer plugin</b> hangs on offers to the outside world.
 * <p>
 * A placed transfer plugin already moves its machine's items on its own (the per-face automation of
 * {@code ExtensionAddonBlockEntity#serverTickTransfer}) and can be configured through its own screen, but
 * nothing outside the plugin could reach the machine through the block it hangs on: Oritech registers no
 * {@code Capabilities.Item.BLOCK} provider for its shared addon block entity type, so a
 * {@code machine_extender} used to answer "no inventory" to every pipe and hopper. This handler closes that gap
 * without touching Oritech: one handler per host position and face, registered for the block entity type of the
 * block the plugin hangs on, so a face whose mode is set behaves exactly like the same face of an Extension
 * Addon - a pipe inserts into the machine while the mode allows it and extracts out of it while the mode allows
 * it, and never into an output slot nor out of an input one (see {@link MachineSlotRoles}).
 * <p>
 * <b>The two placements this mod supports are two subclasses.</b> A plugin on Oritech's machine extender
 * configures the extender's faces ({@link ExtenderFaceStorage}); a plugin hung directly on a machine configures
 * the faces of the plugin block itself, i.e. of the very block the pipe stands next to
 * ({@link MachinePluginStorage}). Both differ only in the question "which plugin configures this face?" and in
 * the machine the plugin works on, so those are the two functions both of them pass in, while the machine
 * inventory, the slot roles, the mode gating and the handler identity live here once.
 * <p>
 * <b>The side is read the way Oritech's pipes ask it.</b> Oritech checks a neighbour with
 * {@code level.getCapability(Capabilities.Item.BLOCK, neighbourPos, direction.getOpposite())}, i.e. the context
 * is the direction <em>from the block that answers towards the block that asked</em> - so a pipe east of the
 * host asks with {@link Direction#EAST} and stands on the face this handler must answer for. A hopper does the
 * same with its own facing. There is no translation to do here; the side is already the host's face.
 * <p>
 * <b>One object per host position and face, forever.</b> NeoForge caches the handler a position and face answer
 * with (and a {@code BlockCapabilityCache} keeps that answer until the level invalidates the position), so the
 * instance must never be replaced. Everything that can change - which plugin hangs where, its modes, the
 * machine behind the host - is therefore resolved fresh inside each call; the cache exists for identity, not
 * for staleness. It is keyed by the host position and grows only where a pipe really asked a host with a plugin
 * on it, which keeps it tiny in practice.
 * <p>
 * <b>Fail safe:</b> a face with no mode, a host no plugin hangs on, a host no machine has claimed, an unloaded
 * chunk and a host that has been unloaded long enough to be collected all resolve to "no inventory" - this
 * handler then reports zero slots and accepts or offers nothing instead of crashing, exactly like
 * {@link MachineFaceStorage}.
 */
public class FacePluginStorage extends DelegatingInventoryStorage {

    /**
     * Handler of every host position and face that was ever asked, indexed by {@link Direction#ordinal()} - see
     * the class comment for why the objects are kept.
     * <p>
     * Bounded in practice by "one entry per host position a pipe ever asked about", and the entry is a fixed
     * six element array, so a server that has seen <i>n</i> hosts asked about holds six references of <i>n</i>.
     * It is deliberately not a cache of answers - the answers are re-resolved on every call, so a stale entry
     * can never be seen; the map only preserves the identity of the handler objects.
     */
    private static final Map<BlockPos, FacePluginStorage[]> HANDLERS = new ConcurrentHashMap<>();

    /**
     * How many nanoseconds have to pass between two "this face answered nothing" diagnostic lines. A pipe asks
     * its neighbours again every tick, so without a limit a face that is deliberately not configured would
     * write twenty lines a second and drown everything else in the log. One line per second and host position
     * is enough to show the mode lookup is really being asked and what it sees.
     */
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 1_000_000_000L;

    /**
     * Last time the diagnostic line of a host position was written. Keyed by position only - and not by
     * position and face - so a host cannot flood the log even while a pipe walks all six faces. Entries are
     * never removed, which is bounded by the number of hosts ever asked about, exactly like {@link #HANDLERS}.
     */
    private static final Map<BlockPos, AtomicLong> LAST_DIAGNOSTIC = new ConcurrentHashMap<>();

    /**
     * Which plugin configures one face of the host, i.e. the question the two placements answer differently.
     * Implementations read the world on every call - a plugin can be broken or placed at any time, and
     * NeoForge's own invalidation (see {@code TransferAddonBlockEntity} and
     * {@code TransferPreviewAddonBlockEntity}) is what makes a pipe ask again - so a stale answer can never be
     * served from here.
     */
    @FunctionalInterface
    public interface FaceLookup {
        /**
         * The transfer mode {@code face} of the host has right now, or {@link TransferMode#NONE} while no
         * plugin of this mod configures it.
         */
        TransferMode modeOf(BlockEntity host, Direction face);
    }

    /**
     * The machine a placed transfer plugin on this host works on, or {@code null} while it cannot be resolved;
     * see {@link MachineFactory} and {@link ExtenderFactory}.
     */
    @FunctionalInterface
    public interface MachineLookup {
        /** Machine of the plugin hanging on {@code host}, or {@code null} while there is none. */
        @Nullable
        BlockPos machinePos(BlockEntity host);
    }

    /**
     * Builds the handler of one placement. A factory is used instead of a plain constructor reference because
     * the concrete handlers are private to their own placement class and because the cache of
     * {@link #handlerAt} has to be able to create exactly one object per host position and face.
     */
    @FunctionalInterface
    public interface HandlerFactory {
        /** The handler of one face of the host, created once and then kept forever. */
        FacePluginStorage create(BlockEntity host, Direction face, FaceLookup lookup, MachineLookup machine);
    }

    /**
     * The host this face belongs to. Held weakly on purpose: the handler object is kept forever (NeoForge caches
     * what a face answers with), and a strong reference would keep the whole chunk a pipe once asked about
     * alive for as long as the cache entry exists. A collected host simply answers "no inventory".
     */
    private final WeakReference<BlockEntity> host;
    private final BlockPos hostPos;
    private final FaceLookup lookup;
    private final MachineLookup machine;
    /** Face of the host this storage belongs to, resolved against the block entity on every call. */
    private final Direction face;

    /**
     * Creates the handler of one face. Protected, not private: the two placements are the two subclasses of this
     * class (see {@link ExtenderFaceStorage} and {@link MachinePluginStorage}), and the factory in
     * {@link #handlerAt} only ever calls one of them.
     */
    protected FacePluginStorage(BlockEntity host, Direction face, FaceLookup lookup, MachineLookup machine) {
        super(() -> machineStorage(host, machine), () -> lookup.modeOf(host, face) != TransferMode.NONE);
        this.host = new WeakReference<>(host);
        this.hostPos = host.getBlockPos();
        this.face = face;
        this.lookup = lookup;
        this.machine = machine;
    }

    /**
     * The handler of one face of the host a placed plugin hangs on, or {@code null} while that face is not an
     * item connection at all - a face with no mode, which includes every face of a host no transfer plugin
     * hangs on. {@code null} is what the capability provider answers with, so a pipe sees the host as "nothing
     * here" until a plugin really offers something on that face.
     * <p>
     * A face the mode lookup answers {@code NONE} for gets one diagnostic line, which is what tells a
     * "configured face does not connect" report apart from "the provider was never asked": the provider itself
     * logs that it was called, and this one says what the mode lookup answered. It is written at most once a
     * second per host position (see {@link #DIAGNOSTIC_INTERVAL_NANOS}) and only while debug logging is on, so a
     * pipe that asks every tick cannot drown the log.
     *
     * @param host    the block entity a plugin hangs on - Oritech's machine extender, or a machine one of this
     *                mod's preview plugins is attached to
     * @param face    the face of that host the pipe asked for, or {@code null} while it asked without a side
     * @param lookup  which plugin configures a face of that host, see {@link FaceLookup}
     * @param machine which machine the plugin on that host works on, see {@link MachineLookup}
     * @param create  the concrete handler of this placement, see {@link ExtenderFaceStorage} and
     *                {@link MachinePluginStorage}
     */
    @Nullable
    public static FacePluginStorage handlerAt(BlockEntity host, @Nullable Direction face, FaceLookup lookup,
            MachineLookup machine, HandlerFactory create) {
        if (face == null) return null;

        var pos = host.getBlockPos();
        if (lookup.modeOf(host, face) == TransferMode.NONE) {
            logNothingAnswered(pos, face);
            return null;
        }

        var handlers = HANDLERS.computeIfAbsent(pos.immutable(),
                key -> new FacePluginStorage[Direction.values().length]);
        var existing = handlers[face.ordinal()];
        if (existing != null) return existing;

        var created = create.create(host, face, lookup, machine);
        handlers[face.ordinal()] = created;

        OritechAddonsOne.LOGGER.debug("[transfer] capability: host {} face {} now answers its handler",
                pos, face);
        return created;
    }

    /** Writes the "answered nothing" diagnostic line, but at most once a second per host position. */
    private static void logNothingAnswered(BlockPos pos, Direction face) {
        var last = LAST_DIAGNOSTIC.computeIfAbsent(pos.immutable(), ignored -> new AtomicLong(Long.MIN_VALUE));
        var now = System.nanoTime();
        var previous = last.get();

        if (previous != Long.MIN_VALUE && now - previous < DIAGNOSTIC_INTERVAL_NANOS) return;
        if (!last.compareAndSet(previous, now)) return;

        OritechAddonsOne.LOGGER.debug("[transfer] capability: {} face {} answered nothing", pos, face);
    }

    /**
     * The inventory of the machine a placed plugin works on, or {@code null} while the face is not configured
     * or that machine cannot be resolved. The machine is the one the host is attached to
     * ({@link MachineLookup}) and the handler is the machine's own item lookup, so this is exactly what
     * Oritech's own item pipes see when they look at the machine.
     */
    @Nullable
    private static ResourceHandler<ItemResource> machineStorage(BlockEntity host, MachineLookup machine) {
        var level = host.getLevel();
        if (level == null) return null;

        return MachineFaceStorage.machineStorageAt(level, machine.machinePos(host));
    }

    /** The host of this handler, or {@code null} while it is gone or was collected. */
    @Nullable
    private BlockEntity host() {
        return host.get();
    }

    /** True while the given slot belongs to the machine inventory this face reaches into. */
    private boolean covers(int index) {
        return index >= 0 && index < size();
    }

    /**
     * True while this face offers the machine's inventory to the outside world, which is also what the
     * {@linkplain DelegatingInventoryStorage backing storage} gate asks before it reads the machine.
     */
    @Override
    public int size() {
        var current = host();
        return current != null && machineStorage(current, machine) != null ? super.size() : 0;
    }

    @Override
    public ItemResource getResource(int index) {
        return covers(index) ? super.getResource(index) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return covers(index) ? super.getAmountAsLong(index) : 0L;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return covers(index) ? super.getCapacityAsLong(index, resource) : 0L;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return covers(index) && super.isValid(index, resource);
    }

    /**
     * Indexed insert of a pipe or a hopper: gated by the face's mode (an OUTPUT face takes nothing) and by the
     * machine's slot roles ({@link MachineSlotRoles}) - an INPUT face fills the machine's input slots and never
     * a slot Oritech reserved for its results. The roles are asked of the machine this face reaches into, which
     * is resolved from the host exactly like the inventory itself.
     */
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert() || !covers(index)) return 0;
        if (!MachineSlotRoles.allowsInsertAt(machineEntity(), index)) return 0;
        return super.insert(index, resource, amount, transaction);
    }

    /**
     * Index-free insert of a pipe or a hopper. The mode gates it here as well as in the indexed overload - so a
     * caller that only knows the handler cannot fill the machine through an output-only face - and the slots are
     * walked here so the machine's roles are respected for a caller that names no slot.
     */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert()) return 0;

        // without a machine block entity there are no roles to respect, and the indexed overload would answer 0
        // for every slot - the machine's own item lookup decides, exactly as it did before
        var machineEntity = machineEntity();
        if (machineEntity == null) return super.insert(resource, amount, transaction);

        var inserted = 0;
        for (var index = 0; index < super.size() && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    /** Indexed extract, gated like {@link #insert(int, ItemResource, int, TransactionContext)}. */
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsExtract() || !covers(index)) return 0;
        if (!MachineSlotRoles.allowsExtractAt(machineEntity(), index)) return 0;
        return super.extract(index, resource, amount, transaction);
    }

    /**
     * Index-free extract, gated exactly like {@link #extract(int, ItemResource, int, TransactionContext)}: an
     * OUTPUT face offers the machine's own output slots and nothing else.
     */
    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsExtract()) return 0;

        var machineEntity = machineEntity();
        if (machineEntity == null) return super.extract(resource, amount, transaction);

        var extracted = 0;
        for (var index = 0; index < super.size() && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }

    /**
     * The machine block entity this face reaches into, or {@code null} while that machine cannot be resolved. It
     * is the same block {@link #machineStorage(BlockEntity, MachineLookup)} takes the inventory from, so the
     * roles asked of it - see {@link MachineSlotRoles} - always belong to the inventory this face exposes.
     */
    @Nullable
    private BlockEntity machineEntity() {
        var current = host();
        if (current == null) return null;

        var level = current.getLevel();
        if (level == null) return null;

        var pos = machine.machinePos(current);
        if (pos == null || !level.isLoaded(pos)) return null;

        return level.getBlockEntity(pos);
    }

    /** True while items may be put into the machine through this face. */
    private boolean allowsInsert() {
        return transferMode().allowsInsert();
    }

    /** True while items may be taken out of the machine through this face. */
    private boolean allowsExtract() {
        return transferMode().allowsExtract();
    }

    /** The mode this face transfers with right now, or {@link TransferMode#NONE} while it transfers nothing. */
    private TransferMode transferMode() {
        var current = host();
        if (current == null) return TransferMode.NONE;
        return lookup.modeOf(current, face);
    }

    /** Position of the host this face belongs to; also the key of the handler cache. */
    protected BlockPos hostPos() {
        return hostPos;
    }

    /** Face of the host this face belongs to. */
    protected Direction face() {
        return face;
    }
}
