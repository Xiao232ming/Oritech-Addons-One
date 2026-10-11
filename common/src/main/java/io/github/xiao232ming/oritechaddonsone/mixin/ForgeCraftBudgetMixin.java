package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rearth.oritech.block.base.entity.UpgradableMachineBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

import io.github.xiao232ming.oritechaddonsone.forge.ForgeCraftBudget;
import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserChambers;

/**
 * Caps an atomic forge's operation at {@code 1 + chambers} items, the parallel limit its charging lasers'
 * processing chambers define.
 * <p>
 * Oritech bounds the repetition by the output space instead: {@code canOutputRecipe} ends in
 * {@code canAddToSlot}, which compares the slot's content against the slot's own maximum, and the forge
 * re-fills its buffer every tick and crafts once per tick. A forge charged by a laser with 96 chambers
 * therefore worked through its <b>whole input</b> - measured, the ingredients for 128 items became 128 items
 * in one go instead of 97. The chamber count is the only thing that should decide how much one operation
 * produces; room in the output slot is not a reason to produce more.
 * <p>
 * One operation is one tick's work, so the tick's first craft is the base craft and every further craft is a
 * chamber repetition, of which the tick gets exactly {@code chambers}. See {@link ForgeCraftBudget}.
 * <p>
 * Only the forge is affected; every other machine keeps Oritech's behaviour untouched.
 */
@Mixin(UpgradableMachineBlockEntity.class)
public abstract class ForgeCraftBudgetMixin {

    @Inject(method = "craftItem", at = @At("HEAD"))
    private void oritechaddonsone$openChamberRepeats(OritechRecipe activeRecipe, List<ItemStack> outputInventory,
                                                     List<ItemStack> inputInventory, CallbackInfo callback) {
        var machine = (UpgradableMachineBlockEntity) (Object) this;
        if (!(machine instanceof AtomicForgeBlockEntity forge)) {
            ForgeCraftBudget.forget(machine);
            return;
        }
        if (!(forge.getLevel() instanceof ServerLevel serverLevel)) return;

        ForgeCraftBudget.open(forge, serverLevel.getGameTime(),
                ForgeLaserChambers.chambersOf(serverLevel, forge.getBlockPos()));
    }
}
