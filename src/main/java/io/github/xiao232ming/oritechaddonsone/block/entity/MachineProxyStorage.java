package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.api.transfer.item.ItemProvider;
import rearth.oritech.util.MachineAddonController;

/**
 * The inventory one <b>face</b> of an Extension Addon / Wireless Extension Dock offers to the outside
 * world: the slot of the machine this block works on that the face was bound to.
 * <p>
 * This is Oritech's own inventory proxy mechanism, applied to our block: Oritech's
 * {@code InventoryProxyAddonBlockEntity} implements {@code ItemProvider} and reports a
 * {@link DelegatingInventoryStorage} that forwards to the machine it is attached to while answering "0"
 * for every slot but its configured one. On 26.1.2 an inventory is NeoForge's
 * {@link ResourceHandler} of {@link ItemResource}, so a pipe, a hopper or another mod asking this block
 * for its item handler sees the machine's slots and can only move items in and out of the bound one.
 * <p>
 * <b>Fail safe:</b> the machine inventory is resolved on every single call instead of being cached, so a
 * machine that was broken, unloaded, replaced or never present simply yields {@code null} - and this
 * handler then reports zero slots and accepts/offers nothing instead of crashing. The same happens while
 * no face is configured.
 */
public final class MachineProxyStorage extends DelegatingInventoryStorage {

    private final BlockEntity owner;
    /** Face this storage belongs to, resolved to a slot on every call. */
    private final Direction face;

    public MachineProxyStorage(BlockEntity owner, Direction face) {
        super(() -> machineStorage(owner), () -> isActive(owner, face));
        this.owner = owner;
        this.face = face;
    }

    /**
     * The inventory handler of the machine this block works on, or {@code null} while there is none.
     * <p>
     * This is the same call Oritech's own inventory proxy addon makes
     * ({@code getInventoryForAddon()} first, then {@code getItemLookup(null)} on the machine), i.e. exactly
     * what an Oritech item pipe sees - and it follows the block's own idea of "the machine I work on", so a
     * wireless dock proxies the machine it is linked to and a wired addon the machine that claimed it.
     */
    @Nullable
    public static ResourceHandler<ItemResource> machineStorage(BlockEntity owner) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return null;

        var target = addon.connectedMachinePos();
        if (target == null || addon.getLevel() == null || !addon.getLevel().isLoaded(target)) return null;

        var machine = addon.getLevel().getBlockEntity(target);
        if (machine == null) return null;

        // the machine's own addon inventory is what Oritech's proxy uses first; the machine's item lookup is
        // the fallback for the few machines that do not offer one
        if (machine instanceof MachineAddonController controller) return controller.getInventoryForAddon();
        return machine instanceof ItemProvider provider ? provider.getItemLookup(null) : null;
    }

    /** The slot this face is bound to on the machine's inventory, or {@code null} while it proxies nothing. */
    @Nullable
    public Integer targetSlot() {
        return !isActive(owner, face) || !(owner instanceof ExtensionAddonBlockEntity addon)
                ? null
                : addon.proxyFaces().slotOf(face);
    }

    /**
     * True while this storage may really be used: the block owns inventory proxy addons, this face is
     * bound to a slot and the machine inventory resolves.
     */
    private static boolean isActive(BlockEntity owner, Direction face) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return false;
        if (!addon.canProxyItems()) return false;
        if (addon.proxyFaces().slotOf(face) == null) return false;
        return machineStorage(owner) != null;
    }

    /** The configured slot, or {@code -1} while nothing is configured (the parent then answers "nothing"). */
    private int slot() {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return -1;
        var configured = addon.proxyFaces().slotOf(face);
        return configured == null ? -1 : configured;
    }

    /**
     * Slot count of the machine inventory. It is the machine's own slot count - like Oritech's inventory
     * proxy addon - so a pipe sees a normal inventory; only the bound slot accepts or offers items.
     */
    @Override
    public int size() {
        return isActive(owner, face) ? super.size() : 0;
    }

    @Override
    public ItemResource getResource(int index) {
        return index == slot() && isActive(owner, face) ? super.getResource(index) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index == slot() && isActive(owner, face) ? super.getAmountAsLong(index) : 0L;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return index == slot() && isActive(owner, face) ? super.getCapacityAsLong(index, resource) : 0L;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index == slot() && isActive(owner, face) && super.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index != slot() || !isActive(owner, face)) return 0;
        return super.insert(index, resource, amount, transaction);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        return insert(slot(), resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index != slot() || !isActive(owner, face)) return 0;
        return super.extract(index, resource, amount, transaction);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        return extract(slot(), resource, amount, transaction);
    }
}
