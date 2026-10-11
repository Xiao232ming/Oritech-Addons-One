package io.github.xiao232ming.oritechaddonsone.block.entity;

/**
 * What one face of an Extension Addon / Wireless Extension Dock does with the items of the machine it works
 * on, set from the "Extension Transfer" page of that block.
 * <p>
 * The mode is stored per face (see {@link TransferFaceModes}) and read by
 * {@code MachineFaceStorage} on every single item transfer, so a face that is switched from input to output
 * starts moving items the other way immediately, without any cache to invalidate.
 */
public enum TransferMode {

    /** The face transfers nothing; this is what a face starts as. */
    NONE,
    /** Items can be put <b>into</b> the machine through this face. */
    INPUT,
    /** Items can be taken <b>out of</b> the machine through this face. */
    OUTPUT,
    /** Both directions, i.e. a face a pipe can fill <em>and</em> empty. */
    BOTH;

    /** True while items may be inserted into the machine through this mode. */
    public boolean allowsInsert() {
        return this == INPUT || this == BOTH;
    }

    /** True while items may be extracted from the machine through this mode. */
    public boolean allowsExtract() {
        return this == OUTPUT || this == BOTH;
    }

    /**
     * The mode of the given ordinal, or {@link #NONE} while the value is out of range - a value written by a
     * version that knew more modes must never break loading.
     */
    public static TransferMode byOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
