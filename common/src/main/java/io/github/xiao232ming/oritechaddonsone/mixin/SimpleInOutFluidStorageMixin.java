package io.github.xiao232ming.oritechaddonsone.mixin;

import dev.architectury.fluid.FluidStack;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Tuple;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.fluid.containers.SimpleInOutFluidStorage;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;
import io.github.xiao232ming.oritechaddonsone.network.TankSyncCodec;

/**
 * Raises the capacity of Oritech's {@code SimpleInOutFluidStorage} - the tank of the machines that keep
 * an input and an output tank in one object (the centrifuge, the refinery's {@code ownStorage}, the
 * tainted refinery and the boiler of Oritech's generators) - by the bonus of this mod's tank addons.
 * <p>
 * It is a class of its own and not a subclass of {@code SimpleFluidStorage}, so the mixin of that class
 * never saw these tanks: the centrifuge reported 8000 mB and accepted 8000 mB with or without a tank
 * addon, which is exactly what a player sees as "the plugin does nothing".
 * <p>
 * Like {@code SimpleFluidStorage} the class keeps its capacity in a final field and never changes it, so
 * the same two places are hooked: {@code getCapacity} (the number the machine's own logic and the GUI
 * bar read) and the {@code capacity} field read inside {@code insert}, which is what actually limits
 * what fits in. The two per-slot containers this class hands out carry their own copy of the capacity
 * and are handled by {@code SimpleInOutFluidStorageInputMixin} / {@code ...OutputMixin}.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is not saved,
 * because the machine recomputes it from its plugins on every addon scan, but it is sent to the client
 * inside the tank's own sync payload so the client's tank reports the same capacity.
 */
@Mixin(SimpleInOutFluidStorage.class)
public abstract class SimpleInOutFluidStorageMixin implements AddonStorageBonus {

    /** The capacity Oritech's constructor stored, shadowed so the bonus can be added without recursion. */
    @Shadow
    @Final
    private Long capacity;

    @Unique
    private long oritechaddonsone$capacityBonus;

    @Override
    public long oritechaddonsone$capacityBonus() {
        return this.oritechaddonsone$capacityBonus;
    }

    @Override
    public void oritechaddonsone$setCapacityBonus(long bonus) {
        this.oritechaddonsone$capacityBonus = Math.max(0L, bonus);
    }

    /**
     * Item storages have no fluid slots; the two methods belong to {@link AddonStorageBonus} and are
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

    /** The capacity Oritech's constructor stored, plus the bonus of this mod's tank addons. */
    @Override
    public long oritechaddonsone$effectiveCapacity() {
        return this.capacity + this.oritechaddonsone$capacityBonus;
    }

    /**
     * Deletes everything above the effective capacity, in both slots. Called after the bonus was lowered
     * (a tank addon was removed), because {@code insertTo} only limits what is added and never checks
     * the stored amount. The excess is not spilled: the tank is simply brought back into a valid state.
     */
    @Override
    public void oritechaddonsone$clampToCapacity() {
        var storage = (SimpleInOutFluidStorage) (Object) this;
        var effective = oritechaddonsone$effectiveCapacity();

        for (var slot = 0; slot < storage.getSlotCount(); slot++) {
            var stack = storage.getStack(slot);
            if (stack.getAmount() > effective) {
                stack.setAmount(effective);
            }
        }
    }

    @Inject(method = "getCapacity", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$addCapacityBonus(CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(oritechaddonsone$effectiveCapacity());
    }

    /**
     * The {@code capacity} field read inside {@code insert}, i.e. the {@code capacity} argument of
     * {@code SimpleInOutFluidStorage#insertTo}.
     */
    @Redirect(
            method = "insert",
            at = @At(
                    value = "FIELD",
                    target = "Lrearth/oritech/api/fluid/containers/SimpleInOutFluidStorage;capacity:Ljava/lang/Long;",
                    opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private Long oritechaddonsone$enlargeInsertCapacity(SimpleInOutFluidStorage storage) {
        return oritechaddonsone$effectiveCapacity();
    }

    /**
     * Sends the bonus with the contents and reads it back on the client, so the client's tank reports the
     * same capacity the server applied instead of the one Oritech's constructor built.
     * <p>
     * The bonus is not synced anywhere else, because the machine recomputes it from its addons and the
     * client cannot: the addon inventories live in block entities of this mod whose contents are not part
     * of any Oritech sync payload. Riding on the tank's own payload also means the number can never arrive
     * in a different packet than the amount in it.
     * <p>
     * See {@link TankSyncCodec} for why the client needs it at all: Oritech's refinery renderer draws the
     * stored fluid with a height of {@code amount / getCapacity()}.
     */
    @Inject(method = "getDeltaCodec", at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$carryCapacityBonus(
            CallbackInfoReturnable<StreamCodec<? extends ByteBuf, Tuple<FluidStack, FluidStack>>> callback) {
        callback.setReturnValue(TankSyncCodec.withCapacityBonus(callback.getReturnValue(),
                this::oritechaddonsone$capacityBonus, this::oritechaddonsone$setCapacityBonus));
    }
}
