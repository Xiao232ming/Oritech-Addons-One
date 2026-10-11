package io.github.xiao232ming.oritechaddonsone.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * NeoForge 入口点。与 Fabric 入口点一一对应：注册配置 spec，挂上必须在事件里执行的注册
 * （能力桥），然后调用共用的 {@link OritechAddonsOne#init()}。
 */
@Mod(OritechAddonsOne.MODID)
public final class OritechAddonsOneNeoForge {

    public OritechAddonsOneNeoForge(IEventBus modEventBus, ModContainer container) {
        // 旧注册名别名要在注册表 flush 之前挂到事件总线上，所以先把总线交给平台实现
        AddonsPlatformNeoForge.setModEventBus(modEventBus);

        // Register config spec
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 能力桥：排队的物品存储提供者在 RegisterCapabilitiesEvent 里真正注册
        modEventBus.addListener(AddonsPlatformNeoForge::applyPendingCapabilities);

        // 共用的注册 + 初始化（注册表 flush、网络、tick、能力桥排队）
        OritechAddonsOne.init();

        // 注册完成后再解析插件集合（那里会读 Oritech 的 BlockContent，构造阶段注册表已冻结，不能提前）
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(OritechAddonsOne::logSetup));
    }
}
