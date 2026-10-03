package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The per-face transfer modes of one Extension Addon / Wireless Extension Dock: which of its six faces feeds
 * the machine it works on, which one empties it and which one does both.
 * <p>
 * A face that has no entry transfers nothing. Like {@code ProxyFaceBindings} this is written into the block
 * entity's save data - the server is the authority and the page only draws what it reports - as one int per
 * face in {@link Direction#values()} order, so a face a later version adds simply reads as
 * {@link TransferMode#NONE}.
 */
public final class TransferFaceModes {

    private static final String TAG = "transfer_faces";

    /** One entry per configured face. */
    private final Map<Direction, TransferMode> modes = new EnumMap<>(Direction.class);

    /**
     * Mode of the given face; {@link TransferMode#NONE} while that face transfers nothing. Never
     * {@code null}, so a caller can always ask what a face does.
     */
    public TransferMode modeOf(@Nullable Direction face) {
        if (face == null) return TransferMode.NONE;
        return modes.getOrDefault(face, TransferMode.NONE);
    }

    /** True while the given face transfers items in either direction. */
    public boolean isConfigured(@Nullable Direction face) {
        return modeOf(face) != TransferMode.NONE;
    }

    /** Number of faces that transfer something, i.e. the "x" of the page's counter. */
    public int configuredFaces() {
        return modes.size();
    }

    /** True while no face transfers anything. */
    public boolean isEmpty() {
        return modes.isEmpty();
    }

    /** The configured faces, in the enum order of {@link Direction} (stable for logs). */
    public Map<Direction, TransferMode> entries() {
        return Collections.unmodifiableMap(modes);
    }

    /** Sets what the given face does; {@link TransferMode#NONE} removes the entry again. */
    public void set(Direction face, TransferMode mode) {
        if (face == null || mode == null || mode == TransferMode.NONE) {
            modes.remove(face);
            return;
        }
        modes.put(face, mode);
    }

    /** Drops every mode, which is what happens when the last transfer addon is taken out. */
    public void clear() {
        modes.clear();
    }

    /** Writes the modes into the block entity's save data. */
    public void save(ValueOutput output) {
        var faces = Direction.values();
        var values = new int[faces.length];
        for (int index = 0; index < faces.length; index++) {
            values[index] = modeOf(faces[index]).ordinal();
        }
        output.putIntArray(TAG, values);
    }

    /** Reads the modes from the block entity's save data; unknown values read as {@link TransferMode#NONE}. */
    public void load(ValueInput input) {
        modes.clear();

        var values = input.getIntArray(TAG).orElse(null);
        if (values == null) return;

        var faces = Direction.values();
        for (int index = 0; index < values.length && index < faces.length; index++) {
            var mode = TransferMode.byOrdinal(values[index]);
            if (mode != TransferMode.NONE) modes.put(faces[index], mode);
        }
    }
}
