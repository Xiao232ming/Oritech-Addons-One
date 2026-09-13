package com.example.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.oritechaddonsone.block.ExtensionPluginBlock;

import rearth.oritech.block.entity.interaction.AddonSplicerBlockEntity;
import rearth.oritech.util.MachineAddonController.AddonBlock;

/**
 * Keeps the Extension Plugin out of Oritech's addon splicer.
 * <p>
 * The splicer collects every addon connected to it, shrinks it into a Heart of the Machine and deletes
 * the addon blocks in the process. Removing our block from the addon list before the splicer evaluates
 * it means the Extension Plugin neither contributes stats to the splicer nor gets consumed by it,
 * while other plugins connected to the same splicer keep working normally.
 * <p>
 * This targets Oritech 2.0.0 internals ({@code AddonSplicerBlockEntity#gatherAddonStats}); it only
 * filters the list that is about to be evaluated, so it does not change any other machine behaviour.
 */
@Mixin(AddonSplicerBlockEntity.class)
public class AddonSplicerBlockEntityMixin {

    @Inject(method = "gatherAddonStats", at = @At("HEAD"))
    private void oritechaddonsone$excludeExtensionPlugins(List<AddonBlock> addons, CallbackInfo callback) {
        addons.removeIf(addon -> addon.addonBlock() instanceof ExtensionPluginBlock);
    }
}
