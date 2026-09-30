package io.github.xiao232ming.oritechaddonsone.mixin;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusHolder;

/**
 * Raises the slot limit of the Oritech machine inventories by the bonus of this mod's warehouse addons.
 * <p>
 * On 26.1.2 Oritech's {@code SimpleInventoryStorage} no longer decides its own slot limit: it extends
 * NeoForge's {@link ItemStacksResourceHandler}, which reports
 * {@code min(item.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE)} for a slot. That is what caps a
 * machine slot, so the limit is changed here, on the NeoForge class every Oritech inventory is built on.
 * <p>
 * While a bonus is active the capacity becomes {@code 64 + bonus} and the item's own maximum is ignored
 * (otherwise a machine would still stop at 64 no matter what the plugin promises - the requested
 * behaviour). Without a bonus the original NeoForge value is returned, so unmodified machines keep the
 * stack limit they had.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is not synced
 * and not saved - the limits of a machine are recomputed from its plugins on every addon scan. Because
 * the bonus is a field of the storage, this mixin cannot change any inventory that was not explicitly
 * given one: a handler without a bonus reports the NeoForge capacity untouched.
 */
@Mixin(value = ItemStacksResourceHandler.class, priority = 1500)
public abstract class ItemStacksResourceHandlerMixin implements StorageBonusHolder {

    @Unique
    private int oritechaddonsone$slotBonus;

    @Override
    public int oritechaddonsone$slotBonus() {
        return this.oritechaddonsone$slotBonus;
    }

    @Override
    public void oritechaddonsone$setSlotBonus(int bonus) {
        this.oritechaddonsone$slotBonus = Math.max(0, bonus);
    }

    /**
     * Item storages have no fluid capacity; the two methods belong to {@link StorageBonusHolder} and are
     * implemented (not applied) so the machine side can treat every storage holder the same way.
     */
    @Override
    public long oritechaddonsone$capacityBonus() {
        return 0L;
    }

    @Override
    public void oritechaddonsone$setCapacityBonus(long bonus) {
        // no-op: an item storage has no fluid capacity
    }

    @Override
    public void oritechaddonsone$clampToCapacity() {
        // no-op: an item storage is clamped by the capacity it reports the next time it is written to
    }

    @Override
    public long oritechaddonsone$effectiveCapacity() {
        return this.oritechaddonsone$slotBonus > 0 ? 64L + this.oritechaddonsone$slotBonus : 0L;
    }

    @Inject(method = "getCapacity(ILnet/neoforged/neoforge/transfer/item/ItemResource;)I",
            at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$raiseSlotLimit(int index, ItemResource resource,
            CallbackInfoReturnable<Integer> callback) {
        if (this.oritechaddonsone$slotBonus <= 0) return;

        // 64 is the vanilla slot limit this feature raises; the item's own maximum is skipped on purpose
        // so a slot can hold more than one stack of the same item.
        callback.setReturnValue(64 + this.oritechaddonsone$slotBonus);
    }
}
