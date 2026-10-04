package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.widgets.SurfaceWidget;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.menu.FaceFilterMenu;
import io.github.xiao232ming.oritechaddonsone.network.FilterNetworking;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * The configuration page the transfer pages open on a click on a face: the same Oritech panel both use - the
 * block's item as its title icon, a prompt line, the three mode plates (输入/输出/输入
 * 输出) with the face's current one sunken, and the 自动化 checkbox.
 * <p>
 * It is one class and not a copy inside each page because the two transfer pages have to stay the same page: a
 * player who learned on the net what the plates mean has to find the same plates, in the same place, in the same
 * colours on the model. The geometry, the surfaces and the mode names are shared already
 * ({@link AddonPickerPanel}, {@link TransferFaceStyle}); this class is the drawing, the hit tests and the send
 * path between them, and the page that opens it supplies what the face does and where a change goes
 * ({@link Current}, {@link Sink}) - the cube net page reads its settings from the menu, the 传输插件 page from the
 * map the server sent, so this class can serve both without knowing either model.
 * <p>
 * What a click does is the cube net page's behaviour: a click on a plate sets that mode and leaves the page open,
 * so the plate turns dark and the player can pick another one; the automation checkbox only toggles and keeps the
 * direction; the right mouse button and any click outside a control close the page. A face the plugin itself
 * stands on is not configurable, and the page says so with the same language key the net page uses.
 */
public final class TransferFaceModal {

    /** The plates and the automation row, in Oritech's own panel coordinates. */
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 14;
    private static final int BUTTON_Y = 30;
    private static final int AUTOMATION_BOX = 10;
    private static final int AUTOMATION_GAP = 4;
    private static final int AUTOMATION_Y = 60;
    /** The 过滤 row: one button under the automation row and above the prompt line, which is all the room there is. */
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

    private TransferFaceModal() {
    }

    /**
     * Draws the configuration page of one face over the page's panel.
     * <p>
     * It has to be called <b>after</b> the page drew its own content, because it paints an opaque backdrop over
     * the panel and everything inside it is then drawn in Oritech's own panel coordinates through one translate -
     * the coordinate system {@link SurfaceWidget} expects.
     * <p>
     * <b>The caller owns the data.</b> This modal is shared by the two transfer pages, whose models are different -
     * the cube net page keys a setting by {@link Direction} on one block, the 传输插件 page by cell and direction on a
     * structure (see {@code CellFaceModes}) - so it neither reads nor writes a model itself. It is told which face it
     * shows, what that face currently does ({@link Current}) and what a change should be sent to ({@link Sink}).
     *
     * @param occupied true while the plugin itself stands on this face, which makes it unconfigurable: the plates
     *                 are then dimmed and refuse the click, and the prompt explains why
     */
    public static void render(AddonPageContext context, GuiGraphics graphics, Direction face, Current current,
            boolean occupied, double mouseX, double mouseY) {
        render(context, graphics, face, current, occupied, mouseX, mouseY, face0 -> {
        });
    }

    /**
     * Draws the configuration page of one face, with the way to open that face's 过滤 page.
     *
     * @see #render(AddonPageContext, GuiGraphics, Direction, Current, boolean, double, double)
     */
    public static void render(AddonPageContext context, GuiGraphics graphics, Direction face, Current current,
            boolean occupied, double mouseX, double mouseY, FilterOpener filter) {
        var font = Minecraft.getInstance().font;
        var placed = AddonPickerPanel.place(context);
        var open = !occupied;

        // a dark backdrop over the whole panel, so the modal reads as a step of its own and nothing of the page
        // behind it shows through
        graphics.fill(context.left(), context.top(), context.panelRight(), context.panelBottom(), 0xD0000000);

        graphics.pose().pushPose();
        graphics.pose().translate(context.screenX(placed.innerX()), context.screenY(placed.innerY()), 0f);

        drawPanel(graphics);
        drawPlates(graphics, font, face, current, placed, open, mouseX, mouseY);
        drawAutomation(graphics, font, current, placed, open, mouseX, mouseY);
        drawFilter(graphics, font, current, placed, open, mouseX, mouseY);
        prompt(graphics, font, occupied);
        header(graphics, context, placed);

        graphics.pose().popPose();
    }

    /**
     * A click while the configuration page is open.
     *
     * @return true while the click belonged to the page, so the page behind it never sees it; false while the
     *         page is not open at all
     */
    public static boolean mouseClicked(AddonPageContext context, Direction face, Current current, Sink sink,
            boolean occupied, double mouseX, double mouseY, int button) {
        return mouseClicked(context, face, current, sink, occupied, mouseX, mouseY, button, face0 -> {
        });
    }

    /**
     * A click while the configuration page is open, with the way to open that face's 过滤 page.
     *
     * @see #mouseClicked(AddonPageContext, Direction, Current, Sink, boolean, double, double, int)
     */
    public static boolean mouseClicked(AddonPageContext context, Direction face, Current current, Sink sink,
            boolean occupied, double mouseX, double mouseY, int button, FilterOpener filter) {
        if (button == 1) {
            TransferPickerState.close();
            return true;
        }

        var placed = AddonPickerPanel.place(context);
        if (!occupied) {
            for (int index = 0; index < MODES.size(); index++) {
                if (!isOverPlate(placed, index, mouseX, mouseY)) continue;

                var mode = MODES.get(index);
                // the plate of the mode this face already has is disabled: clicking it again does nothing, so
                // the page neither closes nor repeats a mode the server already has
                if (mode == current.mode()) {
                    OritechAddonsOne.LOGGER.debug("[transfer] plate {} ignored: that is what face {} already has",
                            mode, face);
                    return true;
                }

                // picking a direction keeps the automation switch of the face as it is
                var automation = current.automation();
                OritechAddonsOne.LOGGER.debug("[transfer] plate {} hit for face {} (automation {}) - sending",
                        mode, face, automation);
                sink.send(face, mode, automation);
                return true;
            }

            if (isOverAutomation(placed, mouseX, mouseY)) {
                OritechAddonsOne.LOGGER.debug("[transfer] automation row hit for face {} (mode {}, at {}/{})",
                        face, current.mode(), (int) mouseX, (int) mouseY);
                if (current.mode() == TransferMode.NONE) return true;

                sink.send(face, current.mode(), !current.automation());
                return true;
            }

            // The 过滤 button: it opens a page of its own, so the click is taken and the modal is left open -
            // the server replaces this screen with the filter page, and a click that misses it closes the modal
            // like any other click outside a control.
            if (isOverFilter(placed, mouseX, mouseY)) {
                OritechAddonsOne.LOGGER.debug("[transfer] filter row hit for face {} (mode {})",
                        face, current.mode());
                filter.open(face);
                return true;
            }
        }

        // anything else - the panel's own background, the prompt, the icon, or the page outside the modal -
        // closes it, like a click outside a modal
        TransferPickerState.close();
        return true;
    }

    /**
     * What the face the modal configures does right now, as the page that opened it reports it: the mode, the
     * automation flag, and where the change is written to.
     */
    public interface Current {
        TransferMode mode();

        boolean automation();
    }

    /** Where a change the player makes in the modal is sent. */
    public interface Sink {
        void send(Direction face, TransferMode mode, boolean automation);
    }

    /**
     * Where the 过滤 button of the modal sends its click, i.e. how a page asks the server to open the filter
     * page of one face.
     * <p>
     * It is its own single-method interface and not a second method on {@link Sink} so that every {@code Sink}
     * stays a lambda: the two pages build those as one expression each, and a second abstract method would
     * turn every one of them into an anonymous class. What each page has to add is exactly what its own model
     * needs - the cube net page sends a face, the 传输插件 page a face <b>and the cell it belongs to</b> - which
     * is the whole reason this is told apart from the mode sink.
     */
    @FunctionalInterface
    public interface FilterOpener {
        void open(Direction face);
    }

    /**
     * The {@link FilterOpener} of a page that keys its settings by direction alone - the cube net page, whose
     * model is one block's six faces. The face is the whole address there, and the cell of the machine's
     * controller is the block's own, which is exactly what the two models have in common.
     */
    public static FilterOpener opener(BlockPos pos) {
        return face -> PacketDistributor.sendToServer(new FilterNetworking.OpenFilter(
                new FilterNetworking.Target(pos, FaceFilterMenu.MODEL_FACE, face, Vec3i.ZERO)));
    }

    /**
     * The {@link Current} of a page that keys its settings by direction and has them only in the menu - the cube net
     * page, whose model is one block's six faces.
     */
    public static Current current(ExtensionAddonMenu menu, Direction face) {
        return new Current() {
            @Override
            public TransferMode mode() {
                return menu.transferMode(face);
            }

            @Override
            public boolean automation() {
                return menu.transferAutomation(face);
            }
        };
    }

    /**
     * The {@link Sink} of a page that keys its settings by direction - the cube net page. The direction and the
     * automation flag travel as the one packed value the block entity and the menu use for a face.
     */
    public static Sink sink(BlockPos pos) {
        return (face, mode, automation) -> PacketDistributor.sendToServer(new TransferNetworking.SetTransferMode(
                pos, ProxyNetworking.faceIndex(face), TransferFaceModes.pack(mode, automation)));
    }

    /** The panel itself: Oritech's nine patch inside a darker frame, as on the cube net page. */
    private static void drawPanel(GuiGraphics graphics) {
        new SurfaceWidget(-PANEL_FRAME, -PANEL_FRAME, AddonPickerPanel.WIDTH + 2 * PANEL_FRAME,
                AddonPickerPanel.HEIGHT + 2 * PANEL_FRAME, OritechSurface.PANEL_DARK).render(graphics, 0, 0, 0f);
        new SurfaceWidget(0, 0, AddonPickerPanel.WIDTH, AddonPickerPanel.HEIGHT, OritechSurface.PANEL)
                .render(graphics, 0, 0, 0f);
    }

    /** The three mode plates, Oritech's own button surfaces, with the face's current mode sunken in. */
    private static void drawPlates(GuiGraphics graphics, Font font, Direction face, Current current,
            AddonPickerPanel.Placed placed, boolean open, double mouseX, double mouseY) {
        var mode = current.mode();

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
    private static void drawAutomation(GuiGraphics graphics, Font font, Current current,
            AddonPickerPanel.Placed placed, boolean open, double mouseX, double mouseY) {
        var enabled = open && current.mode() != TransferMode.NONE;
        var on = enabled && current.automation();
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
    private static void drawFilter(GuiGraphics graphics, Font font, Current current,
            AddonPickerPanel.Placed placed, boolean open, double mouseX, double mouseY) {
        var enabled = open && current.mode() != TransferMode.NONE;
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

    /** True while the given panel relative mouse position is on the 过滤 button. */
    private static boolean isOverFilter(AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        double x = placed.innerX() + (AddonPickerPanel.WIDTH - FILTER_WIDTH) / 2;
        double y = placed.innerY() + FILTER_Y;
        return mouseX >= x && mouseX < x + FILTER_WIDTH && mouseY >= y && mouseY < y + FILTER_HEIGHT;
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

    /**
     * True while the given panel relative mouse position is on the plate with the given index. The whole plate is
     * the hit area, so a click anywhere on it sets that mode.
     */
    private static boolean isOverPlate(AddonPickerPanel.Placed placed, int index, double mouseX, double mouseY) {
        double x = placed.innerX() + plateX(index);
        double y = placed.innerY() + BUTTON_Y;
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    /** Left edge of the automation checkbox, centring the box and its label in the configuration page. */
    private static int automationBoxX(Font font, String label) {
        int row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);
        return (AddonPickerPanel.WIDTH - row) / 2;
    }

    /** True while the given panel relative mouse position is on the automation row, label included. */
    private static boolean isOverAutomation(AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var label = Component.translatable(AUTOMATION_KEY).getString();
        var row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);

        double x = placed.innerX() + automationBoxX(font, label);
        double y = placed.innerY() + AUTOMATION_Y;
        return mouseX >= x && mouseX < x + row && mouseY >= y && mouseY < y + AUTOMATION_BOX;
    }

    /**
     * The mode a face has right now, including what the open page set a moment ago - the pending value first, so
     * a plate turns dark in the same frame as its click instead of a tick later.
     */
    public static TransferMode modeOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.mode() : menu.transferMode(face);
    }

    /** True while a face moves its items by itself, including what the open page set a moment ago. */
    public static boolean automationOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.automation() : menu.transferAutomation(face);
    }

    /** What the open configuration page of this face set a moment ago, or {@code null} while it is closed. */
    @Nullable
    private static TransferPickerState.Pending pending(ExtensionAddonMenu menu, Direction face) {
        var cell = TransferPickerState.openCell();
        if (cell == null) return null;

        return TransferPickerState.isOpen(menu.position(), cell, face)
                ? TransferPickerState.pending()
                : null;
    }

    /**
     * Clears what a face does, without opening the modal: the page's right click on a configured face. It is the
     * same packet a plate sends, with the "not set" mode and automation off, and it drops the pending value so
     * the face does not read as configured until the server has answered.
     */
    public static void clear(BlockPos pos, Direction face) {
        PacketDistributor.sendToServer(new TransferNetworking.SetTransferMode(
                pos, ProxyNetworking.faceIndex(face), TransferFaceModes.pack(TransferMode.NONE, false)));
    }
}

