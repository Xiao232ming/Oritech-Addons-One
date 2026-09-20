package io.github.xiao232ming.oritechaddonsone.wireless;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import rearth.oritech.init.ComponentContent;
import rearth.oritech.item.tools.LaserTargetDesignator;

/**
 * Helpers around Oritech's target designator, which is the item that links a wireless extension addon to
 * a machine: right click the dock to save its position, then right click the machine to link it.
 * <p>
 * A designator that carries a stored position is what makes a click "a linking click". Oritech's own
 * machines (laser arm, drone port, power poles) also key their behaviour on that, and it is the only
 * state in which the player intends to link something - so a stored position is a safe, narrow signal.
 */
public final class WirelessLinking {

    private WirelessLinking() {
    }

    /** True while the stack is Oritech's target designator and it has a position stored. */
    public static boolean isLinkDesignator(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LaserTargetDesignator)) return false;
        return stack.get(ComponentContent.TARGET_POSITION.get()) != null;
    }

    /** True while the player holds such a designator in the main or the off hand. */
    public static boolean isHoldingLinkDesignator(Player player) {
        return isLinkDesignator(player.getMainHandItem()) || isLinkDesignator(player.getOffhandItem());
    }
}