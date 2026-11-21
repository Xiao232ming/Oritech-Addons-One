package io.github.xiao232ming.oritechaddonsone.item;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

/**
 * The Ctrl check the two addon items share. Tooltips are only built on the client, so the guard keeps
 * dedicated servers from touching client only classes.
 */
public final class AddonTooltips {

    private AddonTooltips() {
    }

    /** True while the player holds Ctrl, like Oritech's own addon items check. */
    public static boolean isControlDown() {
        if (Platform.getEnvironment() != Env.CLIENT) return false;
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }
}
