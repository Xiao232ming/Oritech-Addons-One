package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The per-<b>face</b> item filters of one machine: which items may cross one face of an Extension Addon /
 * Wireless Extension Dock, and in which direction.
 * <p>
 * <b>A face has two filters, not one.</b> A face set to 输入 moves items from the container beside it into the
 * machine, and a face set to 输出 moves them the other way; a face set to 输入 + 输出 does both. What may
 * <em>enter</em> the machine and what may <em>leave</em> it are two different questions - a smelter may well be
 * fed only ore while it is only asked to hand its ingots over - so the key is the face <b>and</b> the direction,
 * and the direction is always one of {@link TransferMode#INPUT} and {@link TransferMode#OUTPUT}: a face that is
 * set to 输入 + 输出 uses both of its filters, one per direction it moves.
 * <p>
 * <b>Six faces, so twelve filters at most</b> - the same bound the mode map of {@link TransferFaceModes} has, and
 * for the same reason: the six directions of one block. The cells of a machine structure have their own map
 * ({@link CellFilters}), which has no such bound.
 * <p>
 * <b>A face that was never touched has no filter.</b> {@link #of} answers {@code null} for it, which every
 * caller reads as "this face moves everything" - and that is how a face behaves exactly as it did before the
 * 过滤 page existed. Once the page has sent a filter, though, what it sent is kept <b>as it is</b>: a whitelist
 * with nothing in it is a real answer (it refuses everything) and is stored as one, because deleting the last
 * listed item is a decision and not an undo.
 */
public final class FaceFilters {

    /** Bits of a packed key: 3 for the direction, then 2 for the direction of the movement. */
    private static final int FLOW_SHIFT = 3;
    private static final int FLOW_MASK = 0b11;

    /** One filter: the face it belongs to, and which way through that face it lets items travel. */
    public record Key(Direction face, TransferMode flow) {
    }

    /** One configured filter as the save file carries it: the packed key and the filter itself. */
    public record Entry(int key, ItemFilterData data) {
    }

    /** One packed key per configured filter; insertion ordered, so the wire and the file are deterministic. */
    private final Map<Key, ItemFilterData> filters = new LinkedHashMap<>();

    /**
     * The filter of one face for one direction, or {@code null} while that face was never filtered - which every
     * caller reads as "no filter", i.e. the item moves as it would without one.
     */
    @Nullable
    public ItemFilterData of(@Nullable Direction face, @Nullable TransferMode flow) {
        if (face == null || !isAFlow(flow)) return null;
        return filters.get(new Key(face, flow));
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
     * <b>An emptied filter is kept, not dropped.</b> A whitelist with nothing in it refuses everything, which is
     * what a player who deletes every listed item has asked for - dropping the entry instead would answer the
     * opposite of what they did and let everything through. The <b>absence</b> of an entry is what means "this
     * face was never filtered", and that state is only ever reached by never calling this: the page sends a
     * filter only after an edit, so a face nobody ever touched keeps moving everything, exactly as it did before
     * the page existed.
     *
     * @return true while the settings really changed
     */
    public boolean set(Direction face, TransferMode flow, ItemFilterData data) {
        if (face == null || !isAFlow(flow) || data == null) return false;
        return !data.equals(filters.put(new Key(face, flow), data));
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
     * unknown direction, which is what a newer save could hold - is skipped instead of failing the whole load.
     */
    public void load(ValueInput input) {
        filters.clear();

        for (var entry : input.childrenListOrEmpty(TAG)) {
            var key = unpack(entry.getIntOr(KEY, -1));
            if (key == null) continue;
            filters.put(key, ItemFilterData.load(entry));
        }
    }

    /** Save tag of the six-face filters. */
    private static final String TAG = "transfer_face_filters";
    /** Save key of one entry's packed face and direction. */
    private static final String KEY = "key";

    /** Packs one key into the single int the save file carries. */
    public static int pack(Key key) {
        return key.face().ordinal() & 0b111 | (key.flow().ordinal() & FLOW_MASK) << FLOW_SHIFT;
    }

    /**
     * The key a packed int carries, or {@code null} while it names no face - an unknown direction or a direction
     * of the movement that is not one of the two a filter exists for, which is what a modified client could send.
     */
    @Nullable
    public static Key unpack(int packed) {
        var directions = Direction.values();
        var face = packed & 0b111;
        if (face < 0 || face >= directions.length) return null;

        var flow = TransferMode.byOrdinal(packed >> FLOW_SHIFT & FLOW_MASK);
        return isAFlow(flow) ? new Key(directions[face], flow) : null;
    }

    /** True while a filter exists for that direction of the movement; {@link TransferMode#NONE} and BOTH do not. */
    private static boolean isAFlow(@Nullable TransferMode flow) {
        return flow == TransferMode.INPUT || flow == TransferMode.OUTPUT;
    }
}
