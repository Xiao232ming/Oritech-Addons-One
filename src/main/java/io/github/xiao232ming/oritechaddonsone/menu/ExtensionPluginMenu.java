package io.github.xiao232ming.oritechaddonsone.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionPluginType;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionPluginBlockEntity;

/**
 * Menu of the Extension Plugins: the plugin slots of this {@link ExtensionPluginType} (their amount is
 * configurable, 1-36, and sent along when the menu is opened) followed by the regular player inventory.
 */
public class ExtensionPluginMenu extends AbstractContainerMenu {

    private final Container container;
    private final ExtensionPluginType type;
    private final ExtensionPluginLayout layout;

    /** Server side constructor. */
    public ExtensionPluginMenu(int containerId, Inventory inventory, ExtensionPluginBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, blockEntity.pluginType(), blockEntity.getContainerSize());
    }

    private ExtensionPluginMenu(int containerId, Inventory inventory, Container container,
            ExtensionPluginType type, int slots) {
        super(OritechAddonsOne.EXTENSION_PLUGIN_MENU.get(), containerId);
        this.container = container;
        this.type = type;
        this.layout = ExtensionPluginLayout.forType(type, slots);

        for (int slot = 0; slot < layout.slots(); slot++) {
            final int slotIndex = slot;
            addSlot(new Slot(container, slot, layout.slotX(slot), layout.slotY(slot)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return type.acceptsInSlot(slotIndex, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return Config.slotCapacity(type);
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return Config.slotCapacity(type);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, layout.playerRowsY() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, layout.hotbarY()));
        }
    }

    /** Client side constructor; block position and slot count are sent along when the menu is opened. */
    public ExtensionPluginMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readVarInt());
    }

    private ExtensionPluginMenu(int containerId, Inventory inventory, BlockPos pos, int slots) {
        this(containerId, inventory, resolveContainer(inventory, pos, slots), resolveType(inventory, pos), slots);
    }

    private static Container resolveContainer(Inventory inventory, BlockPos pos, int slots) {
        if (inventory.player.level().getBlockEntity(pos) instanceof ExtensionPluginBlockEntity blockEntity) {
            return blockEntity;
        }
        // Fallback so a missing block entity can never crash the client.
        return new SimpleContainer(Math.max(1, slots));
    }

    private static ExtensionPluginType resolveType(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof ExtensionPluginBlockEntity blockEntity) {
            return blockEntity.pluginType();
        }
        return ExtensionPluginType.TYPE_1;
    }

    /** The plugin type this menu belongs to. */
    public ExtensionPluginType pluginType() {
        return type;
    }

    /** Geometry of this menu (slot count, row layout and panel size). */
    public ExtensionPluginLayout layout() {
        return layout;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var moved = ItemStack.EMPTY;
        var slot = slots.get(index);
        var pluginSlots = layout.slots();

        if (slot.hasItem()) {
            var stack = slot.getItem();
            moved = stack.copy();

            if (index < pluginSlots) {
                // plugin slot -> player inventory
                if (!moveItemStackTo(stack, pluginSlots, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                // player inventory -> plugin slot (only accepts plugins of this type)
                if (!moveItemStackTo(stack, 0, pluginSlots, false)) return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
