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
 * Raises the capacity of the <b>output</b> container of Oritech's {@code SimpleInOutFluidStorage}.
 * <p>
 * Same reasoning as {@link SimpleInOutFluidStorageInputMixin}, for the second of the two anonymous
 * containers: this is the one a machine's own processing inserts into (the centrifuge's fluid output
 * for example) and the one the screen's output bar reads, and it captured the capacity it was built
 * with just like the input container did.
 */
@Mixin(targets = "rearth.oritech.api.fluid.containers.SimpleInOutFluidStorage$2")
public abstract class SimpleInOutFluidStorageOutputMixin {

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
                oritechaddonsone$effectiveCapacity(), this.this$0.getOutStack(),
                stack -> this.this$0.setStack(1, stack)));
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
