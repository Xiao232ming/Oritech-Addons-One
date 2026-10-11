package io.github.xiao232ming.oritechaddonsone.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;
import rearth.oritech.init.OritechConfig;
import rearth.oritech.init.OritechStartupConfig;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.util.MachineAddonController;

/**
 * Feeds the processing chambers of the lasers that charge an atomic forge into the forge's addon data.
 * <p>
 * The forge itself has no addon slots, so its own addon data never carries chambers. Oritech's parallel
 * processing is driven by exactly that value - the upgradable machine repeats its craft once per chamber -
 * so writing the lasers' chambers there is all it takes for the forge to process several items per cycle,
 * and it also makes the panel show the same {@code 🥣: +N} line as every other machine.
 * <p>
 * Chambers of several addons on one laser are already summed by the laser's own addon data, and several
 * lasers add up here on top of that.
 */
public final class ForgeLaserChambers {

    private ForgeLaserChambers() {
    }

    /** Chambers every laser training on that forge contributes, in total. */
    public static int chambersOf(Level level, BlockPos forgePos) {
        int chambers = 0;
        for (var laserPos : LaserAimIndex.lasersOf(level, forgePos)) {
            if (!(level.getBlockEntity(laserPos) instanceof LaserArmBlockEntity laser)) continue;
            // Only lasers that really aim here count: the index is a snapshot and a laser may have moved on.
            if (!forgePos.equals(laser.getCurrentTarget())) continue;

            chambers += Math.max(0, laser.getBaseAddonData().extraChambers());
        }
        return chambers;
    }

    /** Writes the current chamber count into the forge's addon data, if it changed. */
    public static void refresh(Level level, BlockPos forgePos) {
        if (level == null || level.isClientSide() || forgePos == null) return;
        if (!(level.getBlockEntity(forgePos) instanceof AtomicForgeBlockEntity forge)) return;

        int chambers = chambersOf(level, forgePos);
        var data = forge.getBaseAddonData();
        if (data.extraChambers() == chambers) return;

        forge.setBaseAddonData(new MachineAddonController.BaseAddonData(data.speed(), data.efficiency(),
                data.energyBonusCapacity(), data.energyBonusTransfer(), chambers, data.maxBurstTicks()));
    }

    /**
     * Energy multiplier the processing chambers on the charging lasers cost, merged exactly like Oritech
     * merges the addons of one machine. A chamber addon is not only an extra item per cycle: it also has an
     * efficiency multiplier (1.5 by default, so +50% energy each), which every other machine pays through its
     * per tick energy use. The forge has to pay it as extra charge, because its per tick energy use already
     * is the recipe's whole cost.
     * <p>
     * What a chamber costs is decided by how many items it <b>really</b> produces, not by how many chambers
     * are installed: a laser with 96 chambers lets the forge craft 97 items per operation, but with
     * ingredients for 2 it produces 2 and may only be charged for those. {@code actualCrafts} therefore
     * carries the number of items this operation will produce - see {@link #craftsFromInput} - and the
     * installed chambers are only the <b>limit</b> that number can reach. Without ingredients to spare the
     * two are the same and nothing changes.
     * <p>
     * Oritech's own chamber addon is the only one in the game, and it always contributes exactly one chamber
     * with the configured multiplier, so counting the items is enough.
     *
     * @param chambers     chambers the lasers report, i.e. the operation's parallel limit
     * @param actualCrafts items this operation will really produce, already capped at {@code chambers}
     */
    public static float efficiencyFactor(int chambers, int actualCrafts) {
        int paid = effectiveCrafts(chambers, actualCrafts);
        if (paid <= 0) return 1.0F;

        float multiplier = OritechStartupConfig.chamberAddonEfficiency.get().floatValue();
        boolean additive = OritechConfig.additiveAddons.get();

        float efficiency = additive
                ? 1.0F + paid * (1.0F - multiplier)
                : (float) Math.pow(multiplier, paid);

        if (additive) {
            // Oritech's additive mode converts the accumulated value back the same way for every machine.
            float change = efficiency - 1.0F;
            efficiency = 1.0F / efficiency;
            if (change < 0.0F) efficiency = 1.0F + Math.abs(change);
        }
        return efficiency;
    }

    /**
     * Chambers the forge may be charged for: the items it will really produce, never more than the chambers
     * it has.
     * <p>
     * A chamber only costs its efficiency multiplier when it really produces an item. With ingredients for
     * two items and 96 chambers, the forge crafts two and is charged for two - charging all 96 would make it
     * pay for items that were never made.
     */
    private static int effectiveCrafts(int chambers, int actualCrafts) {
        if (chambers <= 0) return 0;
        return Math.min(chambers, Math.max(0, actualCrafts));
    }

    /**
     * How many items the given recipe can still be crafted from these inputs - the forge's base craft plus
     * its chamber repetitions, so the same number the operation will really produce.
     * <p>
     * Every craft consumes one item per ingredient, so the ingredients are consumed in a copy of the input
     * slots until one of them runs out, exactly the way {@code MachineBlockEntity#craftItem} takes them.
     */
    public static int craftsFromInput(OritechRecipe recipe, List<ItemStack> inputs, int limit) {
        if (recipe == null || inputs == null || inputs.isEmpty() || limit <= 0) return 0;

        var remaining = new ArrayList<ItemStack>(inputs.size());
        for (var stack : inputs) {
            remaining.add(stack.copy());
        }

        int crafts = 0;
        while (crafts < limit && consumeOneCraft(recipe, remaining)) {
            crafts++;
        }
        return crafts;
    }

    /** Takes one of every ingredient from the copy, or reports that the recipe cannot be crafted again. */
    private static boolean consumeOneCraft(OritechRecipe recipe, List<ItemStack> remaining) {
        var taken = new ArrayList<Integer>();

        for (var ingredient : recipe.getInputs()) {
            int found = -1;
            for (int slot = 0; slot < remaining.size(); slot++) {
                var stack = remaining.get(slot);
                if (stack.isEmpty() || !ingredient.test(stack) || taken.contains(slot)) continue;

                found = slot;
                break;
            }

            if (found < 0) return false;
            taken.add(found);
        }

        for (int slot : taken) {
            remaining.get(slot).shrink(1);
        }
        return true;
    }
}
