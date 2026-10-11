package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;

import dev.architectury.registry.menu.MenuRegistry;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.client.page.ProxyPickerState;
import io.github.xiao232ming.oritechaddonsone.client.page.TransferFaceState;
import io.github.xiao232ming.oritechaddonsone.network.FilterNetworking;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * Client only setup: registers the screens of this mod's two menus and routes the answers of the pages that
 * the server owns.
 * <p>
 * <b>加载器中立。</b>屏幕注册走 Architectury 的 {@code MenuRegistry.registerScreenFactory}（Oritech 自己的
 * {@code ModScreens} 就是这么写的），两个加载器的客户端入口点各自调用 {@link #init()}。
 * <p>
 * 配置界面不在这里：NeoForge 用自带的 {@code ConfigurationScreen}（{@code IConfigScreenFactory} 扩展点），
 * Fabric 用 Mod Menu（见 fabric 模块的 {@code OritechAddonsOneModMenu}）。
 */
public final class OritechAddonsOneClient {

    private OritechAddonsOneClient() {
    }

    /**
     * 注册两个屏幕并接上三个页面的客户端回调。由加载器的客户端入口点调用。
     */
    public static void init() {
        // the one screen of this mod's GUI, for every block that opens EXTENSION_ADDON_MENU
        MenuRegistry.registerScreenFactory(OritechAddonsOne.EXTENSION_ADDON_MENU_TYPE, ExtensionAddonScreen::new);
        // the 过滤 page, whose menu the server opens for one face at a time (see FilterNetworking.OpenFilter)
        MenuRegistry.registerScreenFactory(OritechAddonsOne.FACE_FILTER_MENU_TYPE, FaceFilterScreen::new);

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

        OritechAddonsOne.LOGGER.debug("Client screens registered for {}", OritechAddonsOne.MODID);
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
}
