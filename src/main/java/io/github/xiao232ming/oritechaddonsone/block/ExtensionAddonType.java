package io.github.xiao232ming.oritechaddonsone.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.init.BlockContent;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * The three known Extension Addon types.
 * <p>
 * Type I takes the stat plugins and aggregates their numbers.
 * Type II takes every other Oritech addon - including the inventory proxy, which gives the block the
 * "Item Proxy" page of its GUI - and additionally forwards their special, block-type based behaviour to
 * the machine.
 * Type III takes the same stat plugins, but gives every plugin tier its own slot.
 * <p>
 * Besides Oritech's six stat plugins this mod adds two plugins of its own, the warehouse addon
 * ({@code warehouse_addon}, +16 item slots per machine slot) and the tank addon ({@code tank_addon},
 * +8000 mB per machine tank). They are ordinary {@link MachineAddonBlock}s with neutral stats, they are
 * categorised like the stat plugins (so types I and III accept them, including in a Type III column of
 * their own) and the machine side implements their effect from {@code MachineStorageBonuses}.
 * <p>
 * Note on this branch's names: Oritech 2.0 calls the combined speed/efficiency plugin
 * {@code SYNERGY_MATRIX_ADDON} (the "ultimate addon" of 1.21.1) and the extra processing chamber plugin
 * {@code AUXILIARY_PROCESSING_CHAMBER_ADDON} (the "processing addon" of 1.21.1). The Heart of the Machine
 * only works as the single addon a machine carries, so it stays out of both plugin sets.
 * <p>
 * Types I and III work on <b>plugin categories and tiers</b> instead of block identity, so the tiered
 * plugins of other addon mods work as well: see {@link #categoryOf(Block)} and {@link #tierOf(Block)}.
 */
public enum ExtensionAddonType {

    /** 扩展插件Ⅰ型 - stat plugins (slot count comes from the config). */
    TYPE_1("extension_addon_1", 5),
    /** 扩展插件Ⅱ型 - all remaining plugins (slot count comes from the config). */
    TYPE_2("extension_addon_2", 5),
    /**
     * 扩展插件Ⅲ型 - one slot per (category, tier) pair, see {@link #type3Slots()}. The fallback count is
     * the one category per column plan with a single tier row; the real count is the size of
     * {@link #type3Slots()}, which grows with every tier the installed addon mods provide.
     */
    TYPE_3("extension_addon_3", 8);

    /**
     * The stat plugin categories shared by type I and type III. The order of the constants is the column
     * order of the type III grid, so the two categories of this mod are appended after Oritech's six and
     * do not shift the columns the existing categories have.
     */
    public enum StatCategory {
        /** Speed only (Oritech: machine speed addon). */
        SPEED,
        /** Efficiency only (Oritech: machine efficiency addon). */
        EFFICIENCY,
        /** Speed and efficiency at once (Oritech: synergy matrix addon). */
        EFFICIENT_SPEED,
        /** Extra processing chambers (Oritech: auxiliary processing chamber addon). */
        PROCESSING,
        /** Energy capacity / transfer (Oritech: machine capacitor addon). */
        CAPACITOR,
        /** Energy input for the machine (Oritech: machine acceptor addon). */
        ACCEPTOR,
        /** Item slot capacity of every machine slot (this mod: warehouse addon). */
        WAREHOUSE,
        /** Fluid capacity of every machine tank (this mod: tank addon). */
        TANK
    }

    /**
     * Category tags of the "Oritech Things" addon mod. They are read through the item tag registry, so
     * this mod does not need it as a dependency: without Oritech Things (or with a different tier set)
     * the tags are simply empty and nothing changes.
     */
    private static final List<TagKey<Item>> SPEED_TAGS = tags("tiered_addon_speed");
    private static final List<TagKey<Item>> EFFICIENCY_TAGS = tags("tiered_addon_efficiency");
    private static final List<TagKey<Item>> EFFICIENT_SPEED_TAGS = tags("tiered_addon_efficient_speed");
    private static final List<TagKey<Item>> PROCESSING_TAGS = tags("tiered_addon_processing");
    private static final List<TagKey<Item>> CAPACITOR_TAGS = tags("tiered_addon_capacitor");
    private static final List<TagKey<Item>> ACCEPTOR_TAGS = tags("tiered_addon_acceptor");

    private static List<TagKey<Item>> tags(String path) {
        return List.of(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("oritechthings", path)));
    }

    private final String id;
    private final int defaultSlots;

    ExtensionAddonType(String id, int defaultSlots) {
        this.id = id;
        this.defaultSlots = defaultSlots;
    }

    /** Registry path of the block, e.g. {@code extension_addon_1}. */
    public String id() {
        return id;
    }

    /** Slot count used when the config value is not available (also the config default). */
    public int defaultSlots() {
        return defaultSlots;
    }

    /** True when the given stack may be inserted into this plugin type. */
    public boolean accepts(ItemStack stack) {
        var block = blockOf(stack);
        if (block == null || !(block instanceof MachineAddonBlock)) return false;

        return switch (this) {
            // I and III take any plugin of the stat categories, which includes the tiered variants added
            // by other addon mods.
            case TYPE_1, TYPE_3 -> categoryOf(block) != null;
            case TYPE_2 -> type2Plugins().contains(block);
        };
    }

    /**
     * True when the stack may be inserted into the given slot. Types I and II accept their plugins in
     * any slot. Type III has one slot per (category, tier) pair, so a tier 5 speed addon goes into the
     * speed column, tier 5 row - exactly where Oritech's own speed addon goes into the tier 1 row.
     */
    public boolean acceptsInSlot(int slot, ItemStack stack) {
        if (this != TYPE_3) return accepts(stack);

        var slots = type3Slots();
        if (slot < 0 || slot >= slots.size()) return false;
        return slots.get(slot).accepts(blockOf(stack));
    }

    private static Block blockOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return null;
        return blockItem.getBlock();
    }

    // ------------------------------------------------------------------ plugin categories and tiers

    /**
     * Resolves the stat category of an addon block, or {@code null} when it is not a stat plugin.
     * <ol>
     *     <li>Oritech's six stat plugins and the two plugin blocks of this mod are matched by block
     *     identity,</li>
     *     <li>then the category tags of Oritech Things are checked (covers every tier),</li>
     *     <li>finally the registry path is matched by keyword, so tiered plugins of other addon mods
     *     are recognised as well.</li>
     * </ol>
     */
    public static StatCategory categoryOf(Block block) {
        if (block == null || !(block instanceof MachineAddonBlock)) return null;

        if (block == BlockContent.MACHINE_SPEED_ADDON.get()) return StatCategory.SPEED;
        if (block == BlockContent.MACHINE_EFFICIENCY_ADDON.get()) return StatCategory.EFFICIENCY;
        if (block == BlockContent.SYNERGY_MATRIX_ADDON.get()) return StatCategory.EFFICIENT_SPEED;
        if (block == BlockContent.AUXILIARY_PROCESSING_CHAMBER_ADDON.get()) return StatCategory.PROCESSING;
        if (block == BlockContent.MACHINE_CAPACITOR_ADDON.get()) return StatCategory.CAPACITOR;
        if (block == BlockContent.MACHINE_ACCEPTOR_ADDON.get()) return StatCategory.ACCEPTOR;

        // This mod's own two plugins. They are matched here instead of by name so that no other addon's
        // block (or a translation-like registry path) can be mistaken for them.
        if (block == OritechAddonsOne.WAREHOUSE_ADDON.get()) return StatCategory.WAREHOUSE;
        if (block == OritechAddonsOne.TANK_ADDON.get()) return StatCategory.TANK;

        var item = block.asItem();
        if (item != Items.AIR) {
            var stack = item.getDefaultInstance();
            if (matches(stack, EFFICIENT_SPEED_TAGS)) return StatCategory.EFFICIENT_SPEED;
            if (matches(stack, SPEED_TAGS)) return StatCategory.SPEED;
            if (matches(stack, EFFICIENCY_TAGS)) return StatCategory.EFFICIENCY;
            if (matches(stack, PROCESSING_TAGS)) return StatCategory.PROCESSING;
            if (matches(stack, CAPACITOR_TAGS)) return StatCategory.CAPACITOR;
            if (matches(stack, ACCEPTOR_TAGS)) return StatCategory.ACCEPTOR;
        }

        return categoryFromName(BuiltInRegistries.BLOCK.getKey(block).getPath());
    }

    /**
     * Tier of an addon block. Addon mods that add tiered plugins expose the tier as an integer block
     * state property named like {@code tier} (Oritech Things uses {@code tier} with the range 2-9, and
     * every one of its blocks carries its own tier in its default state), and the blocks without such a
     * property - all of Oritech's own plugins - count as the base tier 1.
     */
    public static int tierOf(@Nullable Block block) {
        if (block == null) return 1;

        var state = block.defaultBlockState();
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && property.getName().contains("tier")) {
                return state.getValue(integer);
            }
        }
        return 1;
    }

    private static boolean matches(ItemStack stack, List<TagKey<Item>> tags) {
        for (var tag : tags) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    /**
     * Last resort for tiered plugins of other mods: their registry path usually names the category
     * ({@code ..._efficient_speed_tier_5}, {@code ..._speed_tier_3}, {@code ..._capacitor_tier_2}, ...).
     * The combined category is checked first, so "efficient speed" is not mistaken for plain "speed".
     */
    private static StatCategory categoryFromName(String path) {
        // Extender plugins ("机器扩展坞", "储能扩展坞") add addon slots, which cannot be forwarded from
        // inside this block, so they stay type II plugins even though their name mentions a category.
        if (path.contains("extender")) return null;

        if (path.contains("efficient_speed") || path.contains("ultimate") || path.contains("synergy")) {
            return StatCategory.EFFICIENT_SPEED;
        }
        if (path.contains("speed")) return StatCategory.SPEED;
        if (path.contains("efficiency")) return StatCategory.EFFICIENCY;
        if (path.contains("processing") || path.contains("chamber") || path.contains("auxiliary")) {
            return StatCategory.PROCESSING;
        }
        if (path.contains("capacitor") || path.contains("capacity")) return StatCategory.CAPACITOR;
        if (path.contains("acceptor")) return StatCategory.ACCEPTOR;
        return null;
    }

    // ------------------------------------------------------------------ type III slot plan

    private static List<Type3Slot> type3Slots;

    /**
     * Fixed slot plan of type III: one slot per (category, tier) pair, filled column by column so that a
     * column always holds the same plugin category and the tiers increase downwards. The reference block
     * of a slot is the plugin that belongs there (and the icon drawn in the empty slot); it is
     * {@code null} for a combination no installed mod provides, and such a slot accepts nothing.
     * <p>
     * The plan is read from the block registry, so the tiers other addon mods bring (Oritech Things adds
     * tiers 2 to 9 of every category) become rows of their own instead of sharing the row of tier 1.
     */
    public static List<Type3Slot> type3Slots() {
        if (type3Slots == null) {
            // one representative plugin per (category, tier)
            var byCategory = new java.util.EnumMap<StatCategory, java.util.SortedMap<Integer, Block>>(StatCategory.class);
            for (var block : BuiltInRegistries.BLOCK) {
                var category = categoryOf(block);
                if (category == null) continue;
                byCategory.computeIfAbsent(category, key -> new java.util.TreeMap<>())
                        .putIfAbsent(tierOf(block), block);
            }

            var tiers = new java.util.TreeSet<Integer>();
            byCategory.values().forEach(map -> tiers.addAll(map.keySet()));
            if (tiers.isEmpty()) tiers.add(1);

            var allTiers = List.copyOf(tiers);
            var rows = allTiers.subList(0, Math.min(allTiers.size(), ExtensionAddonLayout.TYPE_3_MAX_ROWS));

            var slots = new ArrayList<Type3Slot>(StatCategory.values().length * rows.size());
            for (var category : StatCategory.values()) {
                var tierBlocks = byCategory.getOrDefault(category, java.util.Collections.emptySortedMap());
                for (var tier : rows) {
                    slots.add(new Type3Slot(category, tier, tierBlocks.get(tier)));
                }
            }

            type3Slots = List.copyOf(slots);
        }
        return type3Slots;
    }

    /** One slot of type III: a fixed plugin category combined with a fixed plugin tier. */
    public record Type3Slot(StatCategory category, int tier, @Nullable Block reference) {

        /** True when the given block belongs into this slot. */
        public boolean accepts(@Nullable Block block) {
            return reference != null && categoryOf(block) == category && tierOf(block) == tier;
        }

        /** Number of tier rows of the type III grid. */
        public static int rowCount() {
            return Math.max(1, type3Slots().size() / StatCategory.values().length);
        }
    }

    // ------------------------------------------------------------------ plugin sets

    private static Set<Block> type1;
    private static Set<Block> type2;

    /**
     * The eight plugins of the stat categories that this mod knows by identity: Oritech's six and the two
     * this mod registers. Type I and III accept more than these (any plugin of the same category and
     * tier, including the tiers of other addon mods), this set is the reference list used for the logs
     * and to keep them out of type II.
     */
    public static Set<Block> type1Plugins() {
        if (type1 == null) {
            type1 = Set.of(
                    BlockContent.MACHINE_SPEED_ADDON.get(),
                    BlockContent.MACHINE_EFFICIENCY_ADDON.get(),
                    BlockContent.SYNERGY_MATRIX_ADDON.get(),
                    BlockContent.AUXILIARY_PROCESSING_CHAMBER_ADDON.get(),
                    BlockContent.MACHINE_CAPACITOR_ADDON.get(),
                    BlockContent.MACHINE_ACCEPTOR_ADDON.get(),
                    OritechAddonsOne.WAREHOUSE_ADDON.get(),
                    OritechAddonsOne.TANK_ADDON.get());
        }
        return type1;
    }

    /**
     * Every Oritech addon that is not part of type I, except the Heart of the Machine (which only works
     * as the single addon of a machine). Discovered from the block registry so addons from other mods are
     * included as well.
     * <p>
     * The inventory proxy is one of them since the Item Proxy page exists: the page lets a face of this
     * block proxy one slot of the machine inventory, which is exactly what the proxy would do if it were
     * attached to the machine directly (see {@code MachineFaceStorage}). Type I and III still refuse it,
     * because proxying is not a stat the aggregated numbers could carry. The transfer addon of this mod is
     * one of them as well, and it is also the reason that class handles whole-inventory transfer.
     * <p>
     * The two plugins of this mod are excluded as well: their effect (a bigger inventory / bigger tanks)
     * is implemented by this mod instead of by Oritech, so they belong into the stat categories and not
     * in type II.
     */
    public static Set<Block> type2Plugins() {
        if (type2 == null) {
            var excluded = new HashSet<>(type1Plugins());
            excluded.add(BlockContent.HEART_OF_THE_MACHINE_ADDON.get());
            excluded.add(OritechAddonsOne.WAREHOUSE_ADDON.get());
            excluded.add(OritechAddonsOne.TANK_ADDON.get());
            // The chunk anchor has no effect at all inside a plugin slot: it works while it is attached to
            // a machine as a block, or while it sits in the reserved item slot of the wireless page (which
            // is not a plugin slot of this list). Keeping it out of the plugin sets is what makes a slot
            // refuse it instead of storing a plugin that silently does nothing.
            excluded.add(OritechAddonsOne.CHUNK_ANCHOR_ADDON.get());

            var result = new HashSet<Block>();
            for (var block : BuiltInRegistries.BLOCK) {
                if (block instanceof ExtensionAddonBlock) continue;
                if (block instanceof MachineAddonBlock && !excluded.contains(block)) result.add(block);
            }
            type2 = Set.copyOf(result);
        }
        return type2;
    }

    // ------------------------------------------------------------------ blocks of a type

    /** The wired Extension Addon block of this type. */
    public Block wiredBlock() {
        return switch (this) {
            case TYPE_1 -> OritechAddonsOne.EXTENSION_ADDON_1.get();
            case TYPE_2 -> OritechAddonsOne.EXTENSION_ADDON_2.get();
            case TYPE_3 -> OritechAddonsOne.EXTENSION_ADDON_3.get();
        };
    }

    /** The Wireless Extension Dock block of this type. */
    public Block wirelessBlock() {
        return switch (this) {
            case TYPE_1 -> OritechAddonsOne.WIRELESS_EXTENSION_ADDON_1.get();
            case TYPE_2 -> OritechAddonsOne.WIRELESS_EXTENSION_ADDON_2.get();
            case TYPE_3 -> OritechAddonsOne.WIRELESS_EXTENSION_ADDON_3.get();
        };
    }
}
