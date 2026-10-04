package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.Nullable;

/**
 * One item filter: which items may pass, which are refused, and how exactly "the same item" is decided - the
 * settings behind one face's 过滤 page, i.e. a direct port of Oritech's own item filter
 * ({@code rearth.oritech.block.entity.pipes.ItemFilterBlockEntity.FilterData}) so that a player who has used
 * the filter on an Oritech pipe finds the same twelve slots and the same three switches here, with the same
 * answers.
 * <p>
 * The four settings are the ones Oritech uses, and they are kept in Oritech's own meaning:
 * <ul>
 *     <li>{@link #useWhitelist()} - <b>白名单</b>: only the listed items pass. Off means <b>黑名单</b>: everything
 *     but the listed items passes. This is the switch that decides which of the two readings applies, so it is the
 *     first one to understand.</li>
 *     <li>{@link #useNbt()} - whether the listed items' <b>custom data</b> ({@link DataComponents#CUSTOM_DATA}) has
 *     to match as well. Off compares the item type only.</li>
 *     <li>{@link #useComponents()} - whether <b>all</b> data components (enchantments, stored energy, custom
 *     names, ...) have to match as well. Turning it on also turns NBT on, exactly as Oritech's screen does: a
 *     component match that ignored the custom data would be the weaker of the two checks, so the stronger one
 *     always brings it along.</li>
 * </ul>
 * A filter that lists <b>nothing</b> is therefore a whitelist that refuses everything and a blacklist that
 * allows everything - which is the same answer Oritech gives, and the reason a fresh filter is <b>not</b> an
 * empty filter: see {@link #DEFAULT}.
 * <p>
 * <b>The matching is Oritech's, including where it is surprising.</b> {@link #allows(ItemStack)} walks the
 * listed items in the order they were added and stops at the first one it can decide on, and a component
 * mismatch <b>ends</b> the walk while an NBT mismatch only skips to the next listed item - the two are treated
 * differently on purpose, because a component mismatch means "this is a different item" while an NBT mismatch
 * means "this is the same item with other data on it". Two listed items of one type with different components
 * therefore behave the way Oritech's filter behaves, and a player moving between the two sees no change.
 * <p>
 * <b>Counted as one.</b> A listed item is stored with a count of one and with its components copied over
 * ({@link #withItem(int, ItemStack)}), because a filter describes a <em>kind</em> of item, not a stack of it:
 * how many of them are in the list must not change a single answer.
 * <p>
 * <b>This branch's item type.</b> 26.1.2's twin asks {@link #allows} about a NeoForge
 * {@code net.neoforged.neoforge.transfer.item.ItemResource}, which does not exist here: on 1.21.1 Oritech's own
 * storage is reached through {@code rearth.oritech.api.item.ItemApi.InventoryStorage} and hands out plain
 * {@link ItemStack}s. Every question the transfer asks is therefore asked of a stack - which item it is, which
 * components it carries, which custom data it has - and the walk above, including its two orderings, is Oritech's
 * unchanged.
 */
public record ItemFilterData(boolean useNbt, boolean useWhitelist, boolean useComponents,
        Map<Integer, ItemStack> items) {

    /**
     * How many items one filter can list - Oritech's own 4x3 grid, and the number the page draws. The slots are
     * addressed by their index in that grid, which is what the save file and the packet carry.
     */
    public static final int SLOTS = 12;

    /** The filter a face starts with: a <b>whitelist</b> (so an empty list refuses everything) comparing the item type only. */
    public static final ItemFilterData DEFAULT = new ItemFilterData(false, true, false, Map.of());

    /** Save tag of the three switches inside one filter entry. */
    private static final String WHITELIST = "whitelist";
    private static final String NBT = "nbt";
    private static final String COMPONENTS = "components";
    /** Save tag of the listed items inside one filter entry. */
    private static final String ITEMS = "items";
    /** Keys of one listed item inside {@link #ITEMS}. */
    private static final String SLOT = "slot";
    private static final String STACK = "stack";

    /** The whole filter as the wire carries it; the same four parts Oritech's own filter packet carries. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilterData> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ItemFilterData::useNbt,
            ByteBufCodecs.BOOL, ItemFilterData::useWhitelist,
            ByteBufCodecs.BOOL, ItemFilterData::useComponents,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.INT, ItemStack.STREAM_CODEC), ItemFilterData::items,
            ItemFilterData::new);

    public ItemFilterData {
        items = Map.copyOf(items);
    }

    /** True while this filter lists nothing, i.e. while it answers every item the same way without looking. */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** True while an item passes this filter - the question every transfer of this mod asks before it moves something. */
    public boolean allows(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;

        var matches = false;
        for (var filterItem : items.values()) {
            if (filterItem.isEmpty()) continue;

            if (!stack.getItem().equals(filterItem.getItem())) continue;

            // a component mismatch ends the walk: the listed item is a different item, so a later listed item of the
            // same type would not make this one pass either - this is Oritech's own order of the two checks
            if (useComponents && !componentsMatch(stack, filterItem)) break;

            if (useNbt) {
                var custom = customData(stack);
                var listed = customData(filterItem);

                if (custom != null && listed != null) {
                    // same item and same custom data: this is the listed item
                    if (!custom.equals(listed)) continue;
                    matches = true;
                    break;
                }

                // exactly one of the two carries custom data, so they cannot be the same item
                if (custom != null || listed != null) continue;

                matches = true;
                break;
            }

            matches = true;
            break;
        }

        return useWhitelist ? matches : !matches;
    }

    /** True while the given slot of the grid lists something. */
    public boolean hasSlot(int index) {
        var stack = items.get(index);
        return stack != null && !stack.isEmpty();
    }

    /** What the given slot of the grid lists, or an empty stack while it lists nothing. */
    public ItemStack slot(int index) {
        return items.getOrDefault(index, ItemStack.EMPTY);
    }

    /**
     * The filter with one grid slot changed, and nothing else - the operation behind clicking a slot of the page.
     * An empty stack clears the slot, anything else is stored as a single item carrying the stack's components.
     */
    public ItemFilterData withItem(int index, ItemStack stack) {
        if (index < 0 || index >= SLOTS) return this;

        var items = new HashMap<>(this.items);
        if (stack == null || stack.isEmpty()) {
            items.remove(index);
        } else {
            items.put(index, singleOf(stack));
        }
        return new ItemFilterData(useNbt, useWhitelist, useComponents, items);
    }

    /** The filter with 白名单 flipped. */
    public ItemFilterData withWhitelist(boolean value) {
        return new ItemFilterData(useNbt, value, useComponents, items);
    }

    /** The filter with NBT flipped, leaving the component switch alone. */
    public ItemFilterData withNbt(boolean value) {
        return new ItemFilterData(value, useWhitelist, useComponents, items);
    }

    /**
     * The filter with 组件 flipped - and, when it is turned <b>on</b>, NBT turned on with it, which is what
     * Oritech's screen does and why: a component match that skipped the custom data would be the weaker of the
     * two comparisons, so the stronger one never runs without it. Turning it off leaves NBT as it was.
     */
    public ItemFilterData withComponents(boolean value) {
        return new ItemFilterData(value || useNbt, useWhitelist, value, items);
    }

    // ------------------------------------------------------------------ save data

    /**
     * Writes this filter into one entry of a filter list, i.e. the three switches and the listed items.
     * <p>
     * The switches are always written, so an entry never reads back as the default of a version that knew fewer of
     * them, and every listed slot is written with its index - a list with a hole in it stays a list with a hole
     * in it instead of closing up, which is what keeps the grid stable while the player fills it.
     * <p>
     * The stack of one listed item is written through the given registries, because on 1.21.1 an {@link ItemStack}
     * names its item by holder and only a registry-aware read can turn it back into a stack - the same registries
     * the block entity's own save data was written with.
     */
    public void save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.putBoolean(WHITELIST, useWhitelist);
        nbt.putBoolean(NBT, useNbt);
        nbt.putBoolean(COMPONENTS, useComponents);

        var list = new ListTag();
        for (var index = 0; index < SLOTS; index++) {
            var stack = slot(index);
            if (stack.isEmpty()) continue;

            var entry = new CompoundTag();
            entry.putInt(SLOT, index);
            entry.put(STACK, stack.save(registries));
            list.add(entry);
        }
        nbt.put(ITEMS, list);
    }

    /**
     * Reads the filter out of one entry of a filter list. The switches fall back to {@link #DEFAULT}'s answers,
     * so an entry written by a version that knew fewer of them still loads.
     */
    public static ItemFilterData load(CompoundTag nbt, HolderLookup.Provider registries) {
        var items = new HashMap<Integer, ItemStack>();
        var list = nbt.getList(ITEMS, Tag.TAG_COMPOUND);
        for (var index = 0; index < list.size(); index++) {
            var entry = list.getCompound(index);
            var stack = ItemStack.parse(registries, entry.getCompound(STACK)).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) continue;

            var slot = entry.contains(SLOT, Tag.TAG_ANY_NUMERIC) ? entry.getInt(SLOT) : items.size();
            if (slot < 0 || slot >= SLOTS) continue;

            items.put(slot, singleOf(stack));
        }

        return new ItemFilterData(
                flagOf(nbt, NBT, DEFAULT.useNbt()),
                flagOf(nbt, WHITELIST, DEFAULT.useWhitelist()),
                flagOf(nbt, COMPONENTS, DEFAULT.useComponents()),
                items);
    }

    /**
     * One of the three switches as the save file carries it, or the default's answer while the entry does not name
     * it - which is what an entry written by a version that knew fewer switches reads back as.
     */
    private static boolean flagOf(CompoundTag nbt, String key, boolean fallback) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC) ? nbt.getBoolean(key) : fallback;
    }

    /** The stack as a filter lists it: a single item carrying the given stack's components. */
    private static ItemStack singleOf(ItemStack stack) {
        var single = new ItemStack(stack.getItem(), 1);
        single.applyComponents(stack.getComponents());
        return single;
    }

    /** True while the stack and the listed item carry the same data components. */
    private static boolean componentsMatch(ItemStack stack, ItemStack listed) {
        return stack.getComponentsPatch().equals(listed.getComponentsPatch());
    }

    /** The custom data of an item, or {@code null} while it carries none. */
    @Nullable
    private static CustomData customData(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_DATA);
    }
}