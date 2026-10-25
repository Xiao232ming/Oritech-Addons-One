package io.github.xiao232ming.oritechaddonsone.block.entity;

import dev.architectury.fluid.FluidStack;

import net.minecraft.core.Direction;

import rearth.oritech.api.fluid.containers.DelegatingFluidStorage;

/**
 * The fluid side of one face of an Extension Addon / Wireless Extension Dock.
 * <p>
 * It is the counterpart of {@link MachineFaceStorage}: while the face has a transfer mode (the Extension
 * Transfer page, see {@link TransferFaceModes}) and the machine behind the block can be reached, the face
 * answers the machine's fluid tanks - {@link TransferMode#INPUT} only accepts fluid, {@link TransferMode#OUTPUT}
 * only offers it, and {@link TransferMode#BOTH} does both. A face without a mode, a block without a machine or
 * a machine without a tank answers an empty storage, so a pipe simply sees "nothing here".
 * <p>
 * The object is created once per face and kept; the mode and the machine are resolved on every call.
 */
public final class MachineFaceFluidStorage extends DelegatingFluidStorage {

    private final ExtensionAddonBlockEntity owner;
    private final Direction face;

    MachineFaceFluidStorage(ExtensionAddonBlockEntity owner, Direction face) {
        super(() -> MachineFaceStorage.machineFluidStorage(owner),
                () -> owner.canTransferItems() && owner.transferModes().isConfigured(face));
        this.owner = owner;
        this.face = face;
    }

    private boolean allowsInsert() {
        return owner.transferModes().modeOf(face).allowsInsert();
    }

    private boolean allowsExtract() {
        return owner.transferModes().modeOf(face).allowsExtract();
    }

    @Override
    public long insert(FluidStack toInsert, boolean simulate) {
        return allowsInsert() ? super.insert(toInsert, simulate) : 0;
    }

    @Override
    public long extract(FluidStack toExtract, boolean simulate) {
        return allowsExtract() ? super.extract(toExtract, simulate) : 0;
    }

    @Override
    public boolean supportsInsertion() {
        return allowsInsert() && super.supportsInsertion();
    }

    @Override
    public boolean supportsExtraction() {
        return allowsExtract() && super.supportsExtraction();
    }
}
