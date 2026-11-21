package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.widgets.SurfaceWidget;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * Draws the transfer configuration page itself: Oritech's 176x100 panel, the block's item as its title icon, the
 * prompt line, the three mode plates with the face's current one sunken, the 自动化 checkbox and the 过滤 button.
 * <p>
 * It is the one place that drawing lives, because both transfer pages show this page and have to keep showing the
 * <b>same</b> page: {@link TransferFaceModal} puts it in front of the cube net page and the 3D page of 传输插件,
 * and {@link ExtensionTransferAddonPage} draws it directly as its own picker. The geometry, the hit tests and the
 * surfaces are shared here for the same reason the mode colours are shared in {@link TransferFaceStyle} - a player
 * who learned on one page what a plate means has to find the same plate in the same place on the other.
 * <p>
 * It is <b>stateless and model free</b>: it is told the mode and automation flag to show and, through
 * {@link #isOverPlate}, {@link #isOverAutomation} and {@link #isOverFilter}, which control the mouse is on. What a
 * click then does - and where it is sent - stays with the page, whose model differs: the cube net page keys a
 * setting by {@link net.minecraft.core.Direction} on one block, the 传输插件 page by cell and direction on a
 * structure.
 */
public final class TransferFaceRenderer {

    /** The plates and the automation row, in Oritech's own panel coordinates. */
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 14;
    private static final int BUTTON_Y = 30;
    private static final int AUTOMATION_BOX = 10;
    private static final int AUTOMATION_GAP = 4;
    private static final int AUTOMATION_Y = 60;
    /** The 过滤 row: one button under the automation row and above the prompt line. */
    private static final int FILTER_WIDTH = 60;
    private static final int FILTER_HEIGHT = 12;
    private static final int FILTER_Y = 72;
    /** Frame drawn around Oritech's panel, as on the other configuration pages. */
    private static final int PANEL_FRAME = 2;

    /** The prompt line of a configurable face, and the one of the face the plugin itself occupies. */
    private static final String PROMPT_KEY = "gui.oritechaddonsone.transfer.prompt";
    private static final String OCCUPIED_KEY = "gui.oritechaddonsone.transfer.occupied";
    private static final String AUTOMATION_KEY = "gui.oritechaddonsone.transfer.automation";
    private static final String FILTER_KEY = "gui.oritechaddonsone.transfer.filter";

    /** Colour of the green tick, as on the cube net page's checkbox. */
    private static final int GOOD = 0xFF2ECC71;

    private static final List<TransferMode> MODES = TransferFaceStyle.MODES;

    private TransferFaceRenderer() {
    }

    /**
     * Draws the configuration page of one face over the page's panel.
     * <p>
     * It has to be called <b>after</b> the page drew its own content, because it paints an opaque backdrop over the
     * panel and everything inside it is then drawn in Oritech's own panel coordinates through one translate - the
     * coordinate system {@link SurfaceWidget} expects.
     *
     * @param occupied true while the plugin itself stands on this face, which makes it unconfigurable: the plates
     *                 are then dimmed and the prompt explains why
     */
    public static void render(AddonPageContext context, GuiGraphics graphics, TransferMode mode,
            boolean automation, boolean occupied, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var placed = AddonPickerPanel.place(context);
        var open = !occupied;

        // The whole modal is drawn in front of the model, not just after it: the 3D page's model is real 3D geometry
        // with the depth test on, so a later draw at z = 0 is behind it and would lose every pixel - see
        // FacePreviewWidget#OVER_MODEL_Z for the derivation. The cube net page has no model, and the same z is
        // simply in front of its panels as well.
        //
        // The backdrop has to be inside this pose too, and not only the panel it draws: it covers the whole panel the
        // modal sits on, the model's panel included, so leaving it at z = 0 is what let the machine show through.
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, FacePreviewWidget.OVER_MODEL_Z);

        // a dark backdrop over the whole panel, so the modal reads as a step of its own and nothing of the page
        // behind it shows through
        graphics.fill(context.left(), context.top(), context.panelRight(), context.panelBottom(), 0xD0000000);

        graphics.pose().translate(context.screenX(placed.innerX()), context.screenY(placed.innerY()), 0f);

        drawPanel(graphics);
        drawPlates(graphics, font, mode, placed, open, mouseX, mouseY);
        drawAutomation(graphics, font, mode, automation, placed, open, mouseX, mouseY);
        drawFilter(graphics, font, mode, placed, open, mouseX, mouseY);
        prompt(graphics, font, occupied);
        header(graphics, context, placed);

        graphics.pose().popPose();
    }

    /**
     * True while the given panel relative mouse position is on the plate with the given index. The whole plate is
     * the hit area, so a click anywhere on it sets that mode.
     */
    public static boolean isOverPlate(AddonPickerPanel.Placed placed, int index, double mouseX, double mouseY) {
        double x = placed.innerX() + plateX(index);
        double y = placed.innerY() + BUTTON_Y;
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    /** True while the given panel relative mouse position is on the automation row, label included. */
    public static boolean isOverAutomation(AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var label = Component.translatable(AUTOMATION_KEY).getString();
        var row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);

        double x = placed.innerX() + automationBoxX(font, label);
        double y = placed.innerY() + AUTOMATION_Y;
        return mouseX >= x && mouseX < x + row && mouseY >= y && mouseY < y + AUTOMATION_BOX;
    }

    /** True while the given panel relative mouse position is on the 过滤 button. */
    public static boolean isOverFilter(AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        double x = placed.innerX() + (AddonPickerPanel.WIDTH - FILTER_WIDTH) / 2;
        double y = placed.innerY() + FILTER_Y;
        return mouseX >= x && mouseX < x + FILTER_WIDTH && mouseY >= y && mouseY < y + FILTER_HEIGHT;
    }

    /** The panel itself: Oritech's nine patch inside a darker frame, as on the cube net page. */
    private static void drawPanel(GuiGraphics graphics) {
        new SurfaceWidget(-PANEL_FRAME, -PANEL_FRAME, AddonPickerPanel.WIDTH + 2 * PANEL_FRAME,
                AddonPickerPanel.HEIGHT + 2 * PANEL_FRAME, OritechSurface.PANEL_DARK).render(graphics, 0, 0, 0f);
        new SurfaceWidget(0, 0, AddonPickerPanel.WIDTH, AddonPickerPanel.HEIGHT, OritechSurface.PANEL)
                .render(graphics, 0, 0, 0f);
    }

    /** The three mode plates, Oritech's own button surfaces, with the face's current mode sunken in. */
    private static void drawPlates(GuiGraphics graphics, Font font, TransferMode mode,
            AddonPickerPanel.Placed placed, boolean open, double mouseX, double mouseY) {
        for (int index = 0; index < MODES.size(); index++) {
            var plate = MODES.get(index);
            int x = plateX(index);
            int y = BUTTON_Y;

            var surface = !open || plate == mode ? OritechSurface.PANEL_DARK
                    : isOverPlate(placed, index, mouseX, mouseY) ? OritechSurface.PANEL_HOVER : OritechSurface.PANEL;
            surface.render(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);

            var text = Component.translatable(TransferFaceStyle.modeKey(plate)).getString();
            graphics.drawString(font, text, x + (BUTTON_WIDTH - font.width(text)) / 2, y + (BUTTON_HEIGHT - 8) / 2,
                    open ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
        }
    }

    /**
     * The automation row: Oritech's dark checkbox and its label, centred under the plates. With automation on the
     * face moves items by itself - towards the container on that side for "output", from it for "input", both for
     * "input + output" (see {@code TransferAddonBlockEntity#serverTickTransfer()}).
     * <p>
     * The row is dimmed and refuses clicks while the face has no direction yet: a face that transfers nothing has
     * nothing to move on its own, so the switch only becomes meaningful together with a mode.
     */
    private static void drawAutomation(GuiGraphics graphics, Font font, TransferMode mode,
            boolean automation, AddonPickerPanel.Placed placed, boolean open, double mouseX, double mouseY) {
        var enabled = open && mode != TransferMode.NONE;
        var on = enabled && automation;
        var label = Component.translatable(AUTOMATION_KEY).getString();

        int boxX = automationBoxX(font, label);
        int y = AUTOMATION_Y;

        var surface = enabled && isOverAutomation(placed, mouseX, mouseY) ? OritechSurface.PANEL_DARK_HOVER
                : OritechSurface.PANEL_DARK;
        surface.render(graphics, boxX, y, AUTOMATION_BOX, AUTOMATION_BOX);
        if (on) {
            graphics.fill(boxX + 2, y + 2, boxX + AUTOMATION_BOX - 2, y + AUTOMATION_BOX - 2, GOOD);
        }

        graphics.drawString(font, label, boxX + AUTOMATION_BOX + AUTOMATION_GAP, y + 1,
                enabled ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /**
     * The 过滤 row: the one button that opens the item filter page of this face, centred between the automation
     * row and the prompt line - the only strip of the panel that is free.
     * <p>
     * <b>It is dimmed while the face has no direction yet</b>, for the same reason the automation switch is: a
     * face that transfers nothing has nothing to filter, and a page the player can only look at is worse than a
     * button that explains itself.
     * <p>
     * <b>It does not show whether this face is already filtered.</b> The client is not told the filters - they
     * live in the machine's shared map and are read straight from the server when the page opens - and a wrong
     * marker here would be worse than none. What the button does instead is open the page, and the page shows
     * the real contents.
     */
    private static void drawFilter(GuiGraphics graphics, Font font, TransferMode mode,
            AddonPickerPanel.Placed placed, boolean open, double mouseX, double mouseY) {
        var enabled = open && mode != TransferMode.NONE;
        var label = Component.translatable(FILTER_KEY).getString();

        int x = (AddonPickerPanel.WIDTH - FILTER_WIDTH) / 2;
        int y = FILTER_Y;

        // the light bedrock panel, not the dark one the mode plates and the automation switch use: this button
        // is the page's one way onward to a second page, and a dark plate in the middle of the dark panel did not
        // read as a button - nor did Oritech's dark grey text read on top of it. PANEL/PANEL_HOVER is the light
        // pair, and PANEL_TEXT is the dark text that goes with it
        var surface = enabled && isOverFilter(placed, mouseX, mouseY) ? OritechSurface.PANEL_HOVER
                : OritechSurface.PANEL;
        surface.render(graphics, x, y, FILTER_WIDTH, FILTER_HEIGHT);
        graphics.drawString(font, label, x + (FILTER_WIDTH - font.width(label)) / 2, y + (FILTER_HEIGHT - 8) / 2,
                enabled ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /**
     * The prompt line of the configuration page: what this face does, or - while the plugin itself stands on it -
     * the same occupied-face sentence the cube net page shows, because that face can never carry a mode.
     * <p>
     * The occupied sentence is the longest text of either transfer page, so it is cut to the panel's own width
     * with an ellipsis: a sentence running past the panel and over the model next to it would read as a drawing
     * fault, and the full text is the face's tooltip on the page behind the modal anyway.
     */
    private static void prompt(GuiGraphics graphics, Font font, boolean occupied) {
        var text = fit(font, Component.translatable(occupied ? OCCUPIED_KEY : PROMPT_KEY).getString());
        graphics.drawString(font, text, (AddonPickerPanel.WIDTH - font.width(text)) / 2, AddonPickerPanel.PROMPT_Y,
                AddonPanelStyle.PANEL_TEXT, false);
    }

    /** The text, cut to the panel's width with an ellipsis while it is too long to fit. */
    private static String fit(Font font, String text) {
        if (font.width(text) <= AddonPickerPanel.WIDTH) return text;

        var ellipsis = "...";
        var room = AddonPickerPanel.WIDTH - font.width(ellipsis);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) > room) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    /** The header: the item of the block the menu belongs to, in Oritech's own 28x28 title icon. */
    private static void header(GuiGraphics graphics, AddonPageContext context, AddonPickerPanel.Placed placed) {
        int left = placed.iconX() - placed.innerX();
        int top = placed.iconY() - placed.innerY();

        new SurfaceWidget(left, top, AddonPickerPanel.ICON_SIZE, AddonPickerPanel.ICON_SIZE, OritechSurface.PANEL)
                .withPadding(Insets.of(0, AddonPickerPanel.ICON_PADDING, AddonPickerPanel.ICON_PADDING,
                        AddonPickerPanel.ICON_PADDING))
                .render(graphics, 0, 0, 0f);

        graphics.renderItem(icon(context.menu()), left + AddonPickerPanel.ICON_PADDING, top + AddonPickerPanel.ICON_PADDING);
    }

    /** The item drawn as the configuration page's icon: the block this menu belongs to. */
    private static ItemStack icon(ExtensionAddonMenu menu) {
        var block = menu.addonBlock();
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    /** Panel relative X of the mode plate with the given index, all three centred in the panel. */
    private static int plateX(int index) {
        int total = MODES.size() * BUTTON_WIDTH + (MODES.size() - 1) * BUTTON_GAP;
        return (AddonPickerPanel.WIDTH - total) / 2 + index * (BUTTON_WIDTH + BUTTON_GAP);
    }

    /** Left edge of the automation checkbox, centring the box and its label in the configuration page. */
    private static int automationBoxX(Font font, String label) {
        int row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);
        return (AddonPickerPanel.WIDTH - row) / 2;
    }
}
