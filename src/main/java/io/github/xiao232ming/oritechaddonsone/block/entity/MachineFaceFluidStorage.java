package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.Direction;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jetbrains.annotations.Nullable;

/**
 * The fluid side of one face of an Extension Addon / Wireless Extension Dock.
 * <p>
 * It is the counterpart of {@link MachineFaceStorage}: while the face has a transfer mode (the Extension
 * Transfer page, see {@link TransferFaceModes}) and the machine behind the block can be reached, the face
 * answers the machine's fluid handlers - {@link TransferMode#INPUT} only accepts fluid, {@link TransferMode#OUTPUT}
 * only offers it, and {@link TransferMode#BOTH} does both. A face without a mode, a block without a machine or
 * a machine without a fluid tank answers an empty handler, so a pipe simply sees "nothing here".
 * <p>
 * The object is created once per face and kept (NeoForge caches the handler a face answers with); the mode and
 * the machine are resolved on every call, so a change takes effect on the next query without replacing it.
 */
public final class MachineFaceFluidStorage implements ResourceHandler<FluidResource> {

    private final ExtensionAddonBlockEntity owner;
    private final Direction face;

    MachineFaceFluidStorage(ExtensionAddonBlockEntity owner, Direction face) {
        this.owner = owner;
        this.face = face;
    }

    /** The machine's fluid handler while this face is configured, or {@code null} otherwise. */
    @Nullable
    private ResourceHandler<FluidResource> storage() {
        if (!owner.canTransferItems() || !owner.transferModes().isConfigured(face)) return null;
        return MachineFaceStorage.machineFluidStorage(owner);
    }

    private boolean allowsInsert() {
        return owner.transferModes().modeOf(face).allowsInsert();
    }

    private boolean allowsExtract() {
        return owner.transferModes().modeOf(face).allowsExtract();
    }

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
        if (storage == null || !allowsInsert()) return 0;
        return storage.insert(index, resource, amount, transaction);
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !allowsInsert()) return 0;
        return storage.insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !allowsExtract()) return 0;
        return storage.extract(index, resource, amount, transaction);
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        var storage = storage();
        if (storage == null || !allowsExtract()) return 0;
        return storage.extract(resource, amount, transaction);
    }
}
