package io.github.xiao232ming.oritechaddonsone;

import net.neoforged.neoforge.common.ModConfigSpec;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * Common config of the mod. The inventory of the three plugin types can be adjusted here:
 * the slot count of type I and II (1-36) and the capacity of every slot of type III.
 * The rate the transfer plugins automate at can be adjusted here as well.
 * The file is created at {@code config/oritechaddonsone-common.toml}.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue TYPE_1_SLOTS = BUILDER
            .comment("Number of plugin slots of the Extension Addon Type I.",
                    "扩展插件Ⅰ型的物品栏格数。")
            .defineInRange("type1Slots", 5, ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS);

    public static final ModConfigSpec.IntValue TYPE_2_SLOTS = BUILDER
            .comment("Number of plugin slots of the Extension Addon Type II.",
                    "扩展插件Ⅱ型的物品栏格数。")
            .defineInRange("type2Slots", 5, ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS);

    public static final ModConfigSpec.IntValue TYPE_3_SLOT_CAPACITY = BUILDER
            .comment("Capacity of every plugin slot of the Extension Addon Type III (how many plugins fit per slot).",
                    "扩展插件Ⅲ型每个格子的容量（每格最多能放多少个插件）。")
            .defineInRange("type3SlotCapacity", 256, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue TRANSFER_ITEMS_PER_TICK = BUILDER
            .comment("How many items one configured face of a transfer plugin moves per tick, i.e. the rate of the",
                    "automation switch of the Extension Transfer / Transfer page. The budget is PER FACE and PER",
                    "DIRECTION: a face set to INPUT takes this many items out of the container outside it, an",
                    "OUTPUT face pushes this many into it, and a face set to both does each of the two, so the",
                    "worst case of one face is twice this number per tick.",
                    "The default 64 is one full stack per tick and per direction.",
                    "传输插件每个已配置面每 tick 搬运的物品数（即“自动化”开关的速度）。",
                    "该额度按【每个面、每个方向】计算：输入面从外侧容器抽取这么多，输出面向外侧容器弹出这么多，",
                    "双向面两个方向各算一次，因此单个面每 tick 的上限是这个数字的两倍。",
                    "默认 64，即每 tick 每个方向一整组。")
            .defineInRange("transferItemsPerTick", 64, 1, 6400);

    public static final ModConfigSpec.BooleanValue SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE = BUILDER
            .comment("List the wireless extension addons of a machine in its addon page, next to Oritech's own addons.",
                    "Client side display option: turn it off to get Oritech's stock addon page back.",
                    "在机器的插件页面中列出它的无线扩展坞（与 Oritech 自带插件并列显示）。",
                    "这是客户端显示选项：关掉就恢复 Oritech 原生的插件页面。")
            .define("showWirelessDocksInAddonPage", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /** Configured amount of slots of the given type, clamped to the supported range. */
    public static int slots(ExtensionAddonType type) {
        if (type == ExtensionAddonType.TYPE_3) return type.defaultSlots();

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
