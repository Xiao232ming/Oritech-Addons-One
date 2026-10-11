package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The wireless page: which machine this addon works on, whether that machine's chunk is force loaded, and
 * the reserved single item slot.
 * <p>
 * A wireless extension dock shows the whole binding - the machine's name and the coordinates it is bound
 * to - while a wired extension addon shows the name of the machine it is attached to and nothing else,
 * because it has no link of its own. The binding used to be a single line under the panel, drawn for every
 * page; it now lives here only.
 * <p>
 * Both variants carry the force load state of that machine's chunk as the <b>last line under the binding</b>
 * - for a dock that is the line right below the coordinates it is bound to (see {@link #infoLines}),
 * because the state belongs to that very machine and reads as the tail of its address. The line is green
 * only while something keeps that chunk loaded on purpose (vanilla {@code /forceload}, the spawn area or
 * another mod's force load) and red while the chunk is unloaded or only loaded because a player is nearby,
 * because only a chunk that is kept loaded works while nobody is around.
 * <p>
 * Everything the page shows is read from {@link ExtensionAddonMenu}, which resolved it on the server, so
 * the name is right even while the client has the machine's chunk unloaded.
 */
public final class WirelessAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "wireless";

    /** Language key of the tab label, which is all the tab shows. */
    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;

    /** Language keys of the info lines and of the force load badge. */
    private static final String MACHINE_KEY = "gui.oritechaddonsone.wireless.machine_name";
    private static final String COORDS_KEY = "gui.oritechaddonsone.wireless.coords";
    private static final String CHUNK_FORCE_LOADED_YES_KEY = "gui.oritechaddonsone.wireless.chunk_force_loaded.yes";
    private static final String CHUNK_FORCE_LOADED_NO_KEY = "gui.oritechaddonsone.wireless.chunk_force_loaded.no";
    private static final String UNLINKED_KEY = "gui.oritechaddonsone.wireless.unlinked";
    private static final String UNKNOWN_KEY = "gui.oritechaddonsone.wireless.unknown";

    /**
     * Icon of the tab: this mod's own blue wifi symbol
     * ({@code oritechaddonsone:textures/gui/wireless_tab.png}, 16x16). See that file - it is generated
     * from three concentric arcs and a dot, drawn in two blues so it reads on both tab fills.
     */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/gui/wireless_tab.png");

    /** X of the info lines, in panel space. */
    private static final int TEXT_X = 8;
    /** Y of the first info line: level with the reserved slot and the first plugin row. */
    private static final int FIRST_LINE_Y = 18;
    /** Distance between two info lines. */
    private static final int LINE_HEIGHT = 10;
    /**
     * Width the info lines may use: the panel up to a small gap left of the reserved slot, so a long
     * machine name is cut off instead of being written over the slot.
     */
    private static final int TEXT_WIDTH = ExtensionAddonLayout.RESERVED_SLOT_X - TEXT_X - 6;
    /** Appended to a text that had to be cut off. */
    private static final String ELLIPSIS = "...";

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
     * The lowest content of this page is the reserved slot's frame (level with the first plugin row) and
     * the info lines under it - up to three of them, the machine's name, its coordinates and the force
     * load state of its chunk. Nothing else is drawn here - the page has no net and no counter - so the
     * panel ends just below its text instead of showing the band the Item Proxy page reserves.
     */
    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        int lines = FIRST_LINE_Y + 3 * LINE_HEIGHT;
        return layout.pageHeight(Math.max(ExtensionAddonLayout.RESERVED_SLOT_Y + ExtensionAddonLayout.SLOT_SIZE, lines));
    }

    /**
     * Draws the info lines and the frame of the reserved slot. The slot's item is drawn by the screen like
     * every other slot's item; the frame is painted here, one pixel above and left of the slot, exactly
     * like the plugin page does it for its fields.
     * <p>
     * While the slot is empty the chunk anchor's own icon is drawn into it, dimmed and behind a veil,
     * exactly like the plugin page draws the type III reference plugins - so the cell reads as "this is
     * where the anchor goes" instead of being an unexplained empty field.
     */
    @Override
    public void render(AddonPageContext context, GuiGraphics graphics, float partialTick,
            double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var menu = context.menu();
        var lines = infoLines(menu);

        for (int line = 0; line < lines.size(); line++) {
            var text = fit(font, lines.get(line).text().getString());
            graphics.drawString(font, text, context.left() + TEXT_X, context.top() + FIRST_LINE_Y + line * LINE_HEIGHT,
                    lines.get(line).color(), false);
        }

        AddonPanelStyle.drawSlot(graphics, context.left() + ExtensionAddonLayout.RESERVED_SLOT_X - 1,
                context.top() + ExtensionAddonLayout.RESERVED_SLOT_Y - 1);

        drawReservedSlotHint(menu, graphics, context);
    }

    /**
     * Draws the anchor icon of the empty reserved slot.
     * <p>
     * Same layering as the type III hints of {@link PluginAddonPage}: the icon is pushed back to
     * {@link AddonPanelStyle#HINT_ICON_Z} so the veil drawn at {@link AddonPanelStyle#HINT_VEIL_Z} covers
     * it, while an anchor that is really inserted is drawn by the screen at the usual item depth
     * ({@link AddonPanelStyle#ITEM_Z}) and covers the veil. Nothing is drawn while the slot holds
     * something: the frame and the item are the cell then.
     */
    private static void drawReservedSlotHint(ExtensionAddonMenu menu, GuiGraphics graphics, AddonPageContext context) {
        if (!menu.getSlot(menu.reservedSlot()).getItem().isEmpty()) return;

        int slotX = context.left() + ExtensionAddonLayout.RESERVED_SLOT_X;
        int slotY = context.top() + ExtensionAddonLayout.RESERVED_SLOT_Y;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, AddonPanelStyle.HINT_ICON_Z - AddonPanelStyle.ITEM_Z);
        graphics.renderItem(new ItemStack(OritechAddonsOne.CHUNK_ANCHOR_ADDON_ITEM.get()), slotX, slotY);
        graphics.pose().popPose();

        // renderItem flushes its own batch, so the veil lands on top of the icon and everything drawn
        // afterwards - the real item included - lands on top of the veil.
        graphics.fill(RenderType.guiOverlay(), slotX, slotY, slotX + 16, slotY + 16, AddonPanelStyle.HINT_VEIL_Z,
                AddonPanelStyle.HINT_VEIL);
        graphics.flush();
    }

    /**
     * The text of the force load line. Its wording is still the plain "chunk loaded / not loaded" pair, but
     * the value behind it is the force load state of the connected machine's chunk - see
     * {@link ExtensionAddonMenu#targetChunkForceLoaded()}.
     */
    public static Component forceLoadStatus(ExtensionAddonMenu menu) {
        return Component.translatable(menu.targetChunkForceLoaded() ? CHUNK_FORCE_LOADED_YES_KEY : CHUNK_FORCE_LOADED_NO_KEY);
    }

    /**
     * The force load state as the last info line of the page, in the green / red of the panel's own info
     * text.
     * <p>
     * The value comes from {@link ExtensionAddonMenu#targetChunkForceLoaded()}, which the server resolves and
     * pushes over a container data slot whenever it changes, so the line is live without the client looking
     * anything up. Green means the machine's chunk is kept loaded (vanilla {@code /forceload}, the spawn area
     * or another mod's force load), red means it is not (nothing is linked or claimed, or the chunk is only
     * loaded because a player is nearby).
     */
    private static Line forceLoadLine(ExtensionAddonMenu menu) {
        return new Line(forceLoadStatus(menu),
                menu.targetChunkForceLoaded() ? AddonPanelStyle.PANEL_TEXT_GOOD : AddonPanelStyle.PANEL_TEXT_BAD);
    }

    /**
     * The lines of the page, each with the colour it is drawn in.
     * <p>
     * A wired addon has the name of the machine it is attached to, a dock its whole binding. A dock that is
     * not linked, and a wired addon no machine ever claimed, show the same "not linked" line the under-panel
     * line used to show. The force load state of that machine's chunk is the <b>last</b> line of all of them,
     * so on a dock it sits right under the coordinates it belongs to.
     */
    private static List<Line> infoLines(ExtensionAddonMenu menu) {
        var nameKey = menu.linkedMachineNameKey();
        var lines = new ArrayList<Line>(3);

        if (!menu.wireless()) {
            // a wired addon: only the name of the machine it is attached to, no coordinates
            lines.add(nameKey == null
                    ? new Line(Component.translatable(UNLINKED_KEY), AddonPanelStyle.PANEL_TEXT_DIM)
                    : new Line(Component.translatable(MACHINE_KEY, Component.translatable(nameKey)),
                            AddonPanelStyle.PANEL_TEXT));
            lines.add(forceLoadLine(menu));
            return List.copyOf(lines);
        }

        var machine = menu.linkedMachine();
        if (machine == null) {
            lines.add(new Line(Component.translatable(UNLINKED_KEY), AddonPanelStyle.PANEL_TEXT_DIM));
            lines.add(forceLoadLine(menu));
            return List.copyOf(lines);
        }

        // the dock also shows where that machine stands, and the force load state under that address
        var name = nameKey == null ? Component.translatable(UNKNOWN_KEY) : Component.translatable(nameKey);
        lines.add(new Line(Component.translatable(MACHINE_KEY, name),
                nameKey == null ? AddonPanelStyle.PANEL_TEXT_DIM : AddonPanelStyle.PANEL_TEXT));
        lines.add(new Line(Component.translatable(COORDS_KEY, machine.getX(), machine.getY(), machine.getZ()),
                AddonPanelStyle.PANEL_TEXT));
        lines.add(forceLoadLine(menu));
        return List.copyOf(lines);
    }

    /**
     * Cuts a text off at the width the info lines may use, so a long machine name cannot be written over
     * the reserved slot.
     */
    private static String fit(Font font, String text) {
        if (font.width(text) <= TEXT_WIDTH) return text;

        return font.plainSubstrByWidth(text, TEXT_WIDTH - font.width(ELLIPSIS)) + ELLIPSIS;
    }

    /** One info line: its text and the colour it is drawn in. */
    private record Line(Component text, int color) {
    }
}
