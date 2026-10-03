package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.DelegatingInventoryStorage;

/**
 * The item inventory one <b>face</b> of an Extension Addon / Wireless Extension Dock offers to the outside
 * world. A face answers for exactly one of two features, read on every single call:
 * <ul>
 *     <li>its <b>transfer mode</b> (the Extension Transfer page, see {@link TransferFaceModes}): the machine's
 *     whole inventory, where {@link TransferMode#INPUT} accepts items, {@link TransferMode#OUTPUT} offers
 *     them and {@link TransferMode#BOTH} does both,</li>
 *     <li>its <b>proxy binding</b> (the Item Proxy page, see {@link ProxyFaceBindings}): Oritech's own
 *     inventory proxy mechanism, i.e. the machine's inventory with every slot but the bound one answering
 *     "empty".</li>
 * </ul>
 * A face with both configured transfers items - the newer, whole-inventory behaviour wins over the
 * single-slot one; taking the mode away falls back to the binding. A face with neither offers nothing.
 * <p>
 * <b>One object per face, forever.</b> The capability wrapper Oritech builds for us
 * ({@code NeoforgeItemApiImpl.ContainerStorageWrapper}) caches the storage a face answers with, so this
 * class must never be replaced by a different instance; everything that can change - the mode, the binding,
 * the machine - is therefore resolved fresh inside each call instead of being cached in a field.
 * <p>
 * <b>Fail safe:</b> the machine inventory is resolved on every call too, so a machine that was broken,
 * unloaded, replaced or never present simply yields {@code null} - the
 * {@link DelegatingInventoryStorage} then reports zero slots and accepts/offers nothing instead of
 * crashing.
 * <p>
 * The class also carries the two helpers the <b>automation</b> of a face is built from - looking a
 * neighbour's inventory up ({@link #storageAt}) and moving one stack between two storages ({@link #move}).
 * They need no face of their own, which is what lets the placed transfer addon move items between a machine
 * and the containers around the extender it hangs on (see
 * {@code TransferAddonBlockEntity#serverTickTransfer}) with the very same logic a face of this block uses.
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
     * The inventory storage of the machine this block works on, or {@code null} while there is none.
     * <p>
     * This is the same call Oritech's own inventory proxy addon makes
     * ({@code getInventoryStorage(null)} on the machine's block entity), i.e. exactly what an Oritech item
     * pipe sees - and it follows the block's own idea of "the machine I work on", so a wireless dock
     * transfers to the machine it is linked to and a wired addon to the machine that claimed it.
     */
    @Nullable
    public static ItemApi.InventoryStorage machineStorage(BlockEntity owner) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return null;
        return machineStorageAt(addon.getLevel(), addon.connectedMachinePos());
    }

    /**
     * The inventory storage of the block at {@code machinePos} - the machine an addon works on - or
     * {@code null} while there is none (no position, unloaded chunk, block entity gone, a block without an
     * inventory).
     * <p>
     * It exists next to {@link #machineStorage(BlockEntity)} because the machine of a placed transfer addon
     * is not the machine of the block that holds the plugin: the plugin is asked for the extender's
     * controller position instead of its own, and this is where that position is turned into a storage.
     */
    @Nullable
    public static ItemApi.InventoryStorage machineStorageAt(@Nullable Level level, @Nullable BlockPos machinePos) {
        if (level == null || machinePos == null || !level.isLoaded(machinePos)) return null;

        return level.getBlockEntity(machinePos) instanceof ItemApi.BlockProvider provider
                ? provider.getInventoryStorage(null)
                : null;
    }

    /**
     * The item storage of the block outside one face of the block at {@code pos} - the container automation
     * moves items with - or {@code null} while there is none (air, a machine without an inventory, an
     * unloaded chunk). The side is asked for as the neighbour's own face pointing back at that block, which
     * is what Oritech's own item pipe asks with (see {@code ItemPipeInterfaceEntity}, which does
     * {@code ItemApi.BLOCK.find(world, sourcePos, direction)} with the direction pointing from the neighbour
     * back at the pipe).
     */
    @Nullable
    public static ItemApi.InventoryStorage storageAt(@Nullable Level level, BlockPos pos, Direction face) {
        if (level == null) return null;

        var neighbourPos = pos.relative(face);
        if (!level.isLoaded(neighbourPos)) return null;

        return ItemApi.BLOCK.find(level, neighbourPos, face.getOpposite());
    }

    /**
     * Moves up to {@link #ITEMS_PER_TICK} items of one stack from {@code from} to {@code to}, if the target
     * takes any of it, and stops after that one stack.
     * <p>
     * Oritech's {@code ItemApi.InventoryStorage} has no transaction: both sides are therefore asked first and
     * really moved afterwards. The target is asked how much it would take with a simulated insert, the source
     * with a simulated extract, and only then is exactly that amount taken out and put in. Whatever the target
     * ends up refusing (it can only refuse because something else filled it in between) is handed straight back
     * to the source, so no item can be lost even then.
     */
    public static void move(ItemApi.InventoryStorage from, ItemApi.InventoryStorage to) {
        if (!from.supportsExtraction() || !to.supportsInsertion()) return;

        for (int slot = 0; slot < from.getSlotCount(); slot++) {
            var stack = from.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            // dry run: how much would the target take of this stack, and how much can the source give?
            var offered = stack.copyWithCount(Math.min(stack.getCount(), ITEMS_PER_TICK));
            var wanted = to.insert(offered, true);
            if (wanted <= 0) continue;

            var takeable = from.extractFromSlot(offered.copyWithCount(wanted), slot, true);
            if (takeable <= 0) continue;

            var extracted = from.extractFromSlot(offered.copyWithCount(takeable), slot, false);
            if (extracted <= 0) continue;

            var inserted = to.insert(offered.copyWithCount(extracted), false);
            if (inserted < extracted) {
                // the target changed its mind in between: give the refused items back to where they came from
                from.insert(offered.copyWithCount(extracted - inserted), false);
            }

            return;
        }
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
     * True while this face offers the machine's inventory at all: one of the two features configures it.
     * That the machine can be resolved as well is what the parent's own {@code canUseBackend} check adds, so
     * every call below still answers "nothing" while the machine is gone.
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
    public int getSlotCount() {
        return offers(owner, face) ? super.getSlotCount() : 0;
    }

    /** Index of the slot the proxy behaviour works on, or {@code -1} while the face transfers whole stacks. */
    private int proxyIndex() {
        if (transfers()) return -1;
        var configured = proxySlot();
        return configured == null ? -1 : configured;
    }

    /** True while the given slot may be read or written through this face. */
    private boolean covers(int index) {
        if (!offers(owner, face)) return false;
        var proxy = proxyIndex();
        return proxy < 0 || index == proxy;
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
     * The stack of one machine slot, or nothing while this face does not cover it: a proxy face covers only
     * its bound slot, a transfer face the whole inventory.
     */
    @Override
    public ItemStack getStackInSlot(int index) {
        return covers(index) ? super.getStackInSlot(index) : ItemStack.EMPTY;
    }

    /** Slot limit of one machine slot, or {@code 0} while this face does not cover it; see {@link #covers}. */
    @Override
    public int getSlotLimit(int index) {
        return covers(index) ? super.getSlotLimit(index) : 0;
    }

    @Override
    public int insert(ItemStack inserted, boolean simulate) {
        if (!allowsInsert()) return 0;
        var proxy = proxyIndex();
        return proxy < 0 ? super.insert(inserted, simulate) : super.insertToSlot(inserted, proxy, simulate);
    }

    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        if (!allowsExtract()) return 0;
        var proxy = proxyIndex();
        return proxy < 0 ? super.extract(extracted, simulate) : super.extractFromSlot(extracted, proxy, simulate);
    }

    @Override
    public int insertToSlot(ItemStack inserted, int index, boolean simulate) {
        if (!allowsInsert() || !covers(index)) return 0;
        return super.insertToSlot(inserted, index, simulate);
    }

    @Override
    public int extractFromSlot(ItemStack extracted, int index, boolean simulate) {
        if (!allowsExtract() || !covers(index)) return 0;
        return super.extractFromSlot(extracted, index, simulate);
    }

    /**
     * A pipe that pulled an item out or pushed one in asks us to persist the change. The backing storage
     * belongs to the machine, whose own handler already marks itself dirty, so only our block entity has
     * to be told that the face was used (it holds no items itself, but this is where a future state would
     * be flushed).
     */
    @Override
    public void update() {
        if (owner instanceof ExtensionAddonBlockEntity addon) addon.onProxyUsed();
    }
}
