package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
 */
public final class MachineFaceStorage extends DelegatingInventoryStorage {

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

        var target = addon.connectedMachinePos();
        if (target == null || addon.getLevel() == null || !addon.getLevel().isLoaded(target)) return null;

        return addon.getLevel().getBlockEntity(target) instanceof ItemApi.BlockProvider provider
                ? provider.getInventoryStorage(null)
                : null;
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
