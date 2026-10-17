package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;

import rearth.oritech.api.networking.SyncField;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;

import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserChambers;
import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserSpeedupHost;

/**
 * Publishes how much the lasers aiming at an atomic forge speed it up, so its screen can show the real
 * acceleration instead of the machine's own (unused) addon data.
 * <p>
 * The forge is charged only by lasers: each shot of a laser puts {@code energyPerTick / speed()} into it,
 * so the charge time scales with the lasers' speed plugins and their count, while the forge's own addon
 * data never influences it (it has no addon slots, and its energy container is recomputed from the recipe).
 * The value is summed up on the server - a client cannot read a laser's addons, they are only synced when
 * the laser's own GUI is open - and handed to the client through the synced field of the forge.
 * <p>
 * The refresh itself lives in {@link MachineBlockEntitySyncMixin}: exp11 moved {@code writeClientSideData}
 * from the forge up to {@code MachineBlockEntity}, and a mixin can only inject into methods its own target
 * declares. This mixin keeps the synced field and the charge adjustment of the forge.
 */
@Mixin(AtomicForgeBlockEntity.class)
public abstract class AtomicForgeBlockEntityMixin implements ForgeLaserSpeedupHost, MachineBaseInvokers {

    /** Speed relative to one plain laser; refreshed on the server right before the GUI data is sent. */
    @Unique
    @SyncField(SyncType.GUI_OPEN)
    private float oritechaddonsone$laserSpeedup = 1.0F;

    @Override
    public float oritechaddonsone$laserSpeedup() {
        return this.oritechaddonsone$laserSpeedup;
    }

    @Override
    public void oritechaddonsone$setLaserSpeedup(float speedup) {
        this.oritechaddonsone$laserSpeedup = speedup;
    }

    /**
     * Charges the forge the energy its charging lasers' processing chambers cost. The forge sets its buffer
     * to the recipe's whole cost, so unlike every other machine it never multiplies that by an efficiency
     * value - without this the extra items the chambers process would be free.
     * <p>
     * Charged is what the operation will really produce, not what the chambers could produce: with
     * ingredients for two extra items the forge crafts two, so it pays for two even when its lasers carry 96
     * chambers.
     * <p>
     * The forge re-sets the buffer from the recipe on every resetProgress, so scaling it here cannot compound.
     */
    @Inject(method = "resetProgress", at = @At("RETURN"))
    private void oritechaddonsone$chargeForChambers(CallbackInfo callback) {
        var forge = (AtomicForgeBlockEntity) (Object) this;
        if (!(forge.getLevel() instanceof ServerLevel serverLevel)) return;

        var storage = forge.getStorageForAddon();
        // Without a recipe the buffer is set to 1, which is not a charge requirement to scale.
        if (storage == null || storage.capacity <= 10L) return;

        int chambers = ForgeLaserChambers.chambersOf(serverLevel, forge.getBlockPos());
        if (chambers <= 0) return;

        int items = ForgeLaserChambers.itemsFromInput(forge.getCurrentRecipe(), oritechaddonsone$inputView(),
                chambers);

        float factor = ForgeLaserChambers.efficiencyFactor(chambers, items);
        if (factor != 1.0F) {
            storage.setCapacity(Math.round(storage.capacity * factor));
        }
    }
}
