package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.block.entity.processing.AtomicForgeBlockEntity;

import io.github.xiao232ming.oritechaddonsone.forge.ForgeLaserChambers;
import io.github.xiao232ming.oritechaddonsone.forge.LaserAimIndex;

/**
 * Lets every laser report what it currently aims at, so an atomic forge can find the lasers that charge it
 * without scanning its whole neighbourhood (a laser reaches 128 blocks by default).
 * <p>
 * Reporting once a second is enough - the forge reads the laser's live addon data when it builds its GUI -
 * and a laser that aims somewhere else drops its entry again. Every report also refreshes the chambers the
 * forge processes in parallel with, because that value only ever changes while a laser reports.
 */
@Mixin(LaserArmBlockEntity.class)
public class LaserAimIndexMixin {

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void oritechaddonsone$reportTarget(CallbackInfo callback) {
        var laser = (LaserArmBlockEntity) (Object) this;
        var level = laser.getLevel();
        if (level == null || level.isClientSide()) return;
        if (level.getGameTime() % 20L != 0L) return;

        var laserPos = laser.getBlockPos();
        var target = laser.getCurrentTarget();
        if (target != null && level.getBlockEntity(target) instanceof AtomicForgeBlockEntity) {
            LaserAimIndex.register(level, target, laserPos);
            ForgeLaserChambers.refresh(level, target);
        } else {
            // A laser that turned away may have been the last one of a forge, so every forge it left has to
            // recompute its chambers as well.
            for (var forgePos : LaserAimIndex.unregister(level, laserPos)) {
                ForgeLaserChambers.refresh(level, forgePos);
            }
        }
    }
}
