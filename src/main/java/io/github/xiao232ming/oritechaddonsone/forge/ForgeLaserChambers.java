package io.github.xiao232ming.oritechaddonsone.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import rearth.oritech.block.entity.interaction.EndericLaserBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;
import rearth.oritech.config.OritechConfig;
import rearth.oritech.config.OritechStartupConfig;
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
            if (!(level.getBlockEntity(laserPos) instanceof EndericLaserBlockEntity laser)) continue;
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
     * Oritech's own chamber addon is the only one in the game, and it always contributes exactly one chamber
     * with the configured multiplier, so counting the chambers is enough.
     */
    public static float efficiencyFactor(Level level, BlockPos forgePos) {
        int chambers = chambersOf(level, forgePos);
        if (chambers <= 0) return 1.0F;

        float multiplier = OritechStartupConfig.chamberAddonEfficiency.get().floatValue();
        boolean additive = OritechConfig.additiveAddons.get();

        float efficiency = additive
                ? 1.0F + chambers * (1.0F - multiplier)
                : (float) Math.pow(multiplier, chambers);

        if (additive) {
            // Oritech's additive mode converts the accumulated value back the same way for every machine.
            float change = efficiency - 1.0F;
            efficiency = 1.0F / efficiency;
            if (change < 0.0F) efficiency = 1.0F + Math.abs(change);
        }
        return efficiency;
    }
}
