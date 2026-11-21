package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

/**
 * The fluid handler one face of a block answers with while that face is configured as a fluid connection -
 * the shared part of {@link MachineFaceFluidStorage} and {@link ExtenderFaceFluidStorage}.
 * <p>
 * The two handlers differ only in where their answer comes from: an extension addon owns the machine's
 * handlers itself, Oritech's machine extender has to find the machine first. Everything else is the same
 * question - offer the machine's tanks to the outside world, gated by the mode of the face - so it lives here:
 * {@link TransferMode#INPUT} only accepts fluid, {@link TransferMode#OUTPUT} only offers it and
 * {@link TransferMode#BOTH} does both. A face without a mode, or a machine without a tank, answers an empty
 * handler, so a pipe simply sees "nothing here".
 * <p>
 * A subclass is created once per face and kept (NeoForge caches the handler a face answers with); both hooks
 * are asked on every call, so a mode or machine change takes effect on the next query without replacing the
 * object.
 */
abstract class AbstractFaceFluidStorage implements ResourceHandler<FluidResource> {

    /** The machine's fluid handler while this face is configured, or {@code null} otherwise. */
    @Nullable
    protected abstract ResourceHandler<FluidResource> storage();

    /** The direction this face moves fluid in, as seen from the machine. */
    protected abstract TransferMode mode();

    @Override
    public int size() {
        var storage = storage();
        return storage == null ? 0 : storage.size();
    }

    @Override
    public FluidResource getResource(int index) {
        var storage = storage();
        return storage == null ? FluidResource.EMPTY : storage.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        var storage = storage();
        return storage == null ? 0 : storage.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        var storage = storage();
        return storage == null ? 0 : storage.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        var storage = storage();
        return storage != null && storage.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !mode().allowsInsert()) return 0;
        return storage.insert(index, resource, amount, transaction);
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !mode().allowsInsert()) return 0;
        return storage.insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !mode().allowsExtract()) return 0;
        return storage.extract(index, resource, amount, transaction);
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !mode().allowsExtract()) return 0;
        return storage.extract(resource, amount, transaction);
    }
}
