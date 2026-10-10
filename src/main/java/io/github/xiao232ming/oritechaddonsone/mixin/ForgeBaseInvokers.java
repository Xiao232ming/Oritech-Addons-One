package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.ItemStack;

import rearth.oritech.block.base.entity.UpgradableMachineBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

/**
 * Direct access to {@code MachineBlockEntity#craftItem} - the plain craft, without the repetition loop of
 * {@link UpgradableMachineBlockEntity#craftItem} that aborts on the atomic forge.
 * <p>
 * The invoker lives on {@link UpgradableMachineBlockEntity} because that class overrides {@code craftItem}
 * and so is the target of the injection that uses it (see {@code ForgeChamberCraftMixin}). Mixin resolves
 * an {@code @Invoker} on the target class itself, which is also why the recipe lookup and the slot views
 * sit in {@code MachineBaseInvokers} instead.
 */
@Mixin(UpgradableMachineBlockEntity.class)
public interface ForgeBaseInvokers {

    /** {@code MachineBlockEntity#craftItem}, resolved through the override that declares it here. */
    @Invoker("craftItem")
    void oritechaddonsone$craftBase(OritechRecipe activeRecipe, List<ItemStack> outputInventory,
            List<ItemStack> inputInventory);
}
