package io.github.xiao232ming.oritechaddonsone.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成（仅 Fabric）。
 * <p>
 * NeoForge 侧用的是它自带的配置界面（{@code IConfigScreenFactory} + {@code ConfigurationScreen}），
 * Fabric 侧没有等价物，所以这里给 Mod Menu 提供一个配置屏幕。配置数据本身仍然是 common 里那一份
 * {@code Config.SPEC}（{@code ModConfigSpec}，Fabric 上由 Forge Config API Port 提供），
 * 这个类只负责显示与保存。
 */
public class OritechAddonsOneModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AddonsConfigScreen::new;
    }
}
