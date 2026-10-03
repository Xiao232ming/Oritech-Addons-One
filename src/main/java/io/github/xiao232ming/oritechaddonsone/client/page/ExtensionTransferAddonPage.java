package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.widgets.SurfaceWidget;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.client.FaceTextures;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * The Extension Transfer page (扩展传输): the six faces of this addon unfolded into a cube net, used to decide
 * what each face does with the items of the machine the addon works on.
 * <p>
 * The page only exists while at least one transfer addon is stored inside the block - the registry adds it per
 * menu - and its interface is the Item Proxy page's one: the same net ({@link AddonFaceNet}), the same
 * configuration page layout ({@link AddonPickerPanel}), the same counter in the panel's top right corner. Two
 * things differ:
 * <ul>
 *     <li>there is <b>no limit</b> on how many faces may be configured: the proxy page counts the stored
 *     inventory proxy addons and offers one configurable face each, while a single transfer addon is enough
 *     for all six faces, so the counter's maximum is always six,</li>
 *     <li>a face is not bound to a machine slot but set to a {@link TransferMode}: a click on a face opens a
 *     page with the three modes (input, output, both) and a right click clears the face again,</li>
 *     <li>the face a <b>placed</b> transfer addon hangs on - drawn with a gold border - is not configurable:
 *     the plugin block itself stands in it, so no pipe or hopper can ever be there and a mode on it could
 *     not describe a connection. It is marked and refused, both here and on the server.</li>
 * </ul>
 * The net shows which mode a face has: blue while it takes items <b>in</b>, orange while it gives them
 * <b>out</b>, and half blue half orange while it does both (see {@link #washOf}).
 * <p>
 * The mode itself is stored on the block entity and applied by the server, so a face really feeds or empties
 * the machine for pipes, hoppers and other mods.
 */
public final class ExtensionTransferAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "extension_transfer";

    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;
    // The three texts this page draws are the ones the shared transfer modal uses: it configures a face
    // through the very same mode plates, so the prompt, the automation label and the occupied-face tooltip
    // are the same words for both transfer pages and are not duplicated as extension_transfer.* keys.
    private static final String PROMPT_KEY = "gui.oritechaddonsone.transfer.prompt";
    private static final String AUTOMATION_KEY = "gui.oritechaddonsone.transfer.automation";
    /** Tooltip of the face the plugin block itself occupies, i.e. the face drawn with the gold border. */
    private static final String OCCUPIED_KEY = "gui.oritechaddonsone.transfer.occupied";
    /** Colour of the green tick and of the "this face does something" state, as on the other pages. */
    private static final int GOOD = 0xFF2ECC71;

    /**
     * Gold of the border around the face a placed transfer addon hangs on, and the two mode colours - shared with
     * the 3D page of 传输插件 through {@link TransferFaceStyle}, so a player who learned on the net what
     * blue and orange mean sees the same colours on that model.
     */
    private static final int GOLD = TransferFaceStyle.GOLD;

    /** Icon of the tab: the light blue arrow ({@code oritechaddonsone:textures/gui/transfer_tab.png}, 16x16). */
    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/gui/transfer_tab.png");

    /** Faces of the block, i.e. the maximum of the counter - there is no per-addon limit. */
    private static final int MAX_FACES = Direction.values().length;

    /** The modes the picker offers, in the order its plates are laid out. */
    private static final List<TransferMode> MODES = TransferFaceStyle.MODES;

    // ------------------------------------------------------------------ picker geometry

    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 14;
    /** Y of the three mode plates inside the configuration page. */
    private static final int BUTTON_Y = 30;
    /** The automation row below them: a checkbox, the gap to its label, and the row's y. */
    private static final int AUTOMATION_BOX = 10;
    private static final int AUTOMATION_GAP = 4;
    private static final int AUTOMATION_Y = 60;
    /** Frame drawn around the configuration page, as on the Item Proxy page. */
    private static final int PANEL_FRAME = 2;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component label() {
        return Component.translatable(LABEL_KEY);
    }

    @Override
    public Identifier icon() {
        return ICON;
    }

    @Override
    public List<Component> tooltip() {
        return List.of(label(), Component.translatable(LABEL_KEY + ".tooltip"));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(AddonPageContext context, GuiGraphicsExtractor graphics, float partialTick,
            double mouseX, double mouseY) {
        var menu = context.menu();
        var textures = FaceTextures.of(menu.addonBlock(), menu.addonBlockState());
        var cells = AddonFaceNet.cells(textures);

        var attached = menu.attachedTransferFaces();
        for (var face : Direction.values()) {
            AddonFaceNet.drawFace(context, graphics, face, cells, textures, washOf(modeOf(menu, face)));

            // The face a placed transfer addon hangs on gets a gold border: it is the face this plugin
            // stands in, so a pipe or a hopper can never be there and the face is deliberately not
            // configurable (see occupiedFace). The border is the marker of that, not a selection.
            if ((attached & 1 << face.ordinal()) != 0) {
                drawGoldBorder(context, graphics, cells, face);
            }
        }

        drawCounter(context, graphics, menu.transferFaces(), MAX_FACES);

        var openFace = openFace(menu);
        if (openFace != null) drawPicker(context, graphics, menu, openFace, mouseX, mouseY);
    }

    /**
     * The wash of one face by its mode, from the shared {@link TransferFaceStyle}: blue while it takes items in,
     * orange while it gives them out, half blue half orange while it does both, nothing while it transfers nothing.
     */
    @Nullable
    private static AddonFaceNet.Wash washOf(TransferMode mode) {
        return TransferFaceStyle.wash(mode);
    }

    /**
     * Gold border around one face of the net, drawn over that face's own outline: the face of the extender a
     * placed transfer addon hangs on. The plugin block stands in that face, so it is not configurable - a
     * pipe or a hopper can never be there and a mode on it could not describe a connection (the server
     * refuses it as well, see {@code ExtensionTransferAddonBlockEntity#setTransferConfig}).
     */
    private static void drawGoldBorder(AddonPageContext context, GuiGraphicsExtractor graphics,
            Map<Direction, int[]> cells, Direction face) {
        TransferFaceStyle.drawGoldBorder(graphics,
                context.screenX(AddonFaceNet.localX(cells, face)),
                context.screenY(AddonFaceNet.localY(cells, face)),
                AddonFaceNet.FACE);
    }

    /** Draws the "Configurable: x/6" counter in the panel's top right corner, as the Item Proxy page does. */
    private void drawCounter(AddonPageContext context, GuiGraphicsExtractor graphics, int configured, int maximum) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("gui.oritechaddonsone.extension_transfer.counter", configured, maximum).getString();
        var layout = context.layout();

        graphics.text(font, text, context.screenX(layout.counterRight() - font.width(text)),
                context.screenY(layout.counterY()), AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * Draws the mode picker of the open face: the same configuration page the Item Proxy page uses - Oritech's
     * 176x100 panel with the block's item as its title icon and a prompt line - holding one plate per mode.
     * The plate of the mode that face has right now is the dark, sunken one.
     */
    private void drawPicker(AddonPageContext context, GuiGraphicsExtractor graphics, ExtensionAddonMenu menu,
            Direction face, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var placed = AddonPickerPanel.place(context);

        // a dark backdrop over the drawn panel, so the picker reads as a modal step
        graphics.fill(context.left(), context.top(), context.panelRight(), context.panelBottom(), 0xD0000000);

        graphics.pose().pushMatrix();
        graphics.pose().translate(context.screenX(placed.innerX()), context.screenY(placed.innerY()));

        drawPanel(graphics);
        drawPlates(graphics, font, menu, face, placed, mouseX, mouseY);
        drawAutomation(graphics, font, menu, face, placed, mouseX, mouseY);
        prompt(graphics, font);
        header(graphics, context, placed);

        graphics.pose().popMatrix();
    }

    /** The configuration page itself: Oritech's panel nine patch inside a darker frame. */
    private static void drawPanel(GuiGraphicsExtractor graphics) {
        new SurfaceWidget(-PANEL_FRAME, -PANEL_FRAME, AddonPickerPanel.WIDTH + 2 * PANEL_FRAME,
                AddonPickerPanel.HEIGHT + 2 * PANEL_FRAME, OritechSurface.PANEL_DARK).render(graphics, 0, 0, 0f);
        new SurfaceWidget(0, 0, AddonPickerPanel.WIDTH, AddonPickerPanel.HEIGHT, OritechSurface.PANEL)
                .render(graphics, 0, 0, 0f);
    }

    /** The three mode plates, Oritech's own button surfaces, with the face's current mode sunken in. */
    private void drawPlates(GuiGraphicsExtractor graphics, Font font, ExtensionAddonMenu menu, Direction face,
            AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        var current = modeOf(menu, face);

        for (int index = 0; index < MODES.size(); index++) {
            var mode = MODES.get(index);
            int x = plateX(index);
            int y = BUTTON_Y;

            var surface = mode == current ? OritechSurface.PANEL_DARK
                    : isOverPlate(placed, index, mouseX, mouseY) ? OritechSurface.PANEL_HOVER : OritechSurface.PANEL;
            surface.render(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);

            var text = Component.translatable(modeKey(mode)).getString();
            graphics.text(font, text, x + (BUTTON_WIDTH - font.width(text)) / 2, y + (BUTTON_HEIGHT - 8) / 2,
                    AddonPanelStyle.PANEL_TEXT, false);
        }
    }

    /**
     * The automation row of the configuration page: Oritech's dark checkbox and its label, centred under the
     * three mode plates. With automation on the face moves items by itself - towards the container on that
     * side for "output", from it for "input", both for "input + output" (see
     * {@code ExtensionAddonBlockEntity#serverTickTransfer}).
     * <p>
     * The row is dimmed and refuses clicks while the face has no direction yet: a face that transfers nothing
     * has nothing to move on its own, so the switch only becomes meaningful together with a mode.
     */
    private static void drawAutomation(GuiGraphicsExtractor graphics, Font font, ExtensionAddonMenu menu,
            Direction face, AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        var enabled = modeOf(menu, face) != TransferMode.NONE;
        var on = automationOf(menu, face);
        var label = Component.translatable(AUTOMATION_KEY).getString();

        int boxX = automationBoxX(font, label);
        int y = AUTOMATION_Y;

        // the box itself: a dark sunken plate, filled green while automation is on - the same "dark means
        // chosen" language the mode plates use, in the opposite direction
        var surface = enabled && isOverAutomation(placed, mouseX, mouseY)
                ? OritechSurface.PANEL_DARK_HOVER : OritechSurface.PANEL_DARK;
        surface.render(graphics, boxX, y, AUTOMATION_BOX, AUTOMATION_BOX);
        if (on) {
            graphics.fill(boxX + 2, y + 2, boxX + AUTOMATION_BOX - 2, y + AUTOMATION_BOX - 2, GOOD);
        }

        graphics.text(font, label, boxX + AUTOMATION_BOX + AUTOMATION_GAP, y + 1,
                enabled ? AddonPanelStyle.PANEL_TEXT : AddonPanelStyle.PANEL_TEXT_DIM, false);
    }

    /** Left edge of the automation checkbox, centring the box and its label in the configuration page. */
    private static int automationBoxX(Font font, String label) {
        int row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);
        return (AddonPickerPanel.WIDTH - row) / 2;
    }

    /**
     * True while the given panel relative mouse position is on the automation row. The whole row is the hit
     * area, so a click on the label toggles the switch as well.
     */
    private static boolean isOverAutomation(AddonPickerPanel.Placed placed, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var label = Component.translatable(AUTOMATION_KEY).getString();
        var row = AUTOMATION_BOX + AUTOMATION_GAP + font.width(label);

        double x = placed.innerX() + automationBoxX(font, label);
        double y = placed.innerY() + AUTOMATION_Y;
        return mouseX >= x && mouseX < x + row && mouseY >= y && mouseY < y + AUTOMATION_BOX;
    }

    /** The prompt line of the configuration page, centred in Oritech's panel at its own y. */
    private static void prompt(GuiGraphicsExtractor graphics, Font font) {
        var text = Component.translatable(PROMPT_KEY).getString();
        graphics.text(font, text, (AddonPickerPanel.WIDTH - font.width(text)) / 2, AddonPickerPanel.PROMPT_Y,
                AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * The header of the configuration page: the addon's own item as an icon, exactly like the Item Proxy
     * page's header - Oritech's 28x28 title icon widget with three pixels of padding.
     */
    private static void header(GuiGraphicsExtractor graphics, AddonPageContext context, AddonPickerPanel.Placed placed) {
        int left = placed.iconX() - placed.innerX();
        int top = placed.iconY() - placed.innerY();

        new SurfaceWidget(left, top, AddonPickerPanel.ICON_SIZE, AddonPickerPanel.ICON_SIZE, OritechSurface.PANEL)
                .withPadding(Insets.of(0, AddonPickerPanel.ICON_PADDING, AddonPickerPanel.ICON_PADDING,
                        AddonPickerPanel.ICON_PADDING))
                .render(graphics, 0, 0, 0f);

        graphics.item(icon(context.menu()), left + AddonPickerPanel.ICON_PADDING, top + AddonPickerPanel.ICON_PADDING);
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        var menu = context.menu();
        var openFace = openFace(menu);

        if (openFace != null) return handlePickerClick(context, menu, openFace, mouseX, mouseY, button);

        var face = AddonFaceNet.faceAt(net(menu), mouseX, mouseY);
        if (face == null) return false;

        // the face the plugin itself hangs on is not configurable: the plugin block is there, so no pipe or
        // hopper can be, and a mode would describe a connection that cannot exist
        if (face == occupiedFace(menu)) return true;

        // a right click on a configured face clears what that face does
        if (button == 1) {
            if (menu.transferMode(face) != TransferMode.NONE) {
                ExtensionTransferPickerState.close();
                send(menu.position(), face, TransferMode.NONE, false);
            }
            return true;
        }

        // a left click opens the mode picker of that face; unlike the proxy page there is nothing to check
        // first, because every face may transfer and the three modes are always known
        ExtensionTransferPickerState.open(menu.position(), face);
        return true;
    }

    /**
     * A click while the mode picker is open. A click on one of the three plates sets that mode and leaves the
     * page open, so the player sees the plate turn dark and can pick another one; the right mouse button and
     * any click that is not on a plate close the page.
     */
    private boolean handlePickerClick(AddonPageContext context, ExtensionAddonMenu menu, Direction face,
            double mouseX, double mouseY, int button) {
        if (button == 1) {
            ExtensionTransferPickerState.close();
            return true;
        }

        var placed = AddonPickerPanel.place(context);
        for (int index = 0; index < MODES.size(); index++) {
            if (!isOverPlate(placed, index, mouseX, mouseY)) continue;

            var mode = MODES.get(index);
            // the plate of the mode this face already has is disabled: clicking it again does nothing, so the
            // page neither closes nor sends a mode the server already has
            if (mode == currentMode(menu, face)) return true;

            // picking a direction keeps the automation switch of the face as it is
            var automation = automationOf(menu, face);
            ExtensionTransferPickerState.select(mode, automation);
            send(menu.position(), face, mode, automation);
            return true;
        }

        // The automation switch: it only toggles, the direction is kept, so a face can be switched between
        // "offers its inventory to pipes" and "moves the items by itself" without picking the mode again.
        if (isOverAutomation(placed, mouseX, mouseY)) {
            var mode = currentMode(menu, face);
            if (mode == TransferMode.NONE) return true;

            var automation = !automationOf(menu, face);
            ExtensionTransferPickerState.select(mode, automation);
            send(menu.position(), face, mode, automation);
            return true;
        }

        ExtensionTransferPickerState.close();
        return true;
    }

    @Override
    public List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        var menu = context.menu();

        // the picker explains itself with its plates, so nothing is shown for it
        if (openFace(menu) != null) return List.of();

        var face = AddonFaceNet.faceAt(net(menu), mouseX, mouseY);
        if (face == null) return List.of();

        // the occupied face explains why it cannot be configured instead of naming a mode it can never have
        if (face == occupiedFace(menu)) return List.of(Component.translatable(OCCUPIED_KEY));

        // only what that face does; which face it is, is the cell the mouse is on
        return List.of(Component.translatable(modeKey(modeOf(menu, face))));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The face of the extender the placed transfer addon itself hangs on, or {@code null} while this menu
     * does not belong to a placed one.
     * <p>
     * A placed plugin is the only block that reports attached faces at all, and it reports exactly the one
     * face it was placed against (see {@code ExtensionTransferAddonBlockEntity#scanAttachedTransferFaces}), which is
     * the face the net marks in gold. The wired addons and the wireless dock report none, so their pages
     * keep all six faces configurable.
     */
    @Nullable
    private static Direction occupiedFace(ExtensionAddonMenu menu) {
        var attached = menu.attachedTransferFaces();
        if (attached == 0) return null;

        for (var face : Direction.values()) {
            if ((attached & 1 << face.ordinal()) != 0) return face;
        }
        return null;
    }

    /** The face whose mode picker is open for this menu, or {@code null}. */
    @Nullable
    private static Direction openFace(ExtensionAddonMenu menu) {
        for (var face : Direction.values()) {
            if (ExtensionTransferPickerState.isOpen(menu.position(), face)) return face;
        }
        return null;
    }

    /** The mode a face has right now, including what the open configuration page set a moment ago. */
    private static TransferMode modeOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.mode() : menu.transferMode(face);
    }

    /** True while a face moves its items by itself, including what the open page set a moment ago. */
    private static boolean automationOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.automation() : menu.transferAutomation(face);
    }

    /** What the open configuration page of this face set a moment ago, or {@code null} while it is closed. */
    @Nullable
    private static ExtensionTransferPickerState.Pending pending(ExtensionAddonMenu menu, Direction face) {
        return ExtensionTransferPickerState.isOpen(menu.position(), face) ? ExtensionTransferPickerState.pending() : null;
    }

    /** The mode the server already knows for this face, i.e. without the page's pending value. */
    private static TransferMode currentMode(ExtensionAddonMenu menu, Direction face) {
        return menu.transferMode(face);
    }

    /** The net of the menu's block: the cell of every face, in {@link AddonFaceNet}'s frame. */
    private static Map<Direction, int[]> net(ExtensionAddonMenu menu) {
        return AddonFaceNet.cells(FaceTextures.of(menu.addonBlock(), menu.addonBlockState()));
    }

    /** Panel relative X of the mode plate with the given index, all three centred in the panel. */
    private static int plateX(int index) {
        int total = MODES.size() * BUTTON_WIDTH + (MODES.size() - 1) * BUTTON_GAP;
        return (AddonPickerPanel.WIDTH - total) / 2 + index * (BUTTON_WIDTH + BUTTON_GAP);
    }

    /**
     * True while the given panel relative mouse position is on the plate with the given index. The whole plate
     * is the hit area, so a click anywhere on it sets that mode.
     */
    private static boolean isOverPlate(AddonPickerPanel.Placed placed, int index, double mouseX, double mouseY) {
        double x = placed.innerX() + plateX(index);
        double y = placed.innerY() + BUTTON_Y;
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    /** Language key of a mode name, e.g. {@code gui.oritechaddonsone.transfer.mode.input}. */
    private static String modeKey(TransferMode mode) {
        return TransferFaceStyle.modeKey(mode);
    }

    /** The item drawn as the configuration page's icon: the block this menu belongs to. */
    private static ItemStack icon(ExtensionAddonMenu menu) {
        var block = menu.addonBlock();
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    /**
     * Tells the server what a face should do. The page is client only, so this is the one place it talks
     * back; the direction and the automation flag travel as the one packed value the block entity and the menu
     * use for a face.
     */
    private static void send(BlockPos pos, Direction face, TransferMode mode, boolean automation) {
        ClientPacketDistributor.sendToServer(new TransferNetworking.SetTransferMode(
                pos, ProxyNetworking.faceIndex(face), TransferFaceModes.pack(mode, automation)));
    }
}
