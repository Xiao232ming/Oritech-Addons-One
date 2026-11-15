package io.github.xiao232ming.oritechaddonsone.fluid;

import dev.architectury.hooks.fluid.FluidStackHooks;

/**
 * Converts between the unit this mod's config uses (mB, one bucket = 1000) and the unit Oritech's tanks
 * actually store.
 * <p>
 * Oritech always builds its capacities as {@code buckets * FluidStackHooks.bucketAmount()}, and that
 * amount is platform specific: 1000 on NeoForge (mB) but 81000 on Fabric (droplets). Adding the configured
 * mB number to a tank raw therefore looked right on NeoForge but added only {@code 1000 / 81000} of a
 * bucket on Fabric - the tank addon was effectively invisible there.
 */
public final class FluidAmounts {

    private FluidAmounts() {
    }

    /** The configured mB value in the unit the current platform's tanks use. */
    public static long ofMillibuckets(long millibuckets) {
        return millibuckets * FluidStackHooks.bucketAmount() / 1000L;
    }

    /** The other direction, for numbers that are shown to the player with an mB label. */
    public static long toMillibuckets(long platformAmount) {
        return platformAmount * 1000L / FluidStackHooks.bucketAmount();
    }
}
