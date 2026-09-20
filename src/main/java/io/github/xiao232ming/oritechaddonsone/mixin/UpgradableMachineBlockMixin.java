package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import rearth.oritech.block.base.block.UpgradableMachineBlock;

import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Re-applies the wireless extension addons of a machine whenever the machine recomputes its addons
 * because a player is about to open its GUI.
 * <p>
 * Oritech's upgradable machines call {@code MachineAddonController#initAddons()} at the start of
 * {@code useWithoutItem}, i.e. before the menu is opened, and that call resets the machine's addon data
 * to the stats of its attached addons - which drops the contribution of the wireless extension addons,
 * because those are not attached to the machine. The machine's addon data is synced to the client as
 * {@code SyncType.GUI_OPEN}, and the client builds the speed / efficiency panel from that copy, so
 * without this injection the panel would always show the values of a machine without plugins.
 * <p>
 * Applying the docks here - after {@code initAddons()} and before the GUI (and its sync) is opened -
 * makes the wireless plugins part of the data the client receives. The periodic re-apply of the dock
 * itself stays in place for every other way the machine can recompute its addons.
 */
@Mixin(UpgradableMachineBlock.class)
public abstract class UpgradableMachineBlockMixin {

    @Inject(method = "useWithoutItem", at = @At(value = "INVOKE",
            target = "Lrearth/oritech/util/MachineAddonController;initAddons()V", shift = At.Shift.AFTER))
    private void oritechaddonsone$applyWirelessDocksBeforeGui(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) return;

        WirelessExtensionAddonBlockEntity.applyAllDocks(level, pos);
    }
}
