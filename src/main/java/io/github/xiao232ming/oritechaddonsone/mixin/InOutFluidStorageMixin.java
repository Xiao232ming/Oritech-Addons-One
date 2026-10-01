package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.transfer.fluid.InOutFluidStorage;
import rearth.oritech.util.ContainerSlotAssignment;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusHolder;
import io.github.xiao232ming.oritechaddonsone.network.TankSyncCodec;

/**
 * Raises the capacity of Oritech's {@code InOutFluidStorage} - the tank of the machines that keep an
 * input and an output tank in one handler (the centrifuge, the refinery's {@code ownStorage}, the tainted
 * refinery, the industrial chiller and the boilers of the generators) - by the bonus of this mod's tank
 * addons.
 * <p>
 * It is a class of its own next to {@code SimpleFluidStorage} and, unlike that one, it was not covered
 * before: the centrifuge reported 8000 mB and accepted 8000 mB with or without a tank addon, which is
 * exactly what a player sees as "the plugin does nothing".
 * <p>
 * Two places have to see the enlarged number, the same two as for the single slot tank:
 * {@code getCapacity()} (the number the machine's own logic and the GUI bar read) and the capacity the
 * inherited transfer API path uses for inserts and for the GUI slot
 * ({@code FluidStacksResourceHandler#getCapacity(int, FluidResource)}), which is covered by
 * {@code FluidStacksResourceHandlerMixin} through the shared {@link StorageBonusHolder}.
 * <p>
 * The bonus lives in the storage instance and is set by {@code MachineStorageBonuses}; it is not saved,
 * because the machine recomputes it from its plugins on every addon scan, but it is sent to the client
 * inside the tank's own sync payload so the client's tank reports the same capacity.
 */
@Mixin(value = InOutFluidStorage.class, priority = 1500)
public abstract class InOutFluidStorageMixin implements StorageBonusHolder {

    @Unique
    private long oritechaddonsone$baseCapacity;

    @Unique
    private long oritechaddonsone$capacityBonus;

    /**
     * Remembers the capacity Oritech's constructor was given, so the effective capacity can add the
     * bonus to it without calling the injected {@code getCapacity}.
     * <p>
     * Injected at {@code RETURN} and not at {@code HEAD}: a handler that runs before the {@code super()}
     * call of a constructor may not use the instance it is injected into, so Mixin requires it to be
     * static - which in turn could not write the instance field this needs to fill.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void oritechaddonsone$captureCapacity(int capacity, Runnable onUpdate,
            ContainerSlotAssignment slotAssignment, CallbackInfo callback) {
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
     * Item storages have no fluid slots; the two methods belong to {@link StorageBonusHolder} and are
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
        return this.oritechaddonsone$baseCapacity + this.oritechaddonsone$capacityBonus;
    }

    /**
     * Deletes everything above the effective capacity, in both slots. Called after the bonus was lowered
     * (a tank addon was removed), because the transfer API only limits what is inserted and never checks
     * the stored amount. The excess is not spilled: the tank is simply brought back into a valid state.
     */
    @Override
    public void oritechaddonsone$clampToCapacity() {
        var storage = (InOutFluidStorage) (Object) this;
        var effective = (int) Math.min(Integer.MAX_VALUE, oritechaddonsone$effectiveCapacity());

        for (var index = 0; index < storage.size(); index++) {
            if (storage.getAmountAsInt(index) > effective) {
                storage.set(index, storage.getResource(index), effective);
            }
        }
    }

    @Inject(method = "getCapacity", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$addCapacityBonus(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, oritechaddonsone$effectiveCapacity()));
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
     * See {@link TankSyncCodec} for why the client needs it at all: Oritech's world renderers draw the
     * stored fluid with a height of {@code amount / getCapacity()}.
     */
    @Inject(method = "getDeltaCodec", at = @At("RETURN"), cancellable = true)
    private void oritechaddonsone$carryCapacityBonus(
            CallbackInfoReturnable<StreamCodec<? extends ByteBuf, List<FluidStack>>> callback) {
        callback.setReturnValue(TankSyncCodec.withCapacityBonus(callback.getReturnValue(),
                this::oritechaddonsone$capacityBonus, this::oritechaddonsone$setCapacityBonus));
    }
}
