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
import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Menu of the Extension Addons: the plugin slots of this {@link ExtensionAddonType} (their amount is
 * configurable, 1-36, and sent along when the menu is opened) followed by the regular player inventory.
 */
public class ExtensionAddonMenu extends AbstractContainerMenu {

    private final Container container;
    private final ExtensionAddonType type;
    private final ExtensionAddonLayout layout;

    /** Machine a wireless addon is linked to, or {@code null} (wired addons are never linked). */
    @Nullable
    private BlockPos linkedMachine;
    /** Translation key of that machine's display name, or {@code null} while it is unknown. */
    @Nullable
    private String linkedMachineNameKey;

    /** Server side constructor. */
    public ExtensionAddonMenu(int containerId, Inventory inventory, ExtensionAddonBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, blockEntity.pluginType(), blockEntity.getContainerSize());

        if (blockEntity instanceof WirelessExtensionAddonBlockEntity dock) {
            this.linkedMachine = dock.linkedMachine();
            this.linkedMachineNameKey = dock.linkedMachineNameKey();
        }
    }

    /** Machine this addon is linked to; only the wireless addons ever have one. */
    @Nullable
    public BlockPos linkedMachine() {
        return linkedMachine;
    }

    /** Translation key of that machine's name, or {@code null} while it is unknown. */
    @Nullable
    public String linkedMachineNameKey() {
        return linkedMachineNameKey;
    }

    private ExtensionAddonMenu(int containerId, Inventory inventory, Container container,
            ExtensionAddonType type, int slots) {
        super(OritechAddonsOne.EXTENSION_ADDON_MENU.get(), containerId);
        this.container = container;
        this.type = type;
        this.layout = ExtensionAddonLayout.forType(type, slots);

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
    public ExtensionAddonMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readVarInt());

        // the link of a wireless addon is sent along with the menu, so the GUI can show it right away
        if (buffer.readBoolean()) {
            this.linkedMachine = buffer.readBlockPos();
            var nameKey = buffer.readUtf();
            this.linkedMachineNameKey = nameKey.isEmpty() ? null : nameKey;
        }
    }

    private ExtensionAddonMenu(int containerId, Inventory inventory, BlockPos pos, int slots) {
        this(containerId, inventory, resolveContainer(inventory, pos, slots), resolveType(inventory, pos), slots);
    }

    private static Container resolveContainer(Inventory inventory, BlockPos pos, int slots) {
        if (inventory.player.level().getBlockEntity(pos) instanceof ExtensionAddonBlockEntity blockEntity) {
            return blockEntity;
        }
        // Fallback so a missing block entity can never crash the client.
        return new SimpleContainer(Math.max(1, slots));
    }

    private static ExtensionAddonType resolveType(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof ExtensionAddonBlockEntity blockEntity) {
            return blockEntity.pluginType();
        }
        return ExtensionAddonType.TYPE_1;
    }

    /** The plugin type this menu belongs to. */
    public ExtensionAddonType pluginType() {
        return type;
    }

    /** Geometry of this menu (slot count, row layout and panel size). */
    public ExtensionAddonLayout layout() {
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
