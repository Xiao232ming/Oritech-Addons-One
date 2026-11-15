package io.github.xiao232ming.oritechaddonsone.compat.jade;

import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration of the mod: shows the binding and the chunk state of a wireless extension dock when
 * the player looks at it.
 * <p>
 * The class lives in the common module and is shared by both loaders, exactly like Oritech's own Jade
 * plugin: Fabric discovers it through the {@code jade} entrypoint in {@code fabric.mod.json}, NeoForge
 * through the {@link WailaPlugin} annotation scan Jade performs itself. That is why the annotation is
 * present on both variants, even though only NeoForge reads it.
 * <p>
 * Jade is an optional dependency - nothing here is loaded unless Jade itself asks for the entrypoint or
 * scans the annotation - so the mod still works without it.
 */
@WailaPlugin
public class OritechAddonsOneJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // server side data (the link and the force load state are save data of a plain block entity)
        registration.registerBlockDataProvider(WirelessDockJadeProvider.INSTANCE, WirelessExtensionAddonBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(WirelessDockJadeProvider.INSTANCE, WirelessExtensionAddonBlock.class);
    }
}
