package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockContent;

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
 * <b>The two views share one configuration.</b> The mode of a face is the plugin's
 * ({@link TransferFaceModes}), read on every single call, so the GUI, the automation and this handler can
 * never disagree; nothing is cached here but the identity of the handler itself.
 * <p>
 * <b>One object per position and face, forever.</b> NeoForge caches the handler a position and face answer
 * with (and {@code BlockCapabilityCache} keeps that answer until the level invalidates the position), so the
 * instance must never be replaced. Everything that can change - which plugin hangs where, its modes, the
 * machine behind the extender - is therefore resolved fresh inside each call; the cache exists for identity,
 * not for staleness. It is keyed by the position and grows only where a pipe really asked an extender with a
 * plugin on it, which keeps it tiny in practice.
 * <p>
 * <b>Fail safe:</b> a face without a plugin, a plugin that was taken away, a face with no mode, an extender no
 * machine has claimed and an unloaded chunk all resolve to "no inventory" - this handler then reports zero
 * slots and accepts or offers nothing instead of crashing, exactly like {@link MachineFaceStorage}.
 */
public final class ExtenderFaceStorage extends DelegatingInventoryStorage {

    /**
     * Handler of every extender position and face that was ever asked, indexed by
     * {@link Direction#ordinal()} - see the class comment for why the objects are kept.
     */
    private static final Map<BlockPos, ExtenderFaceStorage[]> HANDLERS = new ConcurrentHashMap<>();

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
     * extender has no inventory of its own, only faces a plugin configured - and while no transfer plugin
     * hangs on the extender or the plugin does not configure this face. {@code null} is also what the
     * capability provider answers with, so a pipe sees the extender as "nothing here" until a plugin really
     * offers something on that face.
     * <p>
     * Only extender faces can answer this way: the plugin itself offers an empty inventory, so the machine is
     * reachable at exactly one place, and the same items cannot be found at two.
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
        return created;
    }

    /**
     * The transfer plugin that hangs on the extender at {@code face}, i.e. the plugin whose host is the
     * extender and whose attachment faces this very face of it, or {@code null} while there is none.
     * <p>
     * The plugin is looked up through the world on every call and not remembered: a plugin can be broken or
     * placed at any time, and NeoForge's own invalidation (see {@code TransferAddonBlockEntity}) is what makes
     * a pipe ask again - the answer itself has to be correct whenever it is asked.
     */
    @Nullable
    private static TransferAddonBlockEntity pluginAt(AddonBlockEntity extender, Direction face) {
        var level = extender.getLevel();
        if (level == null || level.isClientSide()) return null;

        var pluginPos = extender.getBlockPos().relative(face);
        if (!level.isLoaded(pluginPos)) return null;
        if (!(level.getBlockEntity(pluginPos) instanceof TransferAddonBlockEntity plugin)) return null;

        // The plugin has to hang on this very extender: a plugin that stands on a wall somewhere else must
        // not turn the extender's faces into a way into a machine.
        return TransferAddonBlock.attachedFace(plugin.getBlockState()) == face ? plugin : null;
    }

    /**
     * The mode the plugin that hangs on the extender at {@code face} transfers with, or
     * {@link TransferMode#NONE} while nothing is configurable there. Read through the plugin, which is the
     * owner of the modes - so a face of the extender and the face of an Extension Addon at the same place do
     * exactly the same thing.
     */
    private static TransferMode transferMode(AddonBlockEntity extender, Direction face) {
        var plugin = pluginAt(extender, face);
        if (plugin == null || !plugin.canTransferItems()) return TransferMode.NONE;
        return plugin.transferModes().modeOf(face);
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

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert() || !covers(index)) return 0;
        return super.insert(index, resource, amount, transaction);
    }

    /**
     * Index-free insert of a pipe or a hopper. The mode gates it here as well as in the indexed overload, so
     * a caller that only knows the handler cannot fill the machine through an output-only face.
     */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        return allowsInsert() ? super.insert(resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsExtract() || !covers(index)) return 0;
        return super.extract(index, resource, amount, transaction);
    }

    /** Index-free extract, gated exactly like {@link #extract(int, ItemResource, int, TransactionContext)}. */
    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        return allowsExtract() ? super.extract(resource, amount, transaction) : 0;
    }
}
