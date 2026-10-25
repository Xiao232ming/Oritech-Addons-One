package io.github.xiao232ming.oritechaddonsone.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.neoforged.fml.config.ModConfig;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Fabric 入口点。照抄 Oritech 的 {@code OritechFabricMod}：先注册配置 spec（common 里的
 * {@code Config.SPEC} 是 NeoForge 的 {@code ModConfigSpec}，Fabric 上由 Forge Config API Port 提供），
 * 再调用共用的 {@code OritechAddonsOne.init()}。
 */
public class OritechAddonsOneFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register config spec (Forge Config API Port 把 NeoForge 的配置 API 桥到 Fabric)
        NeoForgeConfigRegistry.INSTANCE.register(OritechAddonsOne.MODID, ModConfig.Type.COMMON, Config.SPEC);

        // 共用的注册 + 初始化（注册表 flush、网络、tick、能力桥）
        OritechAddonsOne.init();

        // Fabric 的注册表在 mod 初始化期间是开放的，所以这里可以直接解析插件集合并打日志
        OritechAddonsOne.logSetup();

        // 服务器停止时丢掉按维度+坐标缓存的机器数据，否则同一进程里的下一个世界会读到上一个世界的设置
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OritechAddonsOne.clearServerCaches());
    }
}
