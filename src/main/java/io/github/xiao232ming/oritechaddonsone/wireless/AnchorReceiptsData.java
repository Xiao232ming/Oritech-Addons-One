package io.github.xiao232ming.oritechaddonsone.wireless;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * The saved form of {@link AnchorForceLoad}'s receipts: one level's worth of "this chunk is force loaded
 * because an anchor asked for it".
 * <p>
 * It exists because vanilla <b>does</b> persist the ticket {@code ServerLevel#setChunkForced} adds
 * ({@code TicketType.FORCED} carries the persist flag and {@code TicketStorage} is saved data). Without a
 * receipt of our own, the anchor would come back after a restart, find its chunk already forced by that
 * leftover ticket, and honestly report "somebody else forced this" - so removing the anchor would never
 * release the chunk again. Saving the receipts keeps the ownership across restarts and makes the release
 * path work exactly like it did before the restart.
 * <p>
 * Only the bookkeeping is saved here; which chunks are really forced is still vanilla's ticket storage,
 * and the periodic reconcile re-checks both sides.
 */
public final class AnchorReceiptsData extends SavedData {

    /** One receipt: the source, the chunk it keeps loaded, and whether this mod added the force load. */
    public record Entry(BlockPos source, long chunk, boolean placed, boolean forcedByUs) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("source").forGetter(Entry::source),
                Codec.LONG.fieldOf("chunk").forGetter(Entry::chunk),
                Codec.BOOL.fieldOf("placed").forGetter(Entry::placed),
                Codec.BOOL.fieldOf("forced_by_us").forGetter(Entry::forcedByUs)
        ).apply(instance, Entry::new));
    }

    public static final Codec<AnchorReceiptsData> CODEC = Entry.CODEC.listOf()
            .xmap(AnchorReceiptsData::new, AnchorReceiptsData::entries);

    /** Per-level save slot, e.g. {@code data/oritechaddonsone_chunk_anchor_receipts.dat}. */
    public static final SavedDataType<AnchorReceiptsData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, "chunk_anchor_receipts"),
            AnchorReceiptsData::new, CODEC);

    private List<Entry> entries;

    private AnchorReceiptsData() {
        this(List.of());
    }

    private AnchorReceiptsData(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        setDirty();
    }
}
