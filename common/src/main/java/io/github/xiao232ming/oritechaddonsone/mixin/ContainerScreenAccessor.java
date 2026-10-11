package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 读取 {@code AbstractContainerScreen} 的 GUI 原点（vanilla 里是 protected 的 {@code leftPos}/{@code topPos}；
 * Forge/NeoForge 另外提供了 {@code getGuiLeft()}/{@code getGuiTop()}）。
 * <p>
 * 这是一个**客户端** mixin（见 {@code oritechaddonsone.mixins.json} 的 {@code client} 段），
 * 所以不需要为了这两个字段去动全局的 access transformer / access widener ——
 * 全局放开会同时影响专用服务器（客户端类在那边会被 runtimedistcleaner 剥掉）。
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("leftPos")
    int oritechaddonsone$leftPos();

    @Accessor("topPos")
    int oritechaddonsone$topPos();
}
