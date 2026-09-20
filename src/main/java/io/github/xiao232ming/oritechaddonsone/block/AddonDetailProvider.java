package io.github.xiao232ming.oritechaddonsone.block;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

/**
 * Implemented by the blocks that store Oritech machine plugins and therefore have a Ctrl gated tooltip.
 * <p>
 * Both the wired {@link ExtensionAddonBlock} and the wireless {@link WirelessExtensionAddonBlock}
 * implement it, so {@code ExtensionAddonItem} can serve both without knowing which one it belongs to.
 */
public interface AddonDetailProvider {

    /** Which Extension Addon this block is (decides slots, accepted plugins and GUI). */
    ExtensionAddonType getType();

    /** All detail lines of this addon type, shown while Ctrl is held. */
    void appendDetails(Consumer<Component> consumer);
}
