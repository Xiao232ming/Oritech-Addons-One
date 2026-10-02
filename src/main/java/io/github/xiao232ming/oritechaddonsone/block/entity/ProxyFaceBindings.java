package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The per-face inventory proxy bindings of one Extension Addon / Wireless Extension Dock: which face of
 * the block proxies which slot of the machine this block works on.
 * <p>
 * A face that has no entry does not proxy anything. This is the one data structure behind the "configured
 * faces" counter of the Item Proxy page and behind the inventory the block offers to pipes and hoppers,
 * so the two can never disagree.
 * <p>
 * The bindings are written into the block entity's save data, i.e. the server is the authority; the page
 * only draws what the block entity reports. They are stored as one int per face in
 * {@link Direction#values()} order, with {@code -1} for "not configured", which is compact and forward
 * compatible: a face a later version adds simply reads as unconfigured.
 */
public final class ProxyFaceBindings {

    private static final String TAG = "proxy_faces";
    /** Value stored for a face that proxies nothing. */
    private static final int NONE = -1;

    /** One entry per configured face. */
    private final Map<Direction, Integer> slots = new EnumMap<>(Direction.class);

    /**
     * Slot of the machine inventory the given face proxies, or {@code null} while that face is not
     * configured. A negative slot is treated as "not configured" as well, so a value that somehow got
     * saved wrong can never reach the machine's inventory.
     */
    @Nullable
    public Integer slotOf(@Nullable Direction face) {
        if (face == null) return null;
        var slot = slots.get(face);
        return slot == null || slot < 0 ? null : slot;
    }

    /** True while the given face proxies a slot of the machine inventory. */
    public boolean isConfigured(@Nullable Direction face) {
        return slotOf(face) != null;
    }

    /** Number of configured faces, i.e. the "x" of the page's counter. */
    public int configuredFaces() {
        return slots.size();
    }

    /** True while no face is configured at all. */
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** The configured faces, in the enum order of {@link Direction} (stable for logs). */
    public Map<Direction, Integer> entries() {
        return Collections.unmodifiableMap(slots);
    }

    /** Points the given face at the given machine inventory slot. */
    public void bind(Direction face, int slot) {
        if (face == null || slot < 0) return;
        slots.put(face, slot);
    }

    /** Removes the binding of the given face; after this the face proxies nothing. */
    public void unbind(@Nullable Direction face) {
        if (face == null) return;
        slots.remove(face);
    }

    /** Drops every binding, which is what happens when the last inventory proxy addon is taken out. */
    public void clear() {
        slots.clear();
    }

    /** Writes the bindings into the block entity's save data. */
    public void save(ValueOutput output) {
        var faces = Direction.values();
        var values = new int[faces.length];
        for (int index = 0; index < faces.length; index++) {
            var slot = slots.get(faces[index]);
            values[index] = slot == null ? NONE : slot;
        }
        output.putIntArray(TAG, values);
    }

    /** Reads the bindings from the block entity's save data; negative entries mean "not configured". */
    public void load(ValueInput input) {
        slots.clear();

        var values = input.getIntArray(TAG).orElse(null);
        if (values == null) return;

        var faces = Direction.values();
        for (int index = 0; index < values.length && index < faces.length; index++) {
            if (values[index] >= 0) slots.put(faces[index], values[index]);
        }
    }
}
