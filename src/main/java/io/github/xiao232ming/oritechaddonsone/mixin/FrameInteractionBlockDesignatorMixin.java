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

import rearth.oritech.block.base.block.FrameInteractionBlock;

import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinking;

/**
 * Keeps the target designator usable on machines that open a GUI.
 * <p>
 * Vanilla runs the block interaction first and only falls through to {@code Item#useOn} when the block
 * passes on it (in 1.21.1: {@code useItemOn} passes and {@code useWithoutItem} does not consume). A
 * machine opens its menu in {@code useWithoutItem}, so holding the designator and right clicking a
 * machine would simply open the machine - the designator never sees the click and the wireless dock
 * could never be linked to it.
 * <p>
 * While the player holds a designator with a stored position, the machine therefore passes on the
 * interaction, exactly like Oritech's own laser arm and drone port do. Frame interaction machines open their menu directly instead of delegating to MachineBlock.
 */
@Mixin(FrameInteractionBlock.class)
public class FrameInteractionBlockDesignatorMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$keepClickForDesignator(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (WirelessLinking.isHoldingLinkDesignator(player)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}