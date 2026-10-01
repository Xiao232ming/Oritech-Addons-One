package io.github.xiao232ming.oritechaddonsone.menu;

import java.util.function.BooleanSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/**
 * The reserved item slot of the wireless page: exactly one item of any kind.
 * <p>
 * It is a real menu slot backed by the addon's own block entity ({@link ExtensionAddonBlockEntity#RESERVED_SLOT}),
 * so whatever is put in it is saved with the block and handed back when the block is mined.
 * <p>
 * Two things keep it out of the plugin page's way:
 * <ul>
 *     <li>it is {@linkplain #isActive() only active} while the wireless page is shown, so on the plugin
 *     page it is neither drawn nor clickable (vanilla asks a slot for that in the screen, never on the
 *     server), and</li>
 *     <li>it stands outside the plugin grid ({@link ExtensionAddonLayout#RESERVED_SLOT_X}), so even a
 *     click that reaches both pages' slots can only ever hit one of them.</li>
 * </ul>
 */
public class ReservedItemSlot extends Slot {

    /** Capacity of this slot: one item, whatever the container's own limit is. */
    public static final int CAPACITY = 1;

    /** Told by the menu whether the page this slot belongs to is the page the screen shows. */
    private final BooleanSupplier active;

    public ReservedItemSlot(Container container, int slot, int x, int y, BooleanSupplier active) {
        super(container, slot, x, y);
        this.active = active;
    }

    /** Any item may be placed, but only a single one. */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return CAPACITY;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return CAPACITY;
    }

    /** Usable only while the wireless page is shown; see the class comment. */
    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}
