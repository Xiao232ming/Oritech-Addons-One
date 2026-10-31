package io.github.xiao232ming.oritechaddonsone;

import net.neoforged.neoforge.common.ModConfigSpec;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * Common config of the mod. The inventory of the three plugin types can be adjusted here:
 * the slot count of type I and II (1-72) and the capacity of every slot of type III.
 * The rate the transfer plugins automate at can be adjusted here as well.
 * The file is created at {@code config/oritechaddonsone-common.toml}.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue TYPE_1_SLOTS = BUILDER
            .comment("Number of plugin slots of the Extension Addon I.",
                    "扩展插件Ⅰ型的物品栏格数。")
            .defineInRange("type1Slots", 5, ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS);

    public static final ModConfigSpec.IntValue TYPE_2_SLOTS = BUILDER
            .comment("Number of plugin slots of the Extension Addon II.",
                    "扩展插件Ⅱ型的物品栏格数。")
            .defineInRange("type2Slots", 5, ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS);

    public static final ModConfigSpec.IntValue TYPE_3_SLOT_CAPACITY = BUILDER
            .comment("Capacity of every plugin slot of the Extension Addon III (how many plugins fit per slot).",
                    "扩展插件Ⅲ型每个格子的容量（每格最多能放多少个插件）。")
            .defineInRange("type3SlotCapacity", 256, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue WAREHOUSE_ADDON_SLOTS = BUILDER
            .comment("Item slots one warehouse addon adds to every slot of the machine.",
                    "仓库插件为机器每个物品槽增加的格数。")
            .defineInRange("warehouseAddonSlots", 16, 1, 64);

    public static final ModConfigSpec.IntValue TANK_ADDON_CAPACITY = BUILDER
            .comment("Fluid capacity (mB) one tank addon adds to every tank of the machine.",
                    "储罐插件为机器每个流体罐增加的容量（mB）。")
            .defineInRange("tankAddonCapacity", 8000, 1000, 32000);

    public static final ModConfigSpec.IntValue TRANSFER_ITEMS_PER_TICK = BUILDER
            .comment("修改传输插件自动输入输出的速度")
            .defineInRange("transferItemsPerTick", 64, 1, 6400);

    public static final ModConfigSpec.IntValue TRANSFER_FLUID_PER_TICK = BUILDER
            .comment("修改传输插件自动输入输出的流体速度（mB/t）")
            .defineInRange("transferFluidPerTick", 1000, 1, 1_000_000);

    public static final ModConfigSpec.BooleanValue SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE = BUILDER
            .comment("在机器插件预览中显示无线扩展坞")
            .define("showWirelessDocksInAddonPage", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /**
     * Configured amount of slots of the given type, clamped to the supported range.
     * <p>
     * Type III derives its slot count from the installed plugins: one slot per (category, tier) pair, so
     * the grid grows a row for every tier another addon mod provides - see
     * {@link ExtensionAddonType#type3Slots()}. The other two types read their count from the config.
     */
    public static int slots(ExtensionAddonType type) {
        if (type == ExtensionAddonType.TYPE_3) return ExtensionAddonType.type3Slots().size();

        var value = type == ExtensionAddonType.TYPE_1 ? TYPE_1_SLOTS : TYPE_2_SLOTS;
        try {
            return Math.max(ExtensionAddonLayout.MIN_SLOTS,
                    Math.min(ExtensionAddonLayout.MAX_SLOTS, value.get()));
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return type.defaultSlots();
        }
    }

    /**
     * How many items one configured face of a transfer plugin moves per tick, per direction
     * ({@link #TRANSFER_ITEMS_PER_TICK}). Never below 1, so an automation face can never end up doing
     * nothing at all, and clamped like the other accessors for the stages the values are not readable in.
     */
    public static int transferItemsPerTick() {
        try {
            return Math.max(1, TRANSFER_ITEMS_PER_TICK.get());
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return 64;
        }
    }

    /**
     * How much fluid (in mB) one configured face of a transfer plugin moves per tick, per direction
     * ({@link #TRANSFER_FLUID_PER_TICK}). Clamped like the other accessors for the stages the values are not
     * readable in.
     */
    public static int transferFluidPerTick() {
        try {
            return Math.max(1, TRANSFER_FLUID_PER_TICK.get());
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return 1000;
        }
    }

    /** Configured capacity of one slot of the given type (only type III supports more than 64). */
    public static int slotCapacity(ExtensionAddonType type) {
        if (type != ExtensionAddonType.TYPE_3) return 64;

        try {
            return Math.max(1, TYPE_3_SLOT_CAPACITY.get());
        } catch (IllegalStateException notLoadedYet) {
            return 256;
        }
    }

    /**
     * Item slots one warehouse addon adds to every slot of the machine
     * ({@link #WAREHOUSE_ADDON_SLOTS}, default 16, range 1-64). Read on the addon scan, so a changed value
     * applies to the next scan rather than to already modified stacks.
     */
    public static int warehouseAddonSlots() {
        try {
            return Math.max(1, Math.min(64, WAREHOUSE_ADDON_SLOTS.get()));
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return 16;
        }
    }

    /**
     * Fluid capacity in mB one tank addon adds to every tank of the machine
     * ({@link #TANK_ADDON_CAPACITY}, default 8000, range 1000-32000).
     */
    public static long tankAddonCapacity() {
        try {
            return Math.max(1000L, Math.min(32000L, TANK_ADDON_CAPACITY.get()));
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return 8000L;
        }
    }

    /**
     * Whether the addon pages list the machine's wireless extension addons. Both pages ask this - the stock
     * page of the upgradable machines and the one this mod adds to the machines that have none - so turning
     * it off restores Oritech's original page everywhere.
     */
    public static boolean showWirelessDocksInAddonPage() {
        try {
            return SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE.get();
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return true;
        }
    }
}
