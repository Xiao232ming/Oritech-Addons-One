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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

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
 *     <li>a left click on an unconfigured face opens the slot picker of that face (the machine's GUI slots,
 *     exactly like Oritech's proxy screen draws them),</li>
 *     <li>a right click on any face - or a left click on a configured one - removes the binding again,</li>
 *     <li>the bottom right corner shows the counter {@code 可配置数: x/x} / {@code Configurable: x/x}:
 *     configured faces out of the number of stored inventory proxy addons, which is the maximum.</li>
 * </ul>
 * The binding itself is stored on the block entity and applied by the server, so a configured face really
 * proxies the machine's inventory to pipes, hoppers and other mods.
 */
public final class ItemProxyAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "proxy";

    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;

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

    /** Size of one face of the net, in pixels. 18 keeps the whole net inside the free part of the panel. */
    private static final int FACE = 18;
    /** Left edge of the net, in panel space. */
    private static final int NET_X = 6;
    /** Top edge of the net, in panel space. */
    private static final int NET_Y = 6;

    /** X the counter's text ends at, in panel space (the reserved slot's frame ends at 168). */
    private static final int COUNTER_RIGHT = ExtensionAddonLayout.RESERVED_SLOT_X + 16;
    /** Y of the counter line, in panel space: the panel's top right corner. */
    private static final int COUNTER_Y = 5;

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

    /** Draws the "Configurable: x/x" counter in the bottom right corner of the panel. */
    private void drawCounter(AddonPageContext context, GuiGraphics graphics, int configured, int maximum) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("gui.oritechaddonsone.proxy.counter", configured, maximum).getString();

        graphics.drawString(font, text, context.left() + COUNTER_RIGHT - font.width(text),
                context.top() + context.panelHeight() - 12, AddonPanelStyle.PANEL_TEXT, false);
    }

    /**
     * Draws the slot picker of the open face: the machine's GUI slots, where Oritech's own inventory proxy
     * screen puts them, plus the item that is currently in each of them.
     */
    private void drawPicker(AddonPageContext context, GuiGraphics graphics, ExtensionAddonMenu menu, Direction face) {
        var slots = ProxyPickerState.layout(menu.position(), face);
        var font = Minecraft.getInstance().font;
        int panelWidth = context.panelWidth();
        int panelHeight = context.panelHeight();

        // a dark backdrop over the page, so the picker reads as a modal step
        graphics.fill(context.left() + 2, context.top() + 2, context.left() + panelWidth - 2,
                context.top() + panelHeight - 2, 0xD0000000);

        if (slots == null) {
            centered(graphics, font, panelWidth, panelHeight,
                    Component.translatable("gui.oritechaddonsone.proxy.picker.loading"));
            return;
        }
        if (slots.isEmpty()) {
            centered(graphics, font, panelWidth, panelHeight,
                    Component.translatable("gui.oritechaddonsone.proxy.picker.no_machine"));
            return;
        }

        var origin = pickerOrigin(menu, slots, panelWidth, panelHeight);
        var selected = menu.proxySlotOf(face);
        var inventory = displayedInventory(menu);

        for (var slot : slots) {
            int x = context.left() + origin[0] + slot[1];
            int y = context.top() + origin[1] + slot[2];
            AddonPanelStyle.drawSlot(graphics, x - 1, y - 1);

            if (inventory != null && slot[0] >= 0 && slot[0] < inventory.getContainerSize()) {
                var stack = inventory.getItem(slot[0]);
                if (!stack.isEmpty()) graphics.renderItem(stack, x, y);
            }

            if (selected != null && selected == slot[0]) {
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x552ECC71);
                graphics.fill(x - 1, y - 1, x + 17, y, 0xFF2ECC71);
            }
        }

        centered(graphics, font, panelWidth, panelHeight - 10,
                Component.translatable("gui.oritechaddonsone.proxy.picker.hint", faceName(face)));
    }

    /** One centred line of text. */
    private static void centered(GuiGraphics graphics, net.minecraft.client.gui.Font font, int panelWidth,
            int panelHeight, Component text) {
        var string = text.getString();
        graphics.drawString(font, string, (panelWidth - font.width(string)) / 2, panelHeight / 2,
                AddonPanelStyle.PANEL_LIGHT, false);
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

    /** A click while the picker is open: on a slot it binds the face, anywhere else it closes the picker. */
    private boolean handlePickerClick(AddonPageContext context, ExtensionAddonMenu menu, Direction face,
            double mouseX, double mouseY, int button) {
        if (button == 1) {
            ProxyPickerState.close();
            return true;
        }

        var slots = ProxyPickerState.layout(menu.position(), face);
        if (slots != null && !slots.isEmpty()) {
            var origin = pickerOrigin(menu, slots, context.panelWidth(), context.panelHeight());
            for (var slot : slots) {
                if (mouseX >= origin[0] + slot[1] && mouseX < origin[0] + slot[1] + 16
                        && mouseY >= origin[1] + slot[2] && mouseY < origin[1] + slot[2] + 16) {
                    ProxyPickerState.close();
                    send(new ProxyNetworking.BindFace(menu.position(), ProxyNetworking.faceIndex(face), slot[0]));
                    return true;
                }
            }
        }

        // a click on the dark area behind the picker closes it
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

            var origin = pickerOrigin(menu, slots, context.panelWidth(), context.panelHeight());
            for (var slot : slots) {
                if (mouseX >= origin[0] + slot[1] && mouseX < origin[0] + slot[1] + 16
                        && mouseY >= origin[1] + slot[2] && mouseY < origin[1] + slot[2] + 16) {
                    return List.of(Component.translatable("gui.oritechaddonsone.proxy.picker.slot", slot[0]));
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
     * Panel relative origin of the picker: the machine's GUI slots keep their own layout, but the whole
     * group is centred inside the panel (Oritech's proxy screen uses them at their original position, which
     * fits its own 176x100 panel - ours is placed so it never covers a plugin slot frame).
     */
    private static int[] pickerOrigin(ExtensionAddonMenu menu, List<int[]> slots, int panelWidth, int panelHeight) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (var slot : slots) {
            minX = Math.min(minX, slot[1]);
            minY = Math.min(minY, slot[2]);
            maxX = Math.max(maxX, slot[1] + 18);
            maxY = Math.max(maxY, slot[2] + 18);
        }

        var width = maxX - minX;
        var height = maxY - minY;
        return new int[]{Math.max(4, (panelWidth - width) / 2) - minX,
                Math.max(4, (panelHeight - 16 - height) / 2) - minY};
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

    /** Sends a packet to the server; the page is client only, so this is the one place that talks back. */
    private static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
