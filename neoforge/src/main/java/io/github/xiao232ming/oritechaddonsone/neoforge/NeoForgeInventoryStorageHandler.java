package io.github.xiao232ming.oritechaddonsone.neoforge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import rearth.oritech.api.item.ItemApi;

/**
 * 把 Oritech 的 {@link ItemApi.InventoryStorage}（跨加载器接口）包成 NeoForge 的 {@link IItemHandler}。
 * <p>
 * 这就是 Fabric 侧 {@code FabricInventoryStorage} 的对应物，代码原本是 {@code FacePluginStorage} 里的
 * {@code NeoForgeHandler} 记录：能力注册是加载器的事，所以适配器搬到了加载器模块。
 * <p>
 * 它的物品堆按机器的实际状态交出，与 Oritech 自己的 wrapper 一致 - {@code IItemHandler} 的契约禁止
 * <em>调用方</em>修改它们。
 */
public record NeoForgeInventoryStorageHandler(ItemApi.InventoryStorage storage) implements IItemHandler {

    @Override
    public int getSlots() {
        return storage.getSlotCount();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot < 0 || slot >= storage.getSlotCount() ? ItemStack.EMPTY : storage.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= storage.getSlotCount()) return stack;

        var inserted = storage.insertToSlot(stack, slot, simulate);
        return inserted <= 0 ? stack : stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= storage.getSlotCount()) return ItemStack.EMPTY;

        var available = storage.getStackInSlot(slot);
        if (available.isEmpty()) return ItemStack.EMPTY;

        var wanted = available.copyWithCount(Math.min(amount, available.getCount()));
        var extracted = storage.extractFromSlot(wanted, slot, simulate);
        return extracted <= 0 ? ItemStack.EMPTY : wanted.copyWithCount(extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot < 0 || slot >= storage.getSlotCount() ? 0 : storage.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !stack.isEmpty() && slot >= 0 && slot < storage.getSlotCount()
                && storage.insertToSlot(stack, slot, true) > 0;
    }
}
