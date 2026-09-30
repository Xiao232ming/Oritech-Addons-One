package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.block.entity.interaction.DronePortEntity;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ComponentContent;
import rearth.oritech.item.tools.LaserTargetDesignator;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MachineAddonProvider;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Teaches Oritech's target designator to link a wireless extension addon to a machine.
 * <p>
 * The designator stores the position of the clicked block in the {@code oritech:target_position} component,
 * so saving a dock needs no change. To store a position <b>into</b> a machine it only knows the laser arm
 * and the drone port ({@code setTargetFromDesignator}), so this injection adds the missing case: while the
 * stored position is one of our wireless docks and the clicked block belongs to an upgradable machine, the
 * dock is linked to that machine.
 * <p>
 * The link only ever runs in that one direction - a dock that is stored may be applied to a machine, never
 * the other way round - and it leaves the stored position alone, so the designator keeps holding the dock.
 * What counts as "the machine" is resolved from the clicked block: the machine's own block, the controller
 * behind a multiblock core, or the machine a mounted addon reports. Oritech's own designator targets and
 * everything that is not an addon machine are left untouched, and the stored position is only consumed
 * when it really points at a loaded wireless dock.
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
        var clickedState = level.getBlockState(clickedPos);

        OritechAddonsOne.LOGGER.debug("[diag] designator useOn: clicked={} ({}), stored={}",
                clickedPos, clickedState.getBlock(), dockPos);
        if (dockPos == null) return;

        // keep Oritech's own designator targets working
        if (clickedState.is(BlockContent.LASER_ARM_BLOCK) || clickedState.is(BlockContent.DRONE_PORT_BLOCK)) return;
        if (level.getBlockEntity(clickedPos) instanceof LaserArmBlockEntity
                || level.getBlockEntity(clickedPos) instanceof DronePortEntity) return;

        // The machine the clicked block belongs to. Not an addon machine: leave the click to Oritech, which
        // then stores this position.
        var machinePos = oritechaddonsone$machineOf(level, clickedPos);
        if (machinePos == null) {
            OritechAddonsOne.LOGGER.debug("[diag] designator: {} is not an addon machine (be={})",
                    clickedPos, level.getBlockEntity(clickedPos));
            return;
        }

        var player = context.getPlayer();
        if (!level.isLoaded(dockPos)) {
            if (player != null) {
                player.sendSystemMessage(
                        Component.translatable("message.oritechaddonsone.wireless.not_loaded"));
            }
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (!(level.getBlockEntity(dockPos) instanceof WirelessExtensionAddonBlockEntity dock)) {
            OritechAddonsOne.LOGGER.debug("[diag] designator: stored {} is not a wireless dock (be={})",
                    dockPos, level.getBlockEntity(dockPos));
            return;
        }

        dock.linkTo(machinePos);

        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.oritechaddonsone.wireless.linked",
                    level.getBlockState(machinePos).getBlock().getName()));
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * The addon machine a position belongs to: the machine's own block, the controller behind a multiblock
     * core at that position, or the machine a mounted addon reports as its controller. Returns null for
     * everything else - in particular for our wireless docks, whose controller position is a link and not
     * a mount.
     */
    @Unique
    private static BlockPos oritechaddonsone$machineOf(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;

        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof WirelessExtensionAddonBlock) return null;

        // Multiblock machines keep their controller in a core block. Only a core block may be passed to the
        // helper: Oritech's version casts the block entity at that position to its core type.
        if (state.getBlock() instanceof MachineCoreBlock) {
            var controllerPos = MachineCoreBlock.getControllerPos(level, pos);
            return controllerPos != null && level.getBlockEntity(controllerPos) instanceof MachineAddonController
                    ? controllerPos : null;
        }

        if (level.getBlockEntity(pos) instanceof MachineAddonController) return pos;

        // An addon mounted on a machine reports that machine; an addon that is not mounted reports its own
        // position, which is not a machine.
        if (level.getBlockEntity(pos) instanceof MachineAddonProvider provider) {
            var controllerPos = provider.getControllerPos();
            if (controllerPos != null && !controllerPos.equals(pos)
                    && level.getBlockEntity(controllerPos) instanceof MachineAddonController) {
                return controllerPos;
            }
        }

        return null;
    }
}
