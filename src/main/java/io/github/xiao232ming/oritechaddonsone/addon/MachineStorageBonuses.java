package io.github.xiao232ming.oritechaddonsone.addon;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.fluid.containers.SimpleFluidStorage;
import rearth.oritech.api.item.ItemApi;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinks;

/**
 * Collects the storage bonuses of this mod's addons and applies them to the machine they are attached to.
 * <p>
 * Oritech's addon scan ({@code MachineAddonController#initAddons}) resets the machine's addon data and
 * rebuilds it from the connected addons, but that data only carries the six stats of
 * {@code BaseAddonData}. The item slot bonus and the fluid capacity bonus of the warehouse / tank addons
 * are therefore applied from here, at the two ends of that scan:
 * <ul>
 *     <li>before the scan ({@code gatherAddonStats}) the <b>last known</b> bonus is put back on the
 *     machine's storages. The scan walks through the machine's own update paths - most notably
 *     {@code updateEnergyContainer}, which ends in {@code amount = min(amount, capacity)} - and those
 *     must see the capacity the player actually built, otherwise a machine that recomputes its addons
 *     would silently drop everything above the unmodified capacity,</li>
 *     <li>after the scan ({@code initAddons}) the number is recomputed from the addons that are now
 *     attached - plus the wireless docks linked to the machine, which Oritech's own scan does not know
 *     about - and stored, so the next scan and every later plugin change uses it.</li>
 * </ul>
 * A "warehouse addon" adds {@link AddonStorageBonus#SLOTS_PER_WAREHOUSE_ADDON} to <b>every</b> item slot
 * of the machine and a "tank addon" adds {@link AddonStorageBonus#CAPACITY_PER_TANK_ADDON} mB to
 * <b>every</b> tank; stacked plugins count once per item.
 */
public final class MachineStorageBonuses {

    /**
     * Last bonus applied to a machine, keyed by the machine block entity itself. Weak, so a machine that
     * is removed from the world does not stay in memory, and identity based, because two machines must
     * never share an entry.
     */
    private static final Map<MachineAddonController, Bonus> CACHED = new WeakHashMap<>();

    /**
     * Fluid storage fields per block entity class. The scan is reflective because Oritech keeps its tanks
     * in plain (mostly {@code public final}) fields on the machine block entity - the refinery's
     * {@code nodeA} / {@code nodeB} for example - and there is no interface that lists them.
     */
    private static final Map<Class<?>, List<Field>> FLUID_FIELDS = new WeakHashMap<>();

    /**
     * Tanks this mod has enlarged, per machine. Needed because a tank that is handed out but not found
     * again (a broken multiblock, a machine that swapped its tank object) must get its bonus back, and
     * because the previous reference has to be reset before a new total is applied - otherwise the
     * results of several scans would accumulate. Both maps are weak, so nothing is kept alive.
     */
    private static final Map<BlockEntity, Set<AddonStorageBonus>> KNOWN_TANKS = new WeakHashMap<>();

    /** Directions the fluid provider is asked for, {@code null} first (it is the "no side" variant). */
    private static final Direction[] DIRECTIONS = {
        null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** The two bonuses of one machine. */
    private record Bonus(int slots, long capacity) {
    }

    private MachineStorageBonuses() {
    }

    // ------------------------------------------------------------------ applying

    /**
     * Puts the cached bonus of the machine back on its storages. Called at the start of the machine's
     * addon scan, before anything can clamp against the unmodified capacity.
     */
    public static void applyCached(MachineAddonController controller) {
        var level = controller.getWorldForAddon();
        if (level == null || level.isClientSide()) return;

        var bonus = CACHED.get(controller);
        if (bonus == null) return;

        apply(controller, bonus);
    }

    /**
     * Recomputes the bonus from the addons currently attached to the machine and applies it. Called at
     * the end of the machine's addon scan, where {@code getConnectedAddons} holds both the wired addons
     * Oritech found and the wireless docks this mod merged in.
     */
    public static void refresh(MachineAddonController controller) {
        var level = controller.getWorldForAddon();
        if (level == null || level.isClientSide()) return;

        var bonus = sum(controller, level);
        CACHED.put(controller, bonus);
        apply(controller, bonus);
    }

    /** Sums the bonuses of every addon attached to the machine. */
    private static Bonus sum(MachineAddonController controller, Level level) {
        var slots = 0;
        var capacity = 0L;
        var counted = new HashSet<BlockPos>();

        for (var pos : controller.getConnectedAddons()) {
            var source = bonusSourceAt(level, pos);
            if (source == null) continue;

            counted.add(pos);
            slots += source.oritechaddonsone$itemSlotBonus();
            capacity += source.oritechaddonsone$fluidCapacityBonus();
        }

        // The wireless docks are not always part of the addon list yet when this runs (the runtime index
        // of a cold server is empty, and the machine's list is only written back during its own scan), so
        // they are read from their own index as well. A dock that is already in the list above is skipped
        // instead of counted twice.
        for (var pos : WirelessLinks.docksOf(level, controller.getPosForAddon())) {
            if (!counted.add(pos)) continue;
            var source = bonusSourceAt(level, pos);
            if (source == null) continue;

            slots += source.oritechaddonsone$itemSlotBonus();
            capacity += source.oritechaddonsone$fluidCapacityBonus();
        }

        return new Bonus(slots, capacity);
    }

    /** The bonus source of a connected addon, or {@code null} while that addon is unloaded or not ours. */
    private static AddonBonusSource bonusSourceAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AddonBonusSource source) return source;
        return null;
    }

    private static void apply(MachineAddonController controller, Bonus bonus) {
        applyToInventory(controller, bonus.slots());
        applyToFluidStorages(controller, bonus.capacity());
    }

    /**
     * Sets the item slot bonus of the machine's inventory. Oritech's machine inventory is a
     * {@code SimpleInventoryStorage} (the machine's {@code FilteringInventory}), whose slot limit the
     * mixin raises; a machine that keeps its items somewhere else simply stays at the vanilla limit.
     */
    private static void applyToInventory(MachineAddonController controller, int slots) {
        ItemApi.InventoryStorage inventory = controller.getInventoryForAddon();
        if (inventory instanceof AddonStorageBonus storage) {
            storage.oritechaddonsone$setSlotBonus(slots);
        }
    }

    /**
     * Sets the fluid capacity bonus of every tank of the machine: the {@code SimpleFluidStorage}
     * instances found in its fields and everything its {@code FluidApi.BlockProvider} reports.
     * <p>
     * The tanks of the previous pass are reset first, so this is a full recomputation and not an
     * accumulation. A tank that is no longer found gets its bonus taken back as well, which matters when
     * a machine drops a tank addon: the excess fluid is then clamped to the shrunken capacity instead of
     * staying above it. The excess is deleted, never spilled (see
     * {@link AddonStorageBonus#oritechaddonsone$clampToCapacity()}).
     */
    private static void applyToFluidStorages(MachineAddonController controller, long capacity) {
        if (!(controller instanceof BlockEntity entity)) return;

        var previous = KNOWN_TANKS.remove(entity);
        if (previous != null) {
            for (var holder : previous) clear(holder);
        }

        // Identity based on purpose: two different tanks of one machine must stay two entries.
        Set<AddonStorageBonus> touched = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var field : fluidFields(entity.getClass())) {
            try {
                if (field.get(entity) instanceof SimpleFluidStorage storage) {
                    touched.add(apply(storage, capacity));
                }
            } catch (IllegalAccessException | RuntimeException inaccessible) {
                // An inaccessible field must not break the machine; the other tanks still get their bonus.
                OritechAddonsOne.LOGGER.debug("Could not read fluid storage field {} of {}",
                        field.getName(), entity.getClass().getName(), inaccessible);
            }
        }

        if (entity instanceof FluidApi.BlockProvider provider) {
            // The provider is asked for every side plus null, which is the full set a machine can report.
            for (var direction : DIRECTIONS) {
                try {
                    if (provider.getFluidStorage(direction) instanceof SimpleFluidStorage storage) {
                        touched.add(apply(storage, capacity));
                    }
                } catch (RuntimeException refused) {
                    // A machine may refuse a direction; that is not an error for us.
                }
            }
        }

        KNOWN_TANKS.put(entity, touched);
    }

    /** Applies the bonus to one tank and clamps its content to the new capacity. */
    private static AddonStorageBonus apply(SimpleFluidStorage storage, long capacity) {
        var holder = (AddonStorageBonus) storage;
        holder.oritechaddonsone$setCapacityBonus(capacity);
        holder.oritechaddonsone$clampToCapacity();
        return holder;
    }

    /** Takes the bonus back from a tank that is no longer part of the machine. */
    private static void clear(AddonStorageBonus holder) {
        holder.oritechaddonsone$setCapacityBonus(0L);
        holder.oritechaddonsone$clampToCapacity();
    }

    // ------------------------------------------------------------------ reflective tank lookup

    /**
     * All fluid storage fields of a machine class, inherited ones included. The result is cached per
     * class, because the scan runs on every addon change and on every refresh of a wireless dock.
     */
    private static List<Field> fluidFields(Class<?> type) {
        var cached = FLUID_FIELDS.get(type);
        if (cached != null) return cached;

        List<Field> fields = new ArrayList<>();
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!SimpleFluidStorage.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                } catch (RuntimeException notAccessible) {
                    // A module or a security manager can refuse this; skip the field instead of failing.
                    continue;
                }
                fields.add(field);
            }
        }

        var result = List.copyOf(fields);
        FLUID_FIELDS.put(type, result);
        return result;
    }
}
