package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import rearth.oritech.api.networking.NetworkManager;
import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.widgets.BoxWidget;
import rearth.oritech.api.screen.widgets.ButtonWidget;
import rearth.oritech.api.screen.widgets.ItemSlotWidget;
import rearth.oritech.api.screen.widgets.ItemWidget;
import rearth.oritech.api.screen.widgets.LabelWidget;
import rearth.oritech.api.screen.widgets.SurfaceWidget;
import rearth.oritech.api.screen.widgets.TextureWidget;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity.RedstoneMode;
import rearth.oritech.client.ui.OritechWidgetScreen;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.ScreenProvider;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.RedstoneNetworking;

/**
 * The Redstone Control page (红石控制) of the type II Extension Addon.
 * <p>
 * It is the configuration page Oritech's control unit plugin opens on right click, painted inside the addon
 * panel instead of a separate screen: the same 176 pixel wide {@link OritechSurface#PANEL} panel, the same
 * mode title and description lines, the same {@code <} / {@code >} buttons, the same {@code n / 5} indicator
 * and - in {@code OUTPUT_SLOT} - the same machine GUI slot cells with Oritech's selection plate and progress
 * arrow. The mode and the monitored slot belong to the stored control unit, which is an item here and has no
 * block entity; the addon keeps them and the server applies exactly the behaviour the control unit would.
 * <p>
 * The page has no additional description line of its own: the tab says what it is, and the mode description
 * is the control unit's own, word for word.
 */
public final class RedstoneAddonPage implements AddonPage {

    public static final String ID = "redstone";
    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/gui/redstone_tab.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 100;
    private static final int HEIGHT_SLOT = 194;
    private static final int HEADER_Y = -27;
    private static final int INNER_Y = 29;

    /** Body height of the last rendered frame, so the panel border follows the mode's 100/194 panel. */
    private int bodyHeight = HEIGHT;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component label() {
        return Component.translatable(LABEL_KEY);
    }

    @Override
    public ResourceLocation icon() {
        return ICON;
    }

    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        return Math.max(layout.imageHeight(), bodyHeight + INNER_Y + 4);
    }

    @Override
    public void render(AddonPageContext context, GuiGraphics graphics, float partialTick,
            double mouseX, double mouseY) {
        var menu = context.menu();
        var mode = modeOf(menu.redstoneMode());
        bodyHeight = mode == RedstoneMode.OUTPUT_SLOT ? HEIGHT_SLOT : HEIGHT;
        var innerX = (context.panelWidth() - WIDTH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(context.screenX(innerX), context.screenY(INNER_Y), 0f);

        var relX = (int) (mouseX - innerX);
        var relY = (int) (mouseY - INNER_Y);

        new SurfaceWidget(0, 0, WIDTH, bodyHeight, OritechSurface.PANEL)
                .render(graphics, relX, relY, partialTick);

        new LabelWidget(8, 16, 160, 10, modeText(mode))
                .withAlignment(LabelWidget.Alignment.CENTER)
                .render(graphics, relX, relY, partialTick);

        new LabelWidget(8, 28, 160, 18, descriptionText(mode))
                .withAlignment(LabelWidget.Alignment.CENTER)
                .withDarkColor()
                .withWrap(true)
                .render(graphics, relX, relY, partialTick);

        ButtonWidget.darkPanel(20, 58, 18, 14, Component.literal("<"), button -> { })
                .withTextColor(OritechWidgetScreen.SEPARATOR_COLOR)
                .withSurfacePadding(Insets.of(1, 2, 1, 2))
                .render(graphics, relX, relY, partialTick);
        ButtonWidget.darkPanel(138, 58, 18, 14, Component.literal(">"), button -> { })
                .withTextColor(OritechWidgetScreen.SEPARATOR_COLOR)
                .withSurfacePadding(Insets.of(1, 0, 1, 3))
                .render(graphics, relX, relY, partialTick);

        new LabelWidget(40, 61, 96, 10,
                Component.literal((mode.ordinal() + 1) + " / " + RedstoneMode.values().length))
                .withAlignment(LabelWidget.Alignment.CENTER)
                .withDarkColor()
                .render(graphics, relX, relY, partialTick);

        if (mode == RedstoneMode.OUTPUT_SLOT) {
            renderSlotSelector(context, graphics, relX, relY, partialTick);
        }

        renderHeader(graphics, relX, relY, partialTick);

        graphics.pose().popPose();
    }

    /** Oritech's title: the control unit item and block name, 27 pixels above the panel. */
    private void renderHeader(GuiGraphics graphics, int relX, int relY, float partialTick) {
        var block = BlockContent.MACHINE_REDSTONE_ADDON;
        var title = block.getName();
        var icon = new net.minecraft.world.item.ItemStack(block);

        var iconWidget = new ItemWidget(0, 0, 28, icon);
        iconWidget.withSurface(OritechSurface.PANEL);
        iconWidget.withPadding(Insets.of(0, 2, 3, 2));
        iconWidget.withShowOverlay(false);
        iconWidget.withTooltipFromStack(false);
        iconWidget.withZIndex(50);

        var font = Minecraft.getInstance().font;
        var textWidth = font.width(title);
        var labelWidget = new LabelWidget(0, 0, textWidth + 10, 14, title);
        labelWidget.withSurface(OritechSurface.PANEL);
        labelWidget.withPadding(Insets.of(5, 0, 1, 10));
        labelWidget.withZIndex(50);
        labelWidget.withDarkColor();

        var combinedWidth = iconWidget.getWidth() + labelWidget.getWidth() + 2;
        var titleX = (WIDTH - combinedWidth) * 65 / 100;
        if (title.getString().length() > 15) titleX = WIDTH - combinedWidth;

        iconWidget.setPosition(titleX, HEADER_Y);
        labelWidget.setPosition(titleX + iconWidget.getWidth() + iconWidget.getPadding().right() + 6,
                HEADER_Y + 9);

        iconWidget.render(graphics, relX, relY, partialTick);
        labelWidget.render(graphics, relX, relY, partialTick);
    }

    private void renderSlotSelector(AddonPageContext context, GuiGraphics graphics,
            int relX, int relY, float partialTick) {
        var menu = context.menu();
        var provider = machineScreen(menu);

        BoxWidget.filled(8, 100, 160, 1, LabelWidget.DARK_TEXT).render(graphics, relX, relY, partialTick);

        new LabelWidget(0, 116, WIDTH, 10, Component.translatable("title.oritech.redstone_addon"))
                .withAlignment(LabelWidget.Alignment.CENTER)
                .withDarkColor()
                .render(graphics, relX, relY, partialTick);

        if (provider == null) return;

        var selected = menu.monitoredSlot();
        for (var slot : provider.getGuiSlots()) {
            var x = slot.x();
            var y = 120 + slot.y();

            new ItemSlotWidget(x, y).render(graphics, relX, relY, partialTick);

            var hovered = relX >= x && relX < x + 16 && relY >= y && relY < y + 16;
            var plate = slot.index() == selected
                    ? OritechSurface.PANEL_DARK
                    : hovered ? OritechSurface.PANEL_HOVER : OritechSurface.PANEL;
            plate.render(graphics, x + 3, y + 3, 10, 10);
        }

        if (provider.showProgress()) {
            var arrow = provider.getIndicatorConfiguration();
            new TextureWidget(arrow.x(), 120 + arrow.y(), arrow.width(), arrow.height(), arrow.full(),
                    0, 0, arrow.width(), arrow.height(), arrow.width(), arrow.height())
                    .render(graphics, relX, relY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        var menu = context.menu();
        var mode = modeOf(menu.redstoneMode());
        var innerX = (context.panelWidth() - WIDTH) / 2;
        var x = mouseX - innerX;
        var y = mouseY - INNER_Y;

        if (x >= 20 && x < 38 && y >= 58 && y < 72) {
            shiftMode(menu, mode, -1);
            return true;
        }
        if (x >= 138 && x < 156 && y >= 58 && y < 72) {
            shiftMode(menu, mode, 1);
            return true;
        }

        if (mode == RedstoneMode.OUTPUT_SLOT) {
            var provider = machineScreen(menu);
            if (provider == null) return false;

            for (var slot : provider.getGuiSlots()) {
                var slotX = slot.x();
                var slotY = 120 + slot.y();
                if (x >= slotX && x < slotX + 16 && y >= slotY && y < slotY + 16) {
                    apply(menu, mode, slot.index());
                    return true;
                }
            }
        }

        return false;
    }

    private static void shiftMode(ExtensionAddonMenu menu, RedstoneMode mode, int delta) {
        var modes = RedstoneMode.values();
        var next = Math.floorMod(mode.ordinal() + delta, modes.length);
        apply(menu, modes[next], menu.monitoredSlot());
    }

    private static void apply(ExtensionAddonMenu menu, RedstoneMode mode, int slot) {
        menu.previewRedstoneConfig(mode.ordinal(), slot);
        NetworkManager.sendToServer(new RedstoneNetworking.Configure(menu.position(), mode.ordinal(), slot));
    }

    private static ScreenProvider machineScreen(ExtensionAddonMenu menu) {
        var level = Minecraft.getInstance().level;
        var machinePos = menu.transferMachinePos();
        if (level == null || machinePos == null) return null;

        BlockEntity machine = level.getBlockEntity(machinePos);
        return machine instanceof ScreenProvider provider ? provider : null;
    }

    private static RedstoneMode modeOf(int ordinal) {
        var modes = RedstoneMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1, ordinal))];
    }

    private static Component modeText(RedstoneMode mode) {
        return Component.translatable("title.oritech.redstone_" + mode.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GRAY);
    }

    private static Component descriptionText(RedstoneMode mode) {
        return Component.translatable("tooltip.oritech.redstone_" + mode.name().toLowerCase(Locale.ROOT));
    }
}



