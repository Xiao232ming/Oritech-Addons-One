package io.github.xiao232ming.oritechaddonsone.fabric.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.xiao232ming.oritechaddonsone.client.OritechAddonsOneClient;

/**
 * Fabric 客户端入口点。屏幕注册是加载器中立的（Architectury {@code MenuRegistry}），所以这里只调用共用入口。
 * 配置界面由 Mod Menu 承载，见 {@link OritechAddonsOneModMenu}。
 */
public class OritechAddonsOneFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OritechAddonsOneClient.init();
    }
}
