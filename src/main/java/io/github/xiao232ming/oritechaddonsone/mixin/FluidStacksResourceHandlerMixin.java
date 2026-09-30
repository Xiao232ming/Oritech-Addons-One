package io.github.xiao232ming.oritechaddonsone.mixin;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.transfer.fluid.SimpleFluidStorage;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusHolder;

/**
 * Applies the tank bonus of this mod's addons to the capacity the transfer API uses for inserts.
 * <p>
 * The machines of Oritech insert fluids through NeoForge's {@code ResourceHandler} API, and that path
 * asks {@code StacksResourceHandler#getCapacity(int, resource)} for the limit - a method of NeoForge's
 * base class which Oritech's {@code SimpleFluidStorage} inherits without overriding. A mixin can only
 * inject into the methods its own target declares or overrides, so the injection has to happen here, on
 * the class that declares it, and the value is read back from the tank through
 * {@link StorageBonusHolder}.
 * <p>
 * Only the tanks this feature covers are changed: Oritech's own tank class
 * ({@code rearth.oritech.api.transfer.fluid.SimpleFluidStorage}) and nothing else. The guard matters
 * because the same base class also backs Oritech's input/output tanks, which this feature leaves alone.
 */
@Mixin(value = FluidStacksResourceHandler.class, priority = 1500)
public abstract class FluidStacksResourceHandlerMixin {

    @Inject(method = "getCapacity(ILnet/neoforged/neoforge/transfer/fluid/FluidResource;)I",
            at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$applyTankBonus(int index, FluidResource resource,
            CallbackInfoReturnable<Integer> callback) {
        if (!((Object) this instanceof SimpleFluidStorage)) return;
        if (!((Object) this instanceof StorageBonusHolder holder)) return;
        if (holder.oritechaddonsone$capacityBonus() <= 0L) return;

        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, holder.oritechaddonsone$effectiveCapacity()));
    }
}
