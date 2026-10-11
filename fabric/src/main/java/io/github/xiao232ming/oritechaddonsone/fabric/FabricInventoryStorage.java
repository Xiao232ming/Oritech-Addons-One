package io.github.xiao232ming.oritechaddonsone.fabric;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

import rearth.oritech.api.item.ItemApi;

/**
 * 把 Oritech 的 {@link ItemApi.InventoryStorage}（跨加载器接口）包成 Fabric Transfer API 的
 * {@link SlottedStorage}{@code <ItemVariant>}，供 {@code ItemStorage.SIDED} 查询使用。
 * <p>
 * 这就是 NeoForge 侧 {@code NeoForgeInventoryStorageHandler}（{@code IItemHandler}）的 Fabric 对应物：
 * 两者都只做"Oritech 槽位语义 ↔ 加载器存储语义"的翻译，门控、槽位角色与机器解析都留在被包装的存储里。
 * <p>
 * 事务语义用 {@link SnapshotParticipant} 实现：每个槽位快照自己那一格的 {@link ItemStack}，事务回滚时写回，
 * 提交时调用 {@code update()} 通知底层（与 Fabric 自己的 {@code SingleStackStorage} 同一套做法）。
 */
public final class FabricInventoryStorage implements SlottedStorage<ItemVariant> {

    private final ItemApi.InventoryStorage storage;
    private final Slot[] slots;

    public FabricInventoryStorage(ItemApi.InventoryStorage storage) {
        this.storage = storage;
        var count = Math.max(0, storage.getSlotCount());
        this.slots = new Slot[count];
        for (var index = 0; index < count; index++) {
            this.slots[index] = new Slot(this, index);
        }
    }

    /** 被包装的 Oritech 存储，方便调用方做身份判断（例如 Oritech 自己的 wrapper 逻辑）。 */
    public ItemApi.InventoryStorage storage() {
        return storage;
    }

    @Override
    public boolean supportsInsertion() {
        return storage.supportsInsertion();
    }

    @Override
    public boolean supportsExtraction() {
        return storage.supportsExtraction();
    }

    /**
     * 无索引插入：逐个槽位尝试。每个槽位自己的 {@link SnapshotParticipant} 负责事务回滚，所以这里不需要
     * 再包一层整体快照。
     */
    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) return 0;

        var inserted = 0L;
        for (var slot : slots) {
            if (inserted >= maxAmount) break;
            inserted += slot.insert(resource, maxAmount - inserted, transaction);
        }
        return inserted;
    }

    /** 无索引抽取：从第一个匹配的槽位开始取。 */
    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) return 0;

        var extracted = 0L;
        for (var slot : slots) {
            if (extracted >= maxAmount) break;
            extracted += slot.extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    @Override
    public int getSlotCount() {
        return slots.length;
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        if (slot < 0 || slot >= slots.length) {
            throw new NoSuchElementException("slot " + slot + " of " + slots.length);
        }
        return slots[slot];
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < slots.length;
            }

            @Override
            public StorageView<ItemVariant> next() {
                if (next >= slots.length) throw new NoSuchElementException();
                return slots[next++];
            }
        };
    }

    /**
     * 一个槽位的视图。快照的是这一格的物品堆，因此 insert/extract 可以安全地参与事务。
     * <p>
     * 它是静态嵌套类而不是内部类：equals/hashCode 只比较"哪个存储的哪一格"，写成静态的可以避免
     * 通过另一个实例访问外部私有字段这种绕法。
     */
    private static final class Slot extends SnapshotParticipant<ItemStack> implements SingleSlotStorage<ItemVariant> {

        private final FabricInventoryStorage owner;
        private final int index;

        private Slot(FabricInventoryStorage owner, int index) {
            this.owner = owner;
            this.index = index;
        }

        private ItemApi.InventoryStorage storage() {
            return owner.storage;
        }

        @Override
        protected ItemStack createSnapshot() {
            return storage().getStackInSlot(index).copy();
        }

        @Override
        protected void readSnapshot(ItemStack snapshot) {
            storage().setStackInSlot(index, snapshot);
        }

        @Override
        protected void onFinalCommit() {
            // 让底层把改动同步出去（Oritech 的存储用 update() 通知机器/管道缓存失效）
            storage().update();
        }

        @Override
        public boolean isResourceBlank() {
            return storage().getStackInSlot(index).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(storage().getStackInSlot(index));
        }

        @Override
        public long getAmount() {
            return storage().getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacity() {
            return Math.max(0, storage().getSlotLimit(index));
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource.isBlank() || maxAmount <= 0) return 0;

            var current = storage().getStackInSlot(index);
            if (!current.isEmpty() && !resource.matches(current)) return 0;

            var room = getCapacity() - current.getCount();
            if (room <= 0) return 0;

            var amount = (int) Math.min(Math.min(maxAmount, room), resource.getItem().getDefaultMaxStackSize());
            if (amount <= 0) return 0;

            updateSnapshots(transaction);
            var inserted = storage().insertToSlot(resource.toStack(amount), index, false);
            return Math.max(inserted, 0);
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource.isBlank() || maxAmount <= 0) return 0;

            var current = storage().getStackInSlot(index);
            if (current.isEmpty() || !resource.matches(current)) return 0;

            var amount = (int) Math.min(maxAmount, current.getCount());
            if (amount <= 0) return 0;

            updateSnapshots(transaction);
            var extracted = storage().extractFromSlot(current.copyWithCount(amount), index, false);
            return Math.max(extracted, 0);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Slot slot && slot.owner == owner && slot.index == index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(owner), index);
        }
    }
}