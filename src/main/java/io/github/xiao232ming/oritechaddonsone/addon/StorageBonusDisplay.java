package io.github.xiao232ming.oritechaddonsone.addon;

/**
 * Duck interface of the machine block entities that publish the storage plugin bonuses to the client.
 * <p>
 * The bonus itself is only ever computed on the server (see {@link MachineStorageBonuses}), while the
 * addon panel that shows it is built on the client. The two numbers therefore have to travel with the
 * machine's GUI data, exactly like the atomic forge's laser speedup: a mixin adds two synced fields to
 * Oritech's networked block entities, this interface reads and writes them, and the panel asks the
 * machine it is displaying for the last value the server published.
 * <p>
 * The values are a display copy, nothing else: they never take part in the calculation of the bonus and
 * the storages keep working from the values the server applied to them.
 */
public interface StorageBonusDisplay {

    /** Item slot capacity the machine's warehouse addons currently add to every slot (client copy). */
    int oritechaddonsone$shownItemSlotBonus();

    /** Fluid capacity in mB the machine's tank addons currently add to every tank (client copy). */
    long oritechaddonsone$shownFluidCapacityBonus();

    /**
     * Writes the two numbers so Oritech's next {@code GUI_OPEN} update carries them to the client.
     * Called on the server right after the bonus was recomputed and applied.
     */
    void oritechaddonsone$publishStorageBonus(int slots, long capacity);
}
