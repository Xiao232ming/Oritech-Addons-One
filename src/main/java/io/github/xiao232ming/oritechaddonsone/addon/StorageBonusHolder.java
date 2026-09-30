package io.github.xiao232ming.oritechaddonsone.addon;

/**
 * Duck interface of the machine storages this mod can enlarge.
 * <p>
 * Oritech's {@code BaseAddonData} only carries speed, efficiency, energy capacity, energy transfer and
 * chambers, so the machine has no way to tell its own inventory or tanks that a plugin wants more room.
 * The Oritech storage classes are therefore extended by a mixin which keeps the bonus in a
 * {@code @Unique} field, and that field is read and written through this interface - the machine side
 * only ever sees the interface, so it does not have to know which mixin class is behind it.
 * <p>
 * On 26.1.2 the slots of a machine inventory are handled by NeoForge's
 * {@code ItemStacksResourceHandler}, which serves both the machines of this mod's plugin containers and
 * the machines of Oritech, so this one interface is implemented by both mixins.
 */
public interface StorageBonusHolder {

    /** Item slot limit added on top of the vanilla 64, from the stored warehouse addons. */
    int oritechaddonsone$slotBonus();

    /** Sets the item slot bonus. */
    void oritechaddonsone$setSlotBonus(int bonus);

    /** Fluid capacity in mB added on top of the tank's own capacity, from the stored tank addons. */
    long oritechaddonsone$capacityBonus();

    /** Sets the fluid capacity bonus. */
    void oritechaddonsone$setCapacityBonus(long bonus);

    /**
     * The capacity this storage really has: the base capacity of the storage plus the bonus. Kept in the
     * interface because on 26.1.2 the capacity of a machine inventory is used by NeoForge's
     * {@code StacksResourceHandler} rather than by the Oritech class, so a second mixin has to be able to
     * ask for the final number (a {@code @Unique} method of one mixin is not visible to another).
     */
    long oritechaddonsone$effectiveCapacity();

    /**
     * Clamps stored fluid down to the current effective capacity.
     * <p>
     * Called after the bonus was lowered (a tank addon was removed), because the transfer API only limits
     * what is inserted - it never checks the stored amount afterwards. The excess is deleted instead of
     * spilled, which keeps the tank in a valid state and is deterministic: the player always loses
     * exactly the amount above the new capacity.
     * <p>
     * An item storage has nothing to do here: overwriting a stack with a smaller amount is what Oritech
     * does anyway when it writes into a slot, and item stacks above the limit are simply clamped the next
     * time the machine touches that slot.
     */
    void oritechaddonsone$clampToCapacity();

    /** Item slots one warehouse addon adds to every slot of the machine. */
    int SLOTS_PER_WAREHOUSE_ADDON = 16;

    /** Fluid capacity in mB one tank addon adds to every tank of the machine. */
    long CAPACITY_PER_TANK_ADDON = 8000L;
}
