package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import org.jetbrains.annotations.Nullable;

/**
 * The per-<b>cell-face</b> transfer settings of 传输插件: which <em>individual face of an individual cell</em> of the
 * machine structure moves items in which direction (see {@link TransferMode}) and whether it moves them by itself
 * (automation, see {@code TransferAddonBlockEntity#serverTickTransfer()}).
 * <p>
 * <b>Why a cell and a direction.</b> {@link TransferFaceModes} keys its settings by {@link Direction} alone, because an
 * Extension Addon configures the six faces of one block. This plugin's page draws the machine's whole assembled
 * structure, and a multiblock machine is several cells of world: the north face of the top-left cell and the north
 * face of the top-right cell are two different faces of the structure, and the user wants each of them on its own. A
 * direction is therefore not a key here - it is half of one, the other half being the cell it belongs to.
 * <p>
 * <b>There is no maximum.</b> The six-direction model was bounded by the six faces of one block; this one is bounded
 * only by the machine's own surface, so nothing in this class - and nothing on the page - counts towards a limit or
 * shows one. {@link #configuredFaces()} is a count of what is configured, never a fraction.
 * <p>
 * <b>Cells are named relative to the machine's controller block</b> ({@code (0,0,0)} is the controller's own cell),
 * which is the frame {@code MultiblockMachineController#getCorePositions()} reports and therefore the frame that
 * survives the machine being rotated: the offset is turned into a world direction by the machine's facing when a
 * container is looked up for the automation, so a structure that is broken and rebuilt facing the other way keeps its
 * settings pointing at the same <em>cells of the machine</em>.
 * <p>
 * <b>One int per configured face</b>, on the wire and in the save file: the cell offset in the low twelve bits, then
 * the direction, then the packed value {@link TransferFaceModes} already uses for a mode and its automation flag. The
 * packed value is read and written through {@link TransferFaceModes}, so the two models cannot disagree about what a
 * mode means.
 * <p>
 * <b>Old saves.</b> A world saved by a version with the direction-keyed model has one int per direction in
 * {@link #LEGACY_TAG}; {@link #load(net.minecraft.world.level.storage.ValueInput)} reads those as the six faces of the
 * <b>controller's own cell</b>, which is the only cell the old model could have meant - it described the machine as one
 * block. The old tag is still written as well, so a save that is opened by the older version again keeps the settings
 * it can represent.
 */
public final class CellFaceModes {

    /** Save tag of the cell-face settings. */
    private static final String TAG = "transfer_cell_faces";
    /** Save tag of the direction-keyed settings this model replaced, still written for older versions. */
    private static final String LEGACY_TAG = "transfer_faces";

    /** Bits of an entry: 4 per cell axis, then 3 for the direction, then the packed value. */
    private static final int AXIS_BITS = 4;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;
    private static final int AXIS_BIAS = 1 << (AXIS_BITS - 1);
    private static final int DIRECTION_SHIFT = 3 * AXIS_BITS;
    private static final int VALUE_SHIFT = DIRECTION_SHIFT + 3;
    /** Bits of the packed value an entry carries: the mode's ordinal and the automation flag. */
    private static final int VALUE_MASK = 0b111;

    /**
     * The cell offsets an entry can name, which is also the range the server accepts from a client: four signed bits
     * per axis, i.e. {@code -8 .. 7}. That is far more than a machine's structure needs - Oritech's largest part list
     * reaches two cells in every direction - and it is what keeps an entry one int.
     */
    public static final int MAX_CELL_OFFSET = AXIS_BIAS - 1;
    public static final int MIN_CELL_OFFSET = -AXIS_BIAS;

    /** One configured face of one cell: the cell relative to the machine's controller, and which of its faces. */
    public record Key(Vec3i cell, Direction face) {
    }

    /** One entry as the save file and the wire carry it: the cell offset, the face and the packed value. */
    public record Entry(Vec3i cell, Direction face, int value) {

        /** Mode this entry's face transfers with. */
        public TransferMode mode() {
            return TransferFaceModes.modeOf(value);
        }

        /** True while this entry's face moves items by itself. */
        public boolean automation() {
            return TransferFaceModes.automationOf(value);
        }
    }

    /** One packed value per configured cell-face. Insertion ordered, so the wire and the file are deterministic. */
    private final Map<Key, Integer> faces = new LinkedHashMap<>();

    /**
     * Mode of one face of one cell; {@link TransferMode#NONE} while that face transfers nothing. Never {@code null},
     * so a caller can always ask what a face does.
     */
    public TransferMode modeOf(Vec3i cell, @Nullable Direction face) {
        return TransferFaceModes.modeOf(packed(cell, face));
    }

    /** True while one face of one cell moves items by itself. Always false for a face without a mode. */
    public boolean automationOf(Vec3i cell, @Nullable Direction face) {
        return TransferFaceModes.automationOf(packed(cell, face));
    }

    /** True while one face of one cell transfers items in either direction. */
    public boolean isConfigured(Vec3i cell, @Nullable Direction face) {
        return modeOf(cell, face) != TransferMode.NONE;
    }

    /** True while anything at all is configured. */
    public boolean isEmpty() {
        return faces.isEmpty();
    }

    /**
     * Number of configured cell-faces, i.e. what the page reports. It is a count and never a fraction: this model has
     * no maximum (see the class comment).
     */
    public int configuredFaces() {
        return faces.size();
    }

    /** The packed value of one face of one cell, {@code 0} while it transfers nothing. */
    private int packed(Vec3i cell, @Nullable Direction face) {
        if (cell == null || face == null) return 0;
        return faces.getOrDefault(new Key(cell, face), 0);
    }

    /**
     * Sets what one face of one cell does; {@link TransferMode#NONE} removes the entry again, and with it the
     * automation flag - a face without a mode has nothing to move.
     *
     * @return true while the settings really changed, which is what lets the caller skip a redundant sync
     */
    public boolean set(Vec3i cell, Direction face, TransferMode mode, boolean automation) {
        if (cell == null || face == null || mode == null || mode == TransferMode.NONE) {
            return faces.remove(new Key(cell, face)) != null;
        }

        var value = TransferFaceModes.pack(mode, automation);
        var key = new Key(cell, face);
        if (value == 0) return faces.remove(key) != null;
        return !Integer.valueOf(value).equals(faces.put(key, value));
    }

    /** Drops every setting. */
    public void clear() {
        faces.clear();
    }

    /** The configured cell-faces and their packed values, for diagnostics. */
    public Map<Key, Integer> entries() {
        return Collections.unmodifiableMap(faces);
    }

    /** Every entry as the wire and the save file carry it, in insertion order. */
    public List<Entry> packedEntries() {
        var entries = new ArrayList<Entry>(faces.size());
        faces.forEach((key, value) -> entries.add(new Entry(key.cell(), key.face(), value)));
        return entries;
    }

    /** Replaces every setting with the given entries; used when the client is told what the server has. */
    public void setAll(List<Entry> entries) {
        faces.clear();
        for (var entry : entries) {
            if (!TransferFaceModes.isConfigured(entry.value())) continue;
            faces.put(new Key(entry.cell(), entry.face()), entry.value());
        }
    }

    /**
     * Writes the settings into the block entity's save data, and the direction-keyed form of the old model as well.
     * <p>
     * The legacy form is written for the controller's own cell only: it is a lossy view of this model (everything that
     * is not on the controller's cell has no place in it), and it exists so that a world which is opened by the older
     * version again keeps at least what that version could show. Nothing in this mod reads it back except the
     * migration in {@link #load(net.minecraft.world.level.storage.ValueInput)}.
     */
    public void save(net.minecraft.world.level.storage.ValueOutput output) {
        output.putIntArray(TAG, packedEntries().stream().mapToInt(CellFaceModes::pack).toArray());

        var directions = Direction.values();
        var legacy = new int[directions.length];
        for (int index = 0; index < directions.length; index++) {
            legacy[index] = packed(Vec3i.ZERO, directions[index]);
        }
        output.putIntArray(LEGACY_TAG, legacy);
    }

    /**
     * Reads the settings from the block entity's save data.
     * <p>
     * A world saved by the version with the direction-keyed model has no {@link #TAG} array but does have
     * {@link #LEGACY_TAG}: those six values are read as the six faces of the controller's own cell
     * ({@code (0,0,0)}), which is what that model described - it treated the whole machine as one block, and the
     * controller is the cell whose inventory and whose faces the page was addressing.
     */
    public void load(net.minecraft.world.level.storage.ValueInput input) {
        faces.clear();

        var values = input.getIntArray(TAG).orElse(null);
        if (values != null) {
            for (var value : values) {
                var entry = unpack(value);
                if (entry != null && TransferFaceModes.isConfigured(entry.value())) {
                    faces.put(new Key(entry.cell(), entry.face()), entry.value());
                }
            }
            return;
        }

        // no cell-face data: an older save, or a machine nothing was ever configured on
        var legacy = input.getIntArray(LEGACY_TAG).orElse(null);
        if (legacy == null) return;

        var directions = Direction.values();
        for (int index = 0; index < legacy.length && index < directions.length; index++) {
            if (!TransferFaceModes.isConfigured(legacy[index])) continue;
            faces.put(new Key(Vec3i.ZERO, directions[index]), legacy[index]);
        }
    }

    /** Packs one entry into the single int the save file and the packet carry. */
    public static int pack(Entry entry) {
        return pack(entry.cell(), entry.face(), entry.value());
    }

    /** Packs one cell offset, face and value into the single int the save file and the packet carry. */
    public static int pack(Vec3i cell, Direction face, int value) {
        return (cell.getX() + AXIS_BIAS & AXIS_MASK)
                | (cell.getY() + AXIS_BIAS & AXIS_MASK) << AXIS_BITS
                | (cell.getZ() + AXIS_BIAS & AXIS_MASK) << 2 * AXIS_BITS
                | (face.ordinal() & 0b111) << DIRECTION_SHIFT
                | (value & VALUE_MASK) << VALUE_SHIFT;
    }

    /**
     * The entry a packed int carries, or {@code null} while it names no face - an unknown direction, which is what a
     * modified client could send and what a newer save could hold.
     */
    @Nullable
    public static Entry unpack(int packed) {
        var cell = new Vec3i(
                (packed & AXIS_MASK) - AXIS_BIAS,
                (packed >> AXIS_BITS & AXIS_MASK) - AXIS_BIAS,
                (packed >> 2 * AXIS_BITS & AXIS_MASK) - AXIS_BIAS);

        var directions = Direction.values();
        var direction = packed >> DIRECTION_SHIFT & 0b111;
        if (direction < 0 || direction >= directions.length) return null;

        return new Entry(cell, directions[direction], packed >> VALUE_SHIFT & VALUE_MASK);
    }

    /**
     * True while the given offset could name a cell of a machine: the range one entry can express. The server checks
     * it as well as the machine's own part list, so an offset that is in range but not part of the structure is
     * refused too (see {@code TransferAddonBlockEntity#setTransferConfig}).
     */
    public static boolean isCellOffsetInRange(Vec3i cell) {
        return inRange(cell.getX()) && inRange(cell.getY()) && inRange(cell.getZ());
    }

    private static boolean inRange(int value) {
        return value >= MIN_CELL_OFFSET && value <= MAX_CELL_OFFSET;
    }

    /** The cell offset of a world position relative to the machine's controller block. */
    public static Vec3i offsetOf(BlockPos machinePos, BlockPos cellPos) {
        return new Vec3i(cellPos.getX() - machinePos.getX(), cellPos.getY() - machinePos.getY(),
                cellPos.getZ() - machinePos.getZ());
    }
}
