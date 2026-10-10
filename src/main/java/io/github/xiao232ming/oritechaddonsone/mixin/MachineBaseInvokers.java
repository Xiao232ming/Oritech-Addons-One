package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.ItemStack;

import rearth.oritech.block.base.entity.MachineBlockEntity;

/**
 * Direct access to {@code MachineBlockEntity#getInputView}, which the atomic forge's chamber charge needs to
 * count how many items an operation will really produce.
 * <p>
 * The view is protected, so the mixin cannot touch it directly, and the invoker has to live on the class that
 * declares it - Mixin resolves an {@code @Invoker} on the target class itself, not on its subclasses. Added to
 * the forge through {@code AtomicForgeBlockEntityMixin}, which implements this interface.
 */
@Mixin(MachineBlockEntity.class)
public interface MachineBaseInvokers {

    /** {@code MachineBlockEntity#getInputView}, the machine's input slots. */
    @Invoker("getInputView")
    List<ItemStack> oritechaddonsone$inputView();
}
