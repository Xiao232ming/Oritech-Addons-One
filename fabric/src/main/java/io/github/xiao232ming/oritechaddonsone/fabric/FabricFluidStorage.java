package io.github.xiao232ming.oritechaddonsone.fabric;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import dev.architectury.fluid.FluidStack;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import rearth.oritech.api.fluid.FluidApi;

/**
 * 把 Oritech 的 {@link FluidApi.FluidStorage}（跨加载器接口）包成 Fabric Transfer API 的
 * {@link Storage}{@code <FluidVariant>}，供 {@code FluidStorage.SIDED} 查询使用。
 * <p>
 * 这是 NeoForge 侧 {@code NeoForgeFluidStorageHandler} 的对应物：门控、模式与机器解析都留在被包装的存储里，
 * 这里只做"Oritech 流体存储 ↔ Fabric 流体存储"的翻译。
 * <p>
 * 事务语义用 {@link SnapshotParticipant}：每次改动前快照整份内容，回滚时写回，提交时调用 {@code update()}
 * 通知底层（和 {@code FabricInventoryStorage} 同一套做法）。
 */
public final class FabricFluidStorage extends SnapshotParticipant<List<FluidStack>> implements Storage<FluidVariant> {

    private final FluidApi.FluidStorage storage;

    public FabricFluidStorage(FluidApi.FluidStorage storage) {
        this.storage = storage;
    }

    /** 被包装的 Oritech 存储，方便调用方做身份判断。 */
    public FluidApi.FluidStorage storage() {
        return storage;
    }

    @Override
    protected List<FluidStack> createSnapshot() {
        var copy = new ArrayList<FluidStack>(storage.getContent().size());
        for (var stack : storage.getContent()) {
            copy.add(stack.copy());
        }
        return copy;
    }

    @Override
    protected void readSnapshot(List<FluidStack> snapshot) {
        if (storage instanceof FluidApi.SingleSlotStorage single && snapshot.size() == 1) {
            single.setStack(snapshot.getFirst());
        } else if (storage instanceof FluidApi.MultiSlotStorage multi
                && snapshot.size() == multi.getSlotCount()) {
            for (var index = 0; index < snapshot.size(); index++) {
                multi.setStack(index, snapshot.get(index));
            }
        }
    }

    @Override
    protected void onFinalCommit() {
        storage.update();
    }

    @Override
    public boolean supportsInsertion() {
        return storage.supportsInsertion();
    }

    @Override
    public boolean supportsExtraction() {
        return storage.supportsExtraction();
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) return 0;

        var stack = FluidStack.create(resource.getFluid(), maxAmount);
        updateSnapshots(transaction);
        return Math.max(0L, storage.insert(stack, false));
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) return 0;

        var stack = FluidStack.create(resource.getFluid(), maxAmount);
        updateSnapshots(transaction);
        return Math.max(0L, storage.extract(stack, false));
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        var content = storage.getContent();
        return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < content.size();
            }

            @Override
            public StorageView<FluidVariant> next() {
                if (next >= content.size()) throw new NoSuchElementException();
                return new View(FabricFluidStorage.this, content.get(next++));
            }
        };
    }

    /** 一份内容快照的视图；读写都回到被包装的存储，因此事务仍然一致。 */
    private static final class View implements StorageView<FluidVariant> {

        private final FabricFluidStorage owner;
        private final FluidStack stack;

        private View(FabricFluidStorage owner, FluidStack stack) {
            this.owner = owner;
            this.stack = stack;
        }

        @Override
        public boolean isResourceBlank() {
            return stack.isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return FluidVariant.of(stack.getFluid());
        }

        @Override
        public long getAmount() {
            return Math.max(0L, stack.getAmount());
        }

        @Override
        public long getCapacity() {
            return Math.max(0L, owner.storage.getCapacity());
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return owner.extract(resource, maxAmount, transaction);
        }
    }
}
