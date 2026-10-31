package io.github.xiao232ming.oritechaddonsone.fabric.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.common.ModConfigSpec;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * Fabric 侧的配置界面（由 Mod Menu 承载）。
 * <p>
 * NeoForge 侧用自带的 {@code ConfigurationScreen}，Fabric 没有等价物，所以这里提供一个原版控件组成的
 * 数字输入页：每个整数配置一项，输入框接受十进制数字，Done 时才校验并写入 {@link Config#SPEC}，Cancel
 * 关闭时不会改动任何值。配置数据仍然是 common 里的那一份 {@code ModConfigSpec}。
 * <p>
 * 数值范围就是配置项自己的范围：Ⅰ/Ⅱ型格数、Ⅲ型容量、仓库/储罐插件加成、物品/流体传输速度。文本标签
 * 使用 {@code oritechaddonsone.configuration.*} 翻译键，不再写死英文。
 */
public class AddonsConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 30;
    private static final int FIELD_HEIGHT = 16;

    private final Screen parent;
    private final List<Field> fields = new ArrayList<>();

    private boolean wirelessValue;
    private Component error;

    public AddonsConfigScreen(Screen parent) {
        super(Component.translatable("oritechaddonsone.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        fields.clear();
        error = null;

        int columnWidth = Math.max(120, Math.min(250, (this.width - 30) / 2));
        int gap = 10;
        int left = this.width / 2 - columnWidth - gap / 2;
        int right = this.width / 2 + gap / 2;
        int top = Math.max(42, this.height / 2 - 100);

        addField(left, top + ROW_HEIGHT * 0, columnWidth, "type1Slots",
                Config.TYPE_1_SLOTS, ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS);
        addField(left, top + ROW_HEIGHT * 1, columnWidth, "type2Slots",
                Config.TYPE_2_SLOTS, ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS);
        addField(left, top + ROW_HEIGHT * 2, columnWidth, "type3SlotCapacity",
                Config.TYPE_3_SLOT_CAPACITY, 1, Integer.MAX_VALUE);
        addField(left, top + ROW_HEIGHT * 3, columnWidth, "warehouseAddonSlots",
                Config.WAREHOUSE_ADDON_SLOTS, 1, 64);

        addField(right, top + ROW_HEIGHT * 0, columnWidth, "tankAddonCapacity",
                Config.TANK_ADDON_CAPACITY, 1000, 32000);
        addField(right, top + ROW_HEIGHT * 1, columnWidth, "transferItemsPerTick",
                Config.TRANSFER_ITEMS_PER_TICK, 1, 6400);
        addField(right, top + ROW_HEIGHT * 2, columnWidth, "transferFluidPerTick",
                Config.TRANSFER_FLUID_PER_TICK, 1, 1_000_000);

        wirelessValue = boolValue(Config.SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE);
        var wirelessWidth = Math.min(300, this.width - 40);
        var wirelessY = top + ROW_HEIGHT * 4 + 6;
        addRenderableWidget(Button.builder(
                        wirelessLabel(wirelessValue), button -> {
                            wirelessValue = !wirelessValue;
                            button.setMessage(wirelessLabel(wirelessValue));
                            error = null;
                        })
                .bounds(this.width / 2 - wirelessWidth / 2, wirelessY, wirelessWidth, 20)
                .build());

        var footer = wirelessY + 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(this.width / 2 - 102, footer, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(this.width / 2 + 2, footer, 100, 20)
                .build());
    }

    private void addField(int x, int y, int width, String key, ModConfigSpec.IntValue spec, int min, int max) {
        var box = new EditBox(this.font, x, y + 11, width, FIELD_HEIGHT,
                Component.translatable("oritechaddonsone.configuration." + key));
        box.setMaxLength(10);
        box.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        box.setValue(Integer.toString(intValue(spec)));
        box.setResponder(value -> error = null);
        addRenderableWidget(box);
        fields.add(new Field(key, spec, min, max, box));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.oritechaddonsone.config.note"),
                this.width / 2, 24, 0xA0A0A0);

        for (var field : fields) {
            graphics.drawString(this.font, field.label(), field.box().getX(), field.box().getY() - 10, 0xFFFFFF);
        }

        if (error != null) {
            graphics.drawCenteredString(this.font, error, this.width / 2,
                    fields.getFirst().box().getY() + ROW_HEIGHT * 4 + 56, 0xFF5555);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void saveAndClose() {
        var values = new int[fields.size()];
        for (int index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            var text = field.box().getValue().trim();
            try {
                if (text.isEmpty()) throw new NumberFormatException();
                var value = Integer.parseInt(text);
                if (value < field.min() || value > field.max()) throw new NumberFormatException();
                values[index] = value;
            } catch (NumberFormatException invalid) {
                error = Component.translatable("gui.oritechaddonsone.config.invalid", field.label());
                return;
            }
        }

        try {
            for (int index = 0; index < fields.size(); index++) {
                fields.get(index).spec().set(values[index]);
            }
            Config.SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE.set(wirelessValue);
            Config.SPEC.save();
        } catch (Throwable failure) {
            io.github.xiao232ming.oritechaddonsone.OritechAddonsOne.LOGGER.warn(
                    "Could not save oritechaddonsone config from the Mod Menu screen", failure);
            error = Component.translatable("gui.oritechaddonsone.config.save_failed");
            return;
        }

        this.minecraft.setScreen(parent);
    }

    private static Component wirelessLabel(boolean value) {
        return Component.translatable("oritechaddonsone.configuration.showWirelessDocksInAddonPage")
                .append(Component.literal(": "))
                .append(Component.translatable(value ? "options.on" : "options.off"));
    }

    private static int intValue(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }

    private static boolean boolValue(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }

    /** One numeric config option and its input field. */
    private record Field(String key, ModConfigSpec.IntValue spec, int min, int max, EditBox box) {

        Component label() {
            return Component.translatable("oritechaddonsone.configuration." + key).withStyle(ChatFormatting.WHITE);
        }
    }
}
