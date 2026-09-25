package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;

import rearth.oritech.client.ui.OritechScreenHandler;
import rearth.oritech.client.ui.UpgradableOritechScreenHandler;
import rearth.oritech.util.MachineAddonController;

/**
 * Shows the machine's redstone panel on screens that use a plain {@code OritechScreenHandler}: the
 * refinery decides {@code showRedstoneAddon} purely through {@code screenData.hasRedstoneControlAvailable()},
 * which is always false for it, so a control unit stored in a linked extension addon (wired or wireless)
 * could disable the machine without the screen ever showing why.
 * <p>
 * The upgradable variant of this check lives in {@code UpgradableOritechScreenHandlerMixin}; the logic
 * here is the same, but for the handlers without their own addon controller (currently the two
 * refineries). The stored control unit is visible through the synced {@code HAS_CONTROL_UNIT} block
 * state, and the linked wireless docks are part of the machine's synced addon list (they are re-listed
 * after every addon recomputation, see {@code MachineAddonControllerMixin}).
 */
@Mixin(OritechScreenHandler.class)
public abstract class OritechScreenHandlerMixin {

    @Shadow
    @Final
    public net.minecraft.world.entity.player.Inventory playerInventory;

    @Shadow
    @Final
    public BlockEntity blockEntity;

    /**
     * True while one of the machine's listed addon blocks is an extension addon that stores a control
     * unit. Runs on the client with the synced addon list and block states.
     */
    @Inject(method = "showRedstoneAddon", at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$showRedstoneForStoredControlUnit(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if ((Object) this instanceof UpgradableOritechScreenHandler) return;   // has its own extension
        if (!(this.blockEntity instanceof MachineAddonController controller)) return;

        var level = this.playerInventory.player.level();
        for (BlockPos addonPos : controller.getConnectedAddons()) {
            if (oritechaddonsone$storesControlUnit(level.getBlockState(addonPos))) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    /** True while that block is one of our extension addons and stores a control unit plugin. */
    private static boolean oritechaddonsone$storesControlUnit(BlockState state) {
        if (state.getBlock() instanceof ExtensionAddonBlock) {
            return state.hasProperty(ExtensionAddonBlock.HAS_CONTROL_UNIT)
                    && state.getValue(ExtensionAddonBlock.HAS_CONTROL_UNIT);
        }
        if (state.getBlock() instanceof WirelessExtensionAddonBlock) {
            return state.hasProperty(WirelessExtensionAddonBlock.HAS_CONTROL_UNIT)
                    && state.getValue(WirelessExtensionAddonBlock.HAS_CONTROL_UNIT);
        }
        return false;
    }
}
