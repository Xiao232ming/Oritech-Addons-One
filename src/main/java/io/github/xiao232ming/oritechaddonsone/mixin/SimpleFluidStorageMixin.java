package io.github.xiao232ming.oritechaddonsone.mixin;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.transfer.fluid.SimpleFluidStorage;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusHolder;

/**
 * Raises the capacity of Oritech's {@code SimpleFluidStorage} - the single tank class, used by the
 * machine tanks that are plain fields of a machine block entity (the refinery's {@code nodeA} /
 * {@code nodeB} for example) - by the bonus of this mod's tank addons.
 * <p>
 * On 26.1.2 the class extends NeoForge's {@code FluidStacksResourceHandler}, which reports its
 * constructor capacity for every slot. Oritech's own {@code getCapacity()} returns the same number, so
 * both are changed here: the {@code getCapacity} Oritech code and GUIs call, and the capacity the
 * transfer API uses for inserts.
 * <p>
 * Oritech's {@code InOutFluidStorage} is deliberately not touched: it is a separate class whose two
 * slots share one capacity, so enlarging it would have to change its field and is not part of this
 * feature.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is neither
 * synced nor saved, because the machine recomputes it from its plugins on every addon scan.
 */
@Mixin(value = SimpleFluidStorage.class, priority = 1500)
public abstract class SimpleFluidStorageMixin implements StorageBonusHolder {

    @Unique
    private long oritechaddonsone$baseCapacity;

    @Unique
    private long oritechaddonsone$capacityBonus;

    /**
     * Remembers the capacity Oritech's constructor was given, so
     * {@link #oritechaddonsone$effectiveCapacity()} can add the bonus to it without calling the injected
     * {@code getCapacity}.
     * <p>
     * Injected at {@code RETURN} and not at {@code HEAD}: a handler that runs before the {@code super()}
     * call of a constructor may not use the instance it is injected into, so Mixin requires it to be
     * static - which in turn could not write the instance field this needs to fill.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void oritechaddonsone$captureCapacity(int capacity, Runnable onUpdate, CallbackInfo callback) {
        this.oritechaddonsone$baseCapacity = capacity;
    }

    @Override
    public long oritechaddonsone$capacityBonus() {
        return this.oritechaddonsone$capacityBonus;
    }

    @Override
    public void oritechaddonsone$setCapacityBonus(long bonus) {
        this.oritechaddonsone$capacityBonus = Math.max(0L, bonus);
    }

    /**
     * Fluid storages have no item slots; the two methods belong to {@link StorageBonusHolder} and are
     * implemented (not applied) so the machine side can treat every storage holder the same way.
     */
    @Override
    public int oritechaddonsone$slotBonus() {
        return 0;
    }

    @Override
    public void oritechaddonsone$setSlotBonus(int bonus) {
        // no-op: a fluid tank has no item slots
    }

    /**
     * Deletes everything above the effective capacity. Called after the bonus was lowered (a tank addon
     * was removed), because the transfer API only limits what is inserted and never checks the stored
     * amount. The excess is not spilled: the tank is simply brought back into a valid state.
     */
    @Override
    public void oritechaddonsone$clampToCapacity() {
        var storage = (SimpleFluidStorage) (Object) this;
        var amount = storage.getAmount();

        if (amount > oritechaddonsone$effectiveCapacity()) {
            var resource = storage.getResource(0);
            storage.set(0, resource, (int) oritechaddonsone$effectiveCapacity());
        }
    }

    /** The capacity Oritech's constructor stored, plus the bonus of this mod's tank addons. */
    @Override
    public long oritechaddonsone$effectiveCapacity() {
        return this.oritechaddonsone$baseCapacity + this.oritechaddonsone$capacityBonus;
    }

    @Inject(method = "getCapacity", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$addCapacityBonus(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, oritechaddonsone$effectiveCapacity()));
    }

    /**
     * The capacity the transfer API is given while inserting lives in the inherited
     * {@code getCapacity(int, FluidResource)} of NeoForge's {@code FluidStacksResourceHandler}, which
     * cannot be targeted from this class (a mixin only sees the methods its own target declares or
     * overrides). It is injected by {@code FluidStacksResourceHandlerMixin} instead, which reads
     * {@link #oritechaddonsone$effectiveCapacity()} through the shared interface.
     */
}
