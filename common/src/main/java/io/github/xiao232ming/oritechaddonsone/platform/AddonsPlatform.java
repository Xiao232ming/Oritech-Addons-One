package io.github.xiao232ming.oritechaddonsone.platform;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.item.ItemApi;

/**
 * 本模组的加载器抽象层（照抄 Oritech 的 {@code OritechPlatform} 写法：SPI 接口 + 接口常量 INSTANCE +
 * {@code ServiceLoader}，两个加载器各一份 {@code @AutoService} 实现）。
 * <p>
 * 只放"两个加载器确实没有共同 API"的东西。凡是 Architectury 已经提供跨端抽象的（注册、菜单、事件、平台查询）
 * 一律直接用 Architectury，不在这里再包一层。
 */
public interface AddonsPlatform {

    AddonsPlatform INSTANCE = ServiceLoader.load(AddonsPlatform.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Failed to load addons platform service."));

    /**
     * 注册旧注册名的别名（把 {@code transfer_addon} / {@code transfer_preview_addon} 映射到新名字），
     * 让旧存档里的方块与物品仍然能解析。
     * <p>
     * NeoForge 侧由 {@code DeferredRegister#addAlias} 实现；<b>Fabric 没有任何等价机制</b>，所以那边是
     * no-op（旧存档里的旧 id 会变成缺失方块，见方案的"必须接受的降级"）。
     * <p>
     * 必须在注册表 flush 之前调用。
     */
    default void registerLegacyAliases() {
    }

    /**
     * 把某个方块实体类型在某个面上的物品存储暴露给外部世界（管道、漏斗、其它模组）。
     * <p>
     * NeoForge：注册 {@code Capabilities.ItemHandler.BLOCK}（必须排队到 {@code RegisterCapabilitiesEvent}）。
     * Fabric：注册 {@code ItemStorage.SIDED}，并把 {@link ItemApi.InventoryStorage} 包成
     * {@code Storage<ItemVariant>}。
     * <p>
     * 注意接口实现类的 {@code @AutoService} 已经在加载器模块里，所以这里的调用时机由加载器入口点决定。
     *
     * @param type   <b>延迟</b>给出要暴露的方块实体类型。传供应商而不是实例，是因为在 NeoForge 的 mod 构造器里
     *               去读 Oritech 的注册表容器（例如 {@code BlockEntitiesContent.ADDON_ENTITY}）会触发它的静态初始化，
     *               而那一刻方块注册表已经冻结（{@code IllegalStateException: Registry is already frozen}）。
     *               加载器实现各自在合法时机取值：NeoForge 在 {@code RegisterCapabilitiesEvent}，Fabric 在本模组初始化时。
     * @param source 回答"这个实体的这个面是什么物品存储"的回调；返回 {@code null} 表示该面不是连接
     */
    void registerFaceItemProvider(Supplier<BlockEntityType<?>> type, FaceItemSource source);

    /**
     * 把某个方块实体类型在某个面上的流体存储暴露给外部世界（流体管道、泵、其它模组）。
     * <p>
     * 与 {@link #registerFaceItemProvider} 同一套延迟取值与排队规则：
     * NeoForge 注册 {@code Capabilities.FluidHandler.BLOCK}，Fabric 注册 {@code FluidStorage.SIDED}，
     * 两者都把 Oritech 的 {@link FluidApi.FluidStorage} 包成加载器自己的流体存储接口。
     *
     * @param type   延迟给出要暴露的方块实体类型
     * @param source 回答"这个实体的这个面是什么流体存储"的回调；返回 {@code null} 表示该面不是连接
     */
    void registerFaceFluidProvider(Supplier<BlockEntityType<?>> type, FaceFluidSource source);

    /**
     * 该区块当前是否被<b>模组</b>强制保持加载。
     * <p>
     * NeoForge：合并 {@code ForcedChunksSavedData} 的 block / entity 两个 {@code TicketTracker}。
     * Fabric：原版只有一个 {@code LongSet}，因此无法区分"被模组强制"与"被 /forceload 强制"。
     * 只用于无线页面上的显示徽章，差异可接受。
     */
    boolean isForceLoadedByMods(ServerLevel level, long chunkPos);

    /**
     * 通知加载器"这个位置的方块实体能力/存储变了"。
     * <p>
     * NeoForge 的 {@code Level#invalidateCapabilities(BlockPos)} 是用来让能力缓存失效的；<b>vanilla 与 Fabric
     * 都没有这个方法</b>（Fabric 的 {@code ItemStorage.SIDED} 是每次查询的，本身没有缓存需要失效），
     * 所以 Fabric 侧是 no-op。
     */
    default void invalidateCapabilities(Level level, BlockPos pos) {
    }

    /**
     * 当前是否是客户端环境。客户端专属类（例如 {@code net.minecraft.client.*}）只能在这个判断之后碰。
     */
    boolean isClient();

    /**
     * 一个面上一份物品存储的来源。返回类型用 Oritech 自己的 {@link ItemApi.InventoryStorage}，
     * 因为它是跨加载器的；把它包成 loader 的存储接口是各加载器实现的事。
     */
    @FunctionalInterface
    interface FaceItemSource {

        @Nullable
        ItemApi.InventoryStorage storageFor(BlockEntity blockEntity, @Nullable Direction face);
    }

    /**
     * 一个面上一份流体存储的来源。返回 Oritech 的 {@link FluidApi.FluidStorage}（跨加载器），
     * 把它包成 loader 的流体存储接口是各加载器实现的事。
     */
    @FunctionalInterface
    interface FaceFluidSource {

        @Nullable
        FluidApi.FluidStorage storageFor(BlockEntity blockEntity, @Nullable Direction face);
    }
}
