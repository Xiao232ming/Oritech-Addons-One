package io.github.xiao232ming.oritechaddonsone.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import rearth.oritech.block.entity.interaction.EndericLaserBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;
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
}
