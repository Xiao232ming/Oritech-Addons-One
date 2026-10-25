package io.github.xiao232ming.oritechaddonsone.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import io.github.xiao232ming.oritechaddonsone.client.OritechAddonsOneClient;
import io.github.xiao232ming.oritechaddonsone.client.page.TransferFaceState;

/**
 * Fabric 客户端入口点。屏幕注册是加载器中立的（Architectury {@code MenuRegistry}），所以这里只调用共用入口。
 * 配置界面由 Mod Menu 承载，见 {@link OritechAddonsOneModMenu}。
 */
public class OritechAddonsOneFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OritechAddonsOneClient.init();

        // the cell-face maps are kept across screens; when the client disconnects they have to go, or the next
        // world would read the previous one's maps under the same keys
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TransferFaceState.clear());
    }
}
