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
 * NeoForge does not use a loader entrypoint for Jade - Jade scans the classpath for the
 * {@link WailaPlugin} annotation itself (see Oritech's own Jade plugin, which does the same) - so this
 * annotation is all the registration this side needs.
 * <p>
 * Jade is an optional dependency: nothing here is loaded unless Jade scans the annotation, so the mod
 * still works without it.
 */
@WailaPlugin
public class OritechAddonsOneJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // server side data (the link and the force load state are save data of a plain block entity).
        // Since 1.21.6 Jade rejects a provider that also implements IComponentProvider, so the server
        // and client halves live in two separate classes.
        registration.registerBlockDataProvider(WirelessDockDataProvider.INSTANCE, WirelessExtensionAddonBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(WirelessDockTooltipProvider.INSTANCE, WirelessExtensionAddonBlock.class);
    }
}
