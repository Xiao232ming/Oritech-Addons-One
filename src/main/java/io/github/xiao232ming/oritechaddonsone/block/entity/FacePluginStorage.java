package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.DelegatingInventoryStorage;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * The item inventory one face of a block a <b>placed transfer plugin</b> hangs on offers to the outside world.
 * <p>
 * A placed transfer plugin already moves its machine's items on its own (the per-face automation of
 * {@code ExtensionAddonBlockEntity#serverTickTransfer}) and can be configured through its own screen, but nothing
 * outside the plugin could reach the machine through the block it hangs on: Oritech registers no item capability
 * for its shared addon block entity type on this branch either, so a {@code machine_extender} used to answer "no
 * inventory" to every pipe and hopper. This storage closes that gap without touching Oritech: one storage per host
 * position and face, registered for the block entity type of the block the plugin hangs on, so a face whose mode is
 * set behaves exactly like the same face of an Extension Addon - a pipe inserts into the machine while the mode
 * allows it and extracts out of it while the mode allows it, and never into an output slot nor out of an input one
 * (see {@link MachineSlotRoles}).
 * <p>
 * <b>One placement, one subclass.</b> A placed 扩展传输插件 on Oritech's machine extender configures the
 * extender's faces ({@link ExtenderFaceStorage}), so that is the placement this class serves today: it is told
 * which plugin configures a face and which machine that plugin works on, while the machine inventory, the slot
 * roles, the mode gating and the handler identity live here once. 传输插件 deliberately
 * answers no item capability at all ({@code TransferAddonBlockEntity#getItemLookup}) - its configured faces
 * drive only its own automation, and a machine that wants pipes offers them its own faces - so there is no second
 * subclass any more.
 * <p>
 * <b>The side is read the way Oritech's pipes ask it.</b> Oritech checks a neighbour with
 * {@code ItemApi.BLOCK.find(level, neighbourPos, direction.getOpposite())}, i.e. the context is the direction
 * <em>from the block that answers towards the block that asked</em> - so a pipe east of the host asks with
 * {@link Direction#EAST} and stands on the face this storage must answer for. A hopper does the same with its own
 * facing. There is no translation to do here; the side is already the host's face.
 * <p>
 * <b>One object per host position and face, forever.</b> On this branch the capability wrapper Oritech builds for
 * us ({@code NeoforgeItemApiImpl.ContainerStorageWrapper}) holds the storage a face answered with, and the
 * {@code BlockLookupCache} of Oritech's own pipes keeps that answer until the level invalidates the position, so
 * the instance must never be replaced. Everything that can change - which plugin hangs where, its modes, the
 * machine behind the host - is therefore resolved fresh inside each call; the cache exists for identity, not for
 * staleness. It is keyed by the host position and grows only where a pipe really asked a host with a plugin on it,
 * which keeps it tiny in practice.
 * <p>
 * <b>Fail safe:</b> a face with no mode, a host no plugin hangs on, a host no machine has claimed, an unloaded
 * chunk and a host that has been unloaded long enough to be collected all resolve to "no inventory" - this storage
 * then reports zero slots and accepts or offers nothing instead of crashing, exactly like
 * {@link MachineFaceStorage}.
 */
public class FacePluginStorage extends DelegatingInventoryStorage {

    /**
     * Storage of every host position and face that was ever asked, indexed by {@link Direction#ordinal()} - see the
     * class comment for why the objects are kept.
     * <p>
     * Bounded in practice by "one entry per host position a pipe ever asked about", and the entry is a fixed six
     * element array, so a server that has seen <i>n</i> hosts asked about holds six references of <i>n</i>. It is
     * deliberately not a cache of answers - the answers are re-resolved on every call, so a stale entry can never be
     * seen; the map only preserves the identity of the storage objects.
     */
    private static final Map<BlockPos, FacePluginStorage[]> HANDLERS = new ConcurrentHashMap<>();

    /**
     * How many nanoseconds have to pass between two "this face answered nothing" diagnostic lines. A pipe asks its
     * neighbours again every tick, so without a limit a face that is deliberately not configured would write twenty
     * lines a second and drown everything else in the log. One line per second and host position is enough to show
     * the mode lookup is really being asked and what it sees.
     */
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 1_000_000_000L;

    /**
     * Last time the diagnostic line of a host position was written. Keyed by position only - and not by position and
     * face - so a host cannot flood the log even while a pipe walks all six faces. Entries are never removed, which
     * is bounded by the number of hosts ever asked about, exactly like {@link #HANDLERS}.
     */
    private static final Map<BlockPos, AtomicLong> LAST_DIAGNOSTIC = new ConcurrentHashMap<>();

    /**
     * Which plugin configures one face of the host, i.e. the one question a placement has to answer.
     * Implementations read the world on every call - a plugin can be broken or placed at any time, and
     * NeoForge's own invalidation (see {@code ExtensionTransferAddonBlockEntity}) is what makes a pipe ask again - so a
     * stale answer can never be served from here.
     */
    @FunctionalInterface
    public interface FaceLookup {
        /**
         * The transfer mode {@code face} of the host has right now, or {@link TransferMode#NONE} while no plugin of
         * this mod configures it.
         */
        TransferMode modeOf(BlockEntity host, Direction face);
    }

    /**
     * The machine a placed transfer plugin on this host works on, or {@code null} while it cannot be resolved; see
     * {@link ExtenderFaceStorage}.
     */
    @FunctionalInterface
    public interface MachineLookup {
        /** Machine of the plugin hanging on {@code host}, or {@code null} while there is none. */
        @Nullable
        BlockPos machinePos(BlockEntity host);
    }

    /**
     * Builds the storage of one placement. A factory is used instead of a plain constructor reference because the
     * concrete storages are private to their own placement class and because the cache of {@link #handlerFor} has to
     * be able to create exactly one object per host position and face.
     */
    @FunctionalInterface
    public interface HandlerFactory {
        /** The storage of one face of the host, created once and then kept forever. */
        FacePluginStorage create(BlockEntity host, Direction face, FaceLookup lookup, MachineLookup machine);
    }

    /**
     * The host this face belongs to. Held weakly on purpose: the storage object is kept forever (the capability
     * caches what a face answers with), and a strong reference would keep the whole chunk a pipe once asked about
     * alive for as long as the cache entry exists. A collected host simply answers "no inventory".
     */
    private final WeakReference<BlockEntity> host;
    private final BlockPos hostPos;
    private final FaceLookup lookup;
    private final MachineLookup machine;
    /** Face of the host this storage belongs to, resolved against the block entity on every call. */
    private final Direction face;

    /**
     * Creates the storage of one face. Protected, not private: a placement is a subclass of this class (see
     * {@link ExtenderFaceStorage}), and the factory in {@link #handlerFor} only ever calls one of them.
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
     * The storage of one face of the host a placed plugin hangs on, or {@code null} while that face is not an item
     * connection at all - a face with no mode, which includes every face of a host no transfer plugin hangs on.
     * {@code null} is what the capability provider answers with, so a pipe sees the host as "nothing here" until a
     * plugin really offers something on that face.
     * <p>
     * A face the mode lookup answers {@code NONE} for gets one diagnostic line, which is what tells a "configured
     * face does not connect" report apart from "the provider was never asked": the provider itself logs that it was
     * called, and this one says what the mode lookup answered. It is written at most once a second per host position
     * (see {@link #DIAGNOSTIC_INTERVAL_NANOS}) and only while debug logging is on, so a pipe that asks every tick
     * cannot drown the log.
     *
     * @param host    the block entity a placed extension transfer plugin hangs on - Oritech's machine extender
     * @param face    the face of that host the pipe asked for, or {@code null} while it asked without a side
     * @param lookup  which plugin configures a face of that host, see {@link FaceLookup}
     * @param machine which machine the plugin on that host works on, see {@link MachineLookup}
     * @param create  the concrete storage of this placement, see {@link ExtenderFaceStorage}
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

        OritechAddonsOne.LOGGER.debug("[transfer] capability: host {} face {} now answers its storage",
                pos, face);
        return created;
    }

    /**
     * The same as {@link #handlerAt} in the form the NeoForge item capability has to answer with on this branch: an
     * {@code IItemHandler}, or {@code null} while the face is no connection.
     * <p>
     * Oritech's own bridge wraps an {@link ItemApi.InventoryStorage} in its
     * {@code NeoforgeItemApiImpl.ContainerStorageWrapper}, but that class only ever leaves Oritech as an
     * {@code ItemApi.InventoryStorage} again, so it cannot be the answer of the capability itself. The adapter below
     * is the same mapping with the public NeoForge interface on the outside; the mode gating lives in the storage it
     * wraps, so nothing here has to know about transfer modes.
     */
    @Nullable
    public static IItemHandler handlerFor(BlockEntity host, @Nullable Direction face, FaceLookup lookup,
            MachineLookup machine, HandlerFactory create) {
        var storage = handlerAt(host, face, lookup, machine, create);
        return storage == null ? null : new NeoForgeHandler(storage);
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
     * The inventory of the machine a placed plugin works on, or {@code null} while the face is not configured or
     * that machine cannot be resolved. The machine is the one the host is attached to ({@link MachineLookup}) and the
     * storage is the machine's own inventory, so this is exactly what Oritech's own item pipes see when they look at
     * the machine.
     */
    @Nullable
    private static ItemApi.InventoryStorage machineStorage(BlockEntity host, MachineLookup machine) {
        var level = host.getLevel();
        if (level == null) return null;

        return MachineFaceStorage.machineStorageAt(level, machine.machinePos(host));
    }

    /** The host of this storage, or {@code null} while it is gone or was collected. */
    @Nullable
    private BlockEntity host() {
        return host.get();
    }

    /** True while the given slot belongs to the machine inventory this face reaches into. */
    private boolean covers(int index) {
        return index >= 0 && index < getSlotCount();
    }

    /**
     * True while this face offers the machine's inventory to the outside world, which is also what the
     * {@linkplain DelegatingInventoryStorage backing storage} gate asks before it reads the machine.
     */
    @Override
    public int getSlotCount() {
        var current = host();
        return current != null && machineStorage(current, machine) != null ? super.getSlotCount() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return covers(index) ? super.getStackInSlot(index) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int index) {
        return covers(index) ? super.getSlotLimit(index) : 0;
    }

    /**
     * True while a pipe may push into this face at all. An output-only face answers {@code false}, which is what
     * tells a pipe not to try, and the parent already answers {@code false} while the machine is gone.
     */
    @Override
    public boolean supportsInsertion() {
        return allowsInsert() && super.supportsInsertion();
    }

    /** True while a pipe may pull out of this face at all; see {@link #supportsInsertion()}. */
    @Override
    public boolean supportsExtraction() {
        return allowsExtract() && super.supportsExtraction();
    }

    /**
     * Index-free insert of a pipe or a hopper. The mode gates it here as well as in the indexed overload - so a
     * caller that only knows the storage cannot fill the machine through an output-only face - and the slots are
     * walked here, because the machine's slot roles ({@link MachineSlotRoles}) decide which ones may be filled at
     * all: an INPUT face fills the machine's input slots and never a slot Oritech reserved for its results.
     */
    @Override
    public int insert(ItemStack inserted, boolean simulate) {
        if (!allowsInsert()) return 0;

        // without a machine block entity there are no roles to respect, and the indexed overload would answer 0 for
        // every slot - the machine's own storage decides, exactly as it did before
        var machineEntity = machineEntity();
        if (machineEntity == null) return super.insert(inserted, simulate);

        var insertedTo = 0;
        for (var index = 0; index < super.getSlotCount() && insertedTo < inserted.getCount(); index++) {
            insertedTo += insertToSlot(inserted.copyWithCount(inserted.getCount() - insertedTo), index, simulate);
        }
        return insertedTo;
    }

    /**
     * Indexed insert of a pipe or a hopper: gated by the face's mode (an OUTPUT face takes nothing) and by the
     * machine's slot roles ({@link MachineSlotRoles}). The roles are asked of the machine this face reaches into,
     * which is resolved from the host exactly like the machine's own storage is.
     * <p>
     * The face's item filter for the direction is a third gate, and it is asked of the <b>same</b> machine the
     * inventory comes from, so the filter, the roles and the slots can never describe three different machines.
     */
    /**
     * The face's item filter for one direction of the movement, or {@code null} while this face is not filtered
     * in that direction - which is what every face without a 过滤 configuration answers.
     * <p>
     * The filter is taken from the machine the plugin on this host works on, exactly like the inventory and the
     * slot roles above, so the three can never be read from three different machines.
     */
    @Nullable
    private ItemFilterData filter(TransferMode flow) {
        var current = host();
        if (current == null) return null;

        var level = current.getLevel();
        if (level == null) return null;

        var pos = machine.machinePos(current);
        if (pos == null) return null;

        return MachineFaceConfigs.faceFilters(level, pos).of(face, flow);
    }

    /** True while the face's filter for that direction lets the given item through. */
    private boolean allows(TransferMode flow, ItemStack stack) {
        var filter = filter(flow);
        return filter == null || filter.allows(stack);
    }

    @Override
    public int insertToSlot(ItemStack inserted, int index, boolean simulate) {
        if (!allowsInsert() || !covers(index)) return 0;
        if (!allows(TransferMode.INPUT, inserted)) return 0;
        if (!MachineSlotRoles.allowsInsertAt(machineEntity(), index)) return 0;
        return super.insertToSlot(inserted, index, simulate);
    }

    /**
     * Index-free extract, gated like {@link #insert(ItemStack, boolean)}: an OUTPUT face offers the machine's own
     * output slots and nothing else.
     */
    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        if (!allowsExtract()) return 0;

        var machineEntity = machineEntity();
        if (machineEntity == null) return super.extract(extracted, simulate);

        var extractedFrom = 0;
        for (var index = 0; index < super.getSlotCount() && extractedFrom < extracted.getCount(); index++) {
            extractedFrom += extractFromSlot(extracted.copyWithCount(extracted.getCount() - extractedFrom), index,
                    simulate);
        }
        return extractedFrom;
    }

    /** Indexed extract, gated like {@link #insertToSlot(ItemStack, int, boolean)}. */
    @Override
    public int extractFromSlot(ItemStack extracted, int index, boolean simulate) {
        if (!allowsExtract() || !covers(index)) return 0;
        if (!allows(TransferMode.OUTPUT, extracted)) return 0;
        if (!MachineSlotRoles.allowsExtractAt(machineEntity(), index)) return 0;
        return super.extractFromSlot(extracted, index, simulate);
    }

    /**
     * A pipe that pulled an item out or pushed one in asks us to persist the change. The backing storage belongs to
     * the machine, whose own handler already marks itself dirty, so only the plugin of this mod has to be told that
     * the face was used (it holds no items itself, but this is where a future state would be flushed).
     */
    @Override
    public void update() {
        var current = host();
        if (current instanceof ExtensionAddonBlockEntity addon) addon.onProxyUsed();
    }

    /**
     * The machine block entity this face reaches into, or {@code null} while that machine cannot be resolved. It is
     * the same block {@link #machineStorage(BlockEntity, MachineLookup)} takes the inventory from, so the roles asked
     * of it - see {@link MachineSlotRoles} - always belong to the inventory this face exposes.
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

    /** Position of the host this face belongs to; also the key of the storage cache. */
    protected BlockPos hostPos() {
        return hostPos;
    }

    /** Face of the host this face belongs to. */
    protected Direction face() {
        return face;
    }

    /**
     * Turns the storage of one face into the {@code IItemHandler} a NeoForge item capability has to answer with -
     * "wrapper" in the sense of the whole chain: the capability hands it to a pipe or a hopper, it forwards every
     * question to this storage, and that storage forwards it to the machine behind the host.
     * <p>
     * Its stacks are handed out as the machine holds them, like Oritech's own wrapper does - the
     * {@code IItemHandler} contract forbids the <em>caller</em> to modify them.
     */
    private record NeoForgeHandler(ItemApi.InventoryStorage storage) implements IItemHandler {

        @Override
        public int getSlots() {
            return storage.getSlotCount();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < 0 || slot >= storage.getSlotCount() ? ItemStack.EMPTY : storage.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= storage.getSlotCount()) return stack;

            var inserted = storage.insertToSlot(stack, slot, simulate);
            return inserted <= 0 ? stack : stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || slot < 0 || slot >= storage.getSlotCount()) return ItemStack.EMPTY;

            var available = storage.getStackInSlot(slot);
            if (available.isEmpty()) return ItemStack.EMPTY;

            var wanted = available.copyWithCount(Math.min(amount, available.getCount()));
            var extracted = storage.extractFromSlot(wanted, slot, simulate);
            return extracted <= 0 ? ItemStack.EMPTY : wanted.copyWithCount(extracted);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < 0 || slot >= storage.getSlotCount() ? 0 : storage.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && slot >= 0 && slot < storage.getSlotCount()
                    && storage.insertToSlot(stack, slot, true) > 0;
        }
    }
}
