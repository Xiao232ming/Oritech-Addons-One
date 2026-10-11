package io.github.xiao232ming.oritechaddonsone.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;

/**
 * Raises the stack size a <b>GUI slot</b> accepts, so the warehouse addon really is usable through the
 * machine screen and not only through the item API.
 * <p>
 * {@code SimpleInventoryStorageMixin} raises what the storage itself accepts
 * ({@code getSlotLimit}, {@code insertToSlot}, {@code setItem}), but a machine screen never goes through
 * those methods: the player's click ends up in vanilla's {@link Slot}:
 * <ul>
 *     <li>{@code Slot#getMaxStackSize(ItemStack)} is {@code min(container.getMaxStackSize(), stack.getMaxStackSize())}.
 *     {@code Container#getMaxStackSize()} is 99 for every Oritech inventory (it does not override it) and
 *     {@code ItemStack#getMaxStackSize()} is 64, so the GUI caps at 64 no matter what the storage
 *     reports - that is why a slot accepted 80 items through the API but the player could never get past
 *     64 of them,</li>
 *     <li>that single method is the one {@code AbstractContainerMenu} uses for every path a click can
 *     take: {@code doClick} for picking up and putting down (through {@code Slot#safeInsert}) and for the
 *     swap and quick-craft cases, {@code moveItemStackTo} for shift-click and for the quick-move of a
 *     whole stack, and {@code Slot#safeInsert} itself.</li>
 * </ul>
 * Both overloads are therefore reported as {@code 64 + bonus} while the slot's container is a storage of
 * this mod with an active bonus, which is exactly the number the storage accepts. Without a bonus both
 * are left untouched, so unmodified Oritech and vanilla slots keep the limit they had.
 * <p>
 * The bonus lives on the storage and is set by {@code MachineStorageBonuses} on the server. The client
 * copy of a machine inventory has no bonus, so it predicts a click with the vanilla limit of 64 while the
 * server applies the raised one and sends the result back; the client does show the larger stack, because
 * {@code SimpleInventoryStorageMixin} stops {@code setItem} from shrinking a stack that already exceeds
 * the item's own maximum. Only the prediction of a click is therefore one step behind, and the server's
 * answer is the authoritative one - the same asymmetry Oritech's own machines have on 26.1.2, where the
 * limit comes from the storage and only the server knows the plugins.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    /** The inventory behind this slot; the bonus is read from it. */
    @Shadow
    @Final
    public Container container;

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$raiseSlotLimit(CallbackInfoReturnable<Integer> callback) {
        var bonus = oritechaddonsone$slotBonus();
        if (bonus > 0) {
            callback.setReturnValue(64 + bonus);
        }
    }

    /**
     * The variant the container menus actually call. It has to be answered directly instead of letting
     * vanilla take {@code min(this.getMaxStackSize(), stack.getMaxStackSize())}, because the item's own
     * maximum (64) would cap the result again.
     */
    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$raiseSlotLimitForItem(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        var bonus = oritechaddonsone$slotBonus();
        if (bonus > 0) {
            callback.setReturnValue(64 + bonus);
        }
    }

    /** Item slot bonus of the storage behind this slot, or {@code 0} for every other container. */
    @Unique
    private int oritechaddonsone$slotBonus() {
        return this.container instanceof AddonStorageBonus storage ? storage.oritechaddonsone$slotBonus() : 0;
    }
}
