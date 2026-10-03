package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.network.PacketDistributor;

import rearth.oritech.api.screen.OritechSurface;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferPreviewAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * The page of 传输插件: the machine this plugin serves as a rotatable 3D model, with the faces of that machine
 * configurable on the model itself.
 * <p>
 * What the page <b>does</b> is exactly what the Extension Transfer page does - one {@link TransferMode} plus the
 * automation flag per face, the same occupied-face refusal, the same packet to the server, the same container data
 * back - and the two pages even agree on the look of a configured face (see {@link TransferFaceStyle}). What differs
 * is how a face is chosen: instead of the host unfolded into a cube net, this page renders the machine the plugin
 * works on with {@link FacePreviewWidget} and picks the face the player clicks on the model.
 * <p>
 * <b>The model lives in absolute screen space.</b> This page draws in panel space while the widget is rendered at the
 * pixel position it was given, which is what its own drawing and its picking assume; every coordinate the page hands
 * to the widget is therefore converted once ({@link AddonPageContext#screenX(int)} /
 * {@link AddonPageContext#screenY(int)}) and never mixed with the panel relative coordinates the page uses for its own
 * controls. The same rule applies to the model's interaction: this screen host is an
 * {@code AbstractContainerScreen}, whose widgets know nothing about our pages, so the page implements the drag
 * between {@link #mouseDragged} and {@link AddonPage#mouseClicked}.
 * <p>
 * <b>A click maps to a mode like this:</b> a left click on a face of the model selects it, and the three mode plates
 * and the automation switch below the model then apply to the selected face. Picking a plate sends the mode of that
 * face, picking the switch toggles its automation, and a right click on the face clears it again. A face that is
 * <b>occupied</b> - the machine sits on it, or another plugin of this mod stands on it - is named in gold in the face
 * list and refused, both here and on the server.
 * <p>
 * <b>The page is the same one in both screens.</b> It is the only page of 传输插件 while the plugin is placed in the
 * world, and it is one of the pages of an Extension Addon while the plugin is stored in its slots. Which block entity
 * it configures never depends on that: everything it reads (the modes, the automation flags, the occupied faces) and
 * everything it writes comes from the menu it was handed (see {@link ExtensionAddonMenu#transferPreviewMachinePos()}),
 * and that menu is the block the screen was opened for - the placed plugin, or the addon. The stored case simply has
 * no plugin block to draw into the model, because that plugin is not in the world.
 */
public final class TransferPreviewAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "transfer_preview";

    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;
    /** Instruction line above the model, telling the player what a click does. */
    private static final String HINT_KEY = "gui.oritechaddonsone.preview.hint";
    private static final String AUTOMATION_KEY = "gui.oritechaddonsone.transfer.automation";
    private static final String OCCUPIED_KEY = "gui.oritechaddonsone.transfer.occupied";
    private static final String NO_MACHINE_KEY = "gui.oritechaddonsone.preview.no_machine";
    /** Colour of the green tick and of the "this face does something" state, as on the other pages. */
    private static final int GOOD = 0xFF2ECC71;

    /** Icon of the tab: the orange arrow ({@code oritechaddonsone:textures/gui/transfer_preview_tab.png}, 16x16). */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/gui/transfer_preview_tab.png");

    /** Faces of the machine, i.e. the maximum of the counter - there is no per-plugin limit. */
    private static final int MAX_FACES = Direction.values().length;

    // ------------------------------------------------------------------ geometry

    /**
     * The page's rows, in panel space, top to bottom. The panel's own title label is drawn by the screen in the top
     * band (Y 6, see {@code ExtensionAddonScreen#extractLabels}) and the counter shares that band on the right
     * ({@link ExtensionAddonLayout#counterY()}), so the page's first own row starts below both of them: the
     * instruction line, then the model, then the mode plates, the automation switch and the face list. Only one row
     * exists per line of content - the three strings used to share the title's band, which drew them over each other.
     */
    private static final int HINT_Y = 18;

    /** Width and height of the 3D model's own panel, centred in the page body. */
    private static final int PREVIEW_WIDTH = 140;
    private static final int PREVIEW_HEIGHT = 96;
    /** Top edge of that panel, in panel space: below the panel's title and the instruction line. */
    private static final int PREVIEW_Y = HINT_Y + 10;

    /** The three mode plates and the automation row below the model. */
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 14;
    private static final int BUTTON_Y = PREVIEW_Y + PREVIEW_HEIGHT + 6;
    private static final int AUTOMATION_BOX = 10;
    private static final int AUTOMATION_GAP = 4;
    private static final int AUTOMATION_Y = BUTTON_Y + BUTTON_HEIGHT + 5;

    /** One cell of the face list: a colour marker, the face's name and what it does. */
    private static final int LEGEND_CELL_WIDTH = 62;
    private static final int LEGEND_CELL_HEIGHT = 20;
    private static final int LEGEND_COLUMNS = 3;
    /** Gap between the automation row and the face list below it. */
    private static final int LEGEND_GAP = 4;

    /** The modes the plates offer, in the shared order - the same three the cube net page offers. */
    private static final List<TransferMode> MODES = TransferFaceStyle.MODES;

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
    public List<Component> tooltip() {
        return List.of(label(), Component.translatable(LABEL_KEY + ".tooltip"));
    }

    /**
     * The panel ends below the face list, which is the page's own content: the player inventory sits below it either
     * way, and this page hides those slots while it is shown (see {@code ExtensionAddonScreen#syncVisiblePage}).
     * <p>
     * The page's own content ends at {@code legendY() + 2 * LEGEND_CELL_HEIGHT} and the panel grows to it only while
     * that is taller than the player inventory band the menu reserves: the rows above are laid out to stay inside
     * {@link ExtensionAddonLayout#imageHeight()} for every layout, so the panel this page draws can never reach past
     * the drawer the screen paints (see {@link #render}).
     */
    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        return layout.pageHeight(legendY() + 2 * LEGEND_CELL_HEIGHT);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * One row per line of content, always inside the panel: the title label and the counter own the top band (the
     * screen draws the title, {@link #drawCounter} the counter), and everything this page draws itself starts one row
     * below them with {@link #drawHint}. The model panel, the plates, the automation switch and the face list follow
     * in that order (see the geometry constants).
     */
    @Override
    public void render(AddonPageContext context, GuiGraphics graphics, float partialTick,
            double mouseX, double mouseY) {
        var menu = context.menu();

        drawCounter(context, graphics, menu.transferFaces(), MAX_FACES);
        drawHint(context, graphics);

        var preview = currentPreview(context);
        drawPreviewPanel(context, graphics);
        if (preview == null) {
            drawNoMachine(context, graphics);
            return;
        }

        // the model is drawn at the absolute pixel position it was built for, and it is asked with the same absolute
        // mouse position for its own hover state
        var widget = preview.widget();
        widget.withRotation(preview.pitch(), preview.yaw());
        widget.tick();
        widget.render(graphics, screenX(context, mouseX), screenY(context, mouseY), partialTick);

        drawLegend(context, graphics, menu, preview);
        drawPlates(context, graphics, menu, preview, mouseX, mouseY);
        drawAutomation(context, graphics, menu, preview, mouseX, mouseY);
    }

    /**
     * The six faces of the machine and what each of them does, in the shared mode colours: this is the page's answer
     * to the cube net the other transfer page draws, and it is what makes the 3D model's state readable without a
     * per-face projection (a face of a rotated 3D model is not a rectangle on screen, and drawing one would need the
     * model's own camera).
     * <p>
     * A face the plugin refuses is named as occupied instead of carrying a mode, and the selected face's line is drawn
     * bright - so the plates below always say which face they would change.
     */
    private void drawLegend(AddonPageContext context, GuiGraphics graphics, ExtensionAddonMenu menu,
            TransferPreviewState.Preview preview) {
        var font = Minecraft.getInstance().font;
        int startX = (context.panelWidth() - LEGEND_COLUMNS * LEGEND_CELL_WIDTH) / 2;

        for (int index = 0; index < MAX_FACES; index++) {
            var face = Direction.values()[index];
            int x = context.screenX(startX + (index % LEGEND_COLUMNS) * LEGEND_CELL_WIDTH);
            int y = context.screenY(legendY() + (index / LEGEND_COLUMNS) * LEGEND_CELL_HEIGHT);

            var occupied = isOccupied(menu, face);
            graphics.fill(x, y + 1, x + 3, y + 8, occupied ? TransferFaceStyle.GOLD : markerColor(modeOf(menu, face)));

            graphics.drawString(font, sideName(face), x + 6, y + 1, AddonPanelStyle.PANEL_TEXT_DIM, false);

            var mode = occupied ? Component.translatable(OCCUPIED_KEY)
                    : Component.translatable(TransferFaceStyle.modeKey(modeOf(menu, face)));
            graphics.drawString(font, mode, x + 6, y + 10,
                    face == preview.selected() ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
        }
    }

    /** The three mode plates, applied to the selected face; the plate of the mode it has right now is sunken. */
    private void drawPlates(AddonPageContext context, GuiGraphics graphics, ExtensionAddonMenu menu,
            TransferPreviewState.Preview preview, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var face = preview.selected();
        var current = face == null ? TransferMode.NONE : modeOf(menu, face);
        // a face that cannot be configured has no modes to offer, so the plates are dimmed as a whole
        var open = face != null && !isOccupied(menu, face);

        for (int index = 0; index < MODES.size(); index++) {
            var mode = MODES.get(index);
            int x = context.screenX(plateX(context, index));
            int y = context.screenY(BUTTON_Y);

            var surface = !open || mode == current ? OritechSurface.PANEL_DARK
                    : isOverPlate(context, index, mouseX, mouseY) ? OritechSurface.PANEL_HOVER
                            : OritechSurface.PANEL;
            surface.render(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);

            var text = Component.translatable(TransferFaceStyle.modeKey(mode)).getString();
            graphics.drawString(font, text, x + (BUTTON_WIDTH - font.width(text)) / 2, y + (BUTTON_HEIGHT - 8) / 2,
                    open ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
        }
    }

    /**
     * The automation row: Oritech's dark checkbox and its label, centred below the plates. With automation on, the
     * selected face moves the machine's items by itself - towards the container on that side for "output", from it for
     * "input", both for "input + output" (see {@link TransferPreviewAddonBlockEntity#serverTickTransfer()}).
     * <p>
     * Dimmed and refusing clicks while no configurable face carries a mode: a face that transfers nothing has nothing
     * to move on its own, so the switch only becomes meaningful together with a face and a mode.
     */
    private void drawAutomation(AddonPageContext context, GuiGraphics graphics, ExtensionAddonMenu menu,
            TransferPreviewState.Preview preview, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var face = preview.selected();
        var open = face != null && !isOccupied(menu, face) && modeOf(menu, face) != TransferMode.NONE;
        var on = open && automationOf(menu, face);
        var label = Component.translatable(AUTOMATION_KEY).getString();

        int boxX = context.screenX(automationBoxX(context, font, label));
        int y = context.screenY(AUTOMATION_Y);

        var surface = open && isOverAutomation(context, font, label, mouseX, mouseY)
                ? OritechSurface.PANEL_DARK_HOVER
                : OritechSurface.PANEL_DARK;
        surface.render(graphics, boxX, y, AUTOMATION_BOX, AUTOMATION_BOX);
        if (on) {
            graphics.fill(boxX + 2, y + 2, boxX + AUTOMATION_BOX - 2, y + AUTOMATION_BOX - 2, GOOD);
        }

        graphics.drawString(font, label, boxX + AUTOMATION_BOX + AUTOMATION_GAP, y + 1,
                open ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /**
     * The instruction line, centred in the panel one row below the panel's own title band: it names what the model
     * and the plates below it are for.
     * <p>
     * It is deliberately not drawn in that band: the band already carries the block's title on the left (drawn by
     * the screen) and the counter on the right ({@link #drawCounter}), and three strings in one eight pixel row is
     * what used to draw them on top of each other.
     */
    private void drawHint(AddonPageContext context, GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(HINT_KEY).getString();
        graphics.drawString(font, text, context.screenX((context.panelWidth() - font.width(text)) / 2),
                context.screenY(HINT_Y), AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /**
     * The sunken panel the model lives in, drawn behind it: the same dark inset Oritech uses for a field that is
     * not a button, so the model reads as the page's content rather than as something floating on the panel.
     * <p>
     * It is drawn for the "no machine" state as well, which is the whole point: the message then reads as this
     * page's own empty content instead of as a line of text in an otherwise empty panel.
     */
    private void drawPreviewPanel(AddonPageContext context, GuiGraphics graphics) {
        OritechSurface.PANEL_INSET.render(graphics, context.screenX(previewX(context)), context.screenY(PREVIEW_Y),
                PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    /**
     * What the model's panel shows while there is no model to draw: the machine a plugin is meant to serve is not
     * visible to this client.
     * <p>
     * It is reached in three cases, and they are deliberately not told apart, because the player's next step is the
     * same in all of them: the plugin serves no machine at all (it stands on an extender no machine ever claimed),
     * the machine it serves is in a chunk this client has not loaded, or the plugin has just been picked up. The line
     * is centred in the model's panel, so the panel - which is drawn either way - reads as this page's empty content.
     */
    private void drawNoMachine(AddonPageContext context, GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(NO_MACHINE_KEY).getString();
        graphics.drawString(font, text, context.screenX((context.panelWidth() - font.width(text)) / 2),
                context.screenY(PREVIEW_Y + PREVIEW_HEIGHT / 2), AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /** The "Configurable: x/6" counter in the panel's top right corner, exactly as the cube net page draws it. */
    private void drawCounter(AddonPageContext context, GuiGraphics graphics, int configured, int maximum) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("gui.oritechaddonsone.transfer.counter", configured, maximum).getString();
        var layout = context.layout();

        graphics.drawString(font, text, context.screenX(layout.counterRight() - font.width(text)),
                context.screenY(layout.counterY()), AddonPanelStyle.PANEL_TEXT, false);
    }

    // ------------------------------------------------------------------ clicks and drags

    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        var menu = context.menu();
        var preview = currentPreview(context);
        if (preview == null) return false;

        // a click on the model picks a face; the widget remembers the pick, the page remembers the selection
        var picked = preview.widget().pickFace(screenX(context, mouseX), screenY(context, mouseY));
        if (picked != null) {
            preview.select(picked);

            // a right click on a configured face clears what that face does, exactly like on the cube net page
            if (button == 1 && !isOccupied(menu, picked) && modeOf(menu, picked) != TransferMode.NONE) {
                TransferPickerState.close();
                send(menu.position(), picked, TransferMode.NONE, false);
            }
            return true;
        }

        var face = preview.selected();
        if (face == null || isOccupied(menu, face)) return false;

        if (button == 0) {
            for (int index = 0; index < MODES.size(); index++) {
                if (!isOverPlate(context, index, mouseX, mouseY)) continue;

                var mode = MODES.get(index);
                // the plate of the mode this face already has is disabled: clicking it again sends nothing, so the
                // page never repeats a mode the server already has
                if (mode == currentMode(menu, face)) return true;

                // picking a direction keeps the automation switch of the face as it is
                var automation = automationOf(menu, face);
                TransferPickerState.select(mode, automation);
                send(menu.position(), face, mode, automation);
                return true;
            }

            // the automation switch only toggles, the direction is kept, so a face can be switched between "offers its
            // inventory to pipes" and "moves the items by itself" without picking the mode again
            if (isOverAutomation(context, Minecraft.getInstance().font,
                    Component.translatable(AUTOMATION_KEY).getString(), mouseX, mouseY)) {
                var mode = currentMode(menu, face);
                if (mode == TransferMode.NONE) return true;

                var automation = !automationOf(menu, face);
                TransferPickerState.select(mode, automation);
                send(menu.position(), face, mode, automation);
                return true;
            }
        }

        // anything else - a click on the face list, on the panel border - starts no drag of this page and reaches the
        // slot logic unchanged
        return false;
    }

    /**
     * Rotates the model while the player drags over it. Only a drag that stays over the model is claimed, so dragging
     * an item across a slot of this GUI still reaches vanilla's slot logic, and the drag starts wherever the button
     * went down - the page does not have to remember its own press, because it only ever turns the model while the
     * mouse is on it.
     */
    @Override
    public boolean mouseDragged(AddonPageContext context, double mouseX, double mouseY, double dragX, double dragY,
            int button) {
        if (button != 0) return false;

        var preview = currentPreview(context);
        if (preview == null) return false;
        if (!preview.widget().isOverModel(screenX(context, mouseX), screenY(context, mouseY))) return false;

        preview.drag(dragX, dragY);
        return true;
    }

    /**
     * Nothing has to end here: the rotation ends with the drag itself and the selection stays, so the plates keep
     * naming the face the player just rotated into view. The hook is kept as the counterpart of {@link #mouseDragged}
     * and as the place a gesture that does need an end would use.
     */
    @Override
    public void mouseReleased(AddonPageContext context, double mouseX, double mouseY, int button) {
        // no state of this page ends on a release
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        var menu = context.menu();
        var preview = currentPreview(context);
        if (preview == null) return List.of();

        var hovered = preview.widget().hoveredFace();
        if (hovered == null) return List.of();

        // the occupied face explains why it cannot be configured instead of naming a mode it can never have
        if (isOccupied(menu, hovered)) return List.of(Component.translatable(OCCUPIED_KEY));

        return List.of(Component.translatable(sideKey(hovered)),
                Component.translatable(TransferFaceStyle.modeKey(modeOf(menu, hovered))));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The machine the page renders, as the menu it belongs to reports it: the machine a placed plugin serves, or -
     * while the page is shown inside an Extension Addon that stores a preview plugin - the machine that addon works
     * on. The page therefore never has to know which of the two screens it is drawn in; it configures whatever the
     * menu addresses.
     * <p>
     * The menu answers with the machine the server resolved when the screen was opened and sent along with it
     * ({@link ExtensionAddonMenu#transferPreviewMachinePos()}). It has to come from there: the machine of a placed
     * plugin is the one behind its host extender while the plugin's own controller position names the machine that
     * claimed it, neither of which is synced to this side, so a lookup here would answer "no machine" even for a
     * plugin and a machine that are both loaded.
     */
    @Nullable
    private static BlockPos machinePos(ExtensionAddonMenu menu) {
        return menu.transferPreviewMachinePos();
    }

    /** The preview currently built for this menu, or {@code null} while there is none to draw. */
    @Nullable
    private static TransferPreviewState.Preview currentPreview(AddonPageContext context) {
        return TransferPreviewState.preview(context.menu().position(), machinePos(context.menu()),
                pluginBlockPos(context.menu()), context.screenX(previewX(context)), context.screenY(PREVIEW_Y),
                PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    /**
     * Position of the preview plugin whose block belongs into the model, i.e. the menu's own position while the menu
     * belongs to a <b>placed</b> plugin, or {@code null} while it belongs to an Extension Addon that merely stores
     * one. Only the placed plugin stands on a face of the machine, so only it says anything about which face of the
     * machine is taken - a stored plugin is not in the world and its slot is not a face of the machine.
     */
    @Nullable
    private static BlockPos pluginBlockPos(ExtensionAddonMenu menu) {
        return menu.blockEntity() instanceof TransferPreviewAddonBlockEntity ? menu.position() : null;
    }

    /** X of a panel relative coordinate, converted to the absolute space the model lives in. */
    private static int screenX(AddonPageContext context, double panelX) {
        return (int) Math.round(panelX) + context.left();
    }

    /** Y of a panel relative coordinate, converted to the absolute space the model lives in. */
    private static int screenY(AddonPageContext context, double panelY) {
        return (int) Math.round(panelY) + context.top();
    }

    /** Left edge of the model's panel in panel space: centred in the page body. */
    private static int previewX(AddonPageContext context) {
        return (context.panelWidth() - PREVIEW_WIDTH) / 2;
    }

    /** Top edge of the face list, in panel space: below the automation row. */
    private static int legendY() {
        return AUTOMATION_Y + AUTOMATION_BOX + LEGEND_GAP;
    }

    /** Left edge of the mode plate with the given index, in panel space; all three centred in the body. */
    private static int plateX(AddonPageContext context, int index) {
        int total = MODES.size() * BUTTON_WIDTH + (MODES.size() - 1) * BUTTON_GAP;
        return (context.panelWidth() - total) / 2 + index * (BUTTON_WIDTH + BUTTON_GAP);
    }

    /** True while the given panel relative mouse position is on the plate with the given index. */
    private static boolean isOverPlate(AddonPageContext context, int index, double mouseX, double mouseY) {
        double x = plateX(context, index);
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= BUTTON_Y && mouseY < BUTTON_Y + BUTTON_HEIGHT;
    }

    /** Left edge of the automation checkbox, centring the box and its label under the plates. */
    private static int automationBoxX(AddonPageContext context, Font font, String label) {
        int row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);
        return (context.panelWidth() - row) / 2;
    }

    /** True while the given panel relative mouse position is on the automation row. */
    private static boolean isOverAutomation(AddonPageContext context, Font font, String label, double mouseX,
            double mouseY) {
        int row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);
        double x = automationBoxX(context, font, label);
        return mouseX >= x && mouseX < x + row && mouseY >= AUTOMATION_Y && mouseY < AUTOMATION_Y + AUTOMATION_BOX;
    }

    /**
     * True while the given face of the machine is one the plugin refuses to configure, i.e. while it is in the mask
     * the block entity published: the face the plugin itself occupies, and every face another plugin of this mod
     * stands on.
     */
    private static boolean isOccupied(ExtensionAddonMenu menu, Direction face) {
        return (menu.attachedTransferFaces() & 1 << face.ordinal()) != 0;
    }

    /** The mode a face has right now, including what a click set a moment ago. */
    private static TransferMode modeOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.mode() : menu.transferMode(face);
    }

    /** True while a face moves its items by itself, including what a click set a moment ago. */
    private static boolean automationOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.automation() : menu.transferAutomation(face);
    }

    /**
     * What the page set a moment ago for this face, or {@code null} while the menu's own value is authoritative.
     * Reading the pending value keeps a plate and the switch in step with the click that set them: the value travels
     * to the server, which writes it into the block entity, and comes back through the container data - one tick
     * later, which would otherwise read as a flicker.
     */
    @Nullable
    private static TransferPickerState.Pending pending(ExtensionAddonMenu menu, Direction face) {
        return TransferPickerState.isOpen(menu.position(), face) ? TransferPickerState.pending() : null;
    }

    /** The mode the server already knows for this face, i.e. without the page's pending value. */
    private static TransferMode currentMode(ExtensionAddonMenu menu, Direction face) {
        return menu.transferMode(face);
    }

    /** Colour of the face list's marker of a mode. */
    private static int markerColor(TransferMode mode) {
        return switch (mode) {
            case INPUT, BOTH -> TransferFaceStyle.INPUT_EDGE;
            case OUTPUT -> TransferFaceStyle.OUTPUT_EDGE;
            case NONE -> AddonPanelStyle.SLOT_DARK;
        };
    }

    /** Language key of a face name, e.g. {@code gui.oritechaddonsone.transfer.side.north}. */
    private static String sideKey(Direction face) {
        return "gui.oritechaddonsone.transfer.side." + face.name().toLowerCase(Locale.ROOT);
    }

    /** Translated name of a face, for the face list. */
    private static String sideName(Direction face) {
        return Component.translatable(sideKey(face)).getString();
    }

    /**
     * Tells the server what a face should do. The page is client only, so this is the one place it talks back; the
     * direction and the automation flag travel as the one packed value the block entity and the menu use for a face.
     * <p>
     * The message is the transfer page's own packet: both plugins have the same per-face settings, and the server
     * applies them on whichever plugin block the position names.
     */
    private static void send(BlockPos pos, Direction face, TransferMode mode, boolean automation) {
        PacketDistributor.sendToServer(new TransferNetworking.SetTransferMode(
                pos, ProxyNetworking.faceIndex(face), TransferFaceModes.pack(mode, automation)));
    }
}
