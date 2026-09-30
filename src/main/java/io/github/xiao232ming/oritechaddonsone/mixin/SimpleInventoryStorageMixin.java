package io.github.xiao232ming.oritechaddonsone.mixin;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.item.containers.SimpleInventoryStorage;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;

/**
 * Raises the slot limit of every Oritech machine inventory by the bonus of this mod's warehouse addons.
 * <p>
 * Oritech hardcodes the limit of {@code SimpleInventoryStorage}:
 * <ul>
 *     <li>{@code getSlotLimit} returns {@code 64}, which is why a machine refuses to take more than a
 *     stack of an item - it is the number this mixin reports as {@code 64 + bonus},</li>
 *     <li>{@code insertToSlot} starts with {@code min(getSlotLimit(slot), addedStack.getMaxStackSize())}
 *     and {@code setItem} ends with {@code stack.limitSize(getMaxStackSize(stack))}, so without the two
 *     redirects below the item's own stack size (64, or 16 for tools and eggs) would cap the machine at
 *     64 items per slot no matter what {@code getSlotLimit} says.</li>
 * </ul>
 * The item's own maximum is therefore bypassed while a bonus is active
 * ({@code Integer.MAX_VALUE} instead of {@code stack.getMaxStackSize()}), which is what makes a slot
 * hold more than one stack of the same item. Without a bonus both calls are left untouched, so vanilla
 * and unmodified Oritech behaviour is unchanged.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is not synced
 * and not saved - the limits of a machine are recomputed from its plugins on every addon scan.
 */
@Mixin(SimpleInventoryStorage.class)
public abstract class SimpleInventoryStorageMixin implements AddonStorageBonus {

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
     * Item storages have no fluid capacity; the two methods belong to {@link AddonStorageBonus} and are
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
        // no-op: Oritech clamps item stacks itself (ItemStack#limitSize) when it writes into a slot
    }

    @Inject(method = "getSlotLimit", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$raiseSlotLimit(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(64 + this.oritechaddonsone$slotBonus);
    }

    /**
     * {@code ItemStack#getMaxStackSize} inside {@code insertToSlot}.
     */
    @Redirect(
            method = "insertToSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int oritechaddonsone$ignoreItemLimitOnInsert(ItemStack stack) {
        return this.oritechaddonsone$slotBonus > 0 ? Integer.MAX_VALUE : stack.getMaxStackSize();
    }

    /**
     * The {@code getMaxStackSize(ItemStack)} call inside {@code setItem}. Oritech's own class inherits
     * it from {@link net.minecraft.world.Container}, but the compiler resolves the call against the
     * owning class ({@code invokevirtual SimpleInventoryStorage.getMaxStackSize}), so the owner of the
     * redirect target is this class and not {@code Container} - and the handler's first argument has to
     * use that class as well, not the interface that declares the method.
     */
    @Redirect(
            method = "setItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lrearth/oritech/api/item/containers/SimpleInventoryStorage;getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I"))
    private int oritechaddonsone$ignoreItemLimitOnSet(SimpleInventoryStorage storage, ItemStack stack) {
        return this.oritechaddonsone$slotBonus > 0 ? Integer.MAX_VALUE : storage.getMaxStackSize(stack);
    }
}
