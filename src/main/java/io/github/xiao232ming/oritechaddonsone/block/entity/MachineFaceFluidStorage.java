package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.Direction;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import org.jetbrains.annotations.Nullable;

/**
 * The fluid side of one face of an Extension Addon / Wireless Extension Dock.
 * <p>
 * It is the counterpart of {@link MachineFaceStorage}: while the face has a transfer mode (the Extension
 * Transfer page, see {@link TransferFaceModes}) and the machine behind the block can be reached, the face
 * answers the machine's fluid handlers with the gating described in {@link AbstractFaceFluidStorage}. A face
 * without a mode, a block without a machine or a machine without a fluid tank answers an empty handler, so a
 * pipe simply sees "nothing here".
 * <p>
 * The object is created once per face and kept (NeoForge caches the handler a face answers with); the mode and
 * the machine are resolved on every call, so a change takes effect on the next query without replacing it.
 */
public final class MachineFaceFluidStorage extends AbstractFaceFluidStorage {

    private final ExtensionAddonBlockEntity owner;
    private final Direction face;

    MachineFaceFluidStorage(ExtensionAddonBlockEntity owner, Direction face) {
        this.owner = owner;
        this.face = face;
    }

    @Nullable
    @Override
    protected ResourceHandler<FluidResource> storage() {
        if (!owner.canTransferItems() || !owner.transferModes().isConfigured(face)) return null;
        return MachineFaceStorage.machineFluidStorage(owner);
    }

    @Override
    protected TransferMode mode() {
        return owner.transferModes().modeOf(face);
    }
}
