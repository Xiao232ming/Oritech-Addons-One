package io.github.xiao232ming.oritechaddonsone.neoforge;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.auto.service.AutoService;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;

import io.github.xiao232ming.oritechaddonsone.platform.AddonsPlatform;

/**
 * NeoForge 侧的 {@link AddonsPlatform} 实现（照抄 Oritech 的 {@code OritechPlatformNeoForge} 写法：
 * {@code @AutoService} + 需要事件的注册排队到对应事件里执行）。
 */
@AutoService(AddonsPlatform.class)
public class AddonsPlatformNeoForge implements AddonsPlatform {

    /** mod 事件总线，由 {@code @Mod} 构造器注入（旧注册名别名要在其上注册自己的 DeferredRegister）。 */
    private static IEventBus modEventBus;

    /** {@link #registerFaceItemProvider} 排到这里，等 {@code RegisterCapabilitiesEvent} 再真正注册。 */
    private static final List<PendingFaceItemProvider> PENDING = new ArrayList<>();

    static void setModEventBus(IEventBus bus) {
        modEventBus = bus;
    }

    /**
     * 旧注册名的别名（{@code DeferredRegister#addAlias}），必须在注册表 flush 之前挂到事件总线上，
     * 所以这里直接转发给 {@link TransferAddonMigration}。
     */
    @Override
    public void registerLegacyAliases() {
        if (modEventBus == null) {
            throw new IllegalStateException("mod event bus not set before OritechAddonsOne.init()");
        }
        TransferAddonMigration.register(modEventBus);
    }

    @Override
    public void registerFaceItemProvider(java.util.function.Supplier<BlockEntityType<?>> type, FaceItemSource source) {
        // NeoForge 的能力只能在 RegisterCapabilitiesEvent 里注册，所以先排队（与 Oritech 的
        // PENDING_S2C_INITS 是同一个套路）。
        PENDING.add(new PendingFaceItemProvider(type, source));
    }

    /**
     * 在 {@code RegisterCapabilitiesEvent} 里把排队的能力注册上：把 {@code ItemApi.InventoryStorage}
     * 包成 NeoForge 的 {@code IItemHandler}。
     */
    @SuppressWarnings("unchecked")
    static void applyPendingCapabilities(RegisterCapabilitiesEvent event) {
        for (var pending : PENDING) {
            // 到这里 Oritech 的注册表容器已经初始化过了（它的注册发生在 RegisterEvent 阶段），取值是安全的
            var type = (BlockEntityType<BlockEntity>) pending.type().get();
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (blockEntity, side) -> {
                var storage = pending.source().storageFor(blockEntity, side);
                return storage == null ? null : new NeoForgeInventoryStorageHandler(storage);
            });
        }
        PENDING.clear();
    }

    /**
     * NeoForge 把被强加载的区块按所有者记在两个 {@code TicketTracker} 里（ticking / non-ticking），
     * 合并起来看就是"被模组强制保持加载"的区块。Fabric 侧没有这个信息，见那边的实现。
     */
    @Override
    public boolean isForceLoadedByMods(ServerLevel level, long chunkPos) {
        var forced = level.getDataStorage().get(ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
        if (forced == null) return false;

        return holds(forced.getBlockForcedChunks(), chunkPos) || holds(forced.getEntityForcedChunks(), chunkPos);
    }

    private static <T extends Comparable<? super T>> boolean holds(ForcedChunkManager.TicketTracker<T> tracker, long chunk) {
        return holds(tracker.getChunks().values(), chunk) || holds(tracker.getTickingChunks().values(), chunk);
    }

    private static boolean holds(Collection<LongSet> tracked, long chunk) {
        for (var chunks : tracked) {
            if (chunks.contains(chunk)) return true;
        }
        return false;
    }

    /** NeoForge 有真正的能力缓存，所以这里调用它的失效方法。 */
    @Override
    public void invalidateCapabilities(Level level, BlockPos pos) {
        level.invalidateCapabilities(pos);
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    private record PendingFaceItemProvider(java.util.function.Supplier<BlockEntityType<?>> type, FaceItemSource source) {
    }
}
