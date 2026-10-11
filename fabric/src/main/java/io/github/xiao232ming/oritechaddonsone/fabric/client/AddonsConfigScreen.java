package io.github.xiao232ming.oritechaddonsone.fabric.client;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;

/**
 * Fabric 侧的配置界面（由 Mod Menu 承载）。
 * <p>
 * NeoForge 侧用的是它自带的 {@code ConfigurationScreen}（{@code IConfigScreenFactory} 扩展点），Fabric 没有
 * 等价物，所以这里用原版控件画一个最小的界面，直接读写 common 里那一份 {@code Config.SPEC}。
 * <p>
 * 注意：Ⅰ/Ⅱ型的格数会决定菜单布局与槽位数量，保存后需要重开存档（或重启游戏）才生效；Ⅲ型容量与传输速度
 * 是每次读取的，立即生效。
 */
public class AddonsConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 220;

    private final Screen parent;

    private IntSlider type1Slider;
    private IntSlider type2Slider;
    private IntSlider type3Slider;
    private IntSlider transferSlider;
    private Button wirelessButton;

    /** 打开界面时的原值，Cancel/Esc 时写回。 */
    private boolean showWirelessOriginal;

    public AddonsConfigScreen(Screen parent) {
        super(Component.translatable("oritechaddonsone.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var x = this.width / 2 - WIDGET_WIDTH / 2;
        var y = this.height / 4;

        type1Slider = addRenderableWidget(new IntSlider(x, y, WIDGET_WIDTH, 20,
                "Extension Addon I - slots", ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS,
                intValue(Config.TYPE_1_SLOTS), value -> set(Config.TYPE_1_SLOTS, value)));

        type2Slider = addRenderableWidget(new IntSlider(x, y + ROW_HEIGHT, WIDGET_WIDTH, 20,
                "Extension Addon II - slots", ExtensionAddonLayout.MIN_SLOTS, ExtensionAddonLayout.MAX_SLOTS,
                intValue(Config.TYPE_2_SLOTS), value -> set(Config.TYPE_2_SLOTS, value)));

        type3Slider = addRenderableWidget(new IntSlider(x, y + ROW_HEIGHT * 2, WIDGET_WIDTH, 20,
                "Extension Addon III - slot capacity", 1, 4096,
                intValue(Config.TYPE_3_SLOT_CAPACITY), value -> set(Config.TYPE_3_SLOT_CAPACITY, value)));

        transferSlider = addRenderableWidget(new IntSlider(x, y + ROW_HEIGHT * 3, WIDGET_WIDTH, 20,
                "Transfer plugin - items per tick", 1, 6400,
                intValue(Config.TRANSFER_ITEMS_PER_TICK), value -> set(Config.TRANSFER_ITEMS_PER_TICK, value)));

        showWirelessOriginal = boolValue(Config.SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE);
        wirelessButton = addRenderableWidget(Button.builder(wirelessLabel(showWirelessOriginal), button -> {
            var next = !boolValue(Config.SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE);
            set(Config.SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE, next);
            button.setMessage(wirelessLabel(next));
        }).bounds(x, y + ROW_HEIGHT * 4, WIDGET_WIDTH, 20).build());

        var footer = y + ROW_HEIGHT * 5 + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(this.width / 2 - 102, footer, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> cancel())
                .bounds(this.width / 2 + 2, footer, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 28, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("Slot counts need a world reload; capacity and speed apply immediately."),
                this.width / 2, this.height / 4 - 16, 0xA0A0A0);
        graphics.drawCenteredString(this.font,
                Component.literal("Capacity above 4096 has to be edited in config/oritechaddonsone-common.toml."),
                this.width / 2, this.height / 4 + ROW_HEIGHT * 5 + 40, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        cancel();
    }

    private void saveAndClose() {
        try {
            Config.SPEC.save();
        } catch (Throwable failure) {
            // 配置还没被加载时无法保存；不因为一个界面把游戏搞崩
            io.github.xiao232ming.oritechaddonsone.OritechAddonsOne.LOGGER.warn(
                    "Could not save oritechaddonsone config from the Mod Menu screen", failure);
        }
        this.minecraft.setScreen(parent);
    }

    private void cancel() {
        set(Config.SHOW_WIRELESS_DOCKS_IN_ADDON_PAGE, showWirelessOriginal);
        this.minecraft.setScreen(parent);
    }

    private static Component wirelessLabel(boolean value) {
        return Component.literal("Show wireless docks in the addon page: " + (value ? "ON" : "OFF"));
    }

    private static int intValue(net.neoforged.neoforge.common.ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }

    private static boolean boolValue(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }

    private static void set(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int newValue) {
        try {
            value.set(newValue);
        } catch (IllegalStateException notLoadedYet) {
            // 未加载时忽略：界面上的改动无法生效，保存时也不会写坏文件
        }
    }

    private static void set(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value, boolean newValue) {
        try {
            value.set(newValue);
        } catch (IllegalStateException notLoadedYet) {
            // 见上
        }
    }

    /** 一个最小的整数滑条：左端是 min，右端是 max，拖动时立刻写进配置。 */
    private static final class IntSlider extends AbstractSliderButton {

        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        private IntSlider(int x, int y, int width, int height, String label, int min, int max, int initial,
                IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), max <= min ? 0.0 : (double) (clamp(initial, min, max) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
