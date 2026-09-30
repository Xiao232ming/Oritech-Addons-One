package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;

import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncField;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.block.entity.interaction.EndericLaserBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;

import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserChambers;
import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserSpeedupHost;
import io.github.xiao232ming.oritechaddonsone.forge.LaserAimIndex;

/**
 * Publishes how much the lasers aiming at an atomic forge speed it up, so its screen can show the real
 * acceleration instead of the machine's own (unused) addon data.
 * <p>
 * The forge is charged only by lasers: each shot of a laser puts {@code energyPerTick / speed()} into it,
 * so the charge time scales with the lasers' speed plugins and their count, while the forge's own addon
 * data never influences it (it has no addon slots, and its energy container is recomputed from the recipe).
 * The value is summed up on the server - a client cannot read a laser's addons, they are only synced when
 * the laser's own GUI is open - and handed to the client through a synced field of the forge, which is why
 * this mixin also has to send the forge's GUI update: Oritech's forge does not send one itself.
 */
@Mixin(AtomicForgeBlockEntity.class)
public abstract class AtomicForgeBlockEntityMixin implements ForgeLaserSpeedupHost {

    /** Speed relative to one plain laser; refreshed on the server right before the GUI data is sent. */
    @Unique
    @SyncField(SyncType.GUI_OPEN)
    private float oritechaddonsone$laserSpeedup = 1.0F;

    @Override
    public float oritechaddonsone$laserSpeedup() {
        return this.oritechaddonsone$laserSpeedup;
    }

    @Inject(method = "writeClientSideData", at = @At("HEAD"))
    private void oritechaddonsone$syncLaserSpeedup(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer,
                                                   CallbackInfo callback) {
        var forge = (AtomicForgeBlockEntity) (Object) this;
        if (!(forge.getLevel() instanceof ServerLevel serverLevel)) return;

        // Refresh the chambers first: they live in the addon data that the update below carries to the client.
        ForgeLaserChambers.refresh(serverLevel, forge.getBlockPos());
        this.oritechaddonsone$laserSpeedup = oritechaddonsone$measureLaserSpeedup(serverLevel);

        // The forge's own writeClientSideData is empty, so the addon data (and the value above) would never
        // reach the client. Sending the update here is what it does for every other machine.
        if (forge instanceof NetworkedBlockEntity networked) {
            networked.sendUpdate(SyncType.GUI_OPEN);
        }
    }

    /**
     * Charges the forge the energy its charging lasers' processing chambers cost. The forge sets its buffer
     * to the recipe's whole cost, so unlike every other machine it never multiplies that by an efficiency
     * value - without this the extra items the chambers process would be free.
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

        float factor = ForgeLaserChambers.efficiencyFactor(serverLevel, forge.getBlockPos());
        if (factor != 1.0F) {
            storage.setCapacity(Math.round(storage.capacity * factor));
        }
    }
    /**
     * Sums up the charge rate of every laser training on this forge, relative to one laser without speed
     * plugins: a plain laser contributes 1, a laser whose speed plugin halved its shot cost contributes 2.
     */
    @Unique
    private float oritechaddonsone$measureLaserSpeedup(ServerLevel level) {
        var forge = (AtomicForgeBlockEntity) (Object) this;
        var forgePos = forge.getBlockPos();

        float speedup = 0.0F;
        for (var laserPos : LaserAimIndex.lasersOf(level, forgePos)) {
            if (!(level.getBlockEntity(laserPos) instanceof EndericLaserBlockEntity laser)) continue;
            // Only lasers that really aim here count: the index is a snapshot and a laser may have moved on.
            if (!forgePos.equals(laser.getCurrentTarget())) continue;

            float speed = laser.getBaseAddonData().speed();
            speedup += 1.0F / Math.max(0.0001F, speed);
        }
        return speedup;
    }
}
