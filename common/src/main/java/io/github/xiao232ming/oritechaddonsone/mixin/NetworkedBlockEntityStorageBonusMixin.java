package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncField;
import rearth.oritech.api.networking.SyncType;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusDisplay;

/**
 * Carries the storage plugin bonus of a machine to the client, the same way the atomic forge publishes
 * its laser speedup.
 * <p>
 * The two fields are {@code GUI_OPEN} synced, so Oritech writes them into every GUI update the machine
 * sends - and every machine that can carry addons sends one when its menu is opened
 * ({@code MachineBlockEntity#saveExtraData} and its counterparts). {@link MachineStorageBonuses} writes
 * the numbers after it recomputed the bonus and additionally sends an update when they changed, so a
 * machine whose plugins were edited while its panel is open does not keep showing the old number.
 * <p>
 * The target is Oritech's shared networking base class rather than one machine: every block entity that
 * can report addon stats is networked, and all of them - the upgradable machines, the multiblocks such
 * as the refinery, the storage blocks, the laser arm, the drone port, the shrinker - inherit from it.
 * {@code NetworkManager#getSyncFields} scans the concrete class and all of its superclasses for
 * {@code @SyncField} members, so a field merged into this class is picked up by every one of them.
 */
@Mixin(NetworkedBlockEntity.class)
public abstract class NetworkedBlockEntityStorageBonusMixin implements StorageBonusDisplay {

    /** Item slot capacity the machine's warehouse addons add, as the server last computed it. */
    @Unique
    @SyncField(SyncType.GUI_OPEN)
    private int oritechaddonsone$shownItemSlotBonus;

    /** Fluid capacity in mB the machine's tank addons add, as the server last computed it. */
    @Unique
    @SyncField(SyncType.GUI_OPEN)
    private long oritechaddonsone$shownFluidCapacityBonus;

    @Override
    public int oritechaddonsone$shownItemSlotBonus() {
        return this.oritechaddonsone$shownItemSlotBonus;
    }

    @Override
    public long oritechaddonsone$shownFluidCapacityBonus() {
        return this.oritechaddonsone$shownFluidCapacityBonus;
    }

    @Override
    public void oritechaddonsone$publishStorageBonus(int slots, long capacity) {
        this.oritechaddonsone$shownItemSlotBonus = slots;
        this.oritechaddonsone$shownFluidCapacityBonus = capacity;
    }
}
