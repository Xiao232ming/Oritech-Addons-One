package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;

import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;

import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserChambers;
import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserSpeedupHost;

/**
 * Refreshes the atomic forge's laser speed-up right before its GUI data is written.
 * <p>
 * exp11 moved {@code writeClientSideData} from the forge up to {@code MachineBlockEntity}, and a mixin can
 * only inject into methods its own target declares, so the hook has to sit on the class that declares it
 * now, filtered down to the forge. The base implementation already sends the GUI update itself (that is
 * why the old injection into the forge had to send it manually), so this only has to fill in the value.
 */
@Mixin(MachineBlockEntity.class)
public abstract class MachineBlockEntitySyncMixin {

    @Inject(method = "writeClientSideData", at = @At("HEAD"))
    private void oritechaddonsone$refreshForgeSpeedup(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer,
                                                      CallbackInfo callback) {
        if (!((Object) this instanceof AtomicForgeBlockEntity forge)) return;
        if (!(forge.getLevel() instanceof ServerLevel serverLevel)) return;

        // Refresh the chambers first: they live in the addon data that the update below carries to the client.
        ForgeLaserChambers.refresh(serverLevel, forge.getBlockPos());

        if (forge instanceof ForgeLaserSpeedupHost host) {
            host.oritechaddonsone$setLaserSpeedup(ForgeLaserChambers.laserSpeedup(serverLevel, forge.getBlockPos()));
        }
    }
}
