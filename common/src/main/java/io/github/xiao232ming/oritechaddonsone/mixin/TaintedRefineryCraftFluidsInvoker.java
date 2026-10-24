package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import rearth.oritech.block.entity.processing.TaintedRefineryBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

/**
 * The tainted refinery's twin of {@link RefineryCraftFluidsInvoker}.
 * <p>
 * {@code TaintedRefineryBlockEntity#craftItem} is written exactly like the ordinary refinery's - the plain
 * item craft first, its own {@code craftFluids} once afterwards - so it has the same chamber bug and the same
 * fix. The two {@code craftFluids} methods are separate private methods that write into different storages
 * ({@code ownStorage} here, {@code ownStorage} plus the module nodes there) and therefore cannot share one
 * invoker.
 */
@Mixin(TaintedRefineryBlockEntity.class)
public interface TaintedRefineryCraftFluidsInvoker {

    /** {@code TaintedRefineryBlockEntity#craftFluids}: one craft's worth of fluid input consumed and output produced. */
    @Invoker("craftFluids")
    void oritechaddonsone$craftFluids(OritechRecipe activeRecipe);
}
