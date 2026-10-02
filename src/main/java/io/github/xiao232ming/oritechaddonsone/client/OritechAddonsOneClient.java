package io.github.xiao232ming.oritechaddonsone.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.client.page.ProxyPickerState;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;

/**
 * Client only setup: registers the screen for the Extension Addon menu and the in-game config screen
 * (Mods list -> Oritech Addons One -> Config), so the slot counts can be changed without editing files.
 */
@Mod(value = OritechAddonsOne.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = OritechAddonsOne.MODID, value = Dist.CLIENT)
public class OritechAddonsOneClient {

    public OritechAddonsOneClient(ModContainer container) {
        // NeoForge's standard config screen with editable values for both plugin types.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // the Item Proxy page asks the server for the machine's slot layout; the answer is routed here, so
        // that no client class is ever touched on a dedicated server (see ProxyNetworking.ClientHandler)
        ProxyNetworking.setClientHandler(ProxyPickerState::putLayout);
        OritechAddonsOne.LOGGER.debug("In-game config screen registered for {}", OritechAddonsOne.MODID);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OritechAddonsOne.EXTENSION_ADDON_MENU.get(), ExtensionAddonScreen::new);
    }
}
