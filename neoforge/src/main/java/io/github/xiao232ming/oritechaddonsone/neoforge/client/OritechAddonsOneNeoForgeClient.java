package io.github.xiao232ming.oritechaddonsone.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.client.OritechAddonsOneClient;
import io.github.xiao232ming.oritechaddonsone.client.page.TransferFaceState;

/**
 * NeoForge 客户端入口点：屏幕注册是共用的，这里额外把 NeoForge 自带的配置界面挂上
 * （Fabric 侧对应的是 Mod Menu，见 fabric 模块的 {@code OritechAddonsOneModMenu}）。
 */
@Mod(value = OritechAddonsOne.MODID, dist = Dist.CLIENT)
public final class OritechAddonsOneNeoForgeClient {

    public OritechAddonsOneNeoForgeClient(ModContainer container) {
        // NeoForge's standard config screen with editable values for both plugin types.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        OritechAddonsOneClient.init();

        // the cell-face maps are kept across screens; when the client disconnects they have to go, or the next
        // world would read the previous one's maps under the same keys
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TransferFaceState.clear());
    }
}
