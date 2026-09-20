package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MachineAddonController.AddonBlock;

import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Makes the plugins of the wireless extension addons part of every addon recomputation of a machine.
 * <p>
 * Oritech recomputes a machine's addon data in {@code initAddons}: {@code gatherAddonStats} resets the
 * data to the stats of the machine's own addons, {@code writeAddons} lets those addons merge their own
 * plugins in and {@code updateEnergyContainer} then recalculates the energy container from that data -
 * including {@code energy = min(energy, capacity)}, which throws away everything above the capacity.
 * <p>
 * A wireless dock is not one of the machine's own addons, so its plugins used to be merged after all of
 * that: the machine briefly held the capacity without the dock's bonus (dropping stored energy above it)
 * and got it back a moment later, which shows up as energy that rises and falls. Adding the docks at the
 * end of {@code gatherAddonStats} puts them exactly where a wired addon contributes, i.e. before the
 * container is recalculated, so nothing is dropped and no separate re-scan is needed.
 */
@Mixin(MachineAddonController.class)
public interface MachineAddonControllerMixin {

    @Inject(method = "gatherAddonStats", at = @At("RETURN"))
    private void oritechaddonsone$applyWirelessDocks(List<AddonBlock> addons, CallbackInfo callback) {
        var controller = (MachineAddonController) (Object) this;
        var level = controller.getWorldForAddon();
        if (level == null || level.isClientSide()) return;

        WirelessExtensionAddonBlockEntity.applyAllDocks(level, controller.getPosForAddon());
    }
}
