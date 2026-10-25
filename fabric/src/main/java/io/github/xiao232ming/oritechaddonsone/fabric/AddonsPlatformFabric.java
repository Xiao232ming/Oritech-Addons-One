package io.github.xiao232ming.oritechaddonsone.fabric;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import com.google.auto.service.AutoService;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.item.ItemApi;

import io.github.xiao232ming.oritechaddonsone.platform.AddonsPlatform;

/**
 * Fabric 侧的 {@link AddonsPlatform} 实现（照抄 Oritech 的 {@code OritechPlatformFabric} 写法：
 * {@code @AutoService} + 平台方法各自映射到 Fabric API）。
 */
@AutoService(AddonsPlatform.class)
public class AddonsPlatformFabric implements AddonsPlatform {

    /**
     * <b>Fabric 没有任何注册表别名机制</b>，所以这里是 no-op：NeoForge 侧由
     * {@code DeferredRegister#addAlias} 把 {@code transfer_addon} / {@code transfer_preview_addon} 这两个
     * 旧 id 映射到新名字，让旧存档里的方块与物品仍然能解析；Fabric 上旧存档里的旧 id 会变成缺失方块。
     * <p>
     * 这是方案里明确接受的功能降级（若将来要补，只能在世界加载时扫描 palette 做一次性数据修补）。
     */
    @Override
    public void registerLegacyAliases() {
        // nothing to do on fabric
    }

    /**
     * 把方块实体的物品存储注册到 Fabric 的 {@code ItemStorage.SIDED} 查询上。
     * <p>
     * 传入的 {@link ItemApi.InventoryStorage} 是 Oritech 的跨加载器接口，这里把它包成 Fabric Transfer API 的
     * {@code Storage<ItemVariant>}；Oritech 自己的 {@code FabricItemApi} 也是同样的做法。
     */
    @Override
    public void registerFaceItemProvider(java.util.function.Supplier<BlockEntityType<?>> type, FaceItemSource source) {
        // Fabric 的查询注册需要真实类型；Fabric 的注册表在 mod 初始化期间是开放的，所以这里直接取值
        //（NeoForge 侧相反：那边的实现把取值推迟到 RegisterCapabilitiesEvent）。
        var resolvedType = type.get();
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, side) -> {
                    var storage = source.storageFor(blockEntity, side);
                    return storage == null ? null : new FabricInventoryStorage(storage);
                },
                resolvedType);
    }

    /**
     * 把延伸器某个面的 Oritech 流体存储注册到 Fabric 的 {@code FluidStorage.SIDED} 查询上。
     * <p>
     * 这里返回的 {@link FluidApi.FluidStorage} 由 {@link FabricFluidStorage} 包成
     * {@code Storage<FluidVariant>}；门控与机器解析仍在被包装的存储里。
     */
    @Override
    public void registerFaceFluidProvider(java.util.function.Supplier<BlockEntityType<?>> type, FaceFluidSource source) {
        var resolvedType = type.get();
        FluidStorage.SIDED.registerForBlockEntity(
                (blockEntity, side) -> {
                    var storage = source.storageFor(blockEntity, side);
                    return storage == null ? null : new FabricFluidStorage(storage);
                },
                resolvedType);
    }

    /**
     * 原版只有一个 {@code LongSet}（{@code ForcedChunksSavedData#getChunks()}），所以 Fabric 上无法区分
     * "被模组强制" 与 "被 /forceload 强制"；NeoForge 侧能分开看 block / entity 两个 tracker。
     * 这里只用于无线页面的徽章显示，差异可接受。
     */
    @Override
    public boolean isForceLoadedByMods(ServerLevel level, long chunkPos) {
        var forced = level.getDataStorage().get(ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
        return forced != null && forced.getChunks().contains(chunkPos);
    }

    /**
     * Fabric 侧是 no-op：{@code ItemStorage.SIDED} 的查询没有 NeoForge 那种能力缓存，
     * 每次查询都直接问我们的回调，所以没有需要失效的东西。
     */
    @Override
    public void invalidateCapabilities(Level level, BlockPos pos) {
        // nothing to invalidate on fabric
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
