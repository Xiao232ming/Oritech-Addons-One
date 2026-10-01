package io.github.xiao232ming.oritechaddonsone.mixin;

import net.minecraft.world.item.Item;
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
 * {@code min(item.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE)} for a slot with a known item and
 * {@code Item.ABSOLUTE_MAX_STACK_SIZE} (99) for a slot whose item is not known yet. That is what caps a
 * machine slot, so the limit is changed here, on the NeoForge class every Oritech inventory is built on.
 * <p>
 * The bonus is <b>relative</b>: the number NeoForge computed for this slot and this item is the base and
 * every warehouse addon adds {@link StorageBonusHolder#SLOTS_PER_WAREHOUSE_ADDON} on top of it. A normal
 * 64 stack therefore ends up at 80 (64 + 16, the same number the 1.21.1 branch produces), while a slot
 * whose base is already 99 - Oritech's drone port reports that for an empty slot - grows to 115 instead of
 * being cut down to 80. The item's own maximum is still exceeded on purpose: a slot really holds 80 items
 * of a 64 stack. Without a bonus the value NeoForge computed is returned untouched, so unmodified machines
 * keep the stack limit they had.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is not synced
 * and not saved - the limits of a machine are recomputed from its plugins on every addon scan. Because
 * the bonus is a field of the storage, this mixin cannot change any inventory that was not explicitly
 * given one: a handler without a bonus reports the NeoForge capacity untouched.
 * <p>
 * The raised limit is only half of the feature: the transfer API limits what is inserted and never looks
 * at what is already stored, so a stack that was filled while the bonus was active would stay above the
 * item's own maximum forever once the addon is removed. {@link #oritechaddonsone$clampToCapacity()}
 * closes that, exactly like the 1.21.1 branch does, and it is called by {@code MachineStorageBonuses}
 * right after the bonus was set.
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

    /**
     * Brings every stored stack back down to the limit this storage currently reports: with a bonus a
     * slot may hold more than one stack of the item, without one the item's own maximum is the limit
     * again. Called after the bonus was lowered (a warehouse addon was removed), because the transfer API
     * only limits what is inserted - a stack that was written while the bonus was active keeps its count
     * above the item's own maximum until something rewrites the slot, which on 26.1.2 may never happen on
     * its own. The excess is deleted, never dropped, which is the same rule the fluid side follows.
     * <p>
     * The limit is the same number {@code getCapacity} reports: {@code 64 + bonus} while a bonus is
     * active, otherwise {@code min(item maximum, Item.ABSOLUTE_MAX_STACK_SIZE)}. Writing through
     * {@code set} rebuilds the stack from its {@link ItemResource} and so keeps its components, and it
     * reports the change to the machine like any other write.
     */
    @Override
    public void oritechaddonsone$clampToCapacity() {
        var handler = (ItemStacksResourceHandler) (Object) this;

        for (var index = 0; index < handler.size(); index++) {
            var resource = handler.getResource(index);
            if (resource.isEmpty()) continue;

            var limit = oritechaddonsone$limitFor(resource);
            if (handler.getAmountAsInt(index) > limit) handler.set(index, resource, limit);
        }
    }

    /** The limit one slot of this storage has for that resource, see {@link #oritechaddonsone$clampToCapacity}. */
    @Unique
    private int oritechaddonsone$limitFor(ItemResource resource) {
        if (this.oritechaddonsone$slotBonus > 0) return 64 + this.oritechaddonsone$slotBonus;

        return Math.min(resource.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    /**
     * The slot limit of a normal 64 stack, i.e. the base this feature grows. There is no single capacity
     * for an item storage (it depends on the item in the slot), so this is the number for the common case
     * and only informational - the limit the transfer API uses is the one {@code getCapacity} reports.
     */
    @Override
    public long oritechaddonsone$effectiveCapacity() {
        return this.oritechaddonsone$slotBonus > 0 ? 64L + this.oritechaddonsone$slotBonus : 0L;
    }

    /**
     * Adds the bonus to the limit NeoForge computed for this slot.
     * <p>
     * The injection sits on {@code RETURN} rather than on {@code HEAD} so the base is exactly what the
     * vanilla method produced - its ternary is not duplicated here: 64 for a normal stack, 99 for an item
     * that stacks to 99 and {@code Item.ABSOLUTE_MAX_STACK_SIZE} (99) for an empty slot. Only the bonus is
     * added, which is what makes the feature relative to the machine instead of an absolute 64 + bonus.
     * <p>
     * A result above 99 is only reachable for a slot whose base is already 99, i.e. for an empty slot or
     * for an item whose own maximum is above 83. The transfer API tolerates that: nothing clamps
     * {@code getCapacity} or {@code insert} to {@code Item.ABSOLUTE_MAX_STACK_SIZE},
     * {@code StacksResourceHandler} passes the value through as an {@code int} and
     * {@code ItemResource#toStack} does not limit the count either - measured on a drone port with one
     * warehouse addon: the limit is 115 and a 99 stack item really fills the slot up to 115.
     * <p>
     * The one cap that exists is on the <b>saved</b> count, not on the capacity: the {@code count} field of
     * vanilla's {@code ItemStack.CODEC} is {@code ExtraCodecs.intRange(1, 99)}, so a stack that really holds
     * more than 99 items cannot be serialised (verified: encoding a count of 115 fails with
     * "Value must be within range [1;99]", a count of 99 encodes fine). That is only reachable for an item
     * whose own maximum is above 83, and neither a vanilla nor an Oritech item stacks above 64, so the
     * raised limit stays at 80 for every real item and every stack a player can create stays saveable. The
     * capacity above 99 exists for the reported slot limit, which must not drop below the 99 a machine like
     * the drone port reports - capping the capacity at 99 would leave exactly the downgrade this fixes.
     */
    @Inject(method = "getCapacity(ILnet/neoforged/neoforge/transfer/item/ItemResource;)I",
            at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$raiseSlotLimit(int index, ItemResource resource,
            CallbackInfoReturnable<Integer> callback) {
        if (this.oritechaddonsone$slotBonus <= 0) return;

        int base = callback.getReturnValueI();
        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, (long) base + this.oritechaddonsone$slotBonus));
    }
}
