package io.github.xiao232ming.oritechaddonsone.mixin;

import dev.architectury.fluid.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.fluid.containers.SimpleInOutFluidStorage;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;

/**
 * Raises the capacity of the <b>input</b> container of Oritech's {@code SimpleInOutFluidStorage}.
 * <p>
 * The class creates that container as an anonymous class which captures the capacity it was built with
 * (and, being a {@code Long}, a copy of it), so raising the capacity of the storage itself is not enough:
 * the machine's GUI bar reads {@code getCapacity} of these containers, the machine's own processing
 * inserts its output through the matching output container, and the bucket interaction of the screen
 * inserts into whichever of the two the player clicked. All of those went through the captured, never
 * changing number and therefore ignored the tank addons.
 * <p>
 * Both methods that use the captured number are covered: {@code getCapacity} gets the bonus added, and
 * {@code insert} is answered directly by calling Oritech's own
 * {@code SimpleInOutFluidStorage#insertTo} with the effective capacity, so the insert limit is the same
 * as the one the storage reports. The value is read from the enclosing storage, so the bonus has a
 * single owner and can never drift apart from it.
 */
@Mixin(targets = "rearth.oritech.api.fluid.containers.SimpleInOutFluidStorage$1")
public abstract class SimpleInOutFluidStorageInputMixin {

    /** The storage this container belongs to; it owns the capacity and the bonus. */
    @Shadow
    @Final
    private SimpleInOutFluidStorage this$0;

    @Inject(method = "getCapacity", at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$addCapacityBonus(CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(callback.getReturnValue() + oritechaddonsone$capacityBonus());
    }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$enlargeInsert(FluidStack toInsert, boolean simulate,
            CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(SimpleInOutFluidStorage.insertTo(toInsert, simulate,
                oritechaddonsone$effectiveCapacity(), this.this$0.getInStack(),
                stack -> this.this$0.setStack(0, stack)));
    }

    /** The bonus of the enclosing storage, or {@code 0} while that storage was not touched by us. */
    @Unique
    private long oritechaddonsone$capacityBonus() {
        return this.this$0 instanceof AddonStorageBonus storage ? storage.oritechaddonsone$capacityBonus() : 0L;
    }

    /** The capacity the enclosing storage really has, bonus included. */
    @Unique
    private long oritechaddonsone$effectiveCapacity() {
        return this.this$0 instanceof AddonStorageBonus storage
                ? storage.oritechaddonsone$effectiveCapacity()
                : this.this$0.getCapacity();
    }
}
