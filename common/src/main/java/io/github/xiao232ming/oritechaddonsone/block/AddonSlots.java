package io.github.xiao232ming.oritechaddonsone.block;

/**
 * How many plugin slots an Extension Addon holds, independent of the GUI geometry that draws them.
 * <p>
 * These are domain limits: the block entity keeps a slot for every possible plugin, the config is clamped to
 * them and the type III plan never asks for more tiers than fit. The layout reads them to size its panel, so
 * the dependency points from the GUI to the domain and not the other way round.
 */
public final class AddonSlots {

    /** Never fewer than one plugin slot: an addon without one would hold nothing. */
    public static final int MIN_SLOTS = 1;

    /**
     * Largest slot count a player may configure, and therefore the largest grid any addon type can show:
     * the type III grid at its widest and tallest, i.e. one column per
     * {@link ExtensionAddonType.StatCategory} of {@link #TYPE_3_MAX_ROWS} tier rows each - Oritech's own
     * tier 1 plus the tiers 2 to 9 that Oritech Things adds.
     */
    public static final int MAX_SLOTS = 72;

    /** Highest number of tier rows the type III grid can show. */
    public static final int TYPE_3_MAX_ROWS = MAX_SLOTS / ExtensionAddonType.StatCategory.values().length;

    private AddonSlots() {
    }
}
