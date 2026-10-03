package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockContent;

import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;

/**
 * The item inventory one <b>face of Oritech's machine extender</b> offers to the outside world while a placed
 * transfer plugin hangs on that extender - the pipe side of {@link TransferAddonBlockEntity}.
 * <p>
 * A placed transfer plugin already moves the machine's items on its own (its per-face automation) and can be
 * configured through its own screen, but nothing outside the plugin could reach the machine through the
 * extender: Oritech registers no item capability for its shared addon block entity type (see
 * {@code OritechAddonsOne#registerExtenderItemCapabilities}), so the extender answered "no inventory" to
 * every pipe and hopper. This storage closes that gap without touching Oritech: one storage per extender
 * face, registered for the extender's block entity type, so a face whose mode is set behaves exactly like
 * the same face of an Extension Addon - a pipe inserts into the machine while the mode allows it and
 * extracts out of it while the mode allows it.
 * <p>
 * <b>The plugin configures the whole extender, not one face.</b> The transfer page is opened on the plugin,
 * but what it sets is a mode per face of the extender ({@link TransferFaceModes}), so a face is a connection
 * when <em>that</em> face has a mode - never because the plugin happens to hang on it. The face the plugin
 * occupies is therefore never a connection: a pipe cannot stand there, and the plugin's own faces answer
 * empty (see {@link TransferAddonBlockEntity#getInventoryStorage(Direction)}).
 * <p>
 * <b>The two views share one configuration.</b> The mode of a face is read on every single call, so the GUI,
 * the automation and this storage can never disagree; nothing is cached here but the identity of the storage
 * itself.
 * <p>
 * <b>One object per position and face, forever.</b> The capability wrapper Oritech builds for us
 * ({@code NeoforgeItemApiImpl.ContainerStorageWrapper}) holds the storage a face answered with, and the
 * {@code BlockLookupCache} of Oritech's own pipes keeps that answer until the level invalidates the position,
 * so the instance must never be replaced. Everything that can change - which plugin hangs where, its modes,
 * the machine behind the extender - is therefore resolved fresh inside each call; the cache exists for
 * identity, not for staleness. It is keyed by the position and grows only where a pipe really asked an
 * extender with a plugin on it, which keeps it tiny in practice.
 * <p>
 * <b>Fail safe:</b> a face with no mode, an extender no plugin hangs on, an extender no machine has claimed
 * and an unloaded chunk all resolve to "no inventory" - this storage then reports zero slots and accepts or
 * offers nothing instead of crashing, exactly like {@link MachineFaceStorage}.
 */
public final class ExtenderFaceStorage extends DelegatingInventoryStorage {

    /**
     * Storage of every extender position and face that was ever asked, indexed by
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
     * The storage of one face of the extender at {@code extender}, or {@code null} while that face is not an
     * item connection at all. That is the case while the extender is asked without a side - a machine
     * extender has no inventory of its own, only faces a plugin configured - and while the queried face has
     * no mode, which includes every face of an extender no transfer plugin hangs on. {@code null} is also
     * what the capability provider answers with, so a pipe sees the extender as "nothing here" until a plugin
     * really offers something on that face.
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
     * The storage of one extender face as the {@code IItemHandler} the NeoForge item capability of that face
     * has to answer with, or {@code null} while the face is no connection (see
     * {@link #handlerAt(AddonBlockEntity, Direction)}).
     * <p>
     * It is the answer of the registration in {@code OritechAddonsOne#registerExtenderItemCapabilities} and
     * exists so that the adapter below stays private: the registration is the only caller.
     */
    @Nullable
    public static IItemHandler handlerFor(AddonBlockEntity extender, @Nullable Direction face) {
        var storage = handlerAt(extender, face);
        return storage == null ? null : new NeoForgeHandler(storage);
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
        return TransferAddonBlock.attachedFace(plugin.getBlockState()) == side.getOpposite() ? plugin : null;
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
     */
    private static TransferMode transferMode(AddonBlockEntity extender, Direction face) {
        var level = extender.getLevel();
        if (level == null || level.isClientSide()) return TransferMode.NONE;

        for (var side : Direction.values()) {
            var plugin = pluginAt(extender, level, side);
            if (plugin == null || !plugin.canTransferItems()) continue;

            var mode = plugin.transferModes().modeOf(face);
            if (mode != TransferMode.NONE) return mode;
        }

        return TransferMode.NONE;
    }

    /**
     * The inventory of the machine the extender is attached to, or {@code null} while the face is not
     * configured or that machine cannot be resolved.
     * <p>
     * The machine is the extender's controller position ({@link TransferAddonBlockEntity#attachedMachinePos}
     * answers the same question for the plugin) and the storage is the machine's own inventory, so this is
     * exactly what Oritech's own item pipes see when they look at the machine.
     */
    @Nullable
    private static ItemApi.InventoryStorage machineStorage(AddonBlockEntity extender, Direction face) {
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
     * True while a pipe may push into this face at all. An output-only face answers {@code false}, which is
     * what tells a pipe not to try, and the parent already answers {@code false} while the machine is gone.
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
     * Slot count of the machine inventory. It is the machine's own slot count - like Oritech's inventory
     * proxy addon - so a pipe sees a normal inventory, and {@code 0} while there is no machine to reach.
     */
    @Override
    public int getSlotCount() {
        return machineStorage(extender, face) == null ? 0 : super.getSlotCount();
    }

    /** True while the given slot belongs to the machine inventory this face reaches into. */
    private boolean covers(int index) {
        return index >= 0 && index < getSlotCount();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return covers(index) ? super.getStackInSlot(index) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int index) {
        return covers(index) ? super.getSlotLimit(index) : 0;
    }

    @Override
    public int insert(ItemStack inserted, boolean simulate) {
        return allowsInsert() ? super.insert(inserted, simulate) : 0;
    }

    @Override
    public int insertToSlot(ItemStack inserted, int index, boolean simulate) {
        if (!allowsInsert() || !covers(index)) return 0;
        return super.insertToSlot(inserted, index, simulate);
    }

    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        return allowsExtract() ? super.extract(extracted, simulate) : 0;
    }

    @Override
    public int extractFromSlot(ItemStack extracted, int index, boolean simulate) {
        if (!allowsExtract() || !covers(index)) return 0;
        return super.extractFromSlot(extracted, index, simulate);
    }

    /**
     * Turns the storage of one extender face into the {@code IItemHandler} a NeoForge item capability has to
     * answer with - "wrapper" in the sense of the whole chain: the capability hands it to a pipe or a hopper,
     * it forwards every question to {@link ExtenderFaceStorage}, and that storage forwards it to the machine
     * behind the extender.
     * <p>
     * Oritech's own bridge wraps an {@link ItemApi.InventoryStorage} in its
     * {@code NeoforgeItemApiImpl.ContainerStorageWrapper}, but that class only ever leaves Oritech as an
     * {@code ItemApi.InventoryStorage} again, so it cannot be the answer of the capability itself. This
     * adapter is the same mapping with the public NeoForge interface on the outside; the mode gating lives in
     * the storage it wraps, so nothing here has to know about transfer modes.
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
