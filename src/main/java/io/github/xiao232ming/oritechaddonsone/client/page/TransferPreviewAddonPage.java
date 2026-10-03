package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.OritechSurface;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The page of 传输插件: the machine this plugin serves as a rotatable 3D model, and the configuration of one of
 * its faces in the modal page a click on that face opens.
 * <p>
 * What the page <b>does</b> is exactly what the Extension Transfer page does - one {@link TransferMode} plus the
 * automation flag per face, the same occupied-face refusal, the same packet to the server, the same container data
 * back - and the two pages even agree on the look of a configured face (see {@link TransferFaceStyle} and
 * {@link TransferFaceModal}). What differs is how a face is chosen: instead of the host unfolded into a cube net,
 * this page renders the machine the plugin works on as a 3D model and picks the face the player clicks on it. This
 * branch has no picture in picture GUI rendering, so the model is drawn straight into the page's own pose stack by
 * {@link FacePreviewWidget}, which is also what lets the face under the mouse be marked with a translucent white quad
 * in the model's own pose there.
 * <p>
 * <b>The page itself shows only the model.</b> The counter, the instruction line and the 3D model with its hover
 * highlight are the whole page; everything that configures a face - the three mode plates and the automation switch -
 * lives in the modal page a left click on a face opens, and a right click on a configured face clears it without
 * opening anything. That is the cube net page's interaction, applied to a model instead of a net: the plates are only
 * ever in front of the player while a face is really being configured, so the model - which is what this page is for -
 * stays visible the rest of the time. The face list the page used to draw under the model is gone with them: a face's
 * mode is now read off the modal that configures it, and the counter still says how many faces are configured.
 * <p>
 * <b>The model lives in absolute screen space.</b> This page draws in panel space while the widget is rendered at the
 * pixel position it was given, which is what its own drawing and its picking assume; every coordinate the page hands
 * to the widget is therefore converted once ({@link AddonPageContext#screenX(int)} /
 * {@link AddonPageContext#screenY(int)}) and never mixed with the panel relative coordinates the page uses for its own
 * controls. The same rule applies to the model's interaction: this screen host is an
 * {@code AbstractContainerScreen}, whose widgets know nothing about our pages, so the page implements the drag
 * between {@link #mouseDragged} and {@link AddonPage#mouseClicked}.
 * <p>
 * <b>The page is the same one in both screens.</b> It is the only page of 传输插件 while the plugin is placed in the
 * world, and it is one of the pages of an Extension Addon while the plugin is stored in its slots. Which block entity
 * it configures never depends on that: everything it reads (the modes, the automation flags, the occupied faces) and
 * everything it writes comes from the menu it was handed (see {@link ExtensionAddonMenu#transferPreviewMachinePos()}),
 * and that menu is the block the screen was opened for - the placed plugin, or the addon.
 */
public final class TransferPreviewAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "transfer_preview";

    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;
    /** Instruction line below the title, telling the player what a click does. */
    private static final String HINT_KEY = "gui.oritechaddonsone.preview.hint";
    private static final String NO_MACHINE_KEY = "gui.oritechaddonsone.preview.no_machine";
    /** Tooltip of the face the plugin itself occupies, i.e. the face a click cannot configure. */
    private static final String OCCUPIED_KEY = "gui.oritechaddonsone.transfer.occupied";

    /** Icon of the tab: the orange arrow ({@code oritechaddonsone:textures/gui/transfer_preview_tab.png}, 16x16). */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/gui/transfer_preview_tab.png");

    /** Faces of the machine, i.e. the maximum of the counter - there is no per-plugin limit. */
    private static final int MAX_FACES = Direction.values().length;

    // ------------------------------------------------------------------ geometry

    /**
     * The page's rows, in panel space, top to bottom. The panel's own title label is drawn by the screen in the top
     * band (Y 6, see {@code ExtensionAddonScreen#renderLabels}) and the counter shares that band on the right
     * ({@link ExtensionAddonLayout#counterY()}), so the page's first own row starts below both of them: the
     * instruction line, then the model. The plates and the switch are no part of the page any more - they belong to
     * the modal page - so the model is the page's last row and the shortest a layout can make the panel is tall
     * enough for the modal that opens over it (see {@link #drawnHeight}).
     */
    private static final int HINT_Y = 18;

    /** Width and height of the 3D model's own panel, centred in the page body. */
    private static final int PREVIEW_WIDTH = 140;
    private static final int PREVIEW_HEIGHT = 96;
    /** Top edge of that panel, in panel space: below the panel's title and the instruction line. */
    private static final int PREVIEW_Y = HINT_Y + 10;

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
     * The panel ends below the model, which is the page's own content.
     * <p>
     * The modal configuration page is the reason this is not simply {@code PREVIEW_Y + PREVIEW_HEIGHT}: the modal is
     * Oritech's 176x100 panel with its 28 pixel title icon floating above it ({@link AddonPickerPanel}), and it has to
     * fit inside the drawn panel for <b>every</b> layout - including the shortest one, a single plugin row. So the
     * page asks for enough height to hold the model <em>and</em> the modal, and lets
     * {@link ExtensionAddonLayout#pageHeight(int)} keep the player inventory band as the floor. Without this the
     * one-row panel would be shorter than the modal and the lower third of the plates would be cut off.
     */
    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        int modelBottom = PREVIEW_Y + PREVIEW_HEIGHT;
        // the two pixels AddonPickerPanel keeps above its placement floor, and a small margin so the modal's frame
        // is never flush with the panel's dark bottom bevel
        int modalBottom = AddonPickerPanel.ICON_SIZE + AddonPickerPanel.HEIGHT + 10;
        return layout.pageHeight(Math.max(modelBottom, modalBottom));
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

        var openFace = openFace(menu);
        if (openFace != null) {
            preview.select(openFace);
            TransferFaceModal.render(context, graphics, menu, openFace, isOccupied(menu, openFace), mouseX, mouseY);
        }
    }

    /**
     * The instruction line, centred in the panel one row below the panel's own title band: it names what the model is
     * for and what a click on it does - the plates themselves are only shown by the modal that click opens.
     */
    private void drawHint(AddonPageContext context, GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(HINT_KEY).getString();
        graphics.drawString(font, text, context.screenX((context.panelWidth() - font.width(text)) / 2),
                context.screenY(HINT_Y), AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /**
     * The sunken panel the model lives in, drawn behind it: the same dark inset Oritech uses for a field that is not a
     * button, so the model reads as the page's content rather than as something floating on the panel.
     * <p>
     * It is drawn for the "no machine" state as well, which is the whole point: the message then reads as this page's
     * own empty content instead of as a line of text in an otherwise empty panel.
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
     * same in all of them: the plugin serves no machine at all (it stands on an extender no machine ever claimed), the
     * machine it serves is in a chunk this client has not loaded, or the plugin has just been picked up. The line is
     * centred in the model's panel, so the panel - which is drawn either way - reads as this page's empty content.
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

    /**
     * A click on the model picks the face under it. With no modal open, a left click opens that face's configuration
     * page (or, on the face the plugin itself stands on, does nothing but select it - it can never be configured) and
     * a right click clears what a configured face does without opening anything. While the modal is open every click
     * belongs to it: a plate or the switch is applied and the modal stays open, and anything else closes it.
     */
    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        var menu = context.menu();

        var openFace = openFace(menu);
        if (openFace != null) {
            return TransferFaceModal.mouseClicked(context, menu, openFace, isOccupied(menu, openFace), mouseX, mouseY,
                    button);
        }

        var preview = currentPreview(context);
        if (preview == null) return false;

        // a click on the model picks a face; the widget remembers the pick, the page remembers the selection
        var picked = preview.widget().pickFace(screenX(context, mouseX), screenY(context, mouseY));
        if (picked == null) return false;

        preview.select(picked);
        if (isOccupied(menu, picked)) return true;

        // a right click on a configured face clears what that face does, exactly like on the cube net page
        if (button == 1) {
            if (TransferFaceModal.modeOf(menu, picked) != TransferMode.NONE) {
                TransferPreviewPickerState.close();
                TransferFaceModal.clear(menu.position(), picked);
            }
            return true;
        }

        TransferPreviewPickerState.open(menu.position(), picked);
        return true;
    }

    /**
     * Rotates the model while the player drags over it. Only a drag that stays over the model is claimed, so dragging
     * an item across a slot of this GUI still reaches vanilla's slot logic, and the drag starts wherever the button
     * went down - the page does not have to remember its own press, because it only ever turns the model while the
     * mouse is on it.
     * <p>
     * A drag never reaches the modal: while a face is being configured the model is covered by it, and
     * {@link #mouseDragged} asks the model's own hit test, which the backdrop is not part of - so the rotation would
     * only start from the visible sliver of the panel around the modal. Turning the model while its configuration is
     * open is exactly what a player does to see the face from the other side, so that is allowed: the modal follows the
     * model's rotation, because the face it configures is the one the page selected.
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
     * Nothing has to end here: the rotation ends with the drag itself and the modal stays open until it is deliberately
     * closed. The hook is kept as the counterpart of {@link #mouseDragged} and as the place a gesture that does need an
     * end would use.
     */
    @Override
    public void mouseReleased(AddonPageContext context, double mouseX, double mouseY, int button) {
        // no state of this page ends on a release
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        var menu = context.menu();
        // the modal explains itself with its plates and its prompt, so nothing is shown over it
        if (openFace(menu) != null) return List.of();

        var preview = currentPreview(context);
        if (preview == null) return List.of();

        var hovered = preview.widget().hoveredFace();
        if (hovered == null) return List.of();

        // the occupied face explains why it cannot be configured instead of naming a mode it can never have
        if (isOccupied(menu, hovered)) return List.of(Component.translatable(OCCUPIED_KEY));

        return List.of(Component.translatable(sideKey(hovered)),
                Component.translatable(TransferFaceStyle.modeKey(TransferFaceModal.modeOf(menu, hovered))));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The machine the page renders, as the menu it belongs to reports it: the machine a placed plugin serves, or -
     * while the page is shown inside an Extension Addon that stores a preview plugin - the machine that addon works on.
     * The page therefore never has to know which of the two screens it is drawn in; it configures whatever the menu
     * addresses.
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
                context.screenX(previewX(context)), context.screenY(PREVIEW_Y), PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    /** The face whose configuration page is open for this menu, or {@code null} while none is. */
    @Nullable
    private static Direction openFace(ExtensionAddonMenu menu) {
        for (var face : Direction.values()) {
            if (TransferPreviewPickerState.isOpen(menu.position(), face)) return face;
        }
        return null;
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

    /**
     * True while the given face of the machine is one the plugin refuses to configure, i.e. while it is in the mask
     * the block entity published: the face the plugin itself occupies, and every face another plugin of this mod
     * stands on.
     */
    private static boolean isOccupied(ExtensionAddonMenu menu, Direction face) {
        return (menu.attachedTransferFaces() & 1 << face.ordinal()) != 0;
    }

    /** Language key of a face name, e.g. {@code gui.oritechaddonsone.transfer.side.north}. */
    private static String sideKey(Direction face) {
        return "gui.oritechaddonsone.transfer.side." + face.name().toLowerCase(Locale.ROOT);
    }
}
