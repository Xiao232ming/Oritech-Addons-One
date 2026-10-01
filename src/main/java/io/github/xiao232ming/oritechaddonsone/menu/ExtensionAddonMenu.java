package io.github.xiao232ming.oritechaddonsone.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Menu of the Extension Addons: the plugin slots of this {@link ExtensionAddonType} (their amount is
 * configurable, 1-72, and sent along when the menu is opened), the reserved single item slot of the
 * wireless page and then the regular player inventory.
 * <p>
 * Both pages of the GUI share this one menu, so both slot groups exist at all times; which of them the
 * player can use is decided by {@linkplain Slot#isActive() the slot's own activity}, which the screen
 * sets from the page it currently shows. The server never sees that flag (it only changes what is drawn
 * and clicked), so the plugin page behaves exactly as it did before - and the reserved slot sits outside
 * the plugin grid, so the two groups cannot overlap even while both are active.
 */
public class ExtensionAddonMenu extends AbstractContainerMenu {

    private final Container container;
    private final ExtensionAddonType type;
    private final ExtensionAddonLayout layout;
    /** Position of the addon this menu belongs to, used to re-resolve the machine name on the client. */
    private final BlockPos position;
    /** True for a wireless dock: only the dock shows the coordinates of the machine it is linked to. */
    private final boolean wireless;
    /**
     * True when this menu was built by the client constructor. On the server the machine name and the
     * chunk state are resolved from the block entities; on the client they come from what the server sent
     * (the addons are plain block entities, so their data is not synced by itself).
     */
    private final boolean clientSide;

    /** Machine a wireless addon is linked to, or {@code null} (wired addons are never linked). */
    @Nullable
    private BlockPos linkedMachine;
    /** Translation key of that machine's display name, or {@code null} while it is unknown. */
    @Nullable
    private String linkedMachineNameKey;

    /** Client side: true while the screen shows the wireless page, which is what enables its own slot. */
    private boolean wirelessPageActive;
    /** Client side copy of {@link #targetChunkLoadedSlot} (the server computes the value itself). */
    private boolean targetChunkLoaded;

    /**
     * Container data slot 0: whether the chunk of the connected machine is loaded. Both variants answer
     * it - a wired addon is claimed by a machine in a loaded chunk, a dock by the machine it is linked to -
     * see {@link ExtensionAddonBlockEntity#isTargetChunkLoaded()}.
     * <p>
     * Oritech's synced fields would need a networked block entity, and the addons are plain ones, so the
     * value is published through vanilla's container data instead: the server polls it on every menu tick
     * and sends it only when it changed, which is the same "resolve it on the server, tell the client when
     * it changes" shape the rest of this mod uses for GUI values.
     */
    private final DataSlot targetChunkLoadedSlot = new DataSlot() {
        @Override
        public int get() {
            if (clientSide) return targetChunkLoaded ? 1 : 0;
            return container instanceof ExtensionAddonBlockEntity addon && addon.isTargetChunkLoaded() ? 1 : 0;
        }

        @Override
        public void set(int value) {
            targetChunkLoaded = value != 0;
        }
    };

    /** Server side constructor. */
    public ExtensionAddonMenu(int containerId, Inventory inventory, ExtensionAddonBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, blockEntity.getBlockPos(), blockEntity.pluginType(),
                blockEntity.getContainerSize(), false);

        if (blockEntity instanceof WirelessExtensionAddonBlockEntity dock) {
            this.linkedMachine = dock.linkedMachine();
        }
        // resolved on the server, where the target's chunk is loaded whenever the target really exists
        this.linkedMachineNameKey = blockEntity.connectedMachineNameKey();
    }

    /** Machine this addon is linked to; only the wireless addons ever have one. */
    @Nullable
    public BlockPos linkedMachine() {
        return linkedMachine;
    }

    /**
     * True for a wireless extension dock. Both variants share this menu (and both show the wireless page),
     * but only the dock has a linked machine to report.
     */
    public boolean wireless() {
        return wireless;
    }

    /** True while this menu was built on the client (the values below then come from the server). */
    public boolean clientSide() {
        return clientSide;
    }

    /**
     * Translation key of the connected machine's name, or {@code null} while it is unknown.
     * <p>
     * The value was resolved on the server and sent with the menu, so it is correct even when the client
     * cannot look the block up because its chunk is not loaded. While the client <em>does</em> have that
     * chunk it resolves the name again from its own level, so a machine that was replaced (or a dock that
     * was relinked) shows its new name without reopening the GUI.
     */
    @Nullable
    public String linkedMachineNameKey() {
        if (clientSide && linkedMachine != null) {
            var live = ExtensionAddonBlockEntity.nameKeyAt(containerLevel(), linkedMachine);
            if (live != null) return live;
        }
        return linkedMachineNameKey;
    }

    /**
     * True while the chunk of the connected machine is loaded. On the server this is the value of this
     * tick, on the client the value the server last sent.
     */
    public boolean targetChunkLoaded() {
        return targetChunkLoadedSlot.get() != 0;
    }

    /** Index of the reserved single item slot of the wireless page. */
    public int reservedSlot() {
        return layout.slots();
    }

    /**
     * Whether the wireless page is the page the screen shows. Only ever set on the client: the server has
     * no idea which page is open, and every slot exists on both sides either way.
     */
    public boolean wirelessPageActive() {
        return wirelessPageActive;
    }

    /** Called by the screen whenever the selected page changes, so the pages' slots become active. */
    public void setWirelessPageActive(boolean active) {
        this.wirelessPageActive = active;
    }

    private ExtensionAddonMenu(int containerId, Inventory inventory, Container container, BlockPos position,
            ExtensionAddonType type, int slots, boolean clientSide) {
        super(OritechAddonsOne.EXTENSION_ADDON_MENU.get(), containerId);
        this.container = container;
        this.position = position;
        this.type = type;
        this.layout = ExtensionAddonLayout.of(slots);
        this.wireless = container instanceof WirelessExtensionAddonBlockEntity;
        this.clientSide = clientSide;

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

                /**
                 * The plugin slots are only usable while the plugin page is shown. This is display and
                 * clicking only - nothing of it reaches the server - and it keeps the plugin items from
                 * floating over the wireless page's text.
                 */
                @Override
                public boolean isActive() {
                    return !wirelessPageActive;
                }
            });
        }

        // The reserved slot of the wireless page: one item, nothing else. It sits right behind the plugin
        // slots in this menu, but outside their grid, so it can never cover or steal a click from them.
        // Its container index is the block entity's own reserved index, which is not a plugin slot.
        addSlot(new ReservedItemSlot(container, ExtensionAddonBlockEntity.RESERVED_SLOT,
                ExtensionAddonLayout.RESERVED_SLOT_X, ExtensionAddonLayout.RESERVED_SLOT_Y, this::wirelessPageActive));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, layout.playerRowsY() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, layout.hotbarY()));
        }

        addDataSlot(targetChunkLoadedSlot);
    }

    /** Client side constructor; block position and slot count are sent along when the menu is opened. */
    public ExtensionAddonMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readVarInt());

        // the link of a wireless addon is sent along with the menu, so the GUI can show it right away
        if (buffer.readBoolean()) {
            this.linkedMachine = buffer.readBlockPos();
        }
        // the connected machine's name, resolved on the server (empty while it is unknown)
        var nameKey = buffer.readUtf();
        this.linkedMachineNameKey = nameKey.isEmpty() ? null : nameKey;
    }

    private ExtensionAddonMenu(int containerId, Inventory inventory, BlockPos position, int slots) {
        this(containerId, inventory, resolveContainer(inventory, position), position,
                resolveType(inventory, position), slots, true);
    }

    private static Container resolveContainer(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof ExtensionAddonBlockEntity blockEntity) {
            return blockEntity;
        }
        // Fallback so a missing block entity can never crash the client. It has the same size as the
        // block entity's storage, so the slot indices (including the reserved one) stay the same.
        return new SimpleContainer(ExtensionAddonBlockEntity.STORAGE_SIZE);
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

    /** Position of the addon this menu belongs to. */
    public BlockPos position() {
        return position;
    }

    /** Level this menu's block entity lives in, or {@code null} while it is not placed. */
    @Nullable
    private Level containerLevel() {
        if (container instanceof ExtensionAddonBlockEntity blockEntity) return blockEntity.getLevel();
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var moved = ItemStack.EMPTY;
        var slot = slots.get(index);
        // The plugin slots keep their indices 0 .. pluginSlots - 1; the reserved slot follows them, and
        // the player inventory keeps being the last 36 slots.
        var pluginSlots = layout.slots();
        var reservedSlot = pluginSlots;
        var inventoryStart = pluginSlots + 1;

        if (slot.hasItem()) {
            var stack = slot.getItem();
            moved = stack.copy();

            if (index < pluginSlots || index == reservedSlot) {
                // addon slot (plugin or reserved) -> player inventory
                if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                // Player inventory -> plugin slots only: exactly the slots this addon accepts, and never
                // the reserved slot, so shift clicking behaves the same as it did before that slot existed.
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
