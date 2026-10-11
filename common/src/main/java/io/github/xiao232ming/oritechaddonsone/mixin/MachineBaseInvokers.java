package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

/**
 * Direct access to the recipe lookup and slot views of Oritech's machine base class.
 * <p>
 * Mixin resolves an {@code @Invoker} on the target class itself, not on its superclasses, so these live on
 * {@link MachineBlockEntity} - the class that declares them - while the {@code craftItem} invoker lives on
 * {@code UpgradableMachineBlockEntity}, which overrides that one. Both are mixed into the atomic forge
 * through {@code ForgeChamberCraftMixin}.
 * <p>
 * The point of reaching past the overrides:
 * <ul>
 * <li>{@code getRecipe}: the forge's own override re-sets its energy buffer to the recipe's plain cost,
 * which would throw away the chamber charge that
 * {@code AtomicForgeBlockEntityMixin#oritechaddonsone$chargeForChambers} applied.</li>
 * <li>{@code getInputView} / {@code getOutputView}: the live slot views the craft writes into; they are
 * protected, so the mixin cannot touch them directly.</li>
 * </ul>
 */
@Mixin(MachineBlockEntity.class)
public interface MachineBaseInvokers {

    /** {@code MachineBlockEntity#getRecipe}, without the atomic forge's energy buffer reset. */
    @Invoker("getRecipe")
    Optional<RecipeHolder<OritechRecipe>> oritechaddonsone$baseRecipe();

    /** {@code MachineBlockEntity#getInputView}, the live view of the machine's input slots. */
    @Invoker("getInputView")
    List<ItemStack> oritechaddonsone$inputView();

    /** {@code MachineBlockEntity#getOutputView}, the live view of the machine's output slots. */
    @Invoker("getOutputView")
    List<ItemStack> oritechaddonsone$outputView();
}
