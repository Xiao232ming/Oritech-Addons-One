package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The saved form of {@link AnchorForceLoad}'s receipts: one level's worth of "this chunk is force loaded
 * because an anchor asked for it".
 * <p>
 * It exists because vanilla <b>does</b> persist the forced chunk ({@code TicketType.FORCED} carries the
 * persist flag). Without a receipt of our own, the anchor would come back after a restart, find its chunk
 * already forced by that leftover ticket, and honestly report "somebody else forced this" - so removing
 * the anchor would never release the chunk again. Saving the receipts keeps the ownership across restarts
 * and makes the release path work exactly like it did before the restart.
 * <p>
 * Only the bookkeeping is saved here; which chunks are really forced is still vanilla's own data, and the
 * periodic reconcile re-checks both sides.
 */
public final class AnchorReceiptsData extends SavedData {

    /** One receipt: the source, the chunk it keeps loaded, and whether this mod added the force load. */
    public record Entry(BlockPos source, long chunk, boolean placed, boolean forcedByUs) {
    }

    /** Save slot name, e.g. {@code data/oritechaddonsone_chunk_anchor_receipts.dat}. */
    public static final String NAME = "oritechaddonsone_chunk_anchor_receipts";

    public static final SavedData.Factory<AnchorReceiptsData> FACTORY =
            new SavedData.Factory<>(AnchorReceiptsData::new, AnchorReceiptsData::load, null);

    private List<Entry> entries = List.of();

    public List<Entry> entries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();

        for (var entry : entries) {
            var element = new CompoundTag();
            element.putLong("source", entry.source().asLong());
            element.putLong("chunk", entry.chunk());
            element.putBoolean("placed", entry.placed());
            element.putBoolean("forced_by_us", entry.forcedByUs());
            list.add(element);
        }

        tag.put("receipts", list);
        return tag;
    }

    private static AnchorReceiptsData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new AnchorReceiptsData();
        var loaded = new ArrayList<Entry>();

        for (var element : tag.getList("receipts", Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag receipt)) continue;
            loaded.add(new Entry(BlockPos.of(receipt.getLong("source")), receipt.getLong("chunk"),
                    receipt.getBoolean("placed"), receipt.getBoolean("forced_by_us")));
        }

        data.entries = List.copyOf(loaded);
        return data;
    }
}
