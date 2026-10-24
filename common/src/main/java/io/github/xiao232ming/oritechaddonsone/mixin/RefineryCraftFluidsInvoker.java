package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import rearth.oritech.block.entity.processing.RefineryBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

/**
 * Direct access to the fluid half of the refinery's craft.
 * <p>
 * {@code RefineryBlockEntity#craftItem} is split in two halves: the plain item craft (inherited, which the
 * chamber loop of {@code UpgradableMachineBlockEntity#craftItem} repeats) and {@code craftFluids}, which
 * consumes the fluid input and produces the fluid output. Only the first half lives in the method the loop
 * re-invokes, so the second half has to be repeated explicitly for every chamber repetition - see
 * {@link RefineryChamberFluidMixin}.
 * <p>
 * {@code craftFluids} is {@code private} on the refinery, so it cannot be called from the mixin that owns the
 * injection point (that one is applied to {@code UpgradableMachineBlockEntity}). Mixin resolves an
 * {@code @Invoker} on the class that declares the method, which is why this lives here and not next to the
 * injection.
 * <p>
 * The tainted refinery has its own twin in {@link TaintedRefineryCraftFluidsInvoker}: the two classes keep
 * their fluid storage and their output calculation separate, so each needs its own {@code @Invoker} - a
 * private method is not inherited.
 */
@Mixin(RefineryBlockEntity.class)
public interface RefineryCraftFluidsInvoker {

    /** {@code RefineryBlockEntity#craftFluids}: one craft's worth of fluid input consumed and output produced. */
    @Invoker("craftFluids")
    void oritechaddonsone$craftFluids(OritechRecipe activeRecipe);
}
