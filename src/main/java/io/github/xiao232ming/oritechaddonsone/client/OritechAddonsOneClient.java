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
import io.github.xiao232ming.oritechaddonsone.client.page.TransferFaceState;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

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
        // 传输插件's page draws the cell-face settings of the machine, which the server owns and sends as a whole
        // map because no fixed set of menu slots can carry one setting per face of every cell (see
        // TransferFaceState); routed here for the same reason
        TransferNetworking.setClientHandler(TransferFaceState::put);
        OritechAddonsOne.LOGGER.debug("In-game config screen registered for {}", OritechAddonsOne.MODID);
    }

    /**
     * The one screen of this mod's GUI, for every block that opens {@code EXTENSION_ADDON_MENU}.
     * <p>
     * One menu type serves all of them - the wired addons, the wireless docks and both transfer plugins - so which
     * pages a block shows is decided by the menu itself from the block it belongs to: a placed transfer plugin
     * reports the transfer page only, a preview plugin its 3D preview page only, everything else the whole set (see
     * {@code AddonPageRegistry#pages}). The screen then draws whichever page is selected, so this registration needs
     * no branch per block.
     */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OritechAddonsOne.EXTENSION_ADDON_MENU.get(), ExtensionAddonScreen::new);
    }
}
