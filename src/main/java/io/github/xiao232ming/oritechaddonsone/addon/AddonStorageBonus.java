package io.github.xiao232ming.oritechaddonsone.addon;

/**
 * Duck interface of the machine storages this mod can enlarge.
 * <p>
 * Oritech's {@code BaseAddonData} only carries speed, efficiency, energy capacity, energy transfer and
 * chambers, so the machine has no way to tell its own inventory or tanks that a plugin wants more room.
 * The two storage classes are therefore extended by a mixin which stores the bonus in a
 * {@code @Unique} field, and that field is read and written through this interface - the machine side
 * only ever sees the interface, so it does not have to cast to a mixin class.
 * <p>
 * Both mixins implement it (item storage and fluid storage), so the machine side can treat every
 * enlarged storage the same way. The bonus is per storage instance: setting it never affects other
 * machines, and the machine recomputes it from its connected plugins on every addon scan.
 */
public interface AddonStorageBonus {

    /**
     * Item slots this storage gained: every stored "warehouse addon" adds
     * {@link #SLOTS_PER_WAREHOUSE_ADDON}, on top of the vanilla 64.
     */
    int oritechaddonsone$slotBonus();

    /** Sets the item slot bonus. See the note about removal in {@link #oritechaddonsone$clampToCapacity()}. */
    void oritechaddonsone$setSlotBonus(int bonus);

    /**
     * Fluid capacity in mB this storage gained: every stored "tank addon" adds
     * {@link #CAPACITY_PER_TANK_ADDON}.
     */
    long oritechaddonsone$capacityBonus();

    /** Sets the fluid capacity bonus. */
    void oritechaddonsone$setCapacityBonus(long bonus);

    /**
     * Clamps stored content down to the current effective capacity.
     * <p>
     * Called after the bonus was lowered (a "tank addon" was removed), because Oritech's own
     * {@code insert} only limits what is added - it never checks the stored amount afterwards. The
     * excess is deleted instead of spilled, which keeps the machine in a valid state and is
     * deterministic: the player always loses exactly the amount above the new capacity.
     * <p>
     * The item side has no equivalent here because Oritech clamps item stacks itself (through
     * {@code ItemStack#limitSize}) whenever a machine writes into its inventory.
     */
    void oritechaddonsone$clampToCapacity();

    /** Item slots one warehouse addon adds to every slot of the machine. */
    int SLOTS_PER_WAREHOUSE_ADDON = 16;

    /** Fluid capacity in mB one tank addon adds to every tank of the machine. */
    long CAPACITY_PER_TANK_ADDON = 8000L;
}
