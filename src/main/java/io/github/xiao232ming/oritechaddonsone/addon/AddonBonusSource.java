package io.github.xiao232ming.oritechaddonsone.addon;

/**
 * Implemented by the block entities of this mod that can hold the warehouse addon and the tank addon.
 * <p>
 * Both the wired {@code ExtensionAddonBlockEntity} and the wireless
 * {@code WirelessExtensionAddonBlockEntity} implement it (the wireless one inherits it), so the machine
 * side can collect the bonuses of every connected addon without knowing which variant it is looking at.
 * <p>
 * The numbers are derived from the plugins actually stored in the block, not from a cached value, so
 * they are always in sync with the inventory a player sees.
 */
public interface AddonBonusSource {

    /** Item slot bonus this addon contributes to every slot of the machine ({@code 16} per stored warehouse addon). */
    int oritechaddonsone$itemSlotBonus();

    /** Fluid capacity bonus this addon contributes to every tank of the machine ({@code 8000} mB per stored tank addon). */
    long oritechaddonsone$fluidCapacityBonus();
}
