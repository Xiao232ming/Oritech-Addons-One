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
 * and the drone port ({@code setTargetFromDesignator}), so this injection adds the missing case - in both
 * orders a player can click in:
 * <ul>
 *     <li>dock first: the stored position is a wireless dock and the clicked block is a machine, one of its
 *     addons or the core of a multiblock, and the dock is linked to that machine;</li>
 *     <li>machine first: the stored position is a machine and the clicked block is a wireless dock, which
 *     then joins that machine.</li>
 * </ul>
 * Either way the position that stays stored is the <b>machine's own block</b>, not the clicked addon and
 * not the dock, so the designator keeps pointing at the machine and every further dock that is clicked
 * joins the same one.
 * <p>
 * Shift clicking an addon or a multiblock core likewise stores the machine it belongs to. Oritech's own
 * designator targets (laser arm, drone port) and everything that is not an addon machine are left
 * untouched, and a stored position is only consumed when it really points at a loaded wireless dock.
 */
@Mixin(LaserTargetDesignator.class)
public class LaserTargetDesignatorMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$linkWirelessAddon(UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        var level = context.getLevel();
        if (level.isClientSide()) return;

        var stack = context.getItemInHand();
        var player = context.getPlayer();
        var storedPos = stack.get(ComponentContent.TARGET_POSITION.get());
        var clickedPos = context.getClickedPos();
        var clickedState = level.getBlockState(clickedPos);

        OritechAddonsOne.LOGGER.debug("[diag] designator useOn: clicked={} ({}), stored={}",
                clickedPos, clickedState.getBlock(), storedPos);

        // keep Oritech's own designator targets working
        if (clickedState.is(BlockContent.LASER_ARM_BLOCK) || clickedState.is(BlockContent.DRONE_PORT_BLOCK)) return;
        if (level.getBlockEntity(clickedPos) instanceof LaserArmBlockEntity
                || level.getBlockEntity(clickedPos) instanceof DronePortEntity) return;

        var clickedDock = level.getBlockEntity(clickedPos) instanceof WirelessExtensionAddonBlockEntity dock
                ? dock : null;
        var clickedMachine = clickedDock != null ? null : oritechaddonsone$machineOf(level, clickedPos);

        // Machine was stored first: a dock that is clicked now joins that machine. The stored position stays
        // the machine, so clicking further docks keeps binding them to the same one.
        if (clickedDock != null && storedPos != null) {
            var storedMachine = oritechaddonsone$machineOf(level, storedPos);
            if (storedMachine != null) {
                clickedDock.linkTo(storedMachine);
                oritechaddonsone$sendLinked(player, level, storedMachine);
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
        }

        // Dock was stored first: a machine, one of its addons or its multiblock core is clicked.
        if (storedPos != null && clickedMachine != null) {
            if (!level.isLoaded(storedPos)) {
                if (player != null) {
                    player.sendSystemMessage(
                            Component.translatable("message.oritechaddonsone.wireless.not_loaded"));
                }
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            if (!(level.getBlockEntity(storedPos) instanceof WirelessExtensionAddonBlockEntity dock)) {
                OritechAddonsOne.LOGGER.debug("[diag] designator: stored {} is not a wireless dock (be={})",
                        storedPos, level.getBlockEntity(storedPos));
                return;
            }

            dock.linkTo(clickedMachine);
            stack.set(ComponentContent.TARGET_POSITION.get(), clickedMachine);
            oritechaddonsone$sendLinked(player, level, clickedMachine);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        // Shift clicking an addon or a multiblock core stores the machine it belongs to, so a machine can be
        // picked up by clicking anything that sits on it.
        if (clickedMachine != null && !clickedMachine.equals(clickedPos) && player != null
                && player.isShiftKeyDown()) {
            stack.set(ComponentContent.TARGET_POSITION.get(), clickedMachine);
            player.sendSystemMessage(
                    Component.translatable("message.oritech.target_designator.position_stored"));
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Unique
    private static void oritechaddonsone$sendLinked(Player player, Level level, BlockPos machinePos) {
        if (player == null) return;

        player.sendSystemMessage(Component.translatable("message.oritechaddonsone.wireless.linked",
                level.getBlockState(machinePos).getBlock().getName()));
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
