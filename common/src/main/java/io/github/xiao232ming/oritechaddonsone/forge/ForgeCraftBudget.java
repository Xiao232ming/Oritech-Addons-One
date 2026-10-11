package io.github.xiao232ming.oritechaddonsone.forge;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Allows the extra crafts an atomic forge's operation still owes, so it cannot turn one charge into as many
 * items as its output slot happens to fit.
 * <p>
 * Oritech repeats a craft once per processing chamber, but it bounds that repetition by the <b>output
 * space</b> and by the forge's buffer instead of by the chambers: {@code UpgradableMachineBlockEntity#craftItem}
 * keeps repeating while {@code canOutputRecipe} says yes, that check ends in {@code canAddToSlot} (the
 * slot's own maximum), and the forge re-fills its buffer every tick and crafts once per tick. Together that
 * let it work through <b>the whole input</b> without charging again: measured on a forge charged by a laser
 * with 96 processing chambers, the ingredients for 128 items became 128 items in one go instead of the
 * {@code 1 + 96} an operation may produce.
 * <p>
 * The chamber count is therefore the only thing that decides how much one operation produces. Room left in
 * the output slot is not a reason to produce more, and filling the output slot is not a reason to produce
 * less.
 * <p>
 * One operation is one tick's work - {@code MachineBlockEntity#serverTick} crafts once per tick - so each
 * tick's first craft is the base craft and every further craft in that tick is a chamber repetition. A tick
 * therefore gets {@code chambers} repetitions on top of its base craft, and {@link #open} hands exactly
 * those out. The allowance is fixed when the tick starts, because the lasers re-report their chamber count
 * while the forge works and an operation must not change size halfway through.
 * <p>
 * The state is per forge instance, and entries of removed machines are dropped on the next call for a live
 * one, so the map cannot grow with the world.
 */
public final class ForgeCraftBudget {

    /** Per forge: the tick the repetitions counted so far belong to, and how many are left. */
    private static final Map<BlockEntity, long[]> STATE = new WeakHashMap<>();

    private static final int TICK = 0;
    private static final int REMAINING = 1;

    private ForgeCraftBudget() {
    }

    /**
     * Opens the repetitions of a new tick: {@code chambers} extra crafts on top of the tick's base craft.
     * Does nothing within the same tick, so a forge that crafts several times in one tick keeps sharing the
     * allowance its tick started with.
     */
    public static void open(BlockEntity forge, long gameTime, int chambers) {
        STATE.entrySet().removeIf(entry -> entry.getKey().isRemoved());

        var state = STATE.get(forge);
        if (state == null || state[TICK] != gameTime) {
            STATE.put(forge, new long[]{ gameTime, Math.max(0, chambers) });
        }
    }

    /**
     * Takes one chamber repetition. Returns {@code true} while the operation still owes repetitions, which is
     * what the repetition loop is gated on.
     */
    public static boolean takeRepeat(BlockEntity forge, long gameTime) {
        var state = STATE.get(forge);
        if (state == null || state[TICK] != gameTime) return false;
        if (state[REMAINING] <= 0L) return false;

        state[REMAINING]--;
        return true;
    }

    /** Forgets the state of a machine that is not an atomic forge. */
    public static void forget(BlockEntity machine) {
        STATE.remove(machine);
    }
}
