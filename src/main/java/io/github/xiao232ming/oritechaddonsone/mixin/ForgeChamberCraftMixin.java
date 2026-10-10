package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import rearth.oritech.block.base.entity.UpgradableMachineBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserChambers;

/**
 * Lets the atomic forge actually use the processing chambers of the lasers that charge it.
 * <p>
 * Oritech repeats a craft once per chamber in {@code UpgradableMachineBlockEntity#craftItem}, but that
 * loop stops before its first repetition on the forge: it asks {@code canProceed} again, and the forge
 * answers that with {@code hasEnoughEnergy()} - its energy buffer has to be completely <b>full</b>, and
 * the craft that just ran drained it to zero. The forge is the only machine that works that way (every
 * other machine spends a little energy per tick), so the items the chambers pay for were never produced.
 * <p>
 * This runs those repetitions explicitly for the forge. It is Oritech's own 26.x approach: there
 * {@code UpgradableMachineBlockEntity#craftChamberResults} repeats the craft without asking for energy
 * again, for exactly the same reason.
 * <p>
 * The single charge the forge already paid covers the extra items - the lasers' chamber addons are
 * charged through {@link AtomicForgeBlockEntityMixin} (see {@code oritechaddonsone$chargeForChambers}),
 * which multiplies that charge by the chambers' efficiency.
 * <p>
 * The injection targets {@code UpgradableMachineBlockEntity} rather than the forge because that is where
 * {@code craftItem} is declared, and it leaves every other machine to Oritech: the work below only runs
 * when the machine really is an {@link AtomicForgeBlockEntity}.
 */
@Mixin(UpgradableMachineBlockEntity.class)
public abstract class ForgeChamberCraftMixin implements ForgeBaseInvokers, MachineBaseInvokers {

    @Inject(method = "craftItem", at = @At("RETURN"))
    private void oritechaddonsone$repeatForgeCrafts(OritechRecipe activeRecipe, List<ItemStack> outputInventory,
                                                    List<ItemStack> inputInventory, CallbackInfo callback) {
        var machine = (UpgradableMachineBlockEntity) (Object) this;
        if (!(machine instanceof AtomicForgeBlockEntity forge)) return;
        if (!(forge.getLevel() instanceof ServerLevel serverLevel)) return;

        int chambers = ForgeLaserChambers.chambersOf(serverLevel, forge.getBlockPos());
        for (int i = 0; i < chambers; i++) {
            var recipe = oritechaddonsone$baseRecipe();
            if (recipe.isEmpty()
                    || !recipe.get().value().equals(activeRecipe)
                    || !forge.canOutputRecipe(activeRecipe)) {
                break;
            }

            oritechaddonsone$craftBase(activeRecipe, oritechaddonsone$outputView(), oritechaddonsone$inputView());
        }
    }
}
