package io.github.xiao232ming.oritechaddonsone.mixin;

import dev.architectury.fluid.FluidStack;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.fluid.containers.SimpleFluidStorage;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;
import io.github.xiao232ming.oritechaddonsone.network.TankSyncCodec;

/**
 * Raises the capacity of Oritech's {@code SimpleFluidStorage} - the tank class of the machine tanks that
 * are plain fields of a machine block entity (the refinery's {@code nodeA} / {@code nodeB} for example) -
 * by the bonus of this mod's tank addons.
 * <p>
 * The class keeps its capacity in the final field {@code capacity} and never changes it, so the two
 * places that have to see the enlarged number are redirected/injected here:
 * <ul>
 *     <li>{@code getCapacity} (the number a GUI and the fluid API show),</li>
 *     <li>the {@code GETFIELD capacity} inside {@code insert}, which is what actually limits what fits
 *     in: {@code insertTo} is called with {@code capacity} and only fills the remaining space up to it.</li>
 * </ul>
 * {@code SimpleInOutFluidStorage} is deliberately not touched. It is a different class (not a subclass)
 * with one capacity shared by an input and an output container, so enlarging it would have to change its
 * single field and is not part of this feature.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is not saved,
 * because the machine recomputes it from its plugins on every addon scan, but it is sent to the client
 * inside the tank's own sync payload so the client's tank reports the same capacity.
 */
@Mixin(SimpleFluidStorage.class)
public abstract class SimpleFluidStorageMixin implements AddonStorageBonus {

    /** The capacity Oritech's constructor stored, shadowed so the bonus can be added without recursion. */
    @Shadow
    @Final
    private Long capacity;

    @Unique
    private long oritechaddonsone$capacityBonus;

    /**
     * The capacity Oritech stored, plus the bonus of this mod's tank addons.
     * <p>
     * Reads the shadowed field directly and never calls {@code getCapacity()}: that method is injected
     * below, so calling it here would recurse into this method forever.
     */
    @Override
    public long oritechaddonsone$effectiveCapacity() {
        return this.capacity + this.oritechaddonsone$capacityBonus;
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
     * Fluid storages have no item slots; the two methods belong to {@link AddonStorageBonus} and are
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
     * was removed), because {@code insert} only limits what is added and never checks the stored amount.
     * The excess is not spilled: the tank is simply brought back into a valid state.
     */
    @Override
    public void oritechaddonsone$clampToCapacity() {
        var stack = ((SimpleFluidStorage) (Object) this).getStack();
        if (stack.getAmount() > oritechaddonsone$effectiveCapacity()) {
            stack.setAmount(oritechaddonsone$effectiveCapacity());
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
                    target = "Lrearth/oritech/api/fluid/containers/SimpleFluidStorage;capacity:Ljava/lang/Long;",
                    opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private Long oritechaddonsone$enlargeInsertCapacity(SimpleFluidStorage storage) {
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
     * See {@link TankSyncCodec} for why the client needs it at all: Oritech draws the stored fluid with a
     * height of {@code amount / getCapacity()}.
     */
    @Inject(method = "getDeltaCodec", at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$carryCapacityBonus(
            CallbackInfoReturnable<StreamCodec<? extends ByteBuf, FluidStack>> callback) {
        callback.setReturnValue(TankSyncCodec.withCapacityBonus(callback.getReturnValue(),
                this::oritechaddonsone$capacityBonus, this::oritechaddonsone$setCapacityBonus));
    }
}
