package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.OritechSurface;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.CellFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * The page of 传输插件: the machine this plugin serves as a rotatable 3D model, and the configuration of one of
 * its <b>cell-faces</b> in the modal page a click on that face opens.
 * <p>
 * What the page <b>does</b> is exactly what the Extension Transfer page does - one {@link TransferMode} plus the
 * automation flag per face, the same modal, the same look for a configured face (see {@link TransferFaceStyle} and
 * {@link TransferFaceModal}) - and what differs is <b>what a "face" is</b>, and that this page refuses none of them:
 * the cell-face the plugin block itself stands in while it hangs on the machine is configured, shown and saved like
 * every other cell-face of the structure. The cube net
 * page unfolds one block, so a setting there is one of the six world directions of the host. This page draws the whole
 * assembled structure, and its settings are per <b>cell and direction</b> (see {@link CellFaceModes}): the north face
 * of the top-left cell and the north face of the top-right cell are two faces and two settings. This branch has no
 * picture in picture GUI rendering, so the model is drawn straight into the page's own pose stack by
 * {@link FacePreviewWidget} - which is also what lets the face under the mouse be marked with a translucent white quad
 * in the model's own pose there, and lets the page know exactly which cell that face belongs to.
 * <p>
 * <b>The map lives on the server and is sent whole.</b> The page draws every configured cell-face, so it needs all of
 * them: that is {@code TransferNetworking.FaceModes}, one int per configured cell-face, sent when the menu opens and
 * after every accepted change, and held here by {@link TransferFaceState}. A fixed set of menu data slots - what the
 * cube net page uses - cannot carry a map of unknown size, and a structure's surface has no natural maximum.
 * <p>
 * <b>The page itself shows only the model.</b> The counter, the instruction line and the 3D model with its hover
 * highlight are the whole page; everything that configures a cell-face - the three mode plates and the automation
 * switch - lives in the modal page a left click on a face opens, and a right click on a configured face clears it
 * without opening anything. That is the cube net page's interaction, applied to a model instead of a net: the
 * plates are only ever in front of the player while a face is really being configured, so the model - which is
 * what this page is for - stays visible the rest of the time. The counter reports how many cell-faces are configured
 * and <b>no maximum</b>, because there is none (see {@link #drawCounter}).
 * <p>
 * <b>The model lives in absolute screen space.</b> This page draws in panel space while the widget is rendered at the
 * pixel position it was given, which is what its own drawing and its picking assume; every coordinate the page hands
 * to the widget is therefore converted once ({@link AddonPageContext#screenX(int)} /
 * {@link AddonPageContext#screenY(int)}) and never mixed with the panel relative coordinates the page uses for its own
 * controls. The same rule applies to the model's interaction: this screen host is an
 * {@code AbstractContainerScreen}, whose widgets know nothing about our pages, so the page implements the drag
 * between {@link #mouseDragged} and {@link AddonPage#mouseClicked}, and the wheel in {@link #mouseScrolled}, which
 * zooms the model over the model's own panel (see {@link TransferAddonState.Preview#zoomBy}).
 * <p>
 * <b>The page is the same one in both screens.</b> It is the only page of 传输插件 while the plugin is placed in
 * the world, and it is one of the pages of an Extension Addon while the plugin is stored in its slots. Which
 * block entity it configures never depends on that: everything it reads (the modes, the automation flags) and
 * everything it writes comes from the menu it was handed (see
 * {@link ExtensionAddonMenu#transferMachinePos()}), and that menu is the block the screen was opened for -
 * the placed plugin, or the addon.
 */
public final class TransferAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "transfer";

    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;
    /** Instruction line below the title, telling the player what a click does. */
    private static final String HINT_KEY = "gui.oritechaddonsone.transfer.hint";
    private static final String NO_MACHINE_KEY = "gui.oritechaddonsone.transfer.no_machine";

    /** Icon of the tab: the orange arrow ({@code oritechaddonsone:textures/gui/transfer_tab.png}, 16x16). */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/gui/transfer_tab.png");

    // ------------------------------------------------------------------ geometry

    /** Width and height of the 3D model's own panel, centred in the page body. */
    private static final int PREVIEW_WIDTH = 140;
    private static final int PREVIEW_HEIGHT = 96;

    /**
     * Top edge of that panel, in panel space: the page's first row, because the panel's own title label and the
     * counter are drawn by the screen in the band above it (Y 6, see {@code ExtensionAddonScreen#renderLabels} and
     * {@link ExtensionAddonLayout#counterY()}).
     * <p>
     * It is the same value the hint used to sit at: the hint moved <b>below</b> the model (see {@link #HINT_Y}), so the
     * model took the row the hint had rather than the page growing a row.
     */
    private static final int PREVIEW_Y = 18;

    /**
     * The instruction line's baseline, in panel space: <b>under</b> the model's panel, which is where a caption
     * belongs - it explains the panel above it, and the panel is no longer pushed down by a line of text the player
     * only reads once.
     * <p>
     * Six pixels below the panel's bottom edge, so the text is separated from the panel's dark bevel by a visible gap
     * and not merely by its own line height. It is also the last row the page owns: {@link #drawnHeight} reserves
     * {@link #HINT_TEXT_HEIGHT} more for it.
     */
    private static final int HINT_Y = PREVIEW_Y + PREVIEW_HEIGHT + 6;

    /** Height of one line of the mod's font, i.e. what the instruction line occupies below the panel. */
    private static final int HINT_TEXT_HEIGHT = 9;

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

    /**
     * The tab's tooltip is its label and nothing else, which is the interface's default: the one line this page
     * used to add explained the model, and the page already carries that explanation as its own hint line under
     * the panel (see {@link #drawHint}). Repeating it on the tab only made the reader hover to learn what the
     * hint says anyway.
     */
    @Override
    public List<Component> tooltip() {
        return List.of(label());
    }

    /**
     * The panel ends below the instruction line, which is the page's last row.
     * <p>
     * The modal configuration page is the reason this is not simply {@code HINT_Y + HINT_TEXT_HEIGHT}: the modal
     * is Oritech's 176x100 panel with its 28 pixel title icon floating above it ({@link AddonPickerPanel}), and it
     * has to fit inside the drawn panel for <b>every</b> layout - including the shortest one, a single plugin row.
     * So the page asks for enough height to hold the model, the instruction line <em>and</em> the modal, and lets
     * {@link ExtensionAddonLayout#pageHeight(int)} keep the player inventory band as the floor. Without this the
     * one-row panel would be shorter than the modal and the lower third of the plates would be cut off.
     * <p>
     * The modal is drawn over the page, so it covers the instruction line while a face is being configured - which is
     * why the line can sit below the model without competing with the plates.
     */
    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        int contentBottom = HINT_Y + HINT_TEXT_HEIGHT;
        // the two pixels AddonPickerPanel keeps above its placement floor, and a small margin so the modal's
        // frame is never flush with the panel's dark bottom bevel
        int modalBottom = AddonPickerPanel.ICON_SIZE + AddonPickerPanel.HEIGHT + 10;
        return layout.pageHeight(Math.max(contentBottom, modalBottom));
    }

    // ------------------------------------------------------------------ drawing

    /**
     * One row per line of content, always inside the panel: the title label and the counter own the top band (the
     * screen draws the title, {@link #drawCounter} the counter), then {@link #drawHint} and the model follow. The
     * modal configuration page of an open face is drawn <b>over</b> all of it, so the page behind it never shows
     * through a control the player is not using.
     */
    @Override
    public void render(AddonPageContext context, GuiGraphics graphics, float partialTick,
            double mouseX, double mouseY) {
        drawHint(context, graphics);

        var preview = currentPreview(context);
        drawPreviewPanel(context, graphics);
        if (preview == null) {
            // no model, so nothing can be configured either; the counter is not drawn at all rather than as a zero,
            // because there is no machine whose cell-faces it could be counting
            drawNoMachine(context, graphics);
            return;
        }

        // the widget is drawn at the absolute pixel position it was built for, and it is asked with the same
        // absolute mouse position for its own hover state. The markings it draws are what the page reads back for its
        // counter and its modal - the server's own map, plus the pending value of a click that has just been sent -
        // so a mode the player sets shows on the model in the same frame
        var widget = preview.widget();
        widget.withRotation(preview.pitch(), preview.yaw());
        widget.setZoom(preview.zoom());
        widget.setFaceOverlays(configuredModes(context));
        widget.tick();
        widget.render(graphics, screenX(context, mouseX), screenY(context, mouseY), partialTick);

        // the counter counts what the player configured, over as many cell-faces as the structure has: there is no
        // maximum and the page shows none (see #drawCounter)
        drawCounter(context, graphics, widget);

        var openFace = TransferPickerState.openFace();
        if (openFace != null) {
            var openCell = TransferPickerState.openCell();
            preview.select(openFace);
            // every cell-face of this model is configurable - the plugin's own host face included - so the modal is
            // never opened in its "occupied" form here; only the cube net page still uses that form
            TransferFaceModal.render(context, graphics, openFace, current(context, openCell, openFace),
                    false, mouseX, mouseY);
        }
    }

    /**
     * The instruction line, centred in the panel <b>under</b> the model's own panel ({@link #HINT_Y}): it names what
     * the model is for and what a click on it does - the plates themselves are only shown by the modal that click
     * opens.
     * <p>
     * A caption belongs under the thing it captions: above the model it pushed the model down and read as a heading
     * for the page rather than as an explanation of the panel below it. It is drawn dim ({@link AddonPanelStyle}) so it
     * stays secondary to the model, and it is drawn in both states - with and without a machine - because it explains
     * the panel either way.
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
     * It is reached in three cases, and they are deliberately not told apart, because the player's next step is
     * the same in all of them: the plugin serves no machine at all (it stands on an extender no machine ever
     * claimed), the machine it serves is in a chunk this client has not loaded, or the plugin has just been picked
     * up.
     */
    private void drawNoMachine(AddonPageContext context, GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(NO_MACHINE_KEY).getString();
        graphics.drawString(font, text, context.screenX((context.panelWidth() - font.width(text)) / 2),
                context.screenY(PREVIEW_Y + PREVIEW_HEIGHT / 2), AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /**
     * The counter in the panel's top right corner: how many cell-faces the player has configured, and <b>nothing
     * else</b>.
     * <p>
     * <b>There is no maximum, so none is shown.</b> The model this page configures has one setting per face of every
     * cell of the machine's structure, which is a number the page cannot know before the structure is assembled and
     * which Oritech's part lists do not bound - so a fraction like the cube net page's {@code x/6} would be a lie
     * twice over: there is no denominator, and the "6" would be the wrong shape for this model even if there were.
     * What the count <em>is</em> worth showing is that something is configured at all - a player who has just set a
     * face wants to see the page acknowledge it - so the line stays and the denominator is gone, which is also why the
     * count is taken from the model the frame actually drew: it is what the player can see.
     */
    private void drawCounter(AddonPageContext context, GuiGraphics graphics, FacePreviewWidget widget) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("gui.oritechaddonsone.transfer.counter",
                widget.configuredFaces()).getString();
        var layout = context.layout();

        graphics.drawString(font, text, context.screenX(layout.counterRight() - font.width(text)),
                context.screenY(layout.counterY()), AddonPanelStyle.PANEL_TEXT, false);
    }

    // ------------------------------------------------------------------ clicks and drags

    /**
     * A click on the model picks the <b>cell-face</b> under it. With no modal open, a left click opens that
     * cell-face's configuration page - on <b>every</b> cell-face of the structure, the one the plugin block itself
     * stands in included, because this page refuses none of them (see
     * {@code TransferAddonBlockEntity#setCellFaceConfig}) - and a right click clears what a configured cell-face does
     * without opening anything. While the modal is open every click belongs to it: a plate or the switch is applied
     * and the modal stays open, and anything else closes it.
     * <p>
     * <b>The click configures exactly what was picked.</b> The widget's own picking answers the cell and the face
     * ({@link FacePreviewWidget#pickFace}), and both are remembered and sent - not just the direction, which on a
     * structure names as many faces as the machine has cells.
     */
    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        var menu = context.menu();

        var openFace = TransferPickerState.openFace();
        if (openFace != null) {
            var openCell = TransferPickerState.openCell();
            // INFO on purpose: it separates "the click never reached the page" from "it reached the page and
            // missed every control", which is the difference between a hit test and a packet problem
            OritechAddonsOne.LOGGER.info("[transfer] click {} on the open modal of cell {} face {} (at {}/{})",
                    button, openCell, openFace, (int) mouseX, (int) mouseY);
            return TransferFaceModal.mouseClicked(context, openFace, current(context, openCell, openFace),
                    send(context), false, mouseX, mouseY, button);
        }

        var preview = currentPreview(context);
        if (preview == null) {
            OritechAddonsOne.LOGGER.info("[transfer] click on the page, but there is no model (machine {})",
                    machinePos(menu));
            return false;
        }

        // a click on the model picks a cell-face; the widget remembers the pick, the page remembers the selection
        var picked = preview.widget().pickFace(screenX(context, mouseX), screenY(context, mouseY));
        OritechAddonsOne.LOGGER.info("[transfer] click {} on the model picked {} of cell {} (at {}/{})",
                button, picked, preview.widget().pickedOffset(), (int) mouseX, (int) mouseY);
        if (picked == null) return false;

        var cell = preview.widget().pickedOffset();
        if (cell == null) return false;

        preview.select(picked);

        // a right click on a configured cell-face clears what it does, exactly like on the cube net page
        if (button == 1) {
            if (mode(context, cell, picked) != TransferMode.NONE) {
                TransferPickerState.close();
                clear(menu.transferPluginPos(), cell, picked);
            }
            return true;
        }

        TransferPickerState.open(menu.position(), cell, picked);
        return true;
    }

    /**
     * Rotates the model while the player drags over it. Only a drag that stays over the model is claimed, so
     * dragging an item across a slot of this GUI still reaches vanilla's slot logic, and the drag starts wherever
     * the button went down - the page does not have to remember its own press, because it only ever turns the
     * model while the mouse is on it.
     * <p>
     * A drag never reaches the modal: while a face is being configured the model is covered by it, and
     * {@link #mouseDragged} asks the model's own hit test, which the backdrop is not part of - so the rotation
     * would only start from the visible sliver of the panel around the modal. Turning the model while its
     * configuration is open is exactly what a player does to see the face from the other side, so that is
     * allowed: the modal follows the model's rotation, because the face it configures is the one the page
     * selected.
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
     * Nothing has to end here: the rotation ends with the drag itself and the modal stays open until it is
     * deliberately closed. The hook is kept as the counterpart of {@link #mouseDragged} and as the place a gesture
     * that does need an end would use.
     */
    @Override
    public void mouseReleased(AddonPageContext context, double mouseX, double mouseY, int button) {
        // no state of this page ends on a release
    }

    /**
     * Zooms the model while the wheel turns over the model's own panel, and only there.
     * <p>
     * The test is the panel's rectangle, not the model's silhouette: the panel is what reads as the model's field, so
     * a wheel event anywhere in it zooms - including on the empty corner a zoomed-out model leaves. Outside it the
     * page keeps the default and the scroll is vanilla's again, which is what leaves the wheel to the rest of the GUI.
     * <p>
     * The zoom itself is the interaction state's ({@link TransferAddonState.Preview#zoomBy}), so it carries the same
     * clamp, and the page hands the result to the widget every frame from {@link #render} together with the rotation -
     * which is what puts it into the one shared {@link PreviewTransform} the drawing, the picking and the markings
     * read.
     */
    @Override
    public boolean mouseScrolled(AddonPageContext context, double mouseX, double mouseY, double scrollX,
            double scrollY) {
        if (TransferPickerState.isOpen(context.menu().position())) return false;
        if (mouseX < previewX(context) || mouseX >= previewX(context) + PREVIEW_WIDTH) return false;
        if (mouseY < PREVIEW_Y || mouseY >= PREVIEW_Y + PREVIEW_HEIGHT) return false;

        var preview = currentPreview(context);
        if (preview == null) return false;

        preview.zoomBy(scrollY);
        return true;
    }

    // ------------------------------------------------------------------ tooltips

    /**
     * What the floating text next to the pointer says about the cell-face under it: its mode, and nothing else.
     * <p>
     * <b>The direction word is deliberately not in it.</b> The pointer is already on the face the text describes, so
     * naming that face's side (北/东/…) answered a question nobody asked and made the text longer than the answer it
     * carries - the reader had to skip past it to reach the mode. What the text is for is the one thing the model does
     * not already show: whether that face is configured, and how. The direction names are still where they belong -
     * {@link TransferFaceStyle.MODES} and the cube net page name a side when a side is the thing being chosen, and the
     * {@code gui.oritechaddonsone.extension_transfer.side.*} keys stay in the language files for that.
     */
    @Override
    public List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        var menu = context.menu();
        // the modal explains itself with its plates and its prompt, so nothing is shown over it
        if (TransferPickerState.isOpen(menu.position())) return List.of();

        var preview = currentPreview(context);
        if (preview == null) return List.of();

        var hovered = preview.widget().hoveredFace();
        var hoveredCell = preview.widget().hoveredOffset();
        if (hovered == null || hoveredCell == null) return List.of();

        return List.of(Component.translatable(TransferFaceStyle.modeKey(mode(context, hoveredCell, hovered))));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The machine the page renders, as the menu it belongs to reports it: the machine a placed plugin serves, or -
     * while the page is shown inside an Extension Addon that stores a preview plugin - the machine that addon
     * works on. The page therefore never has to know which of the two screens it is drawn in; it configures
     * whatever the menu addresses.
     * <p>
     * The menu answers with the machine the server resolved when the screen was opened and sent along with it
     * ({@link ExtensionAddonMenu#transferMachinePos()}). It has to come from there: the machine of a placed
     * plugin is the one behind its host extender while the plugin's own controller position names the machine that
     * claimed it, neither of which is synced to this side, so a lookup here would answer "no machine" even for a
     * plugin and a machine that are both loaded.
     */
    @Nullable
    private static BlockPos machinePos(ExtensionAddonMenu menu) {
        return menu.transferMachinePos();
    }

    /** The preview currently built for this menu, or {@code null} while there is none to draw. */
    @Nullable
    private static TransferAddonState.Preview currentPreview(AddonPageContext context) {
        return TransferAddonState.preview(context.menu().position(), machinePos(context.menu()),
                context.screenX(previewX(context)), context.screenY(PREVIEW_Y), PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    /**
     * What every <b>surface cell-face</b> of the machine is configured to do, as the model's markings are drawn
     * from: the server's own map (see {@link TransferFaceState}), with the pending value of a click that has just been
     * sent laid over the cell-face the modal has open.
     * <p>
     * Only the machine's <b>outer surface</b> is listed ({@link FacePreviewWidget#surfaceCells()}): a face between two
     * cells of the structure has no container outside it and cannot be configured, so it must not be marked either.
     * An entry with {@link TransferMode#NONE} means "nothing configured here", which the widget draws nothing for.
     */
    private static Map<FacePreviewWidget.CellFace, TransferMode> configuredModes(AddonPageContext context) {
        var modes = new HashMap<FacePreviewWidget.CellFace, TransferMode>();
        var preview = currentPreview(context);
        if (preview == null) return modes;

        for (var cellFace : preview.widget().surfaceCells()) {
            modes.put(cellFace, mode(context, cellFace.cell(), cellFace.face()));
        }
        return modes;
    }

    /**
     * The mode one cell-face has right now: the pending value the open modal set a moment ago first, so a plate turns
     * dark in the same frame as its click, and otherwise what the server last reported.
     */
    private static TransferMode mode(AddonPageContext context, Vec3i cell, Direction face) {
        var pending = TransferPickerState.pending();
        if (pending != null && TransferPickerState.isOpen(context.menu().position(), cell, face)) {
            return pending.mode();
        }
        // the map is keyed by the plugin, not by the menu: an addon's screen shows a plugin standing in its
        // slots, and the server sends that plugin's map under the plugin's own position
        // (see ExtensionAddonMenu#transferPluginPos)
        return TransferFaceState.modeOf(context.menu().transferPluginPos(), cell, face);
    }

    /** True while a cell-face moves its items by itself, with the open modal's pending value first. */
    private static boolean automation(AddonPageContext context, Vec3i cell, Direction face) {
        var pending = TransferPickerState.pending();
        if (pending != null && TransferPickerState.isOpen(context.menu().position(), cell, face)) {
            return pending.automation();
        }
        return TransferFaceState.automationOf(context.menu().transferPluginPos(), cell, face);
    }

    /** What the face the modal is configuring does, as the modal is told it. */
    private static TransferFaceModal.Current current(AddonPageContext context, Vec3i cell, Direction face) {
        return new TransferFaceModal.Current() {
            @Override
            public TransferMode mode() {
                return TransferAddonPage.mode(context, cell, face);
            }

            @Override
            public boolean automation() {
                return TransferAddonPage.automation(context, cell, face);
            }
        };
    }

    /**
     * Where a change the modal makes is sent: the packet that carries the <b>cell</b> as well as the face
     * ({@code TransferNetworking.SetCellFaceMode}), so the server writes the setting on the cell the player really
     * clicked. The pending value is remembered first, so the page shows the change before the server's answer arrives
     * - and the answer replaces it a round trip later.
     */
    private static TransferFaceModal.Sink send(AddonPageContext context) {
        return (face, mode, automation) -> {
            var cell = TransferPickerState.openCell();
            if (cell == null) return;

            TransferPickerState.select(mode, automation);
            // no limit and no denominator on the answer either: only the cell, the face and the packed value travel
            // the position is the plugin's, not the menu's: a page inside an addon has to address the plugin
            // standing in its slots, which is the block the server knows as the transfer plugin
            var pluginPos = context.menu().transferPluginPos();
            // INFO while the addressing of a stored plugin is being chased: it names the menu the page belongs
            // to, what the menu resolved, and every neighbour it had to choose from
            var level = Minecraft.getInstance().level;
            var at = level == null ? null : level.getBlockEntity(pluginPos);
            var neighbours = new StringBuilder();
            if (level != null) {
                for (var side : Direction.values()) {
                    var candidate = context.menu().position().relative(side);
                    var entity = level.getBlockEntity(candidate);
                    if (entity != null) {
                        neighbours.append(side).append('=').append(entity.getClass().getSimpleName()).append(' ');
                    }
                }
            }
            OritechAddonsOne.LOGGER.info(
                    "[transfer] sending from menu {} -> plugin {} (entity there: {}, serves {}; neighbours: {})",
                    context.menu().position(), pluginPos, at == null ? "none" : at.getClass().getSimpleName(),
                    at instanceof TransferAddonBlockEntity stored ? stored.servedMachinePos() : "n/a",
                    neighbours.length() == 0 ? "none" : neighbours.toString().trim());
            PacketDistributor.sendToServer(new TransferNetworking.SetCellFaceMode(pluginPos,
                    CellFaceModes.pack(cell, face, TransferFaceModes.pack(mode, automation))));
        };
    }

    /** Clears one cell-face without opening the modal: the page's right click on a configured face. */
    private static void clear(BlockPos pluginPos, Vec3i cell, Direction face) {
        PacketDistributor.sendToServer(new TransferNetworking.SetCellFaceMode(pluginPos,
                CellFaceModes.pack(cell, face, TransferFaceModes.pack(TransferMode.NONE, false))));
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
}
