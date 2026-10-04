package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The per-<b>cell-face</b> item filters of one machine: which items may cross one <em>face of one cell</em> of the
 * machine structure, and in which direction - the filter half of the 传输插件 page, and the counterpart of
 * {@link CellFaceModes}.
 * <p>
 * <b>Why a cell and a direction, and a direction of the movement as well.</b> The cell is half of the key for the
 * reason {@link CellFaceModes} gives: a structure is several cells of world, and the north face of the top-left
 * cell and the north face of the top-right cell are two different faces of the machine. The direction of the
 * <em>movement</em> is the other half, for the reason {@link FaceFilters} gives: what may enter the machine and
 * what may leave it are two different questions, and a cell-face set to 输入 + 输出 really has to answer both
 * separately. So one cell-face can carry two filters, and a 3x3x3 structure can carry 324 of them.
 * <p>
 * <b>There is no maximum</b>, exactly like the mode map this mirrors - the bound is the machine's own surface,
 * and nothing in here counts towards a limit or shows one.
 * <p>
 * <b>Cells are named relative to the machine's controller block</b> ({@code (0,0,0)} is the controller's own
 * cell), so a structure that is broken and rebuilt facing the other way keeps its filters pointing at the same
 * cells of the machine. The offsets reuse {@link CellFaceModes}' own packing, which is what lets a filter and the
 * mode behind it name the same face with the same three numbers.
 */
public final class CellFilters {

    /** Bits of a packed key: 4 per cell axis, then 3 for the direction, then 2 for the direction of the movement. */
    private static final int AXIS_BITS = 4;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;
    private static final int AXIS_BIAS = 1 << (AXIS_BITS - 1);
    private static final int DIRECTION_SHIFT = 3 * AXIS_BITS;
    private static final int FLOW_SHIFT = DIRECTION_SHIFT + 3;
    private static final int FLOW_MASK = 0b11;

    /** One filter: the cell it belongs to, that cell's face, and which way through that face it lets items travel. */
    public record Key(Vec3i cell, Direction face, TransferMode flow) {
    }

    /** One configured filter as the save file carries it: the packed key and the filter itself. */
    public record Entry(int key, ItemFilterData data) {
    }

    /** One packed key per configured filter; insertion ordered, so the wire and the file are deterministic. */
    private final Map<Key, ItemFilterData> filters = new LinkedHashMap<>();

    /**
     * The filter of one face of one cell for one direction, or {@code null} while that face was never filtered -
     * which every caller reads as "no filter", i.e. the item moves as it would without one.
     */
    @Nullable
    public ItemFilterData of(Vec3i cell, @Nullable Direction face, @Nullable TransferMode flow) {
        if (cell == null || face == null || !isAFlow(flow)) return null;
        return filters.get(new Key(cell, face, flow));
    }

    /** True while anything at all is filtered. */
    public boolean isEmpty() {
        return filters.isEmpty();
    }

    /** Number of configured filters, i.e. what the page reports next to its 过滤 button. */
    public int configured() {
        return filters.size();
    }

    /**
     * Stores one filter, exactly as it was given.
     * <p>
     * <b>An emptied filter is kept, not dropped</b>, for the reason {@link FaceFilters#set} gives: a whitelist
     * with nothing in it refuses everything, and the absence of an entry - not an empty one - is what means
     * "this face was never filtered".
     *
     * @return true while the settings really changed
     */
    public boolean set(Vec3i cell, Direction face, TransferMode flow, ItemFilterData data) {
        if (cell == null || face == null || !isAFlow(flow) || data == null) return false;
        if (!CellFaceModes.isCellOffsetInRange(cell)) return false;
        return !data.equals(filters.put(new Key(cell, face, flow), data));
    }

    /** Drops every filter. */
    public void clear() {
        filters.clear();
    }

    /** The configured filters, never modifiable. */
    public Map<Key, ItemFilterData> entries() {
        return Collections.unmodifiableMap(filters);
    }

    /** Every filter as the save file carries it, in insertion order. */
    public List<Entry> packedEntries() {
        var entries = new ArrayList<Entry>(filters.size());
        filters.forEach((key, data) -> entries.add(new Entry(pack(key), data)));
        return entries;
    }

    /** Replaces every filter with the given entries; used when the client is told what the server has. */
    public void setAll(List<Entry> entries) {
        filters.clear();
        for (var entry : entries) {
            var key = unpack(entry.key());
            if (key != null) filters.put(key, entry.data());
        }
    }

    // ------------------------------------------------------------------ save data

    /** Writes every configured filter into the block entity's save data. */
    public void save(ValueOutput output) {
        var list = output.childrenList(TAG);
        for (var entry : packedEntries()) {
            var child = list.addChild();
            child.putInt(KEY, entry.key());
            entry.data().save(child);
        }
    }

    /**
     * Reads the filters from the block entity's save data. An entry whose key names no face of this mod - an
     * unknown direction, a direction of the movement that is not one of the two a filter exists for, or a cell
     * offset outside the range an entry can express - is skipped instead of failing the whole load.
     */
    public void load(ValueInput input) {
        filters.clear();

        for (var entry : input.childrenListOrEmpty(TAG)) {
            var key = unpack(entry.getIntOr(KEY, -1));
            if (key == null) continue;
            filters.put(key, ItemFilterData.load(entry));
        }
    }

    /** Save tag of the cell-face filters. */
    private static final String TAG = "transfer_cell_filters";
    /** Save key of one entry's packed cell, face and direction. */
    private static final String KEY = "key";

    /** Packs one key into the single int the save file carries. */
    public static int pack(Key key) {
        return (key.cell().getX() + AXIS_BIAS & AXIS_MASK)
                | (key.cell().getY() + AXIS_BIAS & AXIS_MASK) << AXIS_BITS
                | (key.cell().getZ() + AXIS_BIAS & AXIS_MASK) << 2 * AXIS_BITS
                | (key.face().ordinal() & 0b111) << DIRECTION_SHIFT
                | (key.flow().ordinal() & FLOW_MASK) << FLOW_SHIFT;
    }

    /**
     * The key a packed int carries, or {@code null} while it names no cell-face - an unknown direction, a
     * direction of the movement that is not one of the two a filter exists for, or a cell offset outside the
     * range, which is what a modified client could send.
     */
    @Nullable
    public static Key unpack(int packed) {
        var cell = new Vec3i(
                (packed & AXIS_MASK) - AXIS_BIAS,
                (packed >> AXIS_BITS & AXIS_MASK) - AXIS_BIAS,
                (packed >> 2 * AXIS_BITS & AXIS_MASK) - AXIS_BIAS);
        if (!CellFaceModes.isCellOffsetInRange(cell)) return null;

        var directions = Direction.values();
        var face = packed >> DIRECTION_SHIFT & 0b111;
        if (face < 0 || face >= directions.length) return null;

        var flow = TransferMode.byOrdinal(packed >> FLOW_SHIFT & FLOW_MASK);
        return isAFlow(flow) ? new Key(cell, directions[face], flow) : null;
    }

    /** True while a filter exists for that direction of the movement; {@link TransferMode#NONE} and BOTH do not. */
    private static boolean isAFlow(@Nullable TransferMode flow) {
        return flow == TransferMode.INPUT || flow == TransferMode.OUTPUT;
    }
}
