package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The <b>3D transfer plugin's</b> per-machine settings: the individual faces of the machine's cells, shared by
 * every 3D transfer plugin that serves it.
 * <p>
 * <b>Only the cell-face model is shared.</b> The six faces of an extension transfer plugin are configured per
 * block (see {@code ExtensionAddonBlockEntity#transferModes()}) and never pass through this map, so two such
 * plugins on one machine keep independent settings. The 3D page paints the machine's cells, so its settings
 * describe the machine and are shared by every plugin standing on it ({@code TransferAddonPage}).
 * <p>
 * <b>Runtime only.</b> The map lives while the server does; each block contributes its saved cell settings when
 * it loads, so the shared map is rebuilt from the blocks themselves. Blocks save their own copy as well (see
 * {@link #save}).
 */
public final class MachineFaceConfigs {

    /** The settings of every machine this server has seen, keyed by its dimension and position. */
    private static final Map<GlobalPos, Shared> SHARED = new ConcurrentHashMap<>();

    private MachineFaceConfigs() {
    }

    /** The runtime view of one machine: the individual faces of its cells. */
    private static final class Shared {
        private final CellFaceModes cells = new CellFaceModes();
        private final CellFilters cellFilters = new CellFilters();
    }

    @Nullable
    private static Shared get(@Nullable Level level, @Nullable BlockPos machine) {
        if (level == null || machine == null) return null;
        return SHARED.computeIfAbsent(GlobalPos.of(level.dimension(), machine.immutable()), ignored -> new Shared());
    }

    /** The cell-face settings of the machine, never {@code null}: a machine nobody configured answers an empty map. */
    public static CellFaceModes cellFaces(@Nullable Level level, @Nullable BlockPos machine) {
        var shared = get(level, machine);
        return shared == null ? new CellFaceModes() : shared.cells;
    }

    /** The cell-face settings of a block, i.e. of the machine it serves - or its own, empty ones. */
    public static CellFaceModes cellFaces(ExtensionAddonBlockEntity block) {
        return cellFaces(block.getLevel(), block.servedMachinePos());
    }

    /** The cell-face item filters of the machine, never {@code null}. */
    public static CellFilters cellFilters(@Nullable Level level, @Nullable BlockPos machine) {
        var shared = get(level, machine);
        return shared == null ? new CellFilters() : shared.cellFilters;
    }

    /** The cell-face item filters of a block's machine. */
    public static CellFilters cellFilters(ExtensionAddonBlockEntity block) {
        return cellFilters(block.getLevel(), block.servedMachinePos());
    }

    /**
     * Merges what one block had saved into the machine's cell-face settings.
     * <p>
     * Called with the block's own map as it came out of the save file: every entry the machine does not have yet
     * is taken over, and one it already has is left alone - the shared map is the union of its serving blocks,
     * and a face another plugin configured must survive a block that does not know about it being loaded.
     */
    public static void contribute(@Nullable Level level, @Nullable BlockPos machine, CellFaceModes own) {
        var shared = get(level, machine);
        if (shared == null) return;

        for (var entry : own.packedEntries()) {
            // the block's own copy is what the player configured for this entry, so it wins over whatever the
            // machine's map holds - the map is a runtime cache, the block's data is the save file
            shared.cells.set(entry.cell(), entry.face(), entry.mode(), entry.automation());
        }
    }

    /** Merges what one block had saved into the machine's cell-face item filters - see {@link #contribute}. */
    public static void contribute(@Nullable Level level, @Nullable BlockPos machine, CellFilters own) {
        var shared = get(level, machine);
        if (shared == null) return;

        own.entries().forEach((key, data) -> shared.cellFilters.set(key.cell(), key.face(), key.flow(), data));
    }

    /** Drops every machine's runtime settings, called when the server stops. */
    public static void clear() {
        SHARED.clear();
    }

    // ------------------------------------------------------------------ save data

    /**
     * Writes this block's six-face settings and the machine's shared cell settings into the block's save data.
     * <p>
     * The six-face model is per block, so it is always written from the block's own copy. The cell-face model is
     * shared per machine: while the machine can be named the shared map is the union of its serving blocks and
     * is written; without a machine the block writes its own cell copy, because writing an empty shared map
     * would erase what the block still carries.
     */
    public static void save(ExtensionAddonBlockEntity block, ValueOutput output) {
        var machine = block.servedMachinePos();

        // The six-face transfer model belongs to the block, not to the machine: save its own map even while a
        // machine is named, otherwise a second extension transfer plugin (or a save after an unload) would
        // replace this block's settings with the shared map's - which no longer holds them.
        var faces = block.savedTransferFaces();
        var faceValues = new int[Direction.values().length];
        for (var face : Direction.values()) {
            faceValues[face.ordinal()] = TransferFaceModes.pack(faces.modeOf(face), faces.automationOf(face));
        }
        output.putIntArray(FACE_TAG, faceValues);

        // The cell-faces still belong to the machine (3D transfer plugin) and follow the shared map while it
        // can be named.
        var settings = machine == null ? block.savedCellFaces() : cellFaces(block);
        var values = new ArrayList<Integer>(settings.configuredFaces());
        for (var entry : settings.packedEntries()) {
            values.add(CellFaceModes.pack(entry.cell(), entry.face(), entry.value()));
        }

        var packed = new int[values.size()];
        for (int index = 0; index < packed.length; index++) packed[index] = values.get(index);
        output.putIntArray(CELL_TAG, packed);

        // six-face filters are per block too; cell filters follow the shared map.
        block.savedFaceFilters().save(output);
        if (machine == null) {
            block.savedCellFilters().save(output);
        } else {
            cellFilters(block).save(output);
        }
    }

    /** Reads the machine's settings back out of the block's save data; see {@link #save}. */
    public static void load(ExtensionAddonBlockEntity block, ValueInput input) {
        // Read into the block's own map, never into the machine's, and never mind whether the machine can be
        // named yet: while a world loads, the block entity is still being built and the controller offset that
        // names its machine is written by Oritech afterwards - so asking for the machine here answers "none" and
        // the settings on disk were dropped on every single load. The merge into the machine's map happens as
        // soon as the machine really can be named (see ExtensionAddonBlockEntity#syncCellFaces, which the tick
        // keeps retrying until it can).
        var own = block.savedCellFaces();

        var faceValues = input.getIntArray(FACE_TAG).orElse(null);
        if (faceValues != null) {
            var ownFaces = block.savedTransferFaces();
            var directions = Direction.values();
            for (int index = 0; index < faceValues.length && index < directions.length; index++) {
                if (!TransferFaceModes.isConfigured(faceValues[index])) continue;
                ownFaces.set(directions[index], TransferFaceModes.modeOf(faceValues[index]),
                        TransferFaceModes.automationOf(faceValues[index]));
            }
        }

        var values = input.getIntArray(CELL_TAG).orElse(null);
        if (values != null) {
            for (var value : values) {
                var entry = CellFaceModes.unpack(value);
                if (entry == null || !TransferFaceModes.isConfigured(entry.value())) continue;
                own.set(entry.cell(), entry.face(), entry.mode(), entry.automation());
            }
        }

        // same rule for the filters: they are read into the block's own copies, never into the machine's, because
        // while a world loads the machine cannot be named yet (see above) - syncCellFaces merges them later
        block.savedFaceFilters().load(input);
        block.savedCellFilters().load(input);
    }

    /** Save tag of the machine's six faces. */
    private static final String CELL_TAG = "machine_faces";

    /** Save tag of the machine's six faces. */
    private static final String FACE_TAG = "machine_six_faces";
}
