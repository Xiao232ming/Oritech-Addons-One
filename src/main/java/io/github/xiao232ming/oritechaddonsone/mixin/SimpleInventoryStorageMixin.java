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
 * hold more than one stack of the same item. Without a bonus an item stack a player can create is
 * treated exactly as before; only a stack that already exceeds the item's own maximum survives a
 * {@code setItem}, which is what keeps the larger stack the server sent visible on the client (see
 * {@code oritechaddonsone$ignoreItemLimitOnSet}).
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

    /**
     * The slot limit of a normal 64 stack, i.e. the number this feature raises. An item storage has no
     * single capacity (it depends on the item in the slot), so this is the limit of the common case and
     * only informational.
     */
    @Override
    public long oritechaddonsone$effectiveCapacity() {
        return this.oritechaddonsone$slotBonus > 0 ? 64L + this.oritechaddonsone$slotBonus : 0L;
    }

    /**
     * Brings every stored stack back down to the limit this storage currently reports: with a bonus a
     * slot may hold more than one stack of the item, without one the item's own maximum is the limit
     * again. Called after the bonus was lowered (a warehouse addon was removed), because {@code setItem}
     * deliberately no longer shrinks a stack that was written while the bonus was active - without this
     * the extra items would stay in the slot forever. The excess is deleted, never dropped, which is the
     * same rule the fluid side follows.
     */
    @Override
    public void oritechaddonsone$clampToCapacity() {
        var storage = (SimpleInventoryStorage) (Object) this;

        for (var slot = 0; slot < storage.getContainerSize(); slot++) {
            var stack = storage.getItem(slot);
            if (stack.isEmpty()) continue;

            stack.limitSize(this.oritechaddonsone$slotBonus > 0
                    ? 64 + this.oritechaddonsone$slotBonus
                    : storage.getMaxStackSize(stack));
        }
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
     * <p>
     * Two cases bypass the limit. With an active bonus the machine slot really holds more than the
     * item's own maximum, so the clamp would throw the extra items away again. Without a bonus the clamp
     * is kept for everything a player can create, but a stack that <b>already</b> exceeds the item's own
     * maximum is left alone: a client never sees the bonus (it is computed on the server from the
     * plugins it found), so the stack the server just sent is the client's only evidence that the slot
     * accepts more than 64 - shrinking it here is what made the raised limit invisible in the machine
     * screen even though the server stored the larger stack.
     */
    @Redirect(
            method = "setItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lrearth/oritech/api/item/containers/SimpleInventoryStorage;getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I"))
    private int oritechaddonsone$ignoreItemLimitOnSet(SimpleInventoryStorage storage, ItemStack stack) {
        if (this.oritechaddonsone$slotBonus > 0) return Integer.MAX_VALUE;
        if (stack.getCount() > stack.getMaxStackSize()) return Integer.MAX_VALUE;

        return storage.getMaxStackSize(stack);
    }
}
