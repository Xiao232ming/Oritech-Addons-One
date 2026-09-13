package com.example.oritechaddonsone.block;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.init.BlockContent;

/**
 * The three known Extension Plugin types.
 * <p>
 * Type I takes the stat plugins and aggregates their numbers.
 * Type II takes every other Oritech addon (except the inventory proxy) and additionally forwards its
 * special, block-type based behaviour to the machine.
 * Type III takes the same stat plugins as type I, but with one dedicated slot per plugin.
 * <p>
 * Note on the 1.21.1 mapping: Oritech 1.21.1 calls the combined speed/efficiency plugin
 * {@code MACHINE_ULTIMATE_ADDON} (the "synergy matrix" of newer versions) and the extra processing
 * chamber plugin {@code MACHINE_PROCESSING_ADDON} (the "auxiliary processing chamber" of newer
 * versions). The "Heart of the Machine" plugin does not exist in 1.21.1.
 * <p>
 * Types I and III accept plugins by <b>category</b>, not by block identity, so the tiered plugins of
 * other addon mods work as well: see {@link #categoryOf(Block)} for the resolution order.
 */
public enum ExtensionPluginType {

    /** 扩展插件Ⅰ型 - stat plugins (slot count comes from the config). */
    TYPE_1("extension_plugin_1", 5),
    /** 扩展插件Ⅱ型 - all remaining plugins (slot count comes from the config). */
    TYPE_2("extension_plugin_2", 5),
    /** 扩展插件Ⅲ型 - one dedicated slot per stat plugin (fixed six slots, capacity from the config). */
    TYPE_3("extension_plugin_3", 6);

    /**
     * The stat plugin categories shared by type I and type III. The order of the constants is the fixed
     * slot order of type III.
     */
    public enum StatCategory {
        /** Speed only (Oritech: machine speed addon). */
        SPEED,
        /** Efficiency only (Oritech: machine efficiency addon). */
        EFFICIENCY,
        /** Speed and efficiency at once (Oritech: ultimate addon). */
        EFFICIENT_SPEED,
        /** Extra processing chambers (Oritech: machine processing addon). */
        PROCESSING,
        /** Energy capacity / transfer (Oritech: machine capacitor addon). */
        CAPACITOR,
        /** Energy input for the machine (Oritech: machine acceptor addon). */
        ACCEPTOR
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
        return List.of(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("oritechthings", path)));
    }

    private final String id;
    private final int defaultSlots;

    ExtensionPluginType(String id, int defaultSlots) {
        this.id = id;
        this.defaultSlots = defaultSlots;
    }

    /** Registry path of the block, e.g. {@code extension_plugin_1}. */
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
            // I and III take any plugin that falls into one of the stat categories, which includes the
            // tiered variants added by other addon mods.
            case TYPE_1, TYPE_3 -> categoryOf(block) != null;
            case TYPE_2 -> type2Plugins().contains(block);
        };
    }

    /**
     * True when the stack may be inserted into the given slot. Types I and II accept their plugins in
     * any slot, type III has one dedicated slot per category (see {@link #fixedSlotOrder()}), so a tier
     * 5 speed addon goes into the same slot as Oritech's own speed addon.
     */
    public boolean acceptsInSlot(int slot, ItemStack stack) {
        if (this != TYPE_3) return accepts(stack);

        var order = fixedSlotOrder();
        if (slot < 0 || slot >= order.size()) return false;

        var category = categoryOf(blockOf(stack));
        return category != null && category == categoryOf(order.get(slot));
    }

    private static Block blockOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return null;
        return blockItem.getBlock();
    }

    // ------------------------------------------------------------------ plugin categories

    /**
     * Resolves the stat category of an addon block, or {@code null} when it is not a stat plugin.
     * <ol>
     *     <li>the six Oritech stat plugins are matched by block identity,</li>
     *     <li>then the category tags of Oritech Things are checked (covers every tier),</li>
     *     <li>finally the registry path is matched by keyword, so tiered plugins of other addon mods
     *     are recognised as well.</li>
     * </ol>
     */
    public static StatCategory categoryOf(Block block) {
        if (block == null || !(block instanceof MachineAddonBlock)) return null;

        if (block == BlockContent.MACHINE_SPEED_ADDON) return StatCategory.SPEED;
        if (block == BlockContent.MACHINE_EFFICIENCY_ADDON) return StatCategory.EFFICIENCY;
        if (block == BlockContent.MACHINE_ULTIMATE_ADDON) return StatCategory.EFFICIENT_SPEED;
        if (block == BlockContent.MACHINE_PROCESSING_ADDON) return StatCategory.PROCESSING;
        if (block == BlockContent.MACHINE_CAPACITOR_ADDON) return StatCategory.CAPACITOR;
        if (block == BlockContent.MACHINE_ACCEPTOR_ADDON) return StatCategory.ACCEPTOR;

        var item = block.asItem();
        if (item != net.minecraft.world.item.Items.AIR) {
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

    /**
     * Fixed slot order of type III: slot {@code i} only accepts plugins of the category of
     * {@code fixedSlotOrder().get(i)}. The entries are the Oritech stat plugins, they act as the
     * reference plugin of their category (and are the icons shown in empty slots).
     */
    public static List<Block> fixedSlotOrder() {
        if (fixedSlotOrder == null) {
            fixedSlotOrder = List.of(
                    BlockContent.MACHINE_SPEED_ADDON,
                    BlockContent.MACHINE_EFFICIENCY_ADDON,
                    BlockContent.MACHINE_ULTIMATE_ADDON,
                    BlockContent.MACHINE_PROCESSING_ADDON,
                    BlockContent.MACHINE_CAPACITOR_ADDON,
                    BlockContent.MACHINE_ACCEPTOR_ADDON);
        }
        return fixedSlotOrder;
    }

    // ------------------------------------------------------------------ plugin sets

    private static Set<Block> type1;
    private static Set<Block> type2;
    private static List<Block> fixedSlotOrder;

    /**
     * The six Oritech stat plugins. Type I and III accept more than these (any plugin of the same
     * category, including the tiers of other addon mods), this set is the reference list used for the
     * logs and to keep them out of type II.
     */
    public static Set<Block> type1Plugins() {
        if (type1 == null) {
            type1 = Set.of(
                    BlockContent.MACHINE_SPEED_ADDON,
                    BlockContent.MACHINE_EFFICIENCY_ADDON,
                    BlockContent.MACHINE_ULTIMATE_ADDON,
                    BlockContent.MACHINE_PROCESSING_ADDON,
                    BlockContent.MACHINE_CAPACITOR_ADDON,
                    BlockContent.MACHINE_ACCEPTOR_ADDON);
        }
        return type1;
    }

    /**
     * Every Oritech addon that is not one of the six stat plugins, except the inventory proxy (it
     * provides its own inventory, which cannot be forwarded). Discovered from the block registry so
     * addons from other mods are included as well.
     */
    public static Set<Block> type2Plugins() {
        if (type2 == null) {
            var excluded = new HashSet<>(type1Plugins());
            excluded.add(BlockContent.MACHINE_INVENTORY_PROXY_ADDON);

            var result = new HashSet<Block>();
            for (var block : BuiltInRegistries.BLOCK) {
                if (block instanceof ExtensionPluginBlock) continue;
                if (block instanceof MachineAddonBlock && !excluded.contains(block)) result.add(block);
            }
            type2 = Set.copyOf(result);
        }
        return type2;
    }
}
