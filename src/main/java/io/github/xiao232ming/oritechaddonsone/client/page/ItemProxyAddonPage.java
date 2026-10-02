package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.widgets.ItemSlotWidget;
import rearth.oritech.api.screen.widgets.SurfaceWidget;
import rearth.oritech.util.ScreenProvider;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.client.FaceTextures;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;

/**
 * The Item Proxy page (物品代理): the six faces of this addon unfolded into a cube net, and the inventory
 * of the machine a face can be pointed at.
 * <p>
 * The page only exists while at least one Oritech inventory proxy addon is stored inside the block - the
 * registry adds it per menu - and it is a direct port of what that addon does on its own: Oritech's
 * {@code InventoryProxyAddonBlockEntity} offers an {@code ItemApi.BlockProvider} whose storage forwards to
 * the machine the addon is attached to while only one of its slots is usable, and its screen lets the
 * player pick that slot from the machine's GUI slots. Here the "screen" is this page, the "one slot" is one
 * per <b>face</b>, so the net doubles as the face selector.
 * <ul>
 *     <li>the net is drawn from the block's own per-face textures (see {@link FaceTextures}),</li>
 *     <li>a left click on an unconfigured face opens the configuration page of that face, which is
 *     Oritech's own inventory proxy page: the machine's GUI slots as framed cells, the same prompt and the
 *     same click-to-select interaction (see {@link #drawPicker}),</li>
 *     <li>a right click on any face - or a left click on a configured one - removes the binding again,</li>
 *     <li>the panel's bottom right shows the counter {@code 可配置数: x/x} / {@code Configurable: x/x}:
 *     configured faces out of the number of stored inventory proxy addons, which is the maximum. It sits in
 *     the free band the layout keeps below the hotbar, right aligned to
 *     {@link ExtensionAddonLayout#counterRight()}, so it is off the inventory slots and off the tab
 *     strip.</li>
 * </ul>
 * The binding itself is stored on the block entity and applied by the server, so a configured face really
 * proxies the machine's inventory to pipes, hoppers and other mods.
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
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 100;
    /**
     * Y of Oritech's prompt line inside that panel ({@code InventoryProxyScreen} uses {@code 85}).
     */
    private static final int PROMPT_Y = 85;
    /** Frame drawn around the panel, so Oritech's light panel reads against our light grey body. */
    private static final int PANEL_FRAME = 2;
    /**
     * Size of Oritech's title icon ({@code OritechWidgetScreen#addTitle()} builds a {@code 28x28} widget)
     * and the padding it keeps inside it, which centres the 16x16 item.
     */
    private static final int ICON_SIZE = 28;
    private static final int ICON_PADDING = 3;
    /**
     * Top of the band the configuration page may use: inside the panel's two pixel light bevel with a
     * little room, so even the panel's own two pixel frame stays on the panel body.
     */
    private static final int TOP_BAND = ExtensionAddonLayout.TOP_BAND + 2;

    /**
     * Icon of the tab: a chest front with a latch and a keyhole
     * ({@code oritechaddonsone:textures/gui/item_proxy_tab.png}, 16x16).
     * <p>
     * Vanilla has no standalone chest-front sprite (the chest is drawn by a block entity renderer from a
     * 64x64 atlas, and the "chest front" most players picture is the Minecraft Bedrock GUI texture that
     * does not exist in this jar), and Oritech's own inventory proxy addon texture is a 1.5x1.5 pixel
     * palette patch that reads as plain orange at icon size. The icon was therefore composed by hand in the
     * vanilla chest colours - wooden planks, a darker lid seam, the metal latch with the keyhole - which is
     * the "chest front including the lock" the tab is meant to be recognised by, and it is the same size as
     * the existing wireless tab icon.
     */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/gui/item_proxy_tab.png");

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
    public ResourceLocation icon() {
        return ICON;
    }

    @Override
    public List<Component> tooltip() {
        return List.of(label(), Component.translatable(LABEL_KEY + ".tooltip"));
    }

    /**
     * The panel has to reach past the counter, which is the lowest thing this page draws: the counter sits
     * in the free band below the hotbar and the panel's bottom border ends
     * {@link ExtensionAddonLayout#PAGE_BOTTOM_BAND} pixels under it. Every other page draws less and asks
     * for a shorter panel, which is what removes the empty band they used to show.
     */
    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        return layout.proxyPageHeight();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(AddonPageContext context, GuiGraphics graphics, float partialTick) {
        var menu = context.menu();
        var textures = FaceTextures.of(menu.addonBlock(), menu.addonBlockState());
        var cells = cells(menu);

        for (var face : Direction.values()) {
            drawFace(context, graphics, face, cells, textures, menu.isProxyFaceConfigured(face));
        }

        drawCounter(context, graphics, menu.configuredProxyFaces(), menu.inventoryProxyCount());

        var openFace = openFace(menu);
        if (openFace != null) drawPicker(context, graphics, menu, openFace);
    }

    /** Draws one face of the net, with its frame and its "configured" marker. */
    private void drawFace(AddonPageContext context, GuiGraphics graphics, Direction face, Map<Direction, int[]> cells,
            FaceTextures textures, boolean isConfigured) {
        int x = context.left() + NET_X + cells.get(face)[0] * FACE;
        int y = context.top() + NET_Y + cells.get(face)[1] * FACE;

        var texture = textures.face(face);
        if (texture.flipVertically()) {
            // the flat addon models sample the side textures from the lower half of their texture; the net
            // draws the full 16x16 sprite, so the flip is a vertical mirror around the face's own centre
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + FACE, 0f);
            graphics.pose().scale(1f, -1f, 1f);
            graphics.blit(texture.texture(), 0, 0, FACE, FACE, 0f, 0f, 16, 16, 16, 16);
            graphics.pose().popPose();
        } else {
            graphics.blit(texture.texture(), x, y, FACE, FACE, 0f, 0f, 16, 16, 16, 16);
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
     * Draws the "Configurable: x/x" counter in the panel's bottom right corner.
     * <p>
     * The panel keeps a free band below the hotbar row for exactly this line
     * ({@link ExtensionAddonLayout#counterY()}), so the counter sits off the player inventory slots, and it
     * is right aligned to {@link ExtensionAddonLayout#counterRight()} - the free strip right of the
     * inventory and {@value ExtensionAddonLayout#COUNTER_MARGIN} pixels left of the panel's border, hence
     * clear of the tab strip, which only overlaps the panel by
     * {@link ExtensionAddonLayout#TAB_OVERLAP} pixels.
     */
    private void drawCounter(AddonPageContext context, GuiGraphics graphics, int configured, int maximum) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("gui.oritechaddonsone.proxy.counter", configured, maximum).getString();
        var layout = context.layout();

        graphics.drawString(font, text, context.left() + layout.counterRight() - font.width(text),
                context.top() + layout.counterY(), AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * Draws the slot picker of the open face.
     * <p>
     * This is Oritech's own inventory proxy configuration page, painted where its own screen would be: a
     * 176x100 {@linkplain OritechSurface#PANEL bedrock panel} (the nine patch Oritech itself fills its
     * widgets with - see {@link SurfaceWidget}), a framed cell per
     * {@linkplain ScreenProvider#getGuiSlots() GUI slot} of the machine ({@link ItemSlotWidget}, again
     * Oritech's own widget, so the frames are pixel for pixel the same), the same prompt
     * {@code tooltip.oritech.addon_proxy_select} in Oritech's dark label colour, and the block's item
     * above the panel as its title icon - the same three elements, in the same places and sizes, that
     * {@code rearth.oritech.client.ui.InventoryProxyScreen} builds for its own menu.
     * <p>
     * The one thing that cannot be reused is Oritech's {@code InventoryProxyScreenHandler}: it holds the
     * screen provider and the position of <em>one</em> proxy addon block and sends that block's
     * {@code target_slot} on a click, while this page configures <em>one face per click</em> of our own
     * block and must keep our binding packets, cap and fail-safe. The page therefore repaints Oritech's
     * page with Oritech's classes and handles the click itself, which is what makes a face bind the slot
     * the player clicked.
     * <p>
     * While this is open it covers the net and the counter of the page: a modal step, closed by a click on
     * a slot that binds it, and by a click anywhere else (or the right mouse button) - the same "the slots
     * are the only controls" interaction Oritech's own screen has. The panel is centred in the drawn panel
     * and never resized, so the machine's slot layout stays Oritech's own.
     */
    private void drawPicker(AddonPageContext context, GuiGraphics graphics, ExtensionAddonMenu menu, Direction face) {
        var slots = ProxyPickerState.layout(menu.position(), face);
        var font = Minecraft.getInstance().font;
        var placed = place(context, slots == null ? List.of() : slots);

        // a dark backdrop over the drawn panel, so the picker reads as a modal step and neither the net
        // nor the counter behind it shows through
        graphics.fill(context.left(), context.top(), context.left() + context.panelWidth(),
                context.top() + placed.backdropBottom(), 0xD0000000);

        drawPanel(graphics, placed);

        if (slots == null) {
            centered(graphics, font, context, placed, Component.translatable("gui.oritechaddonsone.proxy.picker.loading"));
            return;
        }
        if (slots.isEmpty()) {
            centered(graphics, font, context, placed, Component.translatable("gui.oritechaddonsone.proxy.picker.no_machine"));
            return;
        }

        var inventory = displayedInventory(menu);
        var selected = menu.proxySlotOf(face);

        // the whole panel is drawn in Oritech's coordinate system: one translate, then every child at the
        // position InventoryProxyScreen uses for it
        graphics.pose().pushPose();
        graphics.pose().translate(context.left() + placed.innerX(), context.top() + placed.innerY(), 0f);

        for (var slot : slots) {
            int x = slot[1];
            int y = slot[2];

            // Oritech's own slot widget paints the frame at the slot's position
            var frame = new ItemSlotWidget(x, y);
            frame.render(graphics, x, y, 0f);

            if (inventory != null && slot[0] >= 0 && slot[0] < inventory.getContainerSize()) {
                var stack = inventory.getItem(slot[0]);
                if (!stack.isEmpty()) graphics.renderItem(stack, x, y);
            }

            // the slot this face is bound to right now, marked like Oritech marks the selected one
            if (selected != null && selected == slot[0]) {
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x552ECC71);
                graphics.fill(x - 1, y - 1, x + 17, y, 0xFF2ECC71);
            }
        }

        graphics.pose().popPose();

        // Oritech's own prompt, its own dark label colour, and the block's item above the panel
        prompt(graphics, font, context, placed, Component.translatable(PROMPT_KEY), placed.promptY());
        header(graphics, context, placed, face, font);
    }

    /** One centred line of text at the given Y inside the panel (Oritech's dark label colour). */
    private static void prompt(GuiGraphics graphics, net.minecraft.client.gui.Font font, AddonPageContext context,
            Placed placed, Component text, int y) {
        var string = text.getString();
        graphics.drawString(font, string, context.left() + placed.innerX() + (PANEL_WIDTH - font.width(string)) / 2,
                context.top() + y, AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * The header of the configuration page: the addon's own item as an icon with the face it is being
     * configured for next to it. Oritech puts the same icon in the same place - {@code 28x28}, with three
     * pixels of padding, centred above the panel by {@code OritechWidgetScreen#addTitle()}.
     */
    private static void header(GuiGraphics graphics, AddonPageContext context, Placed placed, Direction face,
            net.minecraft.client.gui.Font font) {
        int left = context.left() + placed.iconX();
        int top = context.top() + placed.iconY();

        // Oritech's title icon is drawn on a 28x28 PANEL surface with three pixels of padding, so the 16x16
        // item ends up centred in it and the surface is the widget's own nine patch
        new SurfaceWidget(left, top, ICON_SIZE, ICON_SIZE, OritechSurface.PANEL)
                .withPadding(Insets.of(0, ICON_PADDING, ICON_PADDING, ICON_PADDING))
                .render(graphics, 0, 0, 0f);

        graphics.renderItem(icon(context.menu()), left + ICON_PADDING, top + ICON_PADDING);

        var text = faceName(face);
        graphics.drawString(font, text, left + ICON_SIZE + 4, top + (ICON_SIZE - 8) / 2,
                AddonPanelStyle.PANEL_TEXT, false);
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public boolean mouseClicked(AddonPageContext context, double mouseX, double mouseY, int button) {
        var menu = context.menu();
        var openFace = openFace(menu);

        // while the picker is open every click belongs to it: either on a slot of the machine or outside,
        // which closes it again
        if (openFace != null) return handlePickerClick(context, menu, openFace, mouseX, mouseY, button);

        var face = faceAt(menu, mouseX, mouseY);
        if (face == null) return false;

        // right click, or a left click on an already configured face: stop proxying on that face
        if (button == 1 || menu.isProxyFaceConfigured(face)) {
            if (menu.isProxyFaceConfigured(face)) {
                ProxyPickerState.close();
                send(new ProxyNetworking.ClearFace(menu.position(), ProxyNetworking.faceIndex(face)));
            }
            return true;
        }

        // a face can only be configured while there is a free inventory proxy addon for it
        if (menu.configuredProxyFaces() >= menu.inventoryProxyCount()) return true;

        ProxyPickerState.open(menu.position(), face);
        send(new ProxyNetworking.RequestPicker(menu.position(), ProxyNetworking.faceIndex(face)));
        return true;
    }

    /**
     * A click while the configuration page is open. A click on one of the machine's slots binds the face
     * that is being configured to it - the same click, on the same cell, that Oritech's own proxy screen
     * turns into {@code setTargetSlot} - and any other click closes the page without changing anything.
     */
    private boolean handlePickerClick(AddonPageContext context, ExtensionAddonMenu menu, Direction face,
            double mouseX, double mouseY, int button) {
        if (button == 1) {
            ProxyPickerState.close();
            return true;
        }

        var slots = ProxyPickerState.layout(menu.position(), face);
        var placed = place(context, slots == null ? List.of() : slots);

        if (slots != null && !slots.isEmpty()) {
            for (var slot : slots) {
                double x = placed.innerX() + slot[1];
                double y = placed.innerY() + slot[2];
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    ProxyPickerState.close();
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
            var placed = place(context, slots == null ? List.of() : slots);

            if (slots == null || slots.isEmpty()) return List.of();

            for (var slot : slots) {
                double x = placed.innerX() + slot[1];
                double y = placed.innerY() + slot[2];
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    // the slot index and the face it is about to be bound to, so the click is unambiguous
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

        var configured = menu.proxySlotOf(face);
        if (configured == null) {
            lines.add(Component.translatable("gui.oritechaddonsone.proxy.face.unconfigured"));
        } else {
            lines.add(Component.translatable("gui.oritechaddonsone.proxy.face.configured", configured));
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
            return switch (state.getValue(BlockStateProperties.FACING)) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
        }

        if (property(state, ExtensionAddonBlock.PLACEMENT) != null
                && property(state, ExtensionAddonBlock.HORIZONTAL_FACING) != null) {
            // the flat addon shows its port on the up face, which the net puts above the front
            if (state.getValue(ExtensionAddonBlock.PLACEMENT) != ExtensionAddonBlock.Placement.VERTICAL) return 0;
            return switch (state.getValue(ExtensionAddonBlock.HORIZONTAL_FACING)) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
        }
        return 0;
    }

    /** Value of a block state property, or {@code null} while the state does not carry it. */
    @Nullable
    private static <T extends Comparable<T>> T property(net.minecraft.world.level.block.state.BlockState state,
            Property<T> property) {
        return state.hasProperty(property) ? state.getValue(property) : null;
    }

    /** The face under the mouse, or {@code null} while it is not on the net. */
    @Nullable
    private static Direction faceAt(ExtensionAddonMenu menu, double mouseX, double mouseY) {
        var cells = cells(menu);
        for (var face : Direction.values()) {
            double x = NET_X + cells.get(face)[0] * FACE;
            double y = NET_Y + cells.get(face)[1] * FACE;
            if (mouseX >= x && mouseX < x + FACE && mouseY >= y && mouseY < y + FACE) return face;
        }
        return null;
    }

    /** Translation key of a face name, e.g. {@code gui.oritechaddonsone.proxy.side.north}. */
    private static Component faceName(Direction face) {
        return Component.translatable("gui.oritechaddonsone.proxy.side." + face.getName());
    }

    /**
     * Places Oritech's configuration page inside the panel of this page.
     * <p>
     * Oritech's own screen is {@code 176x100}, so that is the panel reproduced here, and the machine's GUI
     * slots are drawn at the coordinates {@link ScreenProvider#getGuiSlots()} gives - the same coordinates
     * Oritech passes to its {@code ItemSlotWidget}s. The panel is centred horizontally in ours (which is
     * {@link ExtensionAddonLayout#RIGHT_GUTTER} pixels wider) and vertically in the drawn panel, and it is
     * never resized, so the machine's slot layout is exactly Oritech's.
     * <p>
     * Oritech's panel is taller than the room the layout keeps above the player inventory on a block with
     * one or two plugin rows (about 89 pixels). The panel therefore reaches over the top of that inventory
     * while it is open - which is what a modal step over the whole page is supposed to do. It works because
     * the screen paints the page first and the inventory afterwards, and the menu marks the player's slots
     * inactive while this page covers the panel (see {@code ExtensionAddonMenu#setPlayerSlotsActive}), so
     * neither their frames nor their items are drawn over the panel: the page reads as a full page, exactly
     * like Oritech's own inventory proxy screen, which shows the machine's slots and nothing of the
     * player's.
     */
    private static Placed place(AddonPageContext context, List<int[]> slots) {
        var layout = context.layout();

        int innerX = Math.max(0, (context.panelWidth() - PANEL_WIDTH) / 2);
        int top = TOP_BAND;
        // centred in the drawn panel, but never far enough down for the panel's frame to reach the player
        // inventory (the player's slots are hidden while this is open, but the panel should not cover the
        // whole inventory either)
        int innerY = Math.max(top, Math.min((context.panelHeight() - PANEL_HEIGHT) / 2,
                layout.playerRowsY() - 2 - PANEL_FRAME - PANEL_HEIGHT));

        int iconY = Math.max(0, innerY - ICON_SIZE);
        int iconX = innerX + (PANEL_WIDTH - ICON_SIZE) / 2;

        // The page has no control of its own beyond the machine's slots, exactly like Oritech's screen: a
        // click on a slot binds it, a click anywhere else (or the right mouse button) closes the page. So
        // the only geometry it adds is the panel and the icon above it.
        return new Placed(innerX, innerY, iconX, iconY, context.panelHeight(),
                iconY + ICON_SIZE + PROMPT_Y);
    }

    /**
     * The bedrock panel of the configuration page: Oritech's own {@link OritechSurface#PANEL} nine patch,
     * drawn twice - a darker copy as the frame around it, exactly the way Oritech's widget screens stack
     * their {@code SurfaceWidget}s.
     */
    private static void drawPanel(GuiGraphics graphics, Placed placed) {
        graphics.pose().pushPose();
        graphics.pose().translate(placed.innerX(), placed.innerY(), 0f);

        new SurfaceWidget(-PANEL_FRAME, -PANEL_FRAME, PANEL_WIDTH + 2 * PANEL_FRAME, PANEL_HEIGHT + 2 * PANEL_FRAME,
                OritechSurface.PANEL_DARK).render(graphics, 0, 0, 0f);
        new SurfaceWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT, OritechSurface.PANEL).render(graphics, 0, 0, 0f);

        graphics.pose().popPose();
    }

    /** One line of the configuration page's own text, centred in Oritech's panel. */
    private static void centered(GuiGraphics graphics, net.minecraft.client.gui.Font font, AddonPageContext context,
            Placed placed, Component text) {
        var string = text.getString();
        graphics.drawString(font, string, context.left() + placed.innerX() + (PANEL_WIDTH - font.width(string)) / 2,
                context.top() + placed.innerY() + (PANEL_HEIGHT - 8) / 2, AddonPanelStyle.PANEL_TEXT, false);
    }

    /** The machine inventory the picker previews items from, or {@code null} while it is out of reach. */
    @Nullable
    private static Container displayedInventory(ExtensionAddonMenu menu) {
        var blockEntity = menu.blockEntity();
        if (blockEntity == null || blockEntity.getLevel() == null) return null;

        var target = blockEntity.connectedMachinePos();
        if (target == null || !blockEntity.getLevel().isLoaded(target)) return null;

        return blockEntity.getLevel().getBlockEntity(target) instanceof ScreenProvider screen
                ? screen.getDisplayedInventory()
                : null;
    }

    /** The item drawn as the configuration page's icon: the block this menu belongs to. */
    private static ItemStack icon(ExtensionAddonMenu menu) {
        var block = menu.addonBlock();
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    /**
     * Geometry of Oritech's configuration page inside our panel, in panel space: the panel itself
     * ({@link #PANEL_WIDTH} x {@link #PANEL_HEIGHT} at {@code innerX/innerY}), the header icon above it,
     * the bottom of the backdrop that covers the page behind it, and the prompt line inside the panel.
     */
    private record Placed(int innerX, int innerY, int iconX, int iconY, int backdropBottom, int promptY) {
    }

    /** Sends a packet to the server; the page is client only, so this is the one place that talks back. */
    private static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
