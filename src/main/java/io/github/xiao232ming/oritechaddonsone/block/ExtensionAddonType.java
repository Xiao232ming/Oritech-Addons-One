package io.github.xiao232ming.oritechaddonsone.block;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.init.BlockContent;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * The three known Extension Addon types.
 * <p>
 * Type I takes the stat plugins and aggregates their numbers.
 * Type II takes every other Oritech addon (except the Heart of the Machine and the inventory proxy)
 * and additionally forwards their special, block-type based behaviour to the machine.
 * Type III takes the same stat plugins, but gives every plugin its own slot.
 * <p>
 * Besides the six Oritech stat plugins this mod adds two plugins of its own, the warehouse addon
 * ({@code warehouse_addon}, +16 item slots per machine slot) and the tank addon ({@code tank_addon},
 * +8000 mB per machine tank). They are ordinary {@link MachineAddonBlock}s with neutral stats, they are
 * listed with the stat plugins (so type I and III accept them, including a Type III slot of their own)
 * and the machine side implements their effect from {@code MachineStorageBonuses}.
 */
public enum ExtensionAddonType {

    /** 扩展插件Ⅰ型 - stat plugins (slot count comes from the config). */
    TYPE_1("extension_addon_1", 5),
    /** 扩展插件Ⅱ型 - all remaining plugins (slot count comes from the config). */
    TYPE_2("extension_addon_2", 5),
    /**
     * 扩展插件Ⅲ型 - one dedicated slot per stat plugin. The count is the size of
     * {@link #fixedSlotOrder()}, which includes the two plugins this mod adds - without those slots there
     * would be no place to put them.
     */
    TYPE_3("extension_addon_3", 8);

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
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        return plugins().contains(blockItem.getBlock());
    }

    /**
     * True when the stack may be inserted into the given slot. Types I and II accept their plugins in
     * any slot, type III has one dedicated slot per plugin (see {@link #fixedSlotOrder()}).
     */
    public boolean acceptsInSlot(int slot, ItemStack stack) {
        if (this != TYPE_3) return accepts(stack);

        var order = fixedSlotOrder();
        var block = blockOf(stack);
        return slot >= 0 && slot < order.size() && block != null && order.get(slot) == block;
    }

    private static Block blockOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return null;
        return blockItem.getBlock();
    }

    /**
     * Fixed slot order of type III: slot {@code i} only accepts {@code fixedSlotOrder().get(i)}.
     * <p>
     * Built from the same plugin list type I accepts. The two plugins of this mod are appended after
     * Oritech's six, so the slots the existing plugins occupy keep their position.
     */
    public static List<Block> fixedSlotOrder() {
        if (fixedSlotOrder == null) {
            fixedSlotOrder = List.of(
                    BlockContent.MACHINE_SPEED_ADDON.get(),
                    BlockContent.MACHINE_EFFICIENCY_ADDON.get(),
                    BlockContent.SYNERGY_MATRIX_ADDON.get(),
                    BlockContent.AUXILIARY_PROCESSING_CHAMBER_ADDON.get(),
                    BlockContent.MACHINE_CAPACITOR_ADDON.get(),
                    BlockContent.MACHINE_ACCEPTOR_ADDON.get(),
                    OritechAddonsOne.WAREHOUSE_ADDON.get(),
                    OritechAddonsOne.TANK_ADDON.get());
        }
        return fixedSlotOrder;
    }

    private Set<Block> plugins() {
        return switch (this) {
            case TYPE_1, TYPE_3 -> type1Plugins();
            case TYPE_2 -> type2Plugins();
        };
    }

    // ------------------------------------------------------------------ plugin sets

    private static Set<Block> type1;
    private static Set<Block> type2;
    private static List<Block> fixedSlotOrder;

    /**
     * The stat plugins handled by type I: Oritech's six and the two this mod registers. Type III accepts
     * exactly the same blocks, one slot each.
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
     * Every Oritech addon that is not part of type I, except the Heart of the Machine (only works as a
     * single addon) and the inventory proxy (provides its own inventory, which cannot be forwarded).
     * Discovered from the block registry so addons from other mods are included as well.
     * <p>
     * The two plugins of this mod are excluded as well: their effect (a bigger inventory / bigger tanks)
     * is implemented by this mod instead of by Oritech, so they belong into the stat categories and not
     * in type II.
     */
    public static Set<Block> type2Plugins() {
        if (type2 == null) {
            var excluded = new HashSet<>(type1Plugins());
            excluded.add(BlockContent.HEART_OF_THE_MACHINE_ADDON.get());
            excluded.add(BlockContent.MACHINE_INVENTORY_PROXY_ADDON.get());
            excluded.add(OritechAddonsOne.WAREHOUSE_ADDON.get());
            excluded.add(OritechAddonsOne.TANK_ADDON.get());

            var result = new HashSet<Block>();
            for (var block : BuiltInRegistries.BLOCK) {
                if (block instanceof ExtensionAddonBlock) continue;
                if (block instanceof MachineAddonBlock && !excluded.contains(block)) result.add(block);
            }
            type2 = Set.copyOf(result);
        }
        return type2;
    }
}
