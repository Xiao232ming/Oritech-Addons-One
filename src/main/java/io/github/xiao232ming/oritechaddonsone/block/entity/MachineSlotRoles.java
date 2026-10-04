package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.util.ContainerSlotAssignment;
import rearth.oritech.util.ScreenProvider;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * The <b>role</b> of every slot of a machine inventory, i.e. whether Oritech itself considers a slot an
 * input, an output or neither. The transfer modes of this mod only make sense with that knowledge:
 * {@link TransferMode#INPUT} may put items into a machine's <em>input</em> slots only,
 * {@link TransferMode#OUTPUT} may take items out of its <em>output</em> slots only, and
 * {@link TransferMode#BOTH} is the two of them together - never an insert into an output slot and never an
 * extract from an input slot.
 * <p>
 * The roles are not invented here, they are read off the two places Oritech keeps them in:
 * <ul>
 *     <li>{@link MachineBlockEntity#getSlotAssignments()} - a {@link ContainerSlotAssignment} naming the
 *     input and the output range of the machine's own storage. It is the same object that machine's
 *     {@code getExternalAccess()} view filters with (an insert into an output slot and an extract from an
 *     input slot both answer {@code 0} there) and that its recipe handling uses to consume inputs and fill
 *     outputs, so it is the authority for every machine built on {@link MachineBlockEntity},</li>
 *     <li>{@link ScreenProvider#getGuiSlots()} - every {@code GuiSlot} carries an {@code output} flag, which
 *     is what Oritech's own screen turns into an output-only menu slot
 *     ({@code OritechScreenHandler#addMachineSlot}). This is the fallback for the machine controllers that
 *     are not {@link MachineBlockEntity}s and therefore have no slot assignment to ask, and it is exactly
 *     the list the Item Proxy page already reads its slot layout from.</li>
 * </ul>
 * <b>Unknown stays permissive.</b> A slot neither source names is reported as
 * {@link TransferMode#BOTH}: it is the slot's own {@code isValid} that decides, which is today's behaviour,
 * and this way a machine whose roles cannot be established keeps working instead of losing half of its
 * transfer capability to a guess.
 * <p>
 * <b>Resolved once per machine inventory and cached by position.</b> A pipe asks its neighbours several
 * times per tick, and the role list is an array walk, so the answer is remembered per position - and
 * recomputed as soon as the block entity there or the size of its inventory changes, which is what a
 * machine broken and replaced by another one looks like. The roles themselves cannot change while a
 * machine lives: both sources derive them from the machine's own (fixed) slot layout.
 */
public final class MachineSlotRoles {

    /**
     * Role list of every machine position a handler ever asked about, mapped to the array itself. Bounded by
     * the number of machines that were ever reached through a configured face, and one small array each -
     * the entries are deliberately never dropped, exactly like the face handler caches of
     * {@link MachineFaceStorage} and {@link ExtenderFaceStorage}, and a stale entry can never be read
     * because the cache is validated against the block entity and the inventory size before it is used.
     */
    private static final Map<BlockPos, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private MachineSlotRoles() {
    }

    /** One remembered answer: the machine it was computed for, its inventory size and the roles. */
    private record CacheEntry(BlockEntity machine, int size, TransferMode[] roles) {
    }

    /**
     * The role of every slot of the machine the given addon works on, as an array over the machine
     * inventory's own indices, or {@code null} while that machine cannot be resolved - the same resolution
     * {@link MachineFaceStorage#machineStorage(BlockEntity)} makes, so roles are only ever looked for on an
     * inventory that really exists.
     * <p>
     * A pipe asks its neighbours several times per tick and every single slot of a transfer asks again, so a
     * valid answer is served from the ready cache without touching the world at all; a block that is not
     * known yet, or whose answer no longer fits the machine standing there, is resolved and remembered.
     */
    @Nullable
    public static TransferMode[] of(BlockEntity owner) {
        var machinePos = MachineFaceStorage.machinePos(owner);
        var level = MachineFaceStorage.machineLevel(owner);
        if (machinePos == null || level == null) return null;

        var machine = level.getBlockEntity(machinePos);
        if (machine == null) return null;

        var cached = CACHE.get(machinePos);
        if (cached != null && cached.machine() == machine) return cached.roles();

        var storage = MachineFaceStorage.machineStorageAt(level, machinePos);
        if (storage == null) return null;

        if (cached != null && cached.size() == storage.size()) return cached.roles();

        var roles = resolve(machine, storage.size());
        CACHE.put(machinePos.immutable(), new CacheEntry(machine, storage.size(), roles));
        return roles;
    }

    /**
     * True while items may be put <b>into</b> the given inventory slot: on an input slot always, on an
     * output slot never and on a slot of unknown role up to the inventory itself - see the class comment.
     * {@code true} is also the answer while there is no machine at all, so a caller that could not resolve
     * roles keeps behaving exactly as it did before.
     */
    public static boolean allowsInsertAt(@Nullable BlockEntity owner, int index) {
        var roles = owner == null ? null : of(owner);
        return roles == null || index < 0 || index >= roles.length || !roles[index].allowsExtract();
    }

    /**
     * True while items may be taken <b>out of</b> the given inventory slot: on an output slot always, on an
     * input slot never and on a slot of unknown role up to the inventory itself.
     */
    public static boolean allowsExtractAt(@Nullable BlockEntity owner, int index) {
        var roles = owner == null ? null : of(owner);
        return roles == null || index < 0 || index >= roles.length || !roles[index].allowsInsert();
    }

    /**
     * Role of every slot of the given machine, read from the most reliable source that machine offers.
     * {@code machine} is the block entity the inventory belongs to and {@code size} the size of that
     * inventory, which the arrays are built to.
     */
    private static TransferMode[] resolve(BlockEntity machine, int size) {
        var roles = new TransferMode[size];

        // the machine's own slot assignment first: it is what its external access view filters with and what
        // its recipe handling reads, so the roles come from the same place Oritech's own pipes are gated by
        if (machine instanceof MachineBlockEntity block) {
            fillFromAssignment(roles, block.getSlotAssignments());
        } else if (machine instanceof ScreenProvider screen) {
            // controllers built on something else keep the roles only in their GUI layout
            fillFromGuiSlots(roles, screen);
        }

        // everything neither source named keeps today's behaviour: the slot itself decides
        for (var index = 0; index < roles.length; index++) {
            if (roles[index] == null) roles[index] = TransferMode.BOTH;
        }

        // INFO on purpose: which slot of a machine counts as an input and which as an output decides every
        // transfer this mod makes, and the two sources it is read from are not always in agreement - a machine
        // whose roles come out wrong would move items into its product slots and empty its input slots, which
        // is impossible to tell apart from a bug in the movement itself without this line
        OritechAddonsOne.LOGGER.info("[transfer] slot roles of {} ({} slots): {}",
                machine.getBlockPos(), size, describe(roles));
        return roles;
    }

    /** The roles of a machine as one short string per slot, for the log line above. */
    private static String describe(TransferMode[] roles) {
        var text = new StringBuilder();
        for (var index = 0; index < roles.length; index++) {
            if (index > 0) text.append(' ');
            text.append(index).append(':').append(roles[index]);
        }
        return text.toString();
    }

    /**
     * Writes the input and the output range of the machine's slot assignment into the role list. A slot
     * outside both ranges - a machine may well have one - is left unset and therefore stays permissive.
     */
    private static void fillFromAssignment(TransferMode[] roles, ContainerSlotAssignment slots) {
        fill(roles, slots.inputStart(), slots.inputCount(), TransferMode.INPUT);
        fill(roles, slots.outputStart(), slots.outputCount(), TransferMode.OUTPUT);
    }

    /**
     * Writes the roles of the machine's GUI slots into the role list. This is how Oritech's own screen
     * decides which menu slot may be filled and which one may only be emptied, so it is the second best
     * answer for a machine whose inventory has no slot assignment to ask.
     */
    private static void fillFromGuiSlots(TransferMode[] roles, ScreenProvider screen) {
        for (var slot : screen.getGuiSlots()) {
            if (slot.index() < 0 || slot.index() >= roles.length) continue;
            roles[slot.index()] = slot.output() ? TransferMode.OUTPUT : TransferMode.INPUT;
        }
    }

    /** Marks {@code count} slots from {@code start} with the given role, ignoring anything out of range. */
    private static void fill(TransferMode[] roles, int start, int count, TransferMode role) {
        var end = Math.min(start + count, roles.length);
        for (var index = Math.max(start, 0); index < end; index++) {
            roles[index] = role;
        }
    }
}
