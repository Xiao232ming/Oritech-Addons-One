package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

import org.jetbrains.annotations.Nullable;

/**
 * The transfer settings of one <b>machine</b>, shared by every block that serves it.
 * <p>
 * <b>Why per machine and not per block.</b> A machine can be served by more than one transfer plugin: two
 * Extension Addons can stand around it, each holding one, and a wireless dock can be linked to it as well. The
 * settings describe the machine - which of its faces feed it and which empty it - so there is <b>one</b> map per
 * machine, and every plugin that serves that machine reads and writes it. That is what makes the page of one
 * plugin show what the player configured through another, and what makes every plugin act on the whole
 * configuration instead of only on its own share.
 * <p>
 * <b>Both pages live in the same map.</b> A setting is one face of one cell of the machine - the 传输插件
 * page's model - and the cube net page's six settings are the six faces of the controller's own cell
 * ({@code (0,0,0)}), which is exactly how {@link CellFaceModes} already reads a world saved by the older,
 * direction-keyed model. There is therefore no second map to keep in step: both pages write and read the same
 * entries, which is also why one machine can be configured from either page without the two disagreeing.
 * <p>
 * <b>Who fills it.</b> {@link #contribute} merges what a block had saved into the machine's map, so a world
 * saved before this class existed keeps everything and the plugins agree from the first tick. The blocks that
 * serve a machine call it while loading and whenever their contents change.
 * <p>
 * <b>Server side, runtime only.</b> The map lives while the server does, exactly like the wireless link index:
 * every block that serves the machine contributes what it has saved as soon as it is loaded, so the shared
 * settings are rebuilt from the blocks themselves instead of being saved twice. Blocks save their own copy as
 * well (see {@link #save}), which is what a world with a single plugin keeps byte for byte.
 */
public final class MachineFaceConfigs {

    /** The settings of every machine this server has seen, keyed by the machine's position. */
    private static final Map<BlockPos, Shared> SHARED = new ConcurrentHashMap<>();

    private MachineFaceConfigs() {
    }

    /** The two views of one machine: the six faces of one block and the individual faces of its cells. */
    private static final class Shared {
        private final TransferFaceModes faces = new TransferFaceModes();
        private final CellFaceModes cells = new CellFaceModes();
    }

    @Nullable
    private static Shared get(@Nullable BlockPos machine) {
        if (machine == null) return null;
        return SHARED.computeIfAbsent(machine.immutable(), ignored -> new Shared());
    }

    /**
     * The six-face settings of the machine, never {@code null}: a machine nobody configured answers an empty
     * map, which reads as "this face transfers nothing".
     */
    public static TransferFaceModes faceModes(@Nullable Level level, @Nullable BlockPos machine) {
        var shared = get(machine);
        return shared == null ? new TransferFaceModes() : shared.faces;
    }

    /** The cell-face settings of the machine, never {@code null} - same shape as {@link #faceModes}. */
    public static CellFaceModes cellFaces(@Nullable Level level, @Nullable BlockPos machine) {
        var shared = get(machine);
        return shared == null ? new CellFaceModes() : shared.cells;
    }

    /** The settings of a block, which are the settings of the machine it serves - or its own, empty ones. */
    public static TransferFaceModes faceModes(ExtensionAddonBlockEntity block) {
        return faceModes(block.getLevel(), block.servedMachinePos());
    }

    /** The cell-face settings of a block, i.e. of the machine it serves - see {@link #faceModes}. */
    public static CellFaceModes cellFaces(ExtensionAddonBlockEntity block) {
        return cellFaces(block.getLevel(), block.servedMachinePos());
    }

    /**
     * Merges what one block had saved into the machine's six-face settings, called while it loads.
     * <p>
     * The same rule as for the cell-faces: a face the machine already has is left alone, so a block that is
     * loaded later cannot overwrite what a plugin of the same machine configured.
     */
    public static void contribute(@Nullable Level level, @Nullable BlockPos machine, TransferFaceModes own) {
        var shared = get(machine);
        if (shared == null) return;

        for (var face : Direction.values()) {
            var mode = own.modeOf(face);
            if (mode == TransferMode.NONE) continue;
            shared.faces.set(face, mode, own.automationOf(face));
        }
    }

    /**
     * Merges what one block had saved into the machine's cell-face settings.
     * <p>
     * Called with the block's own map as it came out of the save file: every entry the machine does not have yet
     * is taken over, and one it already has is left alone - the shared map is the union of its serving blocks,
     * and a face another plugin configured must survive a block that does not know about it being loaded.
     */
    public static void contribute(@Nullable Level level, @Nullable BlockPos machine, CellFaceModes own) {
        var shared = get(machine);
        if (shared == null) return;

        for (var entry : own.packedEntries()) {
            // the block's own copy is what the player configured for this entry, so it wins over whatever the
            // machine's map holds - the map is a runtime cache, the block's data is the save file
            shared.cells.set(entry.cell(), entry.face(), entry.mode(), entry.automation());
        }
    }

    /**
     * Drops the settings of one machine, called when its last serving transfer plugin is taken away: a machine
     * nobody serves has no transfer settings.
     * <p>
     * The blocks that were serving it keep their own copy in their save data, so a plugin put back restores what
     * the player configured by contributing it again.
     */
    public static void forget(@Nullable Level level, @Nullable BlockPos machine) {
        if (machine == null) return;
        var dropped = SHARED.remove(machine);
        // INFO while "the config disappears" is being chased: this is the only place shared settings are
        // dropped, so a lost configuration either passes through here or was never in the map at all
        OritechAddonsOne.LOGGER.debug("[transfer] forget({}) dropped {} face(s)", machine,
                dropped == null ? 0 : dropped.cells.configuredFaces());
    }

    // ------------------------------------------------------------------ save data

    /**
     * Writes the machine's settings as one packed int per configured cell-face, into the save data of the block
     * that is asking, plus the machine's six faces.
     * <p>
     * Every block that serves the machine writes the whole map, so any one of them is enough to restore it. That
     * is deliberate: the alternative - electing one owner - would need the other blocks to know about it, and
     * this way a machine whose plugins are taken apart and rebuilt still comes back with its settings.
     */
    public static void save(ExtensionAddonBlockEntity block, ValueOutput output) {
        var faces = faceModes(block);
        var faceValues = new int[Direction.values().length];
        for (var face : Direction.values()) {
            faceValues[face.ordinal()] = TransferFaceModes.pack(faces.modeOf(face), faces.automationOf(face));
        }
        output.putIntArray(FACE_TAG, faceValues);

        var settings = cellFaces(block);
        var values = new ArrayList<Integer>(settings.configuredFaces());
        for (var entry : settings.packedEntries()) {
            values.add(CellFaceModes.pack(entry.cell(), entry.face(), entry.value()));
        }

        var packed = new int[values.size()];
        for (int index = 0; index < packed.length; index++) packed[index] = values.get(index);
        output.putIntArray(CELL_TAG, packed);
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
        if (values == null) return;

        for (var value : values) {
            var entry = CellFaceModes.unpack(value);
            if (entry == null || !TransferFaceModes.isConfigured(entry.value())) continue;
            own.set(entry.cell(), entry.face(), entry.mode(), entry.automation());
        }
    }

    /** Save tag of the machine's six faces. */
    private static final String CELL_TAG = "machine_faces";

    /** Save tag of the machine's six faces. */
    private static final String FACE_TAG = "machine_six_faces";

    /** Every machine position this server currently holds settings for - used by the diagnostics and the tests. */
    public static List<BlockPos> machines() {
        return List.copyOf(SHARED.keySet());
    }
}
