package io.github.xiao232ming.oritechaddonsone.block.entity;

import dev.architectury.fluid.FluidStack;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.fluid.containers.DelegatingFluidStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;

/**
 * The fluid side of one face of Oritech's machine extender while an extension transfer plugin hangs on it - the
 * fluid counterpart of {@link ExtenderFaceStorage}.
 * <p>
 * Which faces are connections, which machine they belong to and which mode they transfer with is exactly the
 * item side's question ({@link ExtenderFaceStorage#transferMode}), so this class asks the very same helpers.
 * While the face has a mode it answers the machine's fluid tanks, gated by the mode; with no mode - which
 * includes every face of an extender no extension transfer plugin hangs on - it answers {@code null}, so a
 * pipe sees the extender as "nothing here".
 * <p>
 * The mode and the machine are resolved on every call, so a change takes effect on the next query.
 */
public final class ExtenderFaceFluidStorage extends DelegatingFluidStorage {

    private final AddonBlockEntity extender;
    private final Direction face;

    private ExtenderFaceFluidStorage(AddonBlockEntity extender, Direction face) {
        super(() -> machineStorage(extender),
                () -> ExtenderFaceStorage.transferMode(extender, face) != TransferMode.NONE);
        this.extender = extender;
        this.face = face;
    }

    /**
     * The fluid storage of one face of {@code extender}, or {@code null} while that face is not a fluid
     * connection: asked without a side, with no mode on that face, or when the block is not an extender.
     */
    @Nullable
    public static FluidApi.FluidStorage storageFor(BlockEntity host, @Nullable Direction face) {
        if (face == null || !(host instanceof AddonBlockEntity extender)) return null;
        if (ExtenderFaceStorage.transferMode(extender, face) == TransferMode.NONE) return null;
        return new ExtenderFaceFluidStorage(extender, face);
    }

    @Nullable
    private static FluidApi.FluidStorage machineStorage(AddonBlockEntity extender) {
        return MachineFaceStorage.machineFluidStorageAt(extender.getLevel(),
                ExtenderFaceStorage.machinePos(extender));
    }

    private TransferMode mode() {
        return ExtenderFaceStorage.transferMode(extender, face);
    }

    @Override
    public long insert(FluidStack toInsert, boolean simulate) {
        return mode().allowsInsert() ? super.insert(toInsert, simulate) : 0;
    }

    @Override
    public long extract(FluidStack toExtract, boolean simulate) {
        return mode().allowsExtract() ? super.extract(toExtract, simulate) : 0;
    }

    @Override
    public boolean supportsInsertion() {
        return mode().allowsInsert() && super.supportsInsertion();
    }

    @Override
    public boolean supportsExtraction() {
        return mode().allowsExtract() && super.supportsExtraction();
    }
}
