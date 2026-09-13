package com.example.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import com.example.oritechaddonsone.block.ExtensionPluginBlock;

import rearth.oritech.client.ui.UpgradableOritechScreenHandler;
import rearth.oritech.util.MachineAddonController;

/**
 * Shows the machine's redstone panel while a control unit plugin is stored inside an Extension Plugin
 * that is attached to that machine.
 * <p>
 * Oritech decides this in {@code UpgradableOritechScreenHandler#showRedstoneAddon} by looking for a
 * block of type {@code BlockContent.CONTROL_UNIT_ADDON} in the machine's addon slots. This block is an
 * Extension Plugin instead, so the check has to be extended. The stored control unit is visible through
 * the synced {@link ExtensionPluginBlock#HAS_CONTROL_UNIT} block state (a block entity inventory is not
 * synced to the client, and this check runs on the client).
 * <p>
 * The panel itself only displays the state (torch on/off, signal strength, effect text); the machine's
 * {@code receivedRedstoneSignal()} / {@code currentRedstoneEffect()} already report the state that is
 * forwarded by {@code ExtensionPluginBlockEntity#applyRedstoneSignal}.
 */
@Mixin(UpgradableOritechScreenHandler.class)
public abstract class UpgradableOritechScreenHandlerMixin {

    @Shadow
    @Final
    public Level worldAccess;

    @Shadow
    @Final
    public MachineAddonController addonController;

    @Inject(method = "showRedstoneAddon", at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$showRedstoneForStoredControlUnit(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (this.addonController == null) return;

        for (var addonPos : this.addonController.getConnectedAddons()) {
            var state = this.worldAccess.getBlockState(addonPos);
            if (state.getBlock() instanceof ExtensionPluginBlock
                    && state.hasProperty(ExtensionPluginBlock.HAS_CONTROL_UNIT)
                    && state.getValue(ExtensionPluginBlock.HAS_CONTROL_UNIT)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
