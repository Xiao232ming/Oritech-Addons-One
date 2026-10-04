package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.page.ProxyPickerState;
import io.github.xiao232ming.oritechaddonsone.client.page.TransferFaceState;
import io.github.xiao232ming.oritechaddonsone.network.FilterNetworking;
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
        // the 过滤 page edits a filter the server owns, so the server sends back what it really holds after
        // every edit; routed here for the same reason, and the open page - if there is one - is what applies it
        FilterNetworking.setClientHandler(OritechAddonsOneClient::putFilterState);
        OritechAddonsOne.LOGGER.debug("In-game config screen registered for {}", OritechAddonsOne.MODID);
    }

    /**
     * Applies the filter the server really holds to the open 过滤 page, if it is about that face.
     * <p>
     * The answer is matched on the whole address - position, model, face and cell - and not just on the face,
     * because two cell-faces of a structure can share a direction while naming different cells, and handing one
     * of them the other's filter would be worse than dropping the answer. An answer for a page that is not open
     * is simply ignored: there is nothing to correct.
     */
    private static void putFilterState(FilterNetworking.FilterState packet) {
        if (!(Minecraft.getInstance().screen instanceof FaceFilterScreen screen)) return;

        var menu = screen.getMenu();
        if (!packet.target().pos().equals(menu.pos())) return;
        if (packet.target().model() != menu.model()) return;
        if (!packet.target().face().equals(menu.face())) return;

        var cell = menu.cell();
        if (!packet.target().cell().equals(cell == null ? Vec3i.ZERO : cell)) return;

        // the answer names the direction it is about, and it is applied to exactly that one: a player can switch
        // direction while the answer is in flight, and applying it blindly would overwrite the filter now on
        // screen with the other one's data
        var flow = TransferMode.byOrdinal(packet.flow());
        if (flow != TransferMode.INPUT && flow != TransferMode.OUTPUT) return;

        screen.applyAuthoritative(flow, packet.data());
    }

    /**
     * The one screen of this mod's GUI, for every block that opens {@code EXTENSION_ADDON_MENU}.
     * <p>
     * One menu type serves all of them - the wired addons, the wireless docks and both transfer plugins - so which
     * pages a block shows is decided by the menu itself from the block it belongs to: a placed transfer plugin
     * reports the transfer page only, a transfer plugin its 3D page only, everything else the whole set (see
     * {@code AddonPageRegistry#pages}). The screen then draws whichever page is selected, so this registration needs
     * no branch per block.
     */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OritechAddonsOne.EXTENSION_ADDON_MENU.get(), ExtensionAddonScreen::new);
        // the 过滤 page, whose menu the server opens for one face at a time (see FilterNetworking.OpenFilter)
        event.register(OritechAddonsOne.FACE_FILTER_MENU.get(), FaceFilterScreen::new);
    }
}
