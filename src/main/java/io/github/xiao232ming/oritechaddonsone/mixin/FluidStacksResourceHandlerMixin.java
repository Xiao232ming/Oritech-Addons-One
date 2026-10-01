package io.github.xiao232ming.oritechaddonsone.mixin;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusHolder;

/**
 * Applies the tank bonus of this mod's addons to the capacity the transfer API uses for inserts.
 * <p>
 * The machines of Oritech insert fluids through NeoForge's {@code ResourceHandler} API, and that path
 * asks {@code StacksResourceHandler#getCapacity(int, resource)} for the limit - a method of NeoForge's
 * base class which Oritech's tanks inherit without overriding. A mixin can only inject into the methods
 * its own target declares or overrides, so the injection has to happen here, on the class that declares
 * it, and the value is read back from the tank through {@link StorageBonusHolder}.
 * <p>
 * This one hook covers every tank class of Oritech that this mod can enlarge, single slot
 * ({@code SimpleFluidStorage}) and input/output ({@code InOutFluidStorage}) alike, and it is also the
 * number a machine's GUI slot reports, because NeoForge's {@code ResourceHandlerSlot} asks the handler
 * for the capacity of its slot. A tank without a bonus keeps the capacity NeoForge computed for it, so
 * every storage this mod did not explicitly enlarge is untouched.
 */
@Mixin(value = FluidStacksResourceHandler.class, priority = 1500)
public abstract class FluidStacksResourceHandlerMixin {

    @Inject(method = "getCapacity(ILnet/neoforged/neoforge/transfer/fluid/FluidResource;)I",
            at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$applyTankBonus(int index, FluidResource resource,
            CallbackInfoReturnable<Integer> callback) {
        if (!((Object) this instanceof StorageBonusHolder holder)) return;
        if (holder.oritechaddonsone$capacityBonus() <= 0L) return;

        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, holder.oritechaddonsone$effectiveCapacity()));
    }
}
