package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.ItemStack;

import rearth.oritech.block.base.entity.UpgradableMachineBlockEntity;
import rearth.oritech.block.entity.processing.RefineryBlockEntity;
import rearth.oritech.block.entity.processing.TaintedRefineryBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

/**
 * Lets the auxiliary processing chamber addon process the refinery's <b>fluid</b> output in parallel as well,
 * not only its item output.
 * <p>
 * <b>The bug.</b> Oritech repeats a craft once per chamber in
 * {@link UpgradableMachineBlockEntity#craftItem}: the method runs the plain craft and then loops
 * {@code addonData.extraChambers()} times, re-checking the recipe and the output room before each repetition.
 * That loop only ever calls {@code MachineBlockEntity#craftItem}, i.e. the <b>item</b> half of a craft. The
 * refinery is one of the machines that keep their fluid work elsewhere - {@code RefineryBlockEntity#craftItem}
 * is
 * <pre>
 * super.craftItem(activeRecipe, outputInventory, inputInventory);  // items, and the whole chamber loop
 * this.craftFluids(activeRecipe);                                  // fluids, exactly once
 * </pre>
 * so a refinery with N chambers produced {@code 1 + N} item results out of one fluid input: the fluid half
 * ran once while the item half ran {@code 1 + N} times. The tainted refinery
 * ({@link TaintedRefineryBlockEntity}) is written the same way and had the same bug.
 * <p>
 * <b>The fix.</b> One more fluid craft for every repetition the loop really performs, injected right after
 * the loop's own {@code super.craftItem} call - so the two halves stay in lockstep without this mod deciding
 * how often a repetition happens. Oritech's loop is the authority: it re-reads the recipe
 * ({@code getRecipe}, which for the refinery also re-checks the fluid input through
 * {@code recipeInputMatchesTank} - the tank must hold a full craft's worth) and it re-checks
 * {@code canOutputRecipe} (which the refinery overrides to require room for the fluid output) before every
 * repetition. Running our extra fluid craft after that call means it happens only where Oritech decided a
 * whole repetition is possible, so no fluid can be produced without its input and no output can overflow.
 * <p>
 * <b>Why {@code ordinal = 1}.</b> {@code UpgradableMachineBlockEntity#craftItem} contains exactly two calls to
 * {@code MachineBlockEntity#craftItem} with an identical descriptor: the base craft at the top of the method
 * (ordinal 0) and the repetition inside the chamber loop (ordinal 1). Only the second one is a repetition, so
 * only that one may repeat the fluid half - repeating it at the first call as well would double the base
 * craft's fluid. The injection is deliberately {@code required} (the config sets
 * {@code defaultRequire = 1}), so a future Oritech that reshapes this method fails the game at startup
 * instead of silently going back to one fluid craft per operation.
 * <p>
 * <b>Relation to the other two machines that already work.</b> The centrifuge has the same split but opts out
 * of Oritech's loop with {@code supportExtraChambersAuto() -> false} and runs its own loop that calls its
 * fluid half next to every item half; the forge is handled by {@code ForgeChamberCraftMixin} because Oritech's
 * loop aborts there for a different reason. Both refineries do neither, so this mixin is the missing third
 * case. On 26.x the machinery is transaction based ({@code createCraftingOutputs} /
 * {@code removeCraftingInputs}, which the refinery overrides to include the fluid half) and the problem does
 * not exist - this class is only there to bring 1.21.1 to the same behaviour.
 */
@Mixin(UpgradableMachineBlockEntity.class)
public abstract class RefineryChamberFluidMixin {

    /**
     * Runs the fluid half of the craft once per chamber repetition, right after the repetition's item half.
     * <p>
     * Only the two refineries have a fluid half that the loop bypasses; every other machine is left exactly as
     * Oritech wrote it.
     */
    @Inject(method = "craftItem",
            at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
                    target = "Lrearth/oritech/block/base/entity/MachineBlockEntity;craftItem"
                            + "(Lrearth/oritech/init/recipes/OritechRecipe;Ljava/util/List;Ljava/util/List;)V"))
    private void oritechaddonsone$repeatFluidCraft(OritechRecipe activeRecipe, List<ItemStack> outputInventory,
            List<ItemStack> inputInventory, CallbackInfo callback) {
        var machine = (Object) this;

        if (machine instanceof RefineryBlockEntity) {
            ((RefineryCraftFluidsInvoker) machine).oritechaddonsone$craftFluids(activeRecipe);
        } else if (machine instanceof TaintedRefineryBlockEntity) {
            ((TaintedRefineryCraftFluidsInvoker) machine).oritechaddonsone$craftFluids(activeRecipe);
        }
    }
}
