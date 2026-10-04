package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.CellFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * What the server says each <b>cell-face</b> of a 传输插件's machine does, as the client page draws it.
 * <p>
 * <b>Why this is not a menu field.</b> The map is keyed by (cell, direction) and has no maximum: a 3x3x3 structure has
 * a hundred and sixty-two cell-faces, and Oritech's part lists are not bounded by six, so the fixed container data
 * slots that carry a setting per direction cannot carry this. The server sends the whole map instead
 * ({@code TransferNetworking.FaceModes}) and this class holds it, keyed by the plugin it belongs to - the same shape
 * as {@link TransferAddonState}, and the same reason: the packet handler is not the screen, and the screen is not
 * the only thing that can ask.
 * <p>
 * The client is a <b>mirror</b> here, never the authority: every entry in it came from the server, so what the page
 * draws and what the automation does cannot drift. A click does not write here - it sends a packet and waits for the
 * answer, which is what makes a refused configuration (a cell that is not part of the machine's structure)
 * visible on the page as "nothing happened".
 * <p>
 * It is dropped with the model when the GUI closes, so a page that is opened again starts from whatever the server
 * sends with the menu.
 */
public final class TransferFaceState {

    /** The map of every plugin the client currently knows about, keyed by the plugin's position. */
    private static final Map<BlockPos, CellFaceModes> MODES = new ConcurrentHashMap<>();

    private TransferFaceState() {
    }

    /** Replaces what the client knows about one plugin: the whole authoritative map. */
    public static void put(BlockPos pluginPos, List<CellFaceModes.Entry> entries) {
        var modes = new CellFaceModes();
        modes.setAll(entries);
        MODES.put(pluginPos.immutable(), modes);
    }

    /** Drops what the client knows about one plugin, called when its GUI closes. */
    public static void clear(BlockPos pluginPos) {
        MODES.remove(pluginPos);
    }

    /** Drops every map, called when a GUI closes. */
    public static void clear() {
        MODES.clear();
    }

    /** The map of one plugin, or {@code null} while the server has not sent one yet. */
    @Nullable
    public static CellFaceModes modes(BlockPos pluginPos) {
        return MODES.get(pluginPos);
    }

    /**
     * Mode of one face of one cell, {@link TransferMode#NONE} while the server has not sent a map yet or that
     * cell-face transfers nothing. Never {@code null}, so the page can always ask.
     */
    public static TransferMode modeOf(BlockPos pluginPos, Vec3i cell, @Nullable Direction face) {
        var modes = modes(pluginPos);
        return modes == null ? TransferMode.NONE : modes.modeOf(cell, face);
    }

    /** True while one face of one cell moves items by itself, as the server last reported it. */
    public static boolean automationOf(BlockPos pluginPos, Vec3i cell, @Nullable Direction face) {
        var modes = modes(pluginPos);
        return modes != null && modes.automationOf(cell, face);
    }

    /** Number of configured cell-faces the server last reported for one plugin. */
    public static int configuredFaces(BlockPos pluginPos) {
        var modes = modes(pluginPos);
        return modes == null ? 0 : modes.configuredFaces();
    }
}
