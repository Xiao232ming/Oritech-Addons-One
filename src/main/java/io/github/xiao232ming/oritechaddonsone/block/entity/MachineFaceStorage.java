package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.api.transfer.item.ItemProvider;
import rearth.oritech.util.MachineAddonController;

/**
 * The item inventory one <b>face</b> of an Extension Addon / Wireless Extension Dock offers to the outside
 * world. A face answers for exactly one of two features, read on every single call:
 * <ul>
 *     <li>its <b>transfer mode</b> (the Extension Transfer page, see {@link TransferFaceModes}): the machine's
 *     whole inventory, where {@link TransferMode#INPUT} accepts items, {@link TransferMode#OUTPUT} offers
 *     them and {@link TransferMode#BOTH} does both - each of them restricted to the machine's own slot roles
 *     (see {@link MachineSlotRoles}), so an INPUT face fills the machine's input slots and only those, an
 *     OUTPUT face empties its output slots and only those, and BOTH never inserts into an output slot nor
 *     extracts from an input one,</li>
 *     <li>its <b>proxy binding</b> (the Item Proxy page, see {@code ProxyFaceBindings}): Oritech's own
 *     inventory proxy mechanism, i.e. the machine's inventory with every slot but the bound one answering
 *     "empty".</li>
 * </ul>
 * A face with both configured transfers items - the newer, whole-inventory behaviour wins over the
 * single-slot one; taking the mode away falls back to the binding. A face with neither offers nothing.
 * <p>
 * <b>One object per face, forever.</b> NeoForge caches the handler a face answers with, so this class must
 * never be replaced by a different instance; everything that can change - the mode, the binding, the machine
 * - is therefore resolved fresh inside each call instead of being cached in a field.
 * <p>
 * <b>Fail safe:</b> the machine inventory is resolved on every call too, so a machine that was broken,
 * unloaded, replaced or never present simply yields {@code null} - this handler then reports zero slots and
 * accepts/offers nothing instead of crashing.
 * <p>
 * The class also carries the two helpers the <b>automation</b> of a face is built from - looking a
 * neighbour's inventory up ({@link #storageAt}) and moving one stack between two handlers ({@link #move}).
 * They are static and need no face of their own, which is what lets the placed transfer addon move items
 * between a machine and the containers around the extender it hangs on (see
 * {@code ExtensionTransferAddonBlockEntity#serverTickTransfer}) with the very same logic a face of this block uses.
 */
public final class MachineFaceStorage extends DelegatingInventoryStorage {

    /**
     * How many items automation moves per face and tick. Eight is a fast but unremarkable rate - a hopper
     * moves one item, an Oritech item pipe up to a stack - and it keeps a face that is fed and emptied at the
     * same time from starving its own other direction.
     */
    public static final int ITEMS_PER_TICK = 8;

    private final BlockEntity owner;
    /** Face this storage belongs to, resolved against the block entity on every call. */
    private final Direction face;

    public MachineFaceStorage(BlockEntity owner, Direction face) {
        super(() -> machineStorage(owner), () -> offers(owner, face));
        this.owner = owner;
        this.face = face;
    }

    /**
     * The inventory handler of the machine this block works on, or {@code null} while there is none.
     * <p>
     * This is the same call Oritech's own inventory proxy addon makes ({@code getInventoryForAddon()} first,
     * then {@code getItemLookup(null)} on the machine), i.e. exactly what an Oritech item pipe sees - and it
     * follows the block's own idea of "the machine I work on", so a wireless dock transfers to the machine it
     * is linked to and a wired addon to the machine that claimed it.
     */
    @Nullable
    public static ResourceHandler<ItemResource> machineStorage(BlockEntity owner) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return null;
        return machineStorageAt(addon.getLevel(), addon.connectedMachinePos());
    }

    /**
     * The inventory handler of the block at {@code machinePos} - the machine an addon works on - or
     * {@code null} while there is none (no position, unloaded chunk, block entity gone, a block without an
     * inventory).
     * <p>
     * It exists next to {@link #machineStorage(BlockEntity)} because the machine of a placed transfer addon
     * is not the machine of the block that holds the plugin: the plugin is asked for the extender's
     * controller position instead of its own, and this is where that position is turned into a handler.
     */
    @Nullable
    public static ResourceHandler<ItemResource> machineStorageAt(@Nullable Level level, @Nullable BlockPos machinePos) {
        if (level == null || machinePos == null || !level.isLoaded(machinePos)) return null;

        var machine = level.getBlockEntity(machinePos);
        if (machine == null) return null;

        // the machine's own addon inventory is what Oritech's proxy uses first; the machine's item lookup is
        // the fallback for the few machines that do not offer one
        if (machine instanceof MachineAddonController controller) return controller.getInventoryForAddon();
        return machine instanceof ItemProvider provider ? provider.getItemLookup(null) : null;
    }

    /**
     * Position of the machine an addon works on, or {@code null} while this block does not work on one. The
     * one place the machine position is derived from the addon, so the inventory
     * ({@link #machineStorage(BlockEntity)}) and the slot roles of that inventory
     * ({@link MachineSlotRoles}) can never be looked up for two different machines.
     */
    @Nullable
    static BlockPos machinePos(BlockEntity owner) {
        return owner instanceof ExtensionAddonBlockEntity addon ? addon.connectedMachinePos() : null;
    }

    /**
     * Level the machine an addon works on lives in, or {@code null} while this block does not work on one.
     * A placed transfer addon is in the same world as the machine behind its extender, so the addon's own
     * level is the machine's level in both cases.
     */
    @Nullable
    static Level machineLevel(BlockEntity owner) {
        return owner instanceof ExtensionAddonBlockEntity addon ? addon.getLevel() : null;
    }

    /**
     * The item storage of the block outside one face of the block at {@code pos} - the container automation
     * moves items with - or {@code null} while there is none (air, a machine without an inventory, an
     * unloaded chunk). The side is asked for as the neighbour's own face pointing back at that block, which
     * is what a pipe would ask with.
     */
    @Nullable
    public static ResourceHandler<ItemResource> storageAt(@Nullable Level level, BlockPos pos, Direction face) {
        if (level == null) return null;

        var neighbourPos = pos.relative(face);
        if (!level.isLoaded(neighbourPos)) return null;

        var state = level.getBlockState(neighbourPos);
        var blockEntity = level.getBlockEntity(neighbourPos);
        return level.getCapability(Capabilities.Item.BLOCK, neighbourPos, state, blockEntity, face.getOpposite());
    }

    /**
     * Moves up to {@link #ITEMS_PER_TICK} items of one stack from {@code from} to {@code to}, if the target
     * takes any of it, and stops after that one stack. It is the {@link #move(ResourceHandler, BlockEntity,
     * ResourceHandler, BlockEntity) four argument form} without owners, i.e. for two handlers whose machines
     * are not known here.
     */
    public static void move(ResourceHandler<ItemResource> from, ResourceHandler<ItemResource> to) {
        move(from, null, to, null);
    }

    /**
     * The full form of {@link #move(ResourceHandler, ResourceHandler)}, for a caller that knows which
     * machines it is moving between - the automation of a face, which holds the machine and the container
     * next to their handlers.
     * <p>
     * Both halves run in a transaction, so nothing can be lost: the target is first asked how much it would
     * take (that transaction is closed without committing, i.e. rolled back), and only then is exactly that
     * amount extracted and re-inserted. The step is committed only while the two amounts match; if the
     * inventory changed in between (another pipe, a target that filled up) the whole step rolls back instead
     * of dropping items on the floor.
     * <p>
     * The owners are what make the machine's slot roles ({@link MachineSlotRoles}) enforceable here: an item
     * is only taken out of a slot the source may give away and only put into a slot the target may take it
     * in - never out of a machine's input slot and never into one of its output slots. A {@code null} owner
     * is a container with no roles to respect: a chest, a pipe, another mod's inventory.
     */
    public static void move(ResourceHandler<ItemResource> from, @Nullable BlockEntity fromOwner,
            ResourceHandler<ItemResource> to, @Nullable BlockEntity toOwner) {
        for (int slot = 0; slot < from.size(); slot++) {
            if (!MachineSlotRoles.allowsExtractAt(fromOwner, slot)) continue;

            var resource = from.getResource(slot);
            if (resource.isEmpty()) continue;

            var wanted = amountAccepted(to, toOwner, resource, ITEMS_PER_TICK);
            if (wanted <= 0) continue;

            try (var transaction = Transaction.openRoot()) {
                int extracted = from.extract(slot, resource, wanted, transaction);
                if (extracted <= 0) continue;

                int inserted = insertInto(to, toOwner, resource, extracted, transaction);
                if (inserted != extracted) continue;

                transaction.commit();
            }

            return;
        }
    }

    /**
     * How much of {@code resource} the target would take right now, counting only the slots a machine target
     * may take it in. The probe runs in a transaction of its own, so the target is left untouched - the same
     * dry run {@link #move} has always made, just with the machine's slot roles respected.
     */
    private static int amountAccepted(ResourceHandler<ItemResource> to, @Nullable BlockEntity toOwner,
            ItemResource resource, int amount) {
        try (var probe = Transaction.openRoot()) {
            return insertInto(to, toOwner, resource, amount, probe);
        }
    }

    /**
     * Inserts into the target, skipping the slots Oritech reserved for its outputs while the target is a
     * machine whose roles are known. A target that is not a machine - a chest, a pipe, another mod's
     * inventory - has no roles and is asked exactly as before.
     */
    private static int insertInto(ResourceHandler<ItemResource> to, @Nullable BlockEntity toOwner,
            ItemResource resource, int amount, TransactionContext transaction) {
        var roles = toOwner == null ? null : MachineSlotRoles.of(toOwner);
        if (roles == null) return to.insert(resource, amount, transaction);

        var inserted = 0;
        for (var slot = 0; slot < roles.length && inserted < amount; slot++) {
            if (!MachineSlotRoles.allowsInsertAt(toOwner, slot)) continue;
            inserted += to.insert(slot, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    /** The mode this face transfers with, or {@link TransferMode#NONE} while it transfers nothing. */
    private TransferMode transferMode() {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return TransferMode.NONE;
        if (!addon.canTransferItems()) return TransferMode.NONE;
        return addon.transferModes().modeOf(face);
    }

    /** Slot this face proxies, or {@code null} while the proxy feature does not configure it. */
    @Nullable
    private Integer proxySlot() {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return null;
        if (!addon.canProxyItems()) return null;
        return addon.proxyFaces().slotOf(face);
    }

    /**
     * True while this face offers the machine's inventory at all: one of the two features configures it and
     * the machine can be resolved.
     */
    private static boolean offers(BlockEntity owner, Direction face) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return false;
        if (addon.transferModes().isConfigured(face) && addon.canTransferItems()) return true;
        if (addon.proxyFaces().isConfigured(face) && addon.canProxyItems()) return true;
        return false;
    }

    /** True while this face moves whole inventories with a mode instead of proxying a single slot. */
    private boolean transfers() {
        return transferMode() != TransferMode.NONE;
    }

    /**
     * Slot count of the machine inventory. It is the machine's own slot count in both behaviours - like
     * Oritech's inventory proxy addon - so a pipe sees a normal inventory; in the proxy behaviour only the
     * bound slot then accepts or offers items.
     */
    @Override
    public int size() {
        return offers(owner, face) && machineStorage(owner) != null ? super.size() : 0;
    }

    /** Index of the slot the proxy behaviour works on, or {@code -1} while the face transfers whole stacks. */
    private int proxyIndex() {
        if (transfers()) return -1;
        var configured = proxySlot();
        return configured == null ? -1 : configured;
    }

    /** True while the given slot may be read or written through this face. */
    private boolean covers(int index) {
        if (!offers(owner, face) || machineStorage(owner) == null) return false;
        var proxy = proxyIndex();
        return proxy < 0 || index == proxy;
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
     * Indexed insert of a pipe or a hopper. Two gates run here: the face's mode (an OUTPUT face takes
     * nothing) and the machine's slot roles ({@link MachineSlotRoles}) - an INPUT face fills the machine's
     * input slots and never a slot Oritech reserved for its results. The item proxy behaviour, which has no
     * mode, is only gated by the role of its one bound slot, which is what keeps a face bound to an output
     * slot from pushing items into it.
     */
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert() || !covers(index) || !MachineSlotRoles.allowsInsertAt(owner, index)) return 0;
        return super.insert(index, resource, amount, transaction);
    }

    /**
     * Index-free insert of a pipe or a hopper. It is answered by the indexed overload so the role gate
     * applies to both - a caller that only knows the handler must not be able to fill an output slot - and
     * it walks the slots itself, because the machine's roles decide which ones may be filled at all.
     */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsInsert()) return 0;

        var proxy = proxyIndex();
        if (proxy >= 0) return insert(proxy, resource, amount, transaction);

        var supported = machineStorage(owner);
        if (supported == null) return 0;

        var inserted = 0;
        for (var index = 0; index < supported.size() && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    /** Indexed extract; gated by the mode and the machine's slot roles - see {@link #insert(int, ItemResource, int, TransactionContext)}. */
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsExtract() || !covers(index) || !MachineSlotRoles.allowsExtractAt(owner, index)) return 0;
        return super.extract(index, resource, amount, transaction);
    }

    /**
     * Index-free extract, answered by the indexed overload. It starts at the first slot the machine's roles
     * let go of, so an OUTPUT face really offers the machine's products and never its ingredients.
     */
    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        if (!allowsExtract()) return 0;

        var proxy = proxyIndex();
        if (proxy >= 0) return extract(proxy, resource, amount, transaction);

        var supported = machineStorage(owner);
        if (supported == null) return 0;

        var extracted = 0;
        for (var index = 0; index < supported.size() && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }

    /** True while items may be put into the machine through this face. */
    private boolean allowsInsert() {
        var mode = transferMode();
        // no transfer mode means the proxy behaviour, which is bidirectional on its one slot
        return mode == TransferMode.NONE || mode.allowsInsert();
    }

    /** True while items may be taken out of the machine through this face. */
    private boolean allowsExtract() {
        var mode = transferMode();
        return mode == TransferMode.NONE || mode.allowsExtract();
    }
}
