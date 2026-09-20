package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.block.entity.interaction.DronePortEntity;
import rearth.oritech.block.entity.interaction.EndericLaserBlockEntity;
import rearth.oritech.block.entity.interaction.EnergyTransmissionPoleEntity;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ComponentContent;
import rearth.oritech.item.tools.LaserTargetDesignator;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Teaches Oritech's target designator to link a wireless extension addon to a machine.
 * <p>
 * The designator already stores the position of any clicked block in the
 * {@code oritech:target_position} component, so saving a wireless dock needs no change. To store a
 * position <b>into a machine</b> it only knows the enderic laser, the drone port and the energy
 * transmission pole ({@code setTargetFromDesignator} / {@code assignNewTarget}), so this injection adds
 * the missing case: while the stored position is one of our wireless docks and the clicked block belongs
 * to an upgradable machine, the dock is linked to that machine (linking again simply moves the link).
 * <p>
 * The injection runs before Oritech's own code but deliberately keeps out of its way: Oritech's own
 * designator targets and everything that is not an addon machine are left untouched, and the stored
 * position is only consumed when it really points at a loaded wireless dock.
 */
@Mixin(LaserTargetDesignator.class)
public class LaserTargetDesignatorMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$linkWirelessAddon(UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        var level = context.getLevel();
        if (level.isClientSide()) return;

        var stack = context.getItemInHand();
        var dockPos = stack.get(ComponentContent.TARGET_POSITION.get());
        var clickedPos = context.getClickedPos();
        io.github.xiao232ming.oritechaddonsone.OritechAddonsOne.LOGGER.info(
                "[diag] designator useOn: clicked={} ({}) stored={}", clickedPos,
                level.getBlockState(clickedPos).getBlock(), dockPos);
        if (dockPos == null) return;

        var clickedState = level.getBlockState(clickedPos);

        // keep Oritech's own designator targets working
        if (clickedState.is(BlockContent.ENDERIC_LASER.get()) || clickedState.is(BlockContent.DRONE_PORT.get())
                || clickedState.is(BlockContent.ENERGY_TRANSMISSION_POLE.get())) {
            return;
        }
        if (level.getBlockEntity(clickedPos) instanceof EndericLaserBlockEntity
                || level.getBlockEntity(clickedPos) instanceof DronePortEntity
                || level.getBlockEntity(clickedPos) instanceof EnergyTransmissionPoleEntity) {
            return;
        }

        // Multiblock machines keep their controller in a core block, so resolve that - but only when the
        // clicked block really is one of those core blocks. Oritech's helper casts the block entity at that
        // position to its core type, so calling it for anything else (our dock, a plain block, ...) throws.
        var machinePos = clickedPos;
        if (clickedState.getBlock() instanceof MachineCoreBlock) {
            var controllerPos = MachineCoreBlock.getControllerPos(level, clickedPos);
            if (controllerPos != null) machinePos = controllerPos;
        }

        // Not an upgradable machine: leave the click to Oritech, which then stores this position.
        if (!(level.getBlockEntity(machinePos) instanceof MachineAddonController)) {
            io.github.xiao232ming.oritechaddonsone.OritechAddonsOne.LOGGER.info(
                    "[diag] designator: {} is not an addon machine (be={}), leaving it to Oritech",
                    machinePos, level.getBlockEntity(machinePos));
            return;
        }

        if (!level.isLoaded(dockPos)) {
            var player = context.getPlayer();
            if (player != null) {
                player.sendSystemMessage(
                        Component.translatable("message.oritechaddonsone.wireless.not_loaded"));
            }
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (!(level.getBlockEntity(dockPos) instanceof WirelessExtensionAddonBlockEntity dock)) {
            io.github.xiao232ming.oritechaddonsone.OritechAddonsOne.LOGGER.info(
                    "[diag] designator: stored {} is not a wireless dock (be={}, loaded={})",
                    dockPos, level.getBlockEntity(dockPos), level.isLoaded(dockPos));
            return;
        }

        dock.linkTo(machinePos);

        var player = context.getPlayer();
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.oritechaddonsone.wireless.linked",
                    level.getBlockState(machinePos).getBlock().getName()));
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
