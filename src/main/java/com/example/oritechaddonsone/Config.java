package com.example.oritechaddonsone;

import net.neoforged.neoforge.common.ModConfigSpec;

import com.example.oritechaddonsone.block.ExtensionPluginType;
import com.example.oritechaddonsone.menu.ExtensionPluginLayout;

/**
 * Common config of the mod. The inventory of the three plugin types can be adjusted here:
 * the slot count of type I and II (1-36) and the capacity of every slot of type III.
 * The file is created at {@code config/oritechaddonsone-common.toml}.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue TYPE_1_SLOTS = BUILDER
            .comment("Number of plugin slots of the Extension Plugin Type I.",
                    "扩展插件Ⅰ型的物品栏格数。")
            .defineInRange("type1Slots", 5, ExtensionPluginLayout.MIN_SLOTS, ExtensionPluginLayout.MAX_SLOTS);

    public static final ModConfigSpec.IntValue TYPE_2_SLOTS = BUILDER
            .comment("Number of plugin slots of the Extension Plugin Type II.",
                    "扩展插件Ⅱ型的物品栏格数。")
            .defineInRange("type2Slots", 5, ExtensionPluginLayout.MIN_SLOTS, ExtensionPluginLayout.MAX_SLOTS);

    public static final ModConfigSpec.IntValue TYPE_3_SLOT_CAPACITY = BUILDER
            .comment("Capacity of every plugin slot of the Extension Plugin Type III (how many plugins fit per slot).",
                    "扩展插件Ⅲ型每个格子的容量（每格最多能放多少个插件）。")
            .defineInRange("type3SlotCapacity", 256, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /** Configured amount of slots of the given type, clamped to the supported range. */
    public static int slots(ExtensionPluginType type) {
        if (type == ExtensionPluginType.TYPE_3) return type.defaultSlots();

        var value = type == ExtensionPluginType.TYPE_1 ? TYPE_1_SLOTS : TYPE_2_SLOTS;
        try {
            return Math.max(ExtensionPluginLayout.MIN_SLOTS,
                    Math.min(ExtensionPluginLayout.MAX_SLOTS, value.get()));
        } catch (IllegalStateException notLoadedYet) {
            // Config values are not available in every early loading stage; fall back to the default.
            return type.defaultSlots();
        }
    }

    /** Configured capacity of one slot of the given type (only type III supports more than 64). */
    public static int slotCapacity(ExtensionPluginType type) {
        if (type != ExtensionPluginType.TYPE_3) return 64;

        try {
            return Math.max(1, TYPE_3_SLOT_CAPACITY.get());
        } catch (IllegalStateException notLoadedYet) {
            return 256;
        }
    }
}
