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

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.fluid.FluidApi;
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
 * <p>
 * Both plugins count from two places and those two totals add up: every plugin stored inside one of this
 * mod's addons (through {@link AddonBonusSource}) and every <b>placed</b> plugin block that the machine's
 * own scan found next to it (see {@link #placedPluginAt}). A position is never both, so no plugin is
 * counted twice, and a placed plugin needs no block entity of this mod - Oritech's plain
 * {@code AddonBlockEntity} is enough, which is exactly why it is matched by block identity.
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
     * Tanks this mod has enlarged, per machine. Needed because a tank that was enlarged by the previous
     * pass but is not part of the machine any more (a broken multiblock, a machine that swapped its tank
     * object, a removed tank addon) has to lose the bonus again - and with it the content above the
     * capacity it then has. The map is weak, so nothing is kept alive.
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
            var bonus = bonusAt(level, pos);
            if (bonus == null) continue;

            slots += bonus.slots();
            capacity += bonus.capacity();
            counted.add(pos);
        }

        // The wireless docks are not always part of the addon list yet when this runs (the runtime index
        // of a cold server is empty, and the machine's list is only written back during its own scan), so
        // they are read from their own index as well. A dock that is already in the list above is skipped
        // instead of counted twice.
        for (var pos : WirelessLinks.docksOf(level, controller.getPosForAddon())) {
            if (!counted.add(pos)) continue;
            var bonus = bonusAt(level, pos);
            if (bonus == null) continue;

            slots += bonus.slots();
            capacity += bonus.capacity();
        }

        return new Bonus(slots, capacity);
    }

    /** The bonus source of a connected addon, or {@code null} while that addon is unloaded or not ours. */
    private static AddonBonusSource bonusSourceAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AddonBonusSource source) return source;
        return null;
    }

    /**
     * The bonus of one connected addon position, or {@code null} if that position holds nothing of this
     * mod. Two sources are asked, in this order:
     * <ol>
     *     <li>one of this mod's addon block entities ({@link AddonBonusSource}), which reports what is
     *     stored in its plugin slots,</li>
     *     <li>one of this mod's plugin blocks standing there as a real block, which counts as one plugin
     *     each (see {@link #placedPluginAt}).</li>
     * </ol>
     * A position can only ever be one of the two, so a plugin is counted exactly once.
     */
    private static Bonus bonusAt(Level level, BlockPos pos) {
        var source = bonusSourceAt(level, pos);
        if (source != null) {
            return new Bonus(source.oritechaddonsone$itemSlotBonus(), source.oritechaddonsone$fluidCapacityBonus());
        }
        return placedPluginAt(level, pos);
    }

    /**
     * The bonus of the <b>placed</b> plugin block at that position, or {@code null} if there is none.
     * <p>
     * A warehouse or tank addon that stands in an addon slot of the machine instead of lying inside one
     * of this mod's addons is a plain Oritech {@code AddonBlockEntity} and therefore invisible to
     * {@link AddonBonusSource}; it is recognised by block identity instead. A position whose block entity
     * is one of our addons is never a placed plugin, which is what keeps the two sources from being
     * counted twice.
     */
    private static Bonus placedPluginAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AddonBonusSource) return null;

        var block = level.getBlockState(pos).getBlock();
        if (block == OritechAddonsOne.WAREHOUSE_ADDON.get()) {
            return new Bonus(AddonStorageBonus.SLOTS_PER_WAREHOUSE_ADDON, 0L);
        }
        if (block == OritechAddonsOne.TANK_ADDON.get()) {
            return new Bonus(0, AddonStorageBonus.CAPACITY_PER_TANK_ADDON);
        }
        return null;
    }

    private static void apply(MachineAddonController controller, Bonus bonus) {
        applyToInventory(controller, bonus.slots());
        applyToFluidStorages(controller, bonus.capacity());
    }

    /**
     * Sets the item slot bonus of the machine's inventory and clamps what it holds to the new limit.
     * Oritech's machine inventory is a {@code SimpleInventoryStorage} (the machine's
     * {@code FilteringInventory}), whose slot limit the mixin raises; a machine that keeps its items
     * somewhere else simply stays at the vanilla limit.
     * <p>
     * The clamp is the item counterpart of the one the tanks get: {@code setItem} no longer shrinks a
     * stack that was written while the bonus was active (that is what makes the raised limit visible on
     * a client, which never sees the bonus), so the slots have to be brought back to the limit when a
     * warehouse addon is removed.
     */
    private static void applyToInventory(MachineAddonController controller, int slots) {
        ItemApi.InventoryStorage inventory = controller.getInventoryForAddon();
        if (!(inventory instanceof AddonStorageBonus storage)) return;

        storage.oritechaddonsone$setSlotBonus(slots);
        storage.oritechaddonsone$clampToCapacity();
    }

    /**
     * Sets the fluid capacity bonus of every tank of the machine: the {@code FluidApi.FluidStorage}
     * instances found in its fields and everything its {@code FluidApi.BlockProvider} reports.
     * <p>
     * The tanks are matched through {@link AddonStorageBonus} rather than through a concrete Oritech
     * class, because a machine can keep its fluid in any of Oritech's tank classes - the cooler and the
     * refinery nodes use {@code SimpleFluidStorage}, the centrifuge, the refinery's own storage and the
     * boilers of the generators use {@code SimpleInOutFluidStorage}, and a module reports a
     * {@code DelegatingFluidStorage}. Every tank class this mod can enlarge implements the interface, so
     * a new machine with a known tank class is covered without touching this method.
     * <p>
     * The new total is written to every tank the machine reports, and only a tank that the previous pass
     * enlarged but that is <b>not</b> part of the machine any more gets its bonus taken back (a broken
     * multiblock, a tank addon that was removed) - together with
     * {@link AddonStorageBonus#oritechaddonsone$clampToCapacity()}, which deletes whatever the shrunken
     * capacity no longer holds.
     * <p>
     * The order matters and is the whole point of the map: dropping every known tank to "no bonus" first
     * and only then applying the new total would run the clamp with a capacity of zero in between.
     * {@code setCapacityBonus} is an <b>absolute</b> write, so the new total does not need the old one to
     * be reset first, and any content a tank addon made room for would be cut down to the capacity
     * Oritech built the tank with on every addon scan - which is exactly what a UI open is
     * ({@code UpgradableMachineBlock#useWithoutItem} -> {@code initAddons}).
     */
    private static void applyToFluidStorages(MachineAddonController controller, long capacity) {
        if (!(controller instanceof BlockEntity entity)) return;

        // Identity based on purpose: two different tanks of one machine must stay two entries.
        Set<AddonStorageBonus> touched = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var field : fluidFields(entity.getClass())) {
            try {
                var storage = apply(field.get(entity), capacity);
                if (storage != null) touched.add(storage);
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
                    var storage = apply(provider.getFluidStorage(direction), capacity);
                    if (storage != null) touched.add(storage);
                } catch (RuntimeException refused) {
                    // A machine may refuse a direction; that is not an error for us.
                }
            }
        }

        // A tank of the previous pass that is still there was just given the new total above; only the
        // ones the machine does not report any more have to be reset (and clamped) here.
        var previous = KNOWN_TANKS.put(entity, touched);
        if (previous == null) return;

        for (var holder : previous) {
            if (!touched.contains(holder)) clear(holder);
        }
    }

    /**
     * Applies the bonus to one tank and clamps its content to the new capacity.
     *
     * @return the tank that was enlarged, or {@code null} for everything this mod cannot enlarge: a
     * machine that reports no tank for a side, a delegating wrapper, an item-side tank, ...
     */
    @Nullable
    private static AddonStorageBonus apply(@Nullable Object tank, long capacity) {
        if (!(tank instanceof AddonStorageBonus holder)) return null;

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
     * <p>
     * The filter asks for Oritech's fluid storage base class, not for one concrete tank, so a machine
     * whose tank is an input/output pair or a wrapper is found as well; whether the object can actually
     * be enlarged is decided by {@link AddonStorageBonus} when the bonus is applied.
     */
    private static List<Field> fluidFields(Class<?> type) {
        var cached = FLUID_FIELDS.get(type);
        if (cached != null) return cached;

        List<Field> fields = new ArrayList<>();
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!FluidApi.FluidStorage.class.isAssignableFrom(field.getType())) continue;

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
