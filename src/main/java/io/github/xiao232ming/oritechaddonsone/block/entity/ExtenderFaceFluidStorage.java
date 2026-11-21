package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.block.entity.addons.AddonBlockEntity;

/**
 * The fluid side of one face of Oritech's machine extender while a placed transfer plugin hangs on it - the
 * fluid counterpart of {@link ExtenderFaceStorage}.
 * <p>
 * Which faces are connections, which machine they belong to and which mode they transfer with is exactly the
 * item side's question ({@link ExtenderFaceStorage#transferMode}), so this class asks the very same helpers.
 * While the face has a mode it answers the machine's fluid handlers with the gating described in
 * {@link AbstractFaceFluidStorage}; with no mode - which includes every face of an extender no extension
 * transfer plugin hangs on - it answers {@code null}, so a pipe sees the extender as "nothing here".
 * <p>
 * One handler per extender face, kept so NeoForge's capability cache always sees the same object; the plugin,
 * the mode and the machine are resolved on every call, so a change takes effect on the next query.
 */
public final class ExtenderFaceFluidStorage extends AbstractFaceFluidStorage {

    /** One handler per extender dimension/position, indexed by {@link Direction#ordinal()}. */
    private static final Map<GlobalPos, ExtenderFaceFluidStorage[]> HANDLERS = new ConcurrentHashMap<>();

    private final AddonBlockEntity extender;
    private final Direction face;

    private ExtenderFaceFluidStorage(AddonBlockEntity extender, Direction face) {
        this.extender = extender;
        this.face = face;
    }

    /**
     * The handler of one face of {@code extender}, or {@code null} while that face is not a fluid connection:
     * asked without a side, with no mode on that face, or when the extender is not claimed by a machine.
     */
    @Nullable
    public static ExtenderFaceFluidStorage handlerAt(AddonBlockEntity extender, @Nullable Direction face) {
        if (face == null || extender.getLevel() == null) return null;
        if (ExtenderFaceStorage.transferMode(extender, face) == TransferMode.NONE) return null;

        var key = GlobalPos.of(extender.getLevel().dimension(), extender.getBlockPos());
        var handlers = HANDLERS.computeIfAbsent(key, ignored -> new ExtenderFaceFluidStorage[Direction.values().length]);
        var existing = handlers[face.ordinal()];
        if (existing != null && existing.extender == extender) return existing;

        var created = new ExtenderFaceFluidStorage(extender, face);
        handlers[face.ordinal()] = created;
        return created;
    }

    /** Drops every handler, called when the server stops. */
    public static void clear() {
        HANDLERS.clear();
    }

    @Nullable
    @Override
    protected ResourceHandler<FluidResource> storage() {
        if (TransferMode.NONE == mode()) return null;
        return MachineFaceStorage.machineFluidStorageAt(extender.getLevel(),
                ExtenderFaceStorage.machinePos(extender));
    }

    @Override
    protected TransferMode mode() {
        return ExtenderFaceStorage.transferMode(extender, face);
    }
}
