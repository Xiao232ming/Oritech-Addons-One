package com.example.oritechaddonsone.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.example.oritechaddonsone.OritechAddonsOne;

/**
 * Client only setup: registers the screen for the Extension Plugin menu and the in-game config screen
 * (Mods list -> Oritech Addons One -> Config), so the slot counts can be changed without editing files.
 */
@Mod(value = OritechAddonsOne.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = OritechAddonsOne.MODID, value = Dist.CLIENT)
public class OritechAddonsOneClient {

    public OritechAddonsOneClient(ModContainer container) {
        // NeoForge's standard config screen with editable values for both plugin types.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        OritechAddonsOne.LOGGER.debug("In-game config screen registered for {}", OritechAddonsOne.MODID);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OritechAddonsOne.EXTENSION_PLUGIN_MENU.get(), ExtensionPluginScreen::new);
    }
}
