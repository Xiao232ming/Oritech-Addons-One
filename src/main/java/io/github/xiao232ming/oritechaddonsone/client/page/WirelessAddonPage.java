package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * Both variants carry the second line of this page's state, the force load state of that machine's chunk,
 * as a badge in the top right corner of the panel (see {@link #drawForceLoadStatus}) - it is the same value
 * for both, and it is kept out of the info lines so it does not repeat what they say. The badge is green
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
    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/gui/wireless_tab.png");

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

    /**
     * X the force load badge's text ends at, in panel space.
     * <p>
     * The badge lives in the top right corner of this page, i.e. in the free strip above the first line of
     * content and right of where the machine name starts. That strip ends where the reserved slot's frame
     * ends - the frame is drawn from {@code RESERVED_SLOT_X - 1 = 151} and is 18 pixels wide, so its right
     * edge is at {@code 168} - and the tab strip reaches {@code TAB_OVERLAP = 4} pixels into the panel,
     * i.e. down to {@code 172}. Right aligning the text to {@code 168} therefore leaves a pixel of the
     * panel visible before the frame ends, four before the tab overlap starts, and keeps the badge clear
     * of both.
     */
    private static final int CHUNK_BADGE_RIGHT = ExtensionAddonLayout.RESERVED_SLOT_X + 16;

    /**
     * Y of the force load badge's text, in panel space: the first row inside the panel's two pixel light
     * bevel, so the text covers {@code y = 5 .. 14} - below the bevel, above the first slot frame (drawn
     * from {@code y = 17}) and level with the first tab, which only starts four pixels further right.
     */
    private static final int CHUNK_BADGE_Y = 5;

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

    /**
     * The lowest content of this page is the reserved slot's frame (level with the first plugin row) and
     * the info lines under it. Nothing else is drawn here - the page has no net and no counter - so the
     * panel ends just below its text instead of showing the band the Item Proxy page reserves.
     */
    @Override
    public int drawnHeight(ExtensionAddonLayout layout) {
        int lines = FIRST_LINE_Y + 2 * LINE_HEIGHT;
        return layout.pageHeight(Math.max(ExtensionAddonLayout.RESERVED_SLOT_Y + ExtensionAddonLayout.SLOT_SIZE, lines));
    }

    /**
     * Draws the force load badge, the info lines and the frame of the reserved slot. The slot's item is
     * drawn by the screen like every other slot's item; the frame is painted here, one pixel above and left
     * of the slot, exactly like the plugin page does it for its fields.
     * <p>
     * While the slot is empty the chunk anchor's own icon is drawn into it, dimmed behind the same veil the
     * plugin page uses for its fixed slots - so the cell reads as "this is where the anchor goes" instead
     * of being an unexplained empty field. The page draws in the background layer on this version, so a
     * real anchor inserted later simply covers the hint.
     */
    @Override
    public void render(AddonPageContext context, GuiGraphicsExtractor graphics, float partialTick) {
        var font = Minecraft.getInstance().font;
        var menu = context.menu();
        var lines = infoLines(menu);

        drawForceLoadStatus(graphics, font, context, menu);

        for (int line = 0; line < lines.size(); line++) {
            var text = fit(font, lines.get(line).text().getString());
            graphics.text(font, text, context.left() + TEXT_X, context.top() + FIRST_LINE_Y + line * LINE_HEIGHT,
                    lines.get(line).color(), false);
        }

        int slotX = context.left() + ExtensionAddonLayout.RESERVED_SLOT_X;
        int slotY = context.top() + ExtensionAddonLayout.RESERVED_SLOT_Y;
        AddonPanelStyle.drawSlot(graphics, slotX - 1, slotY - 1);

        drawReservedSlotHint(menu, graphics, slotX, slotY);
    }

    /**
     * Draws the anchor icon of the empty reserved slot, exactly like the plugin page draws the reference
     * icons of its fixed slots. Nothing is drawn while the slot holds something: the frame and the item are
     * the cell then.
     */
    private static void drawReservedSlotHint(ExtensionAddonMenu menu, GuiGraphicsExtractor graphics, int slotX,
            int slotY) {
        if (!menu.getSlot(menu.reservedSlot()).getItem().isEmpty()) return;

        graphics.item(new ItemStack(OritechAddonsOne.CHUNK_ANCHOR_ADDON_ITEM.get()), slotX, slotY);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, AddonPanelStyle.HINT_VEIL);
    }

    /**
     * The line of the badge. Its wording is still the plain "chunk loaded / not loaded" pair, but the value
     * behind it is the force load state of the connected machine's chunk - see
     * {@link ExtensionAddonMenu#targetChunkForceLoaded()}.
     */
    public static Component forceLoadStatus(ExtensionAddonMenu menu) {
        return Component.translatable(menu.targetChunkForceLoaded() ? CHUNK_FORCE_LOADED_YES_KEY : CHUNK_FORCE_LOADED_NO_KEY);
    }

    /**
     * Draws the force load state of the connected machine's chunk as one right aligned line in the panel's
     * top right corner - for the wired addon and the dock alike, because both work on a machine.
     * <p>
     * The value comes from {@link ExtensionAddonMenu#targetChunkForceLoaded()}, which the server resolves and
     * pushes over a container data slot whenever it changes, so the badge is live without the client
     * looking anything up. Green means the machine's chunk is kept loaded (vanilla {@code /forceload}, the
     * spawn area or another mod's force load), red means it is not (nothing is linked or claimed, or the
     * chunk is only loaded because a player is nearby). The colours and the plain (no shadow) text are the
     * panel's usual info text style; the text is right aligned to a fixed edge, so both states end in the
     * same place whatever their width.
     */
    private static void drawForceLoadStatus(GuiGraphicsExtractor graphics, Font font, AddonPageContext context,
            ExtensionAddonMenu menu) {
        var forceLoaded = menu.targetChunkForceLoaded();
        var text = forceLoadStatus(menu).getString();

        graphics.text(font, text, context.left() + CHUNK_BADGE_RIGHT - font.width(text),
                context.top() + CHUNK_BADGE_Y,
                forceLoaded ? AddonPanelStyle.PANEL_TEXT_GOOD : AddonPanelStyle.PANEL_TEXT_BAD, false);
    }

    /**
     * The lines of the page, each with the colour it is drawn in.
     * <p>
     * A wired addon has one line - the machine it is attached to - while a dock reports its whole binding.
     * A dock that is not linked, and a wired addon no machine ever claimed, show the same "not linked"
     * line the under-panel line used to show. The force load state is not one of these lines: it is drawn
     * as the badge in the panel's top right corner, so it stays out of the reading order of the text block.
     */
    private static List<Line> infoLines(ExtensionAddonMenu menu) {
        var nameKey = menu.linkedMachineNameKey();

        if (!menu.wireless()) {
            // a wired addon: only the name of the machine it is attached to, no coordinates
            if (nameKey == null) return List.of(new Line(Component.translatable(UNLINKED_KEY), AddonPanelStyle.PANEL_TEXT_DIM));
            return List.of(new Line(Component.translatable(MACHINE_KEY, Component.translatable(nameKey)),
                    AddonPanelStyle.PANEL_TEXT));
        }

        var machine = menu.linkedMachine();
        if (machine == null) {
            return List.of(new Line(Component.translatable(UNLINKED_KEY), AddonPanelStyle.PANEL_TEXT_DIM));
        }

        // the dock also shows where that machine stands
        var name = nameKey == null ? Component.translatable(UNKNOWN_KEY) : Component.translatable(nameKey);
        var lines = new ArrayList<Line>(2);
        lines.add(new Line(Component.translatable(MACHINE_KEY, name),
                nameKey == null ? AddonPanelStyle.PANEL_TEXT_DIM : AddonPanelStyle.PANEL_TEXT));
        lines.add(new Line(Component.translatable(COORDS_KEY, machine.getX(), machine.getY(), machine.getZ()),
                AddonPanelStyle.PANEL_TEXT));
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
