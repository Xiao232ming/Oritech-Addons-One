package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.DelegatingInventoryStorage;

/**
 * The inventory one <b>face</b> of an Extension Addon / Wireless Extension Dock offers to the outside
 * world: the slot of the machine this block works on that the face was bound to.
 * <p>
 * This is Oritech's own inventory proxy mechanism, applied to our block: Oritech's
 * {@code InventoryProxyAddonBlockEntity} implements {@code ItemApi.BlockProvider} and reports a
 * {@link DelegatingInventoryStorage} that forwards to the machine it is attached to while answering "0"
 * for every slot but its configured one. A pipe, a hopper or another mod asking the block for its item
 * handler therefore sees the machine's slots and can only move items in and out of the configured one.
 * <p>
 * <b>Fail safe:</b> the machine inventory is resolved on every single call instead of being cached, so a
 * machine that was broken, unloaded, replaced or never present simply yields {@code null} - and the
 * {@link DelegatingInventoryStorage} then reports zero slots and accepts/offers nothing instead of
 * crashing. The same happens while no face is configured.
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
     * The inventory storage of the machine this block works on, or {@code null} while there is none.
     * <p>
     * This is the same call Oritech's own inventory proxy addon makes
     * ({@code getInventoryStorage(null)} on the machine's block entity), i.e. exactly what an Oritech item
     * pipe sees - and it follows the block's own idea of "the machine I work on", so a wireless dock
     * proxies the machine it is linked to and a wired addon the machine that claimed it.
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
    public int getSlotCount() {
        return isActive(owner, face) ? super.getSlotCount() : 0;
    }

    @Override
    public int insert(ItemStack inserted, boolean simulate) {
        return insertToSlot(inserted, slot(), simulate);
    }

    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        return extractFromSlot(extracted, slot(), simulate);
    }

    @Override
    public int insertToSlot(ItemStack inserted, int index, boolean simulate) {
        if (index != slot() || !isActive(owner, face)) return 0;
        return super.insertToSlot(inserted, index, simulate);
    }

    @Override
    public int extractFromSlot(ItemStack extracted, int index, boolean simulate) {
        if (index != slot() || !isActive(owner, face)) return 0;
        return super.extractFromSlot(extracted, index, simulate);
    }

    /**
     * A pipe that pulled an item out or pushed one in asks us to persist the change. The backing storage
     * belongs to the machine, whose own handler already marks itself dirty, so only our block entity has
     * to be told that the proxy was used (it holds no items itself, but this is where a future state would
     * be flushed).
     */
    @Override
    public void update() {
        if (owner instanceof ExtensionAddonBlockEntity addon) addon.onProxyUsed();
    }
}
