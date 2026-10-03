package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.widgets.ItemSlotWidget;
import rearth.oritech.api.screen.widgets.SurfaceWidget;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.client.FaceTextures;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;

/**
 * The Item Proxy page (物品代理): the six faces of this addon unfolded into a cube net, and the slot
 * layout of the machine a face can be pointed at.
 * <p>
 * The page only exists while at least one Oritech inventory proxy addon is stored inside the block - the
 * registry adds it per menu - and it is a direct port of what that addon does on its own: Oritech's
 * {@code InventoryProxyAddonBlockEntity} offers an {@code ItemProvider} whose handler forwards to the
 * machine the addon is attached to while only one of its slots is usable, and its screen lets the player
 * pick that slot from the machine's GUI slots. Here the "screen" is this page, the "one slot" is one per
 * <b>face</b>, so the net doubles as the face selector.
 * <ul>
 *     <li>the net is drawn from the block's own per-face textures (see {@link FaceTextures}),</li>
 *     <li>a left click on a face opens the configuration page of that face, which is Oritech's own
 *     inventory proxy page: the machine's GUI slots as framed cells with Oritech's own selection plate
 *     inside every cell - the plate of the slot the face is bound to is the dark, sunken one - plus the
 *     same prompt and the same click-to-select interaction (see {@link #drawPicker}). A face that is
 *     already bound opens with that binding selected, so the page shows what the face proxies right now,</li>
 *     <li>a right click on a configured face removes its binding again,</li>
 *     <li>the panel's top right corner shows the counter {@code 可配置数: x/x} / {@code Configurable: x/x}:
 *     configured faces out of the number of stored inventory proxy addons, which is the maximum. It sits on
 *     the panel's title row and is right aligned to {@link ExtensionAddonLayout#counterRight()} - the free
 *     strip right of the inventory - so it is off the inventory slots, off the net and off the tab
 *     strip.</li>
 * </ul>
 * The binding itself is stored on the block entity and applied by the server, so a configured face really
 * proxies the machine's inventory to pipes, hoppers and other mods.
 * <p>
 * <b>Coordinates.</b> Every position in this class is a panel relative coordinate: the net is
 * {@link ExtensionAddonLayout#PROXY_NET_X}/{@link ExtensionAddonLayout#PROXY_NET_Y} of
 * {@link ExtensionAddonLayout}, the configuration page is
 * {@link ProxyPickerState#place(AddonPageContext, List)}, and both are mapped to the screen with
 * {@link AddonPageContext#screenX(int)}/{@link AddonPageContext#screenY(int)} at the single place that
 * draws them. The hit tests, the tooltips and the drawing therefore always describe the same rectangle -
 * an earlier version translated the net by something else than the panel's origin, which put the pixels
 * left of the panel border while every panel space check still passed.
 */
public final class ItemProxyAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "proxy";

    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;

    /**
     * Oritech's own prompt of its inventory proxy page, reused verbatim so the configuration page says
     * exactly what Oritech's screen says ("选择代理目标槽位" / "Select proxy target slot"). The string comes
     * from Oritech's language files, so it is translated wherever Oritech is.
     */
    private static final String PROMPT_KEY = "tooltip.oritech.addon_proxy_select";

    // ------------------------------------------------------------------ Oritech's configuration page

    /**
     * Size of Oritech's own inventory proxy screen, which its {@code InventoryProxyScreen} passes to
     * {@code OritechWidgetScreen} as {@code (176, 100)}. The configuration page is that panel, so the
     * machine's GUI slots keep the coordinates Oritech gives them.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 100;
    /**
     * Y of Oritech's prompt line inside that panel ({@code InventoryProxyScreen} uses {@code 85}, its
     * {@code LabelWidget} is {@code 176} wide and centred, ten pixels tall).
     */
    public static final int PROMPT_Y = 85;
    /** Frame drawn around the panel, so Oritech's light panel reads against our light grey body. */
    private static final int PANEL_FRAME = 2;
    /**
     * Size of Oritech's title icon ({@code OritechWidgetScreen#addTitle()} builds a {@code 28x28} widget)
     * and the padding it keeps inside it, which centres the 16x16 item.
     */
    public static final int ICON_SIZE = 28;
    private static final int ICON_PADDING = 3;
    /**
     * Size of the selection plate Oritech puts inside a slot of its inventory proxy screen, and its
     * offset inside the 16x16 cell - its screen builds
     * {@code ButtonWidget.panel(slot.x() + 3, slot.y() + 3, 10, 10, ...)} per GUI slot of the machine.
     */
    public static final int PLATE_SIZE = 10;
    public static final int PLATE_OFFSET = 3;

    /**
     * Icon of the tab: a chest front with a latch and a keyhole
     * ({@code oritechaddonsone:textures/gui/item_proxy_tab.png}, 16x16).
     * <p>
     * Vanilla has no standalone chest-front sprite (the chest is drawn by a block entity renderer from a
     * 64x64 atlas), and Oritech's own inventory proxy addon texture is a 1.5x1.5 pixel palette patch that
     * reads as plain orange at icon size. The icon was therefore composed by hand in the vanilla chest
     * colours - wooden planks, a darker lid seam, the metal latch with the keyhole - which is the "chest
     * front including the lock" the tab is meant to be recognised by, and it is the same size as the
     * existing wireless tab icon.
     */
    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/gui/item_proxy_tab.png");

    /**
     * Size of one face of the net, in pixels ({@link ExtensionAddonLayout#PROXY_FACE}). 18 keeps the whole
     * net inside the free part of the panel.
     */
    private static final int FACE = ExtensionAddonLayout.PROXY_FACE;
    /** Left edge of the net, in panel space, from the layout so panel and page cannot drift apart. */
    private static final int NET_X = ExtensionAddonLayout.PROXY_NET_X;
    /**
     * Top edge of the net, in panel space, from the layout: below the panel's title label, and the layout
     * keeps the player inventory below {@link ExtensionAddonLayout#PROXY_CONTENT_BOTTOM} for it.
     */
    private static final int NET_Y = ExtensionAddonLayout.PROXY_NET_Y;

    /**
     * Cell of every face inside the net for a block whose port faces north, in face units. The net is the
     * usual cross: the front in the middle, top above it, bottom below it, and the four sides in a row, so
     * the addon reads like an unfolded cardboard box. "North" is the cell the model calls the port face.
     */
    private static final Map<Direction, int[]> NET_CELLS = Map.of(
            Direction.NORTH, new int[]{1, 1},
            Direction.EAST, new int[]{2, 1},
            Direction.SOUTH, new int[]{3, 1},
            Direction.WEST, new int[]{0, 1},
            Direction.UP, new int[]{1, 0},
            Direction.DOWN, new int[]{1, 2});

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
        var cells = cells(menu);

        for (var face : Direction.values()) {
            drawFace(context, graphics, face, cells, textures, menu.isProxyFaceConfigured(face));
        }

        drawCounter(context, graphics, menu.configuredProxyFaces(), menu.inventoryProxyCount());

        var openFace = openFace(menu);
        if (openFace != null) drawPicker(context, graphics, menu, openFace, mouseX, mouseY);
    }

    /**
     * Draws one face of the net, with its frame and its "configured" marker.
     * <p>
     * The cell is a panel relative coordinate ({@code PROXY_NET_X + column * PROXY_FACE}) and is mapped to
     * the screen through {@link AddonPageContext#screenX(int)} - the very same mapping {@link #faceAt} uses
     * for the click and hover test, so what the player sees is what the player clicks.
     */
    private void drawFace(AddonPageContext context, GuiGraphicsExtractor graphics, Direction face,
            Map<Direction, int[]> cells, FaceTextures textures, boolean isConfigured) {
        int x = context.screenX(localFaceX(cells, face));
        int y = context.screenY(localFaceY(cells, face));

        var texture = textures.face(face);
        if (texture.flipVertically()) {
            // the flat addon models sample the side textures from the lower half of their texture; the net
            // draws the full 16x16 sprite, so the flip is a vertical mirror around the face's own centre
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y + FACE);
            graphics.pose().scale(1f, -1f);
            graphics.blit(texture.texture(), 0, 0, FACE, FACE, 0f, 0f, 1f, 1f);
            graphics.pose().popMatrix();
        } else {
            graphics.blit(texture.texture(), x, y, x + FACE, y + FACE, 0f, 1f, 0f, 1f);
        }

        // a configured face gets a green wash, so the net shows at a glance which faces really proxy
        if (isConfigured) {
            graphics.fill(x, y, x + FACE, y + FACE, 0x552ECC71);
            graphics.fill(x, y, x + FACE, y + 1, 0xFF2ECC71);
            graphics.fill(x, y + FACE - 1, x + FACE, y + FACE, 0xFF1E8C4C);
            graphics.fill(x, y, x + 1, y + FACE, 0xFF2ECC71);
            graphics.fill(x + FACE - 1, y, x + FACE, y + FACE, 0xFF1E8C4C);
        }

        // 1px outline so neighbouring faces of the net stay distinguishable
        graphics.fill(x, y, x + FACE, y + 1, AddonPanelStyle.SLOT_DARK);
        graphics.fill(x, y + FACE - 1, x + FACE, y + FACE, AddonPanelStyle.SLOT_DARK);
        graphics.fill(x, y, x + 1, y + FACE, AddonPanelStyle.SLOT_DARK);
        graphics.fill(x + FACE - 1, y, x + FACE, y + FACE, AddonPanelStyle.SLOT_DARK);
    }

    /**
     * Left edge of one face of the net, in panel space. This is the single definition of where a face is;
     * drawing ({@link #drawFace}), clicking and hovering ({@link #faceAt}) all read it, so they cannot drift
     * apart.
     */
    private static int localFaceX(Map<Direction, int[]> cells, Direction face) {
        return NET_X + cells.get(face)[0] * FACE;
    }

    /** Top edge of one face of the net, in panel space; see {@link #localFaceX}. */
    private static int localFaceY(Map<Direction, int[]> cells, Direction face) {
        return NET_Y + cells.get(face)[1] * FACE;
    }

    /**
     * Draws the "Configurable: x/x" counter in the panel's top right corner.
     * <p>
     * It sits on the panel's title row ({@link ExtensionAddonLayout#counterY()}), next to the net but clear
     * of it, and it is right aligned to {@link ExtensionAddonLayout#counterRight()} - the free strip right
     * of the inventory and {@value ExtensionAddonLayout#COUNTER_MARGIN} pixels left of the panel's border,
     * hence clear of the tab strip, which only overlaps the panel by
     * {@link ExtensionAddonLayout#TAB_OVERLAP} pixels.
     */
    private void drawCounter(AddonPageContext context, GuiGraphicsExtractor graphics, int configured, int maximum) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("gui.oritechaddonsone.proxy.counter", configured, maximum).getString();
        var layout = context.layout();

        graphics.text(font, text, context.screenX(layout.counterRight() - font.width(text)),
                context.screenY(layout.counterY()), AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * Draws the configuration page of the open face.
     * <p>
     * This is Oritech's own inventory proxy configuration page, painted where its own screen would be: a
     * 176x100 {@linkplain OritechSurface#PANEL bedrock panel} (the nine patch Oritech itself fills its
     * widgets with - see {@link SurfaceWidget}), a framed cell per GUI slot of the machine
     * ({@link ItemSlotWidget}, again Oritech's own widget, so the frames are pixel for pixel the same)
     * carrying Oritech's selection plate ({@link #drawSlots}), the same prompt
     * {@code tooltip.oritech.addon_proxy_select} in Oritech's dark label colour, and the block's item
     * above the panel as its title icon - the same elements, in the same places and sizes, that
     * {@code rearth.oritech.client.ui.InventoryProxyScreen} builds for its own menu.
     * <p>
     * The one thing that cannot be reused is Oritech's {@code InventoryProxyScreenHandler}: it holds the
     * screen provider and the position of <em>one</em> proxy addon block and sends that block's
     * {@code target_slot} on a click, while this page configures <em>one face per click</em> of our own
     * block and must keep our binding packets, cap and fail-safe. The page therefore repaints Oritech's
     * page with Oritech's classes and handles the click itself, which is what makes a face bind the slot
     * the player clicked.
     * <p>
     * While this is open it covers the net and the counter of the page: a modal step that a click on a
     * slot does <b>not</b> close - like Oritech's screen it stays up with the selected slot's plate turned
     * dark, so the player can read off which slot was taken and pick another one - and it is closed by the
     * right mouse button or by a click anywhere that is not a slot, the same "the slots are the only
     * controls" interaction Oritech's own screen has. The panel is
     * {@linkplain ProxyPickerState#place placed} fully inside our panel - centred horizontally, and as high
     * as the title icon above it allows - so it can never be clipped by the window edge, and it is never
     * resized, so the machine's slot layout stays Oritech's own.
     */
    private void drawPicker(AddonPageContext context, GuiGraphicsExtractor graphics, ExtensionAddonMenu menu,
            Direction face, double mouseX, double mouseY) {
        var slots = ProxyPickerState.layout(menu.position(), face);
        var font = Minecraft.getInstance().font;
        var placed = ProxyPickerState.place(context, slots == null ? List.of() : slots);

        // a dark backdrop over the drawn panel, so the picker reads as a modal step and neither the net
        // nor the counter behind it shows through. It is drawn first and in screen space, so it can never
        // cover the configuration page itself.
        graphics.fill(context.left(), context.top(), context.panelRight(), context.panelBottom(), 0xD0000000);

        // from here on everything is drawn in the configuration page's own coordinate system: one
        // translate to the panel's top left corner in screen space, then every child at the panel relative
        // position Oritech's own screen gives it (this is what SurfaceWidget / ItemSlotWidget expect).
        graphics.pose().pushMatrix();
        graphics.pose().translate(context.screenX(placed.innerX()), context.screenY(placed.innerY()));

        drawPanel(graphics);

        if (slots == null) {
            centered(graphics, font, Component.translatable("gui.oritechaddonsone.proxy.picker.loading"));
        } else if (slots.isEmpty()) {
            centered(graphics, font, Component.translatable("gui.oritechaddonsone.proxy.picker.no_machine"));
        } else {
            drawSlots(graphics, menu, face, slots, placed, mouseX, mouseY);
            prompt(graphics, font, Component.translatable(PROMPT_KEY));
        }

        // the header is painted last, so the title icon stays on top of every slot cell of a machine whose
        // GUI starts above the configuration panel
        header(graphics, context, placed, face, font);

        graphics.pose().popMatrix();
    }

    /**
     * The framed cells of the machine's GUI slots, each with the selection plate Oritech draws inside it,
     * in the translated page.
     * <p>
     * Cell for cell this is what Oritech's {@code InventoryProxyScreen} builds for its own menu: an
     * {@link ItemSlotWidget} frame per GUI slot of the machine, and three pixels inside it a 10x10 plate
     * whose surface says what the cell is. Its screen disables the button of the selected slot with
     * {@code ButtonWidget.setActive(false)}, which renders {@code PANEL_DARK} - the dark, sunken plate -
     * while every other cell is the raised {@code PANEL} and lights up with {@code PANEL_HOVER} under the
     * mouse. So the dark cell is the slot this face proxies, and it stays visible because a click binds
     * without closing the page (see {@link #handlePickerClick}) - exactly like Oritech's screen, where the
     * selected plate is the only thing that says which slot was taken.
     * <p>
     * The items of the machine are deliberately not drawn, again like Oritech's own screen: its menu holds
     * no slots at all, so it shows the bare cells, and it is the cell's position inside the machine's own
     * GUI layout that identifies the slot.
     * <p>
     * The plate is 10x10 but the whole 16x16 cell is both the hover area and the hit area, so the plate
     * lights up - and binds - for a click anywhere on its cell, the same rectangle
     * {@link ProxyPickerState#isOverSlot} tests and the tooltip speaks about. Oritech's own
     * {@code ButtonWidget} would answer only its own ten pixels, which would leave the frame of the cell
     * dead while the tooltip still calls it that slot.
     */
    private void drawSlots(GuiGraphicsExtractor graphics, ExtensionAddonMenu menu, Direction face,
            List<int[]> slots, ProxyPickerState.Placed placed, double mouseX, double mouseY) {
        var selected = selectedSlot(menu, face);

        for (var slot : slots) {
            int x = slot[1];
            int y = slot[2];

            // Oritech's own slot widget paints the frame at the slot's position
            new ItemSlotWidget(x, y).render(graphics, (int) mouseX, (int) mouseY, 0f);

            var hovered = ProxyPickerState.isOverSlot(placed, slot, mouseX, mouseY);
            var plate = selected != null && selected == slot[0]
                    ? OritechSurface.PANEL_DARK
                    : hovered ? OritechSurface.PANEL_HOVER : OritechSurface.PANEL;
            plate.render(graphics, x + PLATE_OFFSET, y + PLATE_OFFSET, PLATE_SIZE, PLATE_SIZE);
        }
    }

    /**
     * The slot the open configuration page shows as selected: the binding the menu already knows, or the
     * one this page bound a moment ago while the server has not answered yet - a plate that only darkens
     * a tick after the click would read as a flicker (see {@link ProxyPickerState#select}).
     */
    @Nullable
    private static Integer selectedSlot(ExtensionAddonMenu menu, Direction face) {
        var pending = ProxyPickerState.pendingSlot();
        return pending != null ? pending : menu.proxySlotOf(face);
    }

    /**
     * The bedrock panel of the configuration page: Oritech's own {@link OritechSurface#PANEL} nine patch,
     * drawn twice - a darker copy as the frame around it, exactly the way Oritech's widget screens stack
     * their {@code SurfaceWidget}s. Called inside the page's translated pose, so the panel sits at the
     * page's own {@code (0, 0)}.
     */
    private static void drawPanel(GuiGraphicsExtractor graphics) {
        new SurfaceWidget(-PANEL_FRAME, -PANEL_FRAME, PANEL_WIDTH + 2 * PANEL_FRAME, PANEL_HEIGHT + 2 * PANEL_FRAME,
                OritechSurface.PANEL_DARK).render(graphics, 0, 0, 0f);
        new SurfaceWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT, OritechSurface.PANEL).render(graphics, 0, 0, 0f);
    }

    /**
     * Oritech's own prompt line: centred in the panel, at {@link #PROMPT_Y}, in Oritech's dark label
     * colour - the same {@code LabelWidget} Oritech's screen adds.
     */
    private static void prompt(GuiGraphicsExtractor graphics, Font font, Component text) {
        centered(graphics, font, text, PROMPT_Y);
    }

    /** One centred line of the configuration page's own text. */
    private static void centered(GuiGraphicsExtractor graphics, Font font, Component text) {
        centered(graphics, font, text, (PANEL_HEIGHT - 8) / 2);
    }

    /** One line of the configuration page's text, centred in Oritech's panel at the given Y. */
    private static void centered(GuiGraphicsExtractor graphics, Font font, Component text, int y) {
        var string = text.getString();
        graphics.text(font, string, (PANEL_WIDTH - font.width(string)) / 2, y, AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * The header of the configuration page: the addon's own item as an icon with the face it is being
     * configured for next to it. Oritech puts the same icon in the same place - a {@code 28x28}
     * {@code PANEL} widget with three pixels of padding, centred above its panel by
     * {@code OritechWidgetScreen#addTitle()}. Called inside the page's translated pose, so the icon is
     * placed relative to the configuration panel.
     */
    private static void header(GuiGraphicsExtractor graphics, AddonPageContext context, ProxyPickerState.Placed placed,
            Direction face, Font font) {
        int left = placed.iconX() - placed.innerX();
        int top = placed.iconY() - placed.innerY();

        // Oritech's title icon is drawn on a 28x28 PANEL surface with three pixels of padding, so the 16x16
        // item ends up centred in it and the surface is the widget's own nine patch
        new SurfaceWidget(left, top, ICON_SIZE, ICON_SIZE, OritechSurface.PANEL)
                .withPadding(Insets.of(0, ICON_PADDING, ICON_PADDING, ICON_PADDING))
                .render(graphics, 0, 0, 0f);

        graphics.item(icon(context.menu()), left + ICON_PADDING, top + ICON_PADDING);

        var text = faceName(face);
        graphics.text(font, text, left + ICON_SIZE + 4, top + (ICON_SIZE - 8) / 2, AddonPanelStyle.PANEL_TEXT, false);
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        var menu = context.menu();
        var openFace = openFace(menu);

        // while the picker is open every click belongs to it: a click on a slot of the machine selects
        // that slot and leaves the page open, any other click closes it again
        if (openFace != null) return handlePickerClick(context, menu, openFace, mouseX, mouseY, button);

        var face = faceAt(menu, mouseX, mouseY);
        if (face == null) return false;

        var configured = menu.isProxyFaceConfigured(face);

        // a right click on a configured face stops proxying on that face
        if (button == 1) {
            if (configured) {
                ProxyPickerState.close();
                send(new ProxyNetworking.ClearFace(menu.position(), ProxyNetworking.faceIndex(face)));
            }
            return true;
        }

        // A left click opens the configuration page of that face. A face that already proxies something may
        // always be opened again - the page then shows that binding as the dark, selected plate, so the
        // player sees what is configured before changing it - while a face that proxies nothing needs a free
        // inventory proxy addon to be configurable at all.
        if (!configured && menu.configuredProxyFaces() >= menu.inventoryProxyCount()) return true;

        ProxyPickerState.open(menu.position(), face);
        send(new ProxyNetworking.RequestPicker(menu.position(), ProxyNetworking.faceIndex(face)));
        return true;
    }

    /**
     * A click while the configuration page is open. A click on one of the machine's slots binds the face
     * that is being configured to it - the same click, on the same cell, that Oritech's own proxy screen
     * turns into {@code setTargetSlot} - and the page then <b>stays open</b>, the way Oritech's screen
     * does, so the plate of the slot that was taken turns dark and the player can read the choice off,
     * pick another slot or close the page. The disabled plate of the slot that is already bound ignores
     * the click like Oritech's disabled button does.
     * <p>
     * The page is closed by the right mouse button and by any click that is not on a slot - a click
     * outside a modal.
     * <p>
     * The mouse position arrives panel relative, exactly like the drawn cells, so the test uses the same
     * {@link ProxyPickerState#place(AddonPageContext, List) placement} the drawing does.
     */
    private boolean handlePickerClick(AddonPageContext context, ExtensionAddonMenu menu, Direction face,
            double mouseX, double mouseY, int button) {
        if (button == 1) {
            ProxyPickerState.close();
            return true;
        }

        var slots = ProxyPickerState.layout(menu.position(), face);
        if (slots != null) {
            var placed = ProxyPickerState.place(context, slots);
            var selected = selectedSlot(menu, face);
            for (var slot : slots) {
                if (ProxyPickerState.isOverSlot(placed, slot, mouseX, mouseY)) {
                    // the plate of the slot this face already proxies is disabled: clicking it again does
                    // nothing, so the page neither closes nor sends a binding the server already has
                    if (selected != null && selected == slot[0]) return true;

                    ProxyPickerState.select(slot[0]);
                    send(new ProxyNetworking.BindFace(menu.position(), ProxyNetworking.faceIndex(face), slot[0]));
                    return true;
                }
            }
        }

        // any other click (also one next to the panel) closes the page, like a click outside a modal
        ProxyPickerState.close();
        return true;
    }

    @Override
    public List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        var menu = context.menu();
        var openFace = openFace(menu);

        if (openFace != null) {
            var slots = ProxyPickerState.layout(menu.position(), openFace);
            if (slots == null || slots.isEmpty()) return List.of();

            var placed = ProxyPickerState.place(context, slots);
            for (var slot : slots) {
                if (ProxyPickerState.isOverSlot(placed, slot, mouseX, mouseY)) {
                    // the slot index and the face it belongs to, so the click is unambiguous
                    return List.of(Component.translatable("gui.oritechaddonsone.proxy.picker.slot", slot[0]),
                            Component.translatable("gui.oritechaddonsone.proxy.face", faceName(openFace)));
                }
            }
            return List.of();
        }

        var face = faceAt(menu, mouseX, mouseY);
        if (face == null) return List.of();

        var lines = new ArrayList<Component>(2);
        lines.add(Component.translatable("gui.oritechaddonsone.proxy.face", faceName(face)));

        // the face name and whether it proxies anything - nothing else, so the tooltip stays a one glance
        // answer (which slot it proxies is shown on the configuration page, not here)
        if (menu.isProxyFaceConfigured(face)) {
            lines.add(Component.translatable("gui.oritechaddonsone.proxy.face.configured"));
        } else {
            lines.add(Component.translatable("gui.oritechaddonsone.proxy.face.unconfigured"));
        }
        return lines;
    }

    // ------------------------------------------------------------------ helpers

    /** The face whose picker is open for this menu, or {@code null}. */
    @Nullable
    private static Direction openFace(ExtensionAddonMenu menu) {
        for (var face : Direction.values()) {
            if (ProxyPickerState.isOpen(menu.position(), face)) return face;
        }
        return null;
    }

    /**
     * Cell of every face inside the net, rotated so the cell the model calls the port face is where the
     * player sees it: for a standing addon the net shows its front first, a flat one starts at the up face.
     */
    private static Map<Direction, int[]> cells(ExtensionAddonMenu menu) {
        var steps = rotationSteps(menu);
        var cells = new EnumMap<Direction, int[]>(Direction.class);

        for (var face : Direction.values()) {
            var cell = NET_CELLS.get(face);
            var x = cell[0];
            var y = cell[1];
            for (int i = 0; i < steps; i++) {
                // rotate the cross a quarter turn clockwise around its centre (1,1)
                var rotatedX = 1 - (y - 1);
                var rotatedY = 1 + (x - 1);
                x = rotatedX;
                y = rotatedY;
            }
            cells.put(face, new int[]{x, y});
        }
        return cells;
    }

    /** Quarter turns the net is rotated by, so the port face of the model lands on the net's front cell. */
    private static int rotationSteps(ExtensionAddonMenu menu) {
        var state = menu.addonBlockState();
        if (state == null) return 0;

        if (property(state, BlockStateProperties.FACING) != null) {
            // the wireless dock is a full cube rotated to its facing
            return steps(state.getValue(BlockStateProperties.FACING));
        }

        if (property(state, ExtensionAddonBlock.PLACEMENT) != null
                && property(state, ExtensionAddonBlock.HORIZONTAL_FACING) != null) {
            // the flat addon shows its port on the up face, which the net puts above the front
            if (state.getValue(ExtensionAddonBlock.PLACEMENT) != ExtensionAddonBlock.Placement.VERTICAL) return 0;
            return steps(state.getValue(ExtensionAddonBlock.HORIZONTAL_FACING));
        }
        return 0;
    }

    private static int steps(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Value of a block state property, or {@code null} while the state does not carry it. */
    @Nullable
    private static <T extends Comparable<T>> T property(BlockState state, Property<T> property) {
        return state.hasProperty(property) ? state.getValue(property) : null;
    }

    /**
     * The face under the mouse, or {@code null} while it is not on the net. The mouse position is panel
     * relative, so this reads the very same panel relative rectangles {@link #drawFace} draws.
     */
    @Nullable
    private static Direction faceAt(ExtensionAddonMenu menu, double mouseX, double mouseY) {
        var cells = cells(menu);
        for (var face : Direction.values()) {
            double x = localFaceX(cells, face);
            double y = localFaceY(cells, face);
            if (mouseX >= x && mouseX < x + FACE && mouseY >= y && mouseY < y + FACE) return face;
        }
        return null;
    }

    /** Translation key of a face name, e.g. {@code gui.oritechaddonsone.proxy.side.north}. */
    private static Component faceName(Direction face) {
        return Component.translatable("gui.oritechaddonsone.proxy.side." + face.getName());
    }

    /** The item drawn as the configuration page's icon: the block this menu belongs to. */
    private static ItemStack icon(ExtensionAddonMenu menu) {
        var block = menu.addonBlock();
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    /** Sends a packet to the server; the page is client only, so this is the one place that talks back. */
    private static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
