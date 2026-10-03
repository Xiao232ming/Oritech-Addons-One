package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;

/**
 * The item inventory one <b>face of Oritech's machine extender</b> offers to the outside world while a placed
 * transfer plugin hangs on that extender - the pipe side of {@link TransferAddonBlockEntity}.
 * <p>
 * A placed transfer plugin already moves the machine's items on its own (its per-face automation) and can be
 * configured through its own screen, but nothing outside the plugin could reach the machine through the
 * extender: Oritech registers no {@code Capabilities.Item.BLOCK} provider for its shared addon block entity
 * type (see {@code OritechAddonsOne#registerCapabilities}), so the extender answered "no inventory" to every
 * pipe and hopper. This handler closes that gap without touching Oritech: one handler per extender face,
 * registered for the extender's block entity type, so a face whose mode is set behaves exactly like the same
 * face of an Extension Addon - a pipe inserts into the machine while the mode allows it and extracts out of it
 * while the mode allows it.
 * <p>
 * <b>The plugin configures the whole extender, not one face.</b> The transfer page is opened on the plugin,
 * but what it sets is a mode per face of the extender ({@link TransferFaceModes}), so a face is a connection
 * when <em>that</em> face has a mode - never because the plugin happens to hang on it. The face the plugin
 * occupies is therefore never a connection: a pipe cannot stand there, the mode is refused for it
 * (see {@link TransferAddonBlockEntity#setTransferConfig}) and the plugin's own faces answer empty
 * (see {@link TransferAddonBlockEntity#getItemLookup}).
 * <p>
 * <b>The side is read the way Oritech's pipes ask it.</b> Oritech checks a neighbour with
 * {@code level.getCapability(Capabilities.Item.BLOCK, neighbourPos, direction.getOpposite())}, i.e. the
 * context is the direction <em>from the block that answers towards the block that asked</em> - so a pipe
 * east of the extender asks with {@link Direction#EAST} and stands on the face this handler must answer for.
 * A hopper does the same with its own facing. There is no translation to do here; the side is already the
 * extender's face.
 * <p>
 * <b>The two views share one configuration.</b> The mode of a face is read on every single call, so the GUI,
 * the automation and this handler can never disagree; nothing is cached here but the identity of the handler
 * itself.
 * <p>
 * <b>One object per position and face, forever.</b> NeoForge caches the handler a position and face answer
 * with (and {@code BlockCapabilityCache} keeps that answer until the level invalidates the position), so the
 * instance must never be replaced. Everything that can change - which plugin hangs where, its modes, the
 * machine behind the extender - is therefore resolved fresh inside each call; the cache exists for identity,
 * not for staleness. It is keyed by the position and grows only where a pipe really asked an extender with a
 * plugin on it, which keeps it tiny in practice.
 * <p>
 * <b>Fail safe:</b> a face with no mode, an extender no plugin hangs on, an extender no machine has claimed
 * and an unloaded chunk all resolve to "no inventory" - this handler then reports zero slots and accepts or
 * offers nothing instead of crashing, exactly like {@link MachineFaceStorage}.
 */
public final class ExtenderFaceStorage extends DelegatingInventoryStorage {

    /**
     * Handler of every extender position and face that was ever asked, indexed by
     * {@link Direction#ordinal()} - see the class comment for why the objects are kept.
     * <p>
     * Bounded in practice by "one entry per extender position a pipe ever asked about", and the entry is a
     * fixed six element array, so a server that has seen <i>n</i> extenders asked about holds six references
     * of <i>n</i>. It is deliberately not a cache of answers - the answers are re-resolved on every call, so
     * a stale entry can never be seen; the map only preserves the identity of the handler objects.
     */
    private static final Map<BlockPos, ExtenderFaceStorage[]> HANDLERS = new ConcurrentHashMap<>();

    /**
     * How many nanoseconds have to pass between two "this face answered nothing" diagnostic lines. A pipe
     * asks its neighbours again every tick, so without a limit a face that is deliberately not configured
     * would write twenty lines a second and drown everything else in the log. One line per second and
     * position is enough to show the mode lookup is really being asked and what it sees.
     */
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 1_000_000_000L;

    /**
     * Last time the diagnostic line of a position was written, per position. Keyed by position only - and
     * not by position and face - so an extender cannot flood the log even while a pipe walks all six faces.
     * Entries are never removed, which is bounded by the number of extenders ever asked about, exactly like
     * {@link #HANDLERS}.
     */
    private static final Map<BlockPos, AtomicLong> LAST_DIAGNOSTIC = new ConcurrentHashMap<>();

    private final AddonBlockEntity extender;
    /** Face of the extender this storage belongs to, resolved against the block entity on every call. */
    private final Direction face;

    private ExtenderFaceStorage(AddonBlockEntity extender, Direction face) {
        super(() -> machineStorage(extender, face), () -> transferMode(extender, face) != TransferMode.NONE);
        this.extender = extender;
        this.face = face;
    }

    /**
     * The handler of one face of the extender at {@code extender}, or {@code null} while that face is not an
     * item connection at all. That is the case while the extender is asked without a side - a machine
     * extender has no inventory of its own, only faces a plugin configured - and while the queried face has
     * no mode, which includes every face of an extender no transfer plugin hangs on. {@code null} is also
     * what the capability provider answers with, so a pipe sees the extender as "nothing here" until a plugin
     * really offers something on that face.
     * <p>
     * Only extender faces can answer this way: the plugin itself offers an empty inventory, so the machine is
     * reachable at exactly one place, and the same items cannot be found at two.
     * <p>
     * The diagnostic line is the only way to see this provider from outside the game: the provider itself
     * logs that it was called, and this one says what the mode lookup answered and which plugin it saw on
     * the extender. It is written at most once a second per extender position (see
     * {@link #DIAGNOSTIC_INTERVAL_NANOS}) and only while debug logging is on, so a pipe that asks every tick
     * cannot drown the log.
     */
    @Nullable
    public static ExtenderFaceStorage handlerAt(AddonBlockEntity extender, @Nullable Direction face) {
        if (face == null) return null;
        if (transferMode(extender, face) == TransferMode.NONE) return null;

        var handlers = HANDLERS.computeIfAbsent(extender.getBlockPos().immutable(),
                key -> new ExtenderFaceStorage[Direction.values().length]);
        var existing = handlers[face.ordinal()];
        if (existing != null) return existing;

        var created = new ExtenderFaceStorage(extender, face);
        handlers[face.ordinal()] = created;

        OritechAddonsOne.LOGGER.debug("[transfer] capability: extender {} face {} now answers its handler",
                extender.getBlockPos(), face);
        return created;
    }

    /**
     * The mode the given face of the extender transfers with, or {@link TransferMode#NONE} while it transfers
     * nothing.
     * <p>
     * The mode is the <b>plugin's</b>, not the face's own: a placed transfer plugin owns one mode per face of
     * the extender it hangs on (the transfer page configures the extender through it), so this asks the
     * plugin on the extender - whichever face it hangs on - what it was told about {@code face}. That is the
     * whole point of the lookup: the plugin occupies one of the six faces, and the connections are the other
     * five, so a face can only ever be a connection while a plugin elsewhere on the same extender is
     * configured for it.
     * <p>
     * More than one plugin may hang on the same extender. Each of them carries its own modes, and only the
     * plugin a mode was set on knows it, so a face counts as configured when <em>any</em> of them configures
     * it - scanning every plugin and not only the first one is what keeps such an extender working.
     * <p>
     * The face a plugin hangs on is skipped for <em>that</em> plugin: the plugin block occupies it, so no
     * pipe or hopper can ever be there and a mode an older version stored on it must not turn the extender
     * into a connection that leads into the plugin block itself. With several plugins on one extender every
     * plugin therefore still answers for all faces but its own.
     * <p>
     * A face the mode lookup answers {@code NONE} for gets one diagnostic line, which is what tells a
     * "configured face does not connect" report apart from "the provider was never asked": the line names
     * every plugin that was found on the extender, so it also shows whether the plugin the page wrote to is
     * the plugin this lookup really sees.
     */
    private static TransferMode transferMode(AddonBlockEntity extender, Direction face) {
        var level = extender.getLevel();
        if (level == null || level.isClientSide()) return TransferMode.NONE;

        var diagnostic = OritechAddonsOne.LOGGER.isDebugEnabled();
        var found = diagnostic ? new StringBuilder() : null;

        for (var side : Direction.values()) {
            var plugin = pluginAt(extender, level, side);
            if (plugin == null) continue;

            var pluginFace = TransferAddonBlock.attachedFace(plugin.getBlockState());
            if (diagnostic) {
                found.append(' ').append(side).append('=')
                        .append(plugin.canTransferItems() ? "configured" : "not-ready")
                        .append("/hangs-on-").append(pluginFace);
            }
            if (face == pluginFace) continue;
            if (!plugin.canTransferItems()) continue;

            var mode = plugin.transferModes().modeOf(face);
            if (mode != TransferMode.NONE) return mode;
        }

        if (diagnostic) {
            logNothingAnswered(extender.getBlockPos(), face, found.isEmpty() ? " none" : found.toString());
        }
        return TransferMode.NONE;
    }

    /** Writes the "answered nothing" diagnostic line, but at most once a second per extender position. */
    private static void logNothingAnswered(BlockPos pos, Direction face, String plugins) {
        var last = LAST_DIAGNOSTIC.computeIfAbsent(pos.immutable(), ignored -> new AtomicLong(Long.MIN_VALUE));
        var now = System.nanoTime();
        var previous = last.get();

        if (previous != Long.MIN_VALUE && now - previous < DIAGNOSTIC_INTERVAL_NANOS) return;
        if (!last.compareAndSet(previous, now)) return;

        OritechAddonsOne.LOGGER.debug(
                "[transfer] capability: {} face {} answered nothing; plugins on the extender:{}",
                pos, face, plugins);
    }

    /**
     * The transfer plugin hanging on the given face of the extender, i.e. the neighbour whose attachment
     * points back at this very extender, or {@code null} while there is none.
     * <p>
     * The plugin is looked up through the world on every call and not remembered: a plugin can be broken or
     * placed at any time, and NeoForge's own invalidation (see {@code TransferAddonBlockEntity}) is what makes
     * a pipe ask again - the answer itself has to be correct whenever it is asked.
     */
    @Nullable
    private static TransferAddonBlockEntity pluginAt(AddonBlockEntity extender, Level level, Direction side) {
        var pluginPos = extender.getBlockPos().relative(side);
        if (!level.isLoaded(pluginPos)) return null;
        if (!(level.getBlockEntity(pluginPos) instanceof TransferAddonBlockEntity plugin)) return null;

        // The plugin has to hang on this very extender: a plugin that stands on a machine or a wall
        // somewhere else must not turn this extender's faces into a way into a machine.
        // The plugin has to hang on this very extender: a plugin standing on a machine or a wall somewhere
        // else must not turn this extender's faces into a way into a machine. {@code side} runs from the
        // extender to the neighbour and {@code attachedFace} is the extender's own face the plugin hangs on,
        // so the two are the same direction - taking its opposite here asked for the far face and never
        // matched, which left every face of every extender answering "no inventory".
        return TransferAddonBlock.attachedFace(plugin.getBlockState()) == side ? plugin : null;
    }

    /**
     * The inventory of the machine the extender is attached to, or {@code null} while the face is not
     * configured or that machine cannot be resolved.
     * <p>
     * The machine is the extender's controller position ({@link TransferAddonBlockEntity#attachedMachinePos}
     * answers the same question for the plugin) and the handler is the machine's own item lookup, so this is
     * exactly what Oritech's own item pipes see when they look at the machine.
     */
    @Nullable
    private static ResourceHandler<ItemResource> machineStorage(AddonBlockEntity extender, Direction face) {
        if (transferMode(extender, face) == TransferMode.NONE) return null;

        var level = extender.getLevel();
        if (level == null) return null;

        var machinePos = extender.getControllerPos();
        if (machinePos == null || machinePos.equals(extender.getBlockPos())) return null;

        return MachineFaceStorage.machineStorageAt(level, machinePos);
    }

    /**
     * True while items may be put into the machine through this face. The mode names the direction as seen
     * from the machine, so {@link TransferMode#INPUT} and {@link TransferMode#BOTH} open this face for
     * insertion - the same test {@link MachineFaceStorage} makes for a face of an Extension Addon.
     */
    private boolean allowsInsert() {
        return transferMode(extender, face).allowsInsert();
    }

    /** True while items may be taken out of the machine through this face; see {@link #allowsInsert()}. */
    private boolean allowsExtract() {
        return transferMode(extender, face).allowsExtract();
    }

    /**
     * True while this face offers the machine's inventory to the outside world, which is also what the
     * {@linkplain DelegatingInventoryStorage backing storage} gate asks before it reads the machine: the
     * extender is at {@code level} and a plugin really configures this face.
     */
    @Override
    public int size() {
        return machineStorage(extender, face) == null ? 0 : super.size();
    }

    /**
     * The machine block entity this face reaches into, or {@code null} while that machine cannot be resolved.
     * It is the same block {@link #machineStorage(AddonBlockEntity, Direction)} takes the inventory from, so
     * the roles asked of it - see {@link MachineSlotRoles} - always belong to the inventory this face
     * exposes.
     */
    @Nullable
    private BlockEntity machine() {
        var level = extender.getLevel();
        if (level == null) return null;

        var machinePos = extender.getControllerPos();
        if (machinePos == null || machinePos.equals(extender.getBlockPos())) return null;
        if (!level.isLoaded(machinePos)) return null;

        return level.getBlockEntity(machinePos);
    }

    /** True while the given slot belongs to the machine inventory this face reaches into. */
    private boolean covers(int index) {
        return index >= 0 && index < size();
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
     * Indexed insert of a pipe or a hopper: gated by the face's mode (an OUTPUT face takes nothing) and by
     * the machine's slot roles ({@link MachineSlotRoles}) - an INPUT face fills the machine's input slots
     * and never a slot Oritech reserved for its results. The roles are asked of the machine this face
     * reaches into, which is resolved from the extender exactly like the inventory itself.
     */
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert() || !covers(index)) return 0;
        if (!MachineSlotRoles.allowsInsertAt(machine(), index)) return 0;
        return super.insert(index, resource, amount, transaction);
    }

    /**
     * Index-free insert of a pipe or a hopper. The mode gates it here as well as in the indexed overload -
     * so a caller that only knows the handler cannot fill the machine through an output-only face - and the
     * slots are walked here so the machine's roles are respected for a caller that names no slot.
     */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert()) return 0;

        // without a machine block entity there are no roles to respect, and the indexed overload would
        // answer 0 for every slot - the machine's own item lookup decides, exactly as it did before
        var machine = machine();
        if (machine == null) return super.insert(resource, amount, transaction);

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
        if (!MachineSlotRoles.allowsExtractAt(machine(), index)) return 0;
        return super.extract(index, resource, amount, transaction);
    }

    /**
     * Index-free extract, gated exactly like {@link #extract(int, ItemResource, int, TransactionContext)}:
     * an OUTPUT face offers the machine's own output slots and nothing else.
     */
    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsExtract()) return 0;

        var machine = machine();
        if (machine == null) return super.extract(resource, amount, transaction);

        var extracted = 0;
        for (var index = 0; index < super.size() && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }
}
