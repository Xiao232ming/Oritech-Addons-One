package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The per-face transfer settings of one Extension Addon / Wireless Extension Dock: which direction a face
 * moves items in (see {@link TransferMode}) and whether it moves them <b>by itself</b> (automation, see
 * {@code ExtensionAddonBlockEntity#serverTickTransfer}) or only offers them to pipes, hoppers and other mods.
 * <p>
 * Both settings live in <b>one int per face</b> - the two low bits are the mode's ordinal, the third bit is
 * the automation flag - so the menu's container data and the packet that sets a face keep carrying exactly one
 * value per face. Values written by a version without automation are mode ordinals, which read back as "no
 * automation" and therefore keep working.
 * <p>
 * A face that has no entry transfers nothing. Like {@code ProxyFaceBindings} this is written into the block
 * entity's save data as one int per face in {@link Direction#values()} order: the server is the authority and
 * the pages only draw what it reports.
 */
public final class TransferFaceModes {

    private static final String TAG = "transfer_faces";

    /** Bit of the packed value that means "this face moves items on its own". */
    private static final int AUTOMATION_BIT = 0b100;
    /** Bits of the packed value that hold the mode's ordinal. */
    private static final int MODE_MASK = 0b011;

    /** One packed value per configured face. */
    private final Map<Direction, Integer> faces = new EnumMap<>(Direction.class);

    /** Packs a mode and the automation flag into the one int the menu and the packet carry per face. */
    public static int pack(TransferMode mode, boolean automation) {
        var value = (mode == null ? TransferMode.NONE : mode).ordinal() & MODE_MASK;
        return automation ? value | AUTOMATION_BIT : value;
    }

    /** The mode of a packed value; unknown values read as {@link TransferMode#NONE}. */
    public static TransferMode modeOf(int packed) {
        return TransferMode.byOrdinal(packed & MODE_MASK);
    }

    /** True while the automation flag of a packed value is set. */
    public static boolean automationOf(int packed) {
        return (packed & AUTOMATION_BIT) != 0;
    }

    /** True while a packed value configures a direction at all. */
    public static boolean isConfigured(int packed) {
        return modeOf(packed) != TransferMode.NONE;
    }

    /**
     * Mode of the given face; {@link TransferMode#NONE} while that face transfers nothing. Never
     * {@code null}, so a caller can always ask what a face does.
     */
    public TransferMode modeOf(@Nullable Direction face) {
        return modeOf(packed(face));
    }

    /** True while the given face moves items on its own. Always false for a face without a mode. */
    public boolean automationOf(@Nullable Direction face) {
        return automationOf(packed(face));
    }

    /** True while the given face transfers items in either direction. */
    public boolean isConfigured(@Nullable Direction face) {
        return modeOf(face) != TransferMode.NONE;
    }

    /** Number of faces that transfer something, i.e. the "x" of the page's counter. */
    public int configuredFaces() {
        return faces.size();
    }

    /** True while no face transfers anything. */
    public boolean isEmpty() {
        return faces.isEmpty();
    }

    /**
     * The packed value of the given face, {@code 0} while it transfers nothing - the single place the map is
     * read, so mode and automation can never disagree about a face.
     */
    private int packed(@Nullable Direction face) {
        return face == null ? 0 : faces.getOrDefault(face, 0);
    }

    /**
     * Sets what the given face does; {@link TransferMode#NONE} removes the entry again, and with it the
     * automation flag - a face without a direction has nothing to move.
     */
    public void set(Direction face, TransferMode mode, boolean automation) {
        if (face == null || mode == null || mode == TransferMode.NONE) {
            faces.remove(face);
            return;
        }

        var value = pack(mode, automation);
        if (value == 0) {
            faces.remove(face);
            return;
        }
        faces.put(face, value);
    }

    /** Drops every setting, which is what happens when the last transfer addon is taken out. */
    public void clear() {
        faces.clear();
    }

    /** Writes the settings into the block entity's save data. */
    public void save(ValueOutput output) {
        var directions = Direction.values();
        var values = new int[directions.length];
        for (int index = 0; index < directions.length; index++) {
            values[index] = packed(directions[index]);
        }
        output.putIntArray(TAG, values);
    }

    /** Reads the settings from the block entity's save data; unknown bits read as "not configured". */
    public void load(ValueInput input) {
        faces.clear();

        var values = input.getIntArray(TAG).orElse(null);
        if (values == null) return;

        var directions = Direction.values();
        for (int index = 0; index < values.length && index < directions.length; index++) {
            if (isConfigured(values[index])) faces.put(directions[index], values[index]);
        }
    }

    /** The configured faces and their packed values, for diagnostics. */
    public Map<Direction, Integer> entries() {
        return Collections.unmodifiableMap(faces);
    }
}
