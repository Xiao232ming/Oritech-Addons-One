package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import rearth.oritech.init.ComponentContent;
import rearth.oritech.item.tools.LaserTargetDesignator;

/**
 * Right clicking the air with Oritech's target designator clears the position it stored.
 * <p>
 * Oritech's designator only ever overwrites its stored position, so a player who wants to drop a stored
 * block would have to aim at some other block first. The designator does not override {@code Item#use}
 * (it only implements {@code useOn}, which handles clicks on blocks), so the "click into the air"
 * gesture has to be added here.
 * <p>
 * Only the designator is affected: the injection returns immediately for every other item.
 */
@Mixin(Item.class)
public abstract class ItemUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$clearDesignatorTarget(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!((Object) this instanceof LaserTargetDesignator)) return;

        var stack = player.getItemInHand(hand);
        var targetType = ComponentContent.TARGET_POSITION.get();
        if (stack.get(targetType) == null) return;

        if (!level.isClientSide()) {
            stack.remove(targetType);
            player.sendSystemMessage(Component.translatable("message.oritechaddonsone.wireless.cleared"));
        }
        cir.setReturnValue(InteractionResultHolder.success(stack));
    }
}
