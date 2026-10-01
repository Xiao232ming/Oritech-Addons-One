package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The wireless page: which machine this addon works on, whether that machine's chunk is loaded, and the
 * reserved single item slot.
 * <p>
 * A wireless extension dock shows the whole binding - the machine's name and the coordinates it is bound
 * to - while a wired extension addon shows the name of the machine it is attached to and nothing else,
 * because it has no link of its own. The binding used to be a single line under the panel, drawn for every
 * page; it now lives here only.
 * <p>
 * Both variants carry the second line of this page's state, the chunk status of that machine, as a badge
 * in the top right corner of the panel (see {@link #drawChunkStatus}) - it is the same value for both, and
 * it is kept out of the info lines so it does not repeat what they say. A dock only works while its
 * target's chunk is loaded, a wired addon is claimed by a machine in a loaded chunk, so the badge normally
 * reads "loaded" there and turns red as soon as the target is gone or its chunk is unloaded.
 * <p>
 * Everything the page shows is read from {@link ExtensionAddonMenu}, which resolved it on the server, so
 * the name is right even while the client has the machine's chunk unloaded.
 */
public final class WirelessAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "wireless";

    /** Language keys of the tab label, its tooltip and the hint line every tab carries. */
    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;
    private static final String TOOLTIP_KEY = LABEL_KEY + ".tooltip";
    private static final String HINT_KEY = LABEL_KEY + ".hint";

    /** Language keys of the info lines and of the chunk status badge. */
    private static final String MACHINE_KEY = "gui.oritechaddonsone.wireless.machine_name";
    private static final String COORDS_KEY = "gui.oritechaddonsone.wireless.coords";
    private static final String CHUNK_LOADED_YES_KEY = "gui.oritechaddonsone.wireless.chunk_loaded.yes";
    private static final String CHUNK_LOADED_NO_KEY = "gui.oritechaddonsone.wireless.chunk_loaded.no";
    private static final String UNLINKED_KEY = "gui.oritechaddonsone.wireless.unlinked";
    private static final String UNKNOWN_KEY = "gui.oritechaddonsone.wireless.unknown";
    private static final String SLOT_KEY = "gui.oritechaddonsone.wireless.reserved_slot";
    private static final String SLOT_HINT_KEY = SLOT_KEY + ".hint";

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
     * X the chunk status badge's text ends at, in panel space.
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
     * Y of the chunk status badge's text, in panel space: the first row inside the panel's two pixel light
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

    @Override
    public List<Component> tooltip() {
        return List.of(label(), Component.translatable(TOOLTIP_KEY), Component.translatable(HINT_KEY));
    }

    /**
     * Draws the chunk status badge, the info lines and the frame of the reserved slot. The slot's item is
     * drawn by the screen like every other slot's item; the frame is painted here, one pixel above and left
     * of the slot, exactly like the plugin page does it for its fields.
     */
    @Override
    public void render(AddonPageContext context, GuiGraphicsExtractor graphics, float partialTick) {
        var font = Minecraft.getInstance().font;
        var menu = context.menu();
        var lines = infoLines(menu);

        drawChunkStatus(graphics, font, context, menu);

        for (int line = 0; line < lines.size(); line++) {
            var text = fit(font, lines.get(line).text().getString());
            graphics.text(font, text, context.left() + TEXT_X, context.top() + FIRST_LINE_Y + line * LINE_HEIGHT,
                    lines.get(line).color(), false);
        }

        AddonPanelStyle.drawSlot(graphics, context.left() + ExtensionAddonLayout.RESERVED_SLOT_X - 1,
                context.top() + ExtensionAddonLayout.RESERVED_SLOT_Y - 1);
    }

    /**
     * The line of the badge: "chunk loaded" while the connected machine's chunk is loaded, "not loaded"
     * otherwise. Both variants answer it, see {@link ExtensionAddonMenu#targetChunkLoaded()}.
     */
    public static Component chunkStatus(ExtensionAddonMenu menu) {
        return Component.translatable(menu.targetChunkLoaded() ? CHUNK_LOADED_YES_KEY : CHUNK_LOADED_NO_KEY);
    }

    /**
     * Draws the chunk status of the connected machine as one right aligned line in the panel's top right
     * corner - for the wired addon and the dock alike, because both work on a machine.
     * <p>
     * The value comes from {@link ExtensionAddonMenu#targetChunkLoaded()}, which the server resolves and
     * pushes over a container data slot whenever it changes, so the badge is live without the client
     * looking anything up. Green means the machine's chunk is loaded, red means it is not (nothing is
     * linked or claimed, or the target's chunk is unloaded). The colours and the plain (no shadow) text
     * are the panel's usual info text style; the text is right aligned to a fixed edge, so both states end
     * in the same place whatever their width.
     */
    private static void drawChunkStatus(GuiGraphicsExtractor graphics, Font font, AddonPageContext context,
            ExtensionAddonMenu menu) {
        var loaded = menu.targetChunkLoaded();
        var text = chunkStatus(menu).getString();

        graphics.text(font, text, context.left() + CHUNK_BADGE_RIGHT - font.width(text),
                context.top() + CHUNK_BADGE_Y,
                loaded ? AddonPanelStyle.PANEL_TEXT_GOOD : AddonPanelStyle.PANEL_TEXT_BAD, false);
    }

    /** Tooltip of the reserved slot: what it is for, shown while the slot is empty (or hovered) too. */
    @Override
    public List<Component> tooltipAt(AddonPageContext context, double mouseX, double mouseY) {
        if (!isOverReservedSlot(mouseX, mouseY)) return List.of();

        return List.of(Component.translatable(SLOT_KEY), Component.translatable(SLOT_HINT_KEY));
    }

    /** True while the given panel relative position is inside the reserved slot's 16x16 item area. */
    private static boolean isOverReservedSlot(double mouseX, double mouseY) {
        return mouseX >= ExtensionAddonLayout.RESERVED_SLOT_X && mouseX < ExtensionAddonLayout.RESERVED_SLOT_X + 16
                && mouseY >= ExtensionAddonLayout.RESERVED_SLOT_Y && mouseY < ExtensionAddonLayout.RESERVED_SLOT_Y + 16;
    }

    /**
     * The lines of the page, each with the colour it is drawn in.
     * <p>
     * A wired addon has one line - the machine it is attached to - while a dock reports its whole binding.
     * A dock that is not linked, and a wired addon no machine ever claimed, show the same "not linked"
     * line the under-panel line used to show. The chunk status is not one of these lines: it is drawn as
     * the badge in the panel's top right corner, so it stays out of the reading order of the text block.
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
