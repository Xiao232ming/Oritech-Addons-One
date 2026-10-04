package io.github.xiao232ming.oritechaddonsone.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionTransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Menu of the Extension Addons: the plugin slots of this {@link ExtensionAddonType} (their amount is
 * configurable, 1-36, and sent along when the menu is opened), the reserved single item slot of the
 * wireless page and then the regular player inventory.
 * <p>
 * All pages of the GUI share this one menu, so all slot groups exist at all times; which of them the
 * player can use is decided by {@linkplain Slot#isActive() the slot's own activity}, which the screen
 * sets from the page it currently shows - the plugin slots are active on the plugin page only and the
 * reserved slot on the wireless page only, so the Item Proxy page (which owns no menu slot) shows neither
 * group's items. The server never sees that flag (it only changes what is drawn and clicked), so the
 * plugin page behaves exactly as it did before - and the reserved slot sits outside the plugin grid, so
 * the two groups cannot overlap even while both are active.
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
     * force load state are resolved from the blocks; on the client they come from what the server sent (the
     * addons are plain block entities, so their data is not synced by itself).
     */
    private final boolean clientSide;

    /** Machine a wireless addon is linked to, or {@code null} (wired addons are never linked). */
    @Nullable
    private BlockPos linkedMachine;
    /** Translation key of that machine's display name, or {@code null} while it is unknown. */
    @Nullable
    private String linkedMachineNameKey;

    /**
     * The machine 传输插件's page renders, as the server resolved it when this menu was opened, or {@code null}
     * while this block serves none.
     * <p>
     * It is sent through the menu-open buffer for the same reason {@link #linkedMachine} is: the page draws a 3D
     * model of that machine, and the machine is server-only knowledge. Both the addon and the placed plugin keep it
     * in a controller <em>offset</em>, which is plain block entity save data and never reaches a client, so the
     * client's own {@code servedMachinePos()} answers {@code null} even for a block that really serves a machine -
     * which is what made the page claim "the machine this plugin serves is not loaded" about a machine that was
     * loaded all along.
     * <p>
     * Unlike the wireless link it is set on the server side too, so both sides read the same field and
     * {@link #transferMachinePos()} needs no side check.
     */
    @Nullable
    private BlockPos servedMachine;

    /** Client side: true while the screen shows the plugin page, which is what enables the plugin slots. */
    private boolean pluginPageActive = true;
    /** Client side: true while the screen shows the wireless page, which is what enables its own slot. */
    private boolean wirelessPageActive;
    /**
     * Client side: true while the player's own inventory is usable. It is turned off only while the Item
     * Proxy page shows its configuration panel, which is an opaque modal step over the whole panel - the
     * panel is painted in the background layer, so the inventory's frames and items would otherwise be
     * drawn over it. The slot positions are untouched; this is the same display only switch the page groups
     * use, and it is what a full screen configuration page like Oritech's own inventory proxy screen shows:
     * the slots of the machine, nothing of the player's.
     */
    private boolean playerSlotsActive = true;
    /** Client side copy of {@link #targetChunkForceLoadedSlot} (the server computes the value itself). */
    private boolean targetChunkForceLoaded;

    /**
     * Container data slot 0: whether the chunk of the connected machine is force loaded, i.e. kept loaded
     * without a player nearby. Both variants answer it - a wired addon by the chunk of the machine it is
     * attached to, a dock by the chunk of the machine it is linked to - see
     * {@link ExtensionAddonBlockEntity#isTargetChunkForceLoaded()}.
     * <p>
     * Oritech's synced fields would need a networked block entity, and the addons are plain ones, so the
     * value is published through vanilla's container data instead: the server polls it on every menu tick
     * and sends it only when it changed, which is the same "resolve it on the server, tell the client when
     * it changes" shape the rest of this mod uses for GUI values.
     */
    private final DataSlot targetChunkForceLoadedSlot = new DataSlot() {
        @Override
        public int get() {
            if (clientSide) return targetChunkForceLoaded ? 1 : 0;
            return container instanceof ExtensionAddonBlockEntity addon && addon.isTargetChunkForceLoaded() ? 1 : 0;
        }

        @Override
        public void set(int value) {
            targetChunkForceLoaded = value != 0;
        }
    };

    /** Client side copy of the per-face proxy bindings, one value per {@link Direction#values()} entry. */
    private final int[] syncedProxyFaces = new int[Direction.values().length];
    /** One container data slot per face: the proxy binding of that face (see {@link #proxyFaceSlot}). */
    private final DataSlot[] proxyFaceSlots = new DataSlot[Direction.values().length];

    /**
     * Container data slot of one face: the machine slot that face proxies plus one, or {@code 0} while the
     * face proxies nothing - container data only carries non-negative values, hence the offset.
     * <p>
     * The bindings live in the block entity's save data, which only the server writes, and the addons are
     * plain block entities rather than networked ones, so nothing of them reaches a client on its own. The
     * server therefore publishes one value per face here and vanilla keeps the client's copy up to date,
     * exactly like {@link #targetChunkForceLoadedSlot}. That is what makes the page's counter, its green
     * face markers and the selection plate of an already configured face correct on the client, and what
     * keeps a binding visible when the configuration page of that face is reopened.
     */
    private DataSlot proxyFaceSlot(Direction face) {
        return proxyFaceSlots[face.ordinal()];
    }

    /** Creates the container data slot of one face; see {@link #proxyFaceSlot(Direction)}. */
    private DataSlot createProxyFaceSlot(Direction face) {
        return new DataSlot() {
            @Override
            public int get() {
                if (clientSide) return syncedProxyFaces[face.ordinal()];
                var blockEntity = blockEntity();
                if (blockEntity == null) return 0;
                var slot = blockEntity.proxyFaces().slotOf(face);
                return slot == null ? 0 : slot + 1;
            }

            @Override
            public void set(int value) {
                syncedProxyFaces[face.ordinal()] = value;
            }
        };
    }

    /** Client side copy of the per-face transfer modes, one value per {@link Direction#values()} entry. */
    private final int[] syncedTransferFaces = new int[Direction.values().length];
    /** One container data slot per face: the transfer mode of that face (see {@link #transferFaceSlot}). */
    private final DataSlot[] transferFaceSlots = new DataSlot[Direction.values().length];

    /** Client side copy of the faces a placed transfer addon hangs on (bitmask). */
    private int syncedAttachedTransferFaces = 0;

    /**
     * Container data slot of the faces a placed transfer addon hangs on this block, as a bitmask over
     * {@link Direction#values()}. The block entity recomputes it every server tick, so publishing it here is
     * what lets the page draw the gold border of the face the plugin hangs on (and decide whether the page
     * exists at all) without the client having to look at the world itself.
     */
    private final DataSlot attachedTransferFacesSlot = new DataSlot() {
        @Override
        public int get() {
            if (clientSide) return syncedAttachedTransferFaces;
            var blockEntity = blockEntity();
            return blockEntity == null ? 0 : blockEntity.attachedTransferFaces();
        }

        @Override
        public void set(int value) {
            syncedAttachedTransferFaces = value;
        }
    };

    /**
     * Container data slot of one face: the ordinal of the {@link TransferMode} that face transfers with, i.e.
     * {@code 0} while it transfers nothing. Published exactly like the proxy bindings
     * ({@link #proxyFaceSlot}), so the net's colours and the page's counter follow what the server wrote.
     */
    private DataSlot transferFaceSlot(Direction face) {
        return transferFaceSlots[face.ordinal()];
    }

    /** Creates the container data slot of one face; see {@link #transferFaceSlot(Direction)}. */
    private DataSlot createTransferFaceSlot(Direction face) {
        return new DataSlot() {
            @Override
            public int get() {
                if (clientSide) return syncedTransferFaces[face.ordinal()];
                var blockEntity = blockEntity();
                if (blockEntity == null) return 0;

                var modes = blockEntity.transferModes();
                return TransferFaceModes.pack(modes.modeOf(face), modes.automationOf(face));
            }

            @Override
            public void set(int value) {
                syncedTransferFaces[face.ordinal()] = value;
            }
        };
    }

    /** Server side constructor. */
    public ExtensionAddonMenu(int containerId, Inventory inventory, ExtensionAddonBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, blockEntity.getBlockPos(), blockEntity.pluginType(),
                blockEntity.getContainerSize(), false);

        if (blockEntity instanceof WirelessExtensionAddonBlockEntity dock) {
            this.linkedMachine = dock.linkedMachine();
        }
        // Resolved here, where the world is: 传输插件's page needs the machine behind the plugin's host extender as
        // well, and neither the extender's controller position nor the plugin's own reaches a client. The field is
        // what the client receives, so it is set on both sides and not only in the buffer (see #servedMachine).
        this.servedMachine = blockEntity.servedMachinePos();
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
     * True while the chunk of the connected machine is force loaded, i.e. kept loaded without a player
     * nearby. On the server this is the value of this tick, on the client the value the server last sent.
     */
    public boolean targetChunkForceLoaded() {
        return targetChunkForceLoadedSlot.get() != 0;
    }

    /** Index of the reserved single item slot of the wireless page. */
    public int reservedSlot() {
        return layout.slots();
    }

    /**
     * Whether the plugin page is the page the screen shows. Only ever set on the client: the server has no
     * idea which page is open, and every slot exists on both sides either way.
     * <p>
     * It defaults to {@code true}, so a menu whose screen was never initialised behaves like the GUI did
     * before the page framework: the plugin slots - the slots that really hold the addons - stay usable.
     */
    public boolean pluginPageActive() {
        return pluginPageActive;
    }

    /**
     * Whether the wireless page is the page the screen shows. Only ever set on the client: the server has
     * no idea which page is open, and every slot exists on both sides either way.
     */
    public boolean wirelessPageActive() {
        return wirelessPageActive;
    }

    /**
     * Whether the player's own inventory slots are usable. Only ever set on the client, and only off while the
     * player's inventory is not part of the screen: a page's configuration panel is open over the whole panel
     * (see {@link #setPlayerSlotsActive}), or the visible page is the whole page - the 传输插件 page, or the
     * placed plugin's own screen.
     */
    public boolean playerSlotsActive() {
        return playerSlotsActive;
    }

    /** Called by the screen whenever the selected page changes, so the pages' slots become active. */
    public void setPluginPageActive(boolean active) {
        this.pluginPageActive = active;
    }

    /** Called by the screen whenever the selected page changes, so the pages' slots become active. */
    public void setWirelessPageActive(boolean active) {
        this.wirelessPageActive = active;
    }

    /**
     * Called by the screen whenever the player's inventory stops or starts being part of the screen: a page's
     * configuration panel is an opaque modal step over the whole panel, and the 传输插件 page is a whole page
     * of its own, so the player's inventory is drawn - and clickable - only while neither is the case.
     */
    public void setPlayerSlotsActive(boolean active) {
        this.playerSlotsActive = active;
    }

    private ExtensionAddonMenu(int containerId, Inventory inventory, Container container, BlockPos position,
            ExtensionAddonType type, int slots, boolean clientSide) {
        super(OritechAddonsOne.EXTENSION_ADDON_MENU.get(), containerId);
        this.container = container;
        this.position = position;
        this.type = type;
        this.layout = ExtensionAddonLayout.forType(type, slots);
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
                 * The plugin slots are only usable while the plugin page is shown. Vanilla asks a slot for
                 * this before it draws it and before it hands a click to it, so no plugin item can appear
                 * on another page (the wireless page's text, the Item Proxy page's net) and no click can
                 * reach a plugin slot from there. It is display and clicking only - nothing of it reaches
                 * the server.
                 */
                @Override
                public boolean isActive() {
                    return pluginPageActive;
                }
            });
        }

        // The reserved slot of the wireless page: one item, nothing else. It sits right behind the plugin
        // slots in this menu, but outside their grid, so it can never cover or steal a click from them.
        // Its container index is the block entity's own reserved index, which is not a plugin slot.
        addSlot(new ReservedItemSlot(container, ExtensionAddonBlockEntity.RESERVED_SLOT,
                ExtensionAddonLayout.RESERVED_SLOT_X, ExtensionAddonLayout.RESERVED_SLOT_Y, this::wirelessPageActive));

        // The player's own inventory: three rows and the hotbar. They are hidden - only drawn and clicked
        // while active - while the Item Proxy page's configuration panel covers the panel; their positions
        // never change.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(playerSlot(inventory, column + row * 9 + 9, 8 + column * 18,
                        layout.playerRowsY() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(playerSlot(inventory, column, 8 + column * 18, layout.hotbarY()));
        }

        addDataSlot(targetChunkForceLoadedSlot);

        // One data slot per face, so the client's page sees the bindings the server wrote: the counter, the
        // green marker on a configured face and the selection plate all read them (see #proxyFaceSlot).
        for (var face : Direction.values()) {
            proxyFaceSlots[face.ordinal()] = createProxyFaceSlot(face);
            addDataSlot(proxyFaceSlots[face.ordinal()]);
        }

        // Same for the transfer modes of the Extension Transfer page (see #transferFaceSlot).
        for (var face : Direction.values()) {
            transferFaceSlots[face.ordinal()] = createTransferFaceSlot(face);
            addDataSlot(transferFaceSlots[face.ordinal()]);
        }

        // and the faces a placed transfer addon hangs on, which the page marks in gold
        addDataSlot(attachedTransferFacesSlot);
    }

    /** One slot of the player's own inventory; see {@link #playerSlotsActive}. */
    private Slot playerSlot(Container inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean isActive() {
                return playerSlotsActive;
            }
        };
    }

    /** Client side constructor; block position and slot count are sent along when the menu is opened. */
    public ExtensionAddonMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readVarInt());

        // the link of a wireless addon is sent along with the menu, so the GUI can show it right away
        if (buffer.readBoolean()) {
            this.linkedMachine = buffer.readBlockPos();
        }
        // the machine 传输插件's page renders, resolved by the server and sent along for the same reason: it is
        // buildable from server-only controller offsets (see #servedMachine)
        if (buffer.readBoolean()) {
            this.servedMachine = buffer.readBlockPos();
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

    /**
     * The addon block entity this menu shows, or {@code null} while it cannot be resolved (client side,
     * chunk not loaded, block already broken). The Item Proxy page reads its per-face bindings and the
     * machine it works on from here; both fail safe to "nothing configured" without it.
     */
    @Nullable
    public ExtensionAddonBlockEntity blockEntity() {
        return container instanceof ExtensionAddonBlockEntity blockEntity ? blockEntity : null;
    }

    /**
     * Number of inventory proxy addons stored in the addon, i.e. the maximum number of configurable faces
     * of the Item Proxy page. Answered from the client side container, which holds the same items as the
     * server's one because the plugin slots are menu slots.
     */
    public int inventoryProxyCount() {
        var blockEntity = blockEntity();
        return blockEntity == null ? 0 : blockEntity.inventoryProxyCount();
    }

    /** True while the Item Proxy page has anything to show, i.e. while an inventory proxy addon is stored. */
    public boolean hasInventoryProxy() {
        return inventoryProxyCount() > 0;
    }

    /** True while the given face of the addon is bound to a machine inventory slot. */
    public boolean isProxyFaceConfigured(Direction face) {
        return proxySlotOf(face) != null;
    }

    /**
     * Slot the given face proxies, or {@code null} while it proxies nothing.
     * <p>
     * Read from that face's container data slot ({@link #proxyFaceSlot}), not from the block entity
     * directly: the server side answers the live binding, the client the copy the container keeps in sync,
     * so an open page sees the binding the moment the server has written it.
     */
    @Nullable
    public Integer proxySlotOf(Direction face) {
        var value = proxyFaceSlot(face).get();
        return value <= 0 ? null : value - 1;
    }

    /** Number of currently configured faces, i.e. the "x" of the page's counter. */
    public int configuredProxyFaces() {
        var count = 0;
        for (var face : Direction.values()) {
            if (proxySlotOf(face) != null) count++;
        }
        return count;
    }

    /**
     * Number of transfer addons stored in the addon. Unlike the inventory proxy this is not a limit on the
     * configurable faces - all six faces may transfer - it only decides whether the page exists at all.
     */
    public int transferAddonCount() {
        var blockEntity = blockEntity();
        return blockEntity == null ? 0 : blockEntity.transferAddonCount();
    }

    /**
     * True while the Extension Transfer page has anything to show: a transfer addon stored in the plugin
     * slots, or one placed on a block that hosts placed plugins, which reaches that block's faces just the
     * same.
     */
    public boolean hasExtensionTransferAddon() {
        return transferAddonCount() > 0 || attachedTransferFaces() != 0;
    }

    /**
     * True while the 传输插件 page has anything to show, i.e. while 传输插件 is stored in this
     * block's plugin slots.
     * <p>
     * Read from the container exactly like {@link #transferPluginCount()}, so both screens get their page list
     * from the same contents: the addon's own screen offers the 传输插件 tab as soon as one is put in, and that
     * plugin's <b>placed</b> screen never takes this path at all (it is narrowed to that one page, see
     * {@link #transferOnly()}).
     */
    public boolean hasTransferAddon() {
        var blockEntity = blockEntity();
        return blockEntity != null && blockEntity.hasTransferAddon();
    }

    /**
     * True while this menu belongs to 扩展传输插件 that is <b>placed</b> on an Oritech machine extender.
     * <p>
     * Such a plugin is not a container of plugins and has no machine of its own: the only thing it has to
     * offer is the transfer page of the extender's faces. The page list is therefore narrowed to that one
     * page (see {@code AddonPageRegistry#pages}), so the plugin grid, the wireless page and the Item Proxy
     * page stay out of the GUI.
     * <p>
     * The question is answered from the block entity, which exists on both sides: the server has it while
     * the GUI is open, and the client has it because the plugin is a block in its own level.
     */
    public boolean extensionTransferOnly() {
        return blockEntity() instanceof ExtensionTransferAddonBlockEntity;
    }

    /**
     * True while this menu belongs to 传输插件. Such a plugin is not a container of plugins either and has
     * exactly one page: the rotatable 3D model of the machine it serves. The page list is therefore narrowed to
     * that one page (see {@code AddonPageRegistry#pages}), so the plugin grid, the wireless page and the Item
     * Proxy page stay out of the GUI.
     * <p>
     * Like {@link #extensionTransferOnly()} the question is answered from the block entity, which exists on both
     * sides - the server has it while the GUI is open, the client has it because the plugin is a block in its own
     * level - so the screen and the page registry need no separate flag and no second menu class.
     */
    public boolean transferOnly() {
        return blockEntity() instanceof TransferAddonBlockEntity;
    }

    /**
     * Faces of this block a placed transfer addon hangs on, as a bitmask over {@link Direction#values()}; the
     * page draws its gold border from this. Read from the block entity's container data slot, so the client
     * sees what the server found.
     */
    public int attachedTransferFaces() {
        return attachedTransferFacesSlot.get();
    }

    /**
     * Mode the given face transfers with, read from that face's container data slot so the client sees what
     * the server wrote; {@link TransferMode#NONE} while that face transfers nothing.
     */
    public TransferMode transferMode(Direction face) {
        return TransferFaceModes.modeOf(transferFaceSlot(face).get());
    }

    /**
     * True while that face moves its items by itself instead of only offering them to pipes, i.e. the
     * automation switch of the configuration page. Read from the same container data slot as the mode.
     */
    public boolean transferAutomation(Direction face) {
        return TransferFaceModes.automationOf(transferFaceSlot(face).get());
    }

    /** Number of faces that transfer something, i.e. the "x" of the transfer page's counter. */
    public int transferFaces() {
        var count = 0;
        for (var face : Direction.values()) {
            if (transferMode(face) != TransferMode.NONE) count++;
        }
        return count;
    }

    /** The block this addon is, used by the Item Proxy page to draw its six faces. */
    public Block addonBlock() {
        var blockEntity = blockEntity();
        if (blockEntity != null) return blockEntity.transferPageBlockState().getBlock();
        return wireless ? type.wirelessBlock() : type.wiredBlock();
    }

    /**
     * The block state the pages draw their net from, or {@code null} while it cannot be resolved.
     * <p>
     * It is not necessarily the addon's own state: a transfer addon placed on an Oritech machine extender
     * shows the extender's faces, so the net says where the items go rather than what the plugin looks like
     * (see {@link ExtensionAddonBlockEntity#transferPageBlockState()}). For every other block - and on the
     * Item Proxy page, which only exists while a proxy addon is stored, i.e. never for a placed plugin -
     * this is the addon's own state and therefore what it always was.
     */
    @Nullable
    public BlockState addonBlockState() {
        var blockEntity = blockEntity();
        return blockEntity == null ? null : blockEntity.transferPageBlockState();
    }

    /** Level this menu's block entity lives in, or {@code null} while it is not placed. */
    @Nullable
    private Level containerLevel() {
        if (container instanceof ExtensionAddonBlockEntity blockEntity) return blockEntity.getLevel();
        return null;
    }

    // ------------------------------------------------------------------ the machine of the transfer page

    /**
     * The machine the transfer page renders, or {@code null} while this menu's block serves none.
     * <p>
     * Which block the answer belongs to depends on which screen shows the page, and that is exactly what the menu
     * addresses:
     * <ul>
     *     <li>on 传输插件's <b>own</b> screen the menu belongs to the placed plugin, so the answer is the machine it
     *     serves - the machine it hangs on directly, or the one behind the extender it hangs on,</li>
     *     <li>on an <b>Extension Addon's</b> screen the menu belongs to the addon, so the answer is the machine that
     *     addon works on - the same machine the stored plugin would work on, and the same one the addon's other face
     *     pages configure.</li>
     * </ul>
     * Both are {@link ExtensionAddonBlockEntity#servedMachinePos()}, resolved by the server when the menu was opened
     * and sent along with it ({@link #servedMachine}). The page therefore needs no case per screen: it asks the menu,
     * and the menu answers with what the server resolved for the block the screen was opened for.
     * <p>
     * It deliberately does <b>not</b> fall back to the block entity: {@code servedMachinePos()} is built from the
     * controller offset Oritech writes into the block it claimed, and on a client that offset is zero, so the block
     * entity answers {@code null} for a block that really serves a machine. The page would report "no machine" for
     * every placement, which is the defect this field exists to fix. The block entity's own answer stays what it
     * always was - the server's, authoritative for the capability and automation paths.
     */
    @Nullable
    public BlockPos transferMachinePos() {
        return servedMachine;
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

    // ------------------------------------------------------------------ the map of a transfer plugin stored inside

    /**
     * Position of the 传输插件 whose cell-face map this menu already sent to its player, or {@code null} while
     * none was sent yet.
     * <p>
     * Server side only - {@link #broadcastChanges()} returns on the client before it is read - and per menu,
     * i.e. per player, which is what the map's second source needs: {@code TransferNetworking#sendFaceModes(Level,
     * BlockPos)} sends to every player whose open menu belongs to that block, so one flag per menu sends one
     * packet per player instead of one per player per tick.
     */
    @Nullable
    private BlockPos sentTransferMap;

    /**
     * Sends the cell-face map of a 传输插件 stored in this addon's slots to this player, once per plugin.
     * <p>
     * <b>Why this is needed at all.</b> The map - what every cell-face of the machine is configured to do - is
     * the server's, and it cannot travel with the menu: the container data is a fixed set of slots, while a
     * structure has one setting per face of every cell of it (see {@code TransferNetworking.FaceModes}). The
     * placed plugin's own screen therefore sends it right after opening the menu
     * ({@code TransferAddonBlock#openPluginMenu}), and every accepted change is answered with the whole map
     * again. An addon's screen had neither: opening it sent no map, so a player who configured a face saw
     * nothing - no colour on the model, and no setting after reopening the GUI - although the server had
     * written it and the automation moved items by it.
     * <p>
     * <b>Why it runs here.</b> {@link #broadcastChanges()} is the server's per tick hook of an open menu, which
     * is exactly the moment the map has to be sent - and the moment a plugin can have appeared: the page only
     * exists while 传输插件 is in the slots, and a plugin put in afterwards was not there when the menu was
     * opened. It also covers the client, whose screen asks for the page list a moment after the map arrives.
     * <p>
     * Nothing is sent while the block does not hold a transfer plugin or holds one that serves no machine -
     * {@code sendFaceModes} answers nothing for a plain block entity, and a map that is empty costs one packet
     * that says so.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        var level = containerLevel();
        if (level == null || level.isClientSide()) return;

        // the machine of whichever transfer plugin this menu's block holds or hosts: the settings belong to the
        // machine and a block that serves none has no map to send
        var machine = blockEntity() == null ? null : blockEntity().servedMachinePos();
        if (machine == null) {
            // INFO while "the config disappears when the UI closes" is being chased: a menu whose block names no
            // machine sends no map, so the page comes back empty although the server still has the settings
            OritechAddonsOne.LOGGER.info("[transfer] menu {} has no machine to send a map for", position);
            return;
        }
        if (machine.equals(sentTransferMap)) return;

        sentTransferMap = machine;
        TransferNetworking.sendFaceModes(level, machine);
    }

    /**
     * True while this menu is the one showing the 传输插件 standing at {@code pluginPos}, i.e. while that
     * plugin is stored in this addon's plugin slots.
     * <p>
     * It is what lets the map of a <b>stored</b> plugin find its way to the right players
     * ({@code TransferNetworking#sendFaceModes(Level, BlockPos)}): such a plugin is drawn by this addon's menu
     * rather than by a menu of its own, so the map's recipient cannot be found by comparing the menu's position
     * with the plugin's.
     */
    public boolean holdsTransferPlugin(BlockPos pluginPos) {
        var level = containerLevel();
        if (level == null) return false;

        if (level.getBlockEntity(pluginPos) instanceof TransferAddonBlockEntity plugin
                && plugin.servedMachinePos() != null) {
            return true;
        }

        // A plugin stored in this addon's slots is a BlockEntity reached through a block next to it
        // (see menuBlockPos), and on the client that block entity cannot answer servedMachinePos() at all: the
        // plugin's controller offset is plain server side save data, so the client's copy answers null - exactly
        // like the addon's own offset does (see ExtensionAddonMenu#servedMachine). Asking only for
        // "serves a machine" therefore failed on the client and made transferPluginPos() fall back to the
        // addon's own position, which is a position no transfer plugin stands at: every setting was then sent
        // for the addon and dropped by the server.
        return isTransferPluginBlock(level, pluginPos);
    }

    /** True while a transfer plugin block - with or without a machine - stands at {@code pos}. */
    private static boolean isTransferPluginBlock(Level level, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof TransferAddonBlockEntity;
    }

    /**
     * The position of the 传输插件 whose page this menu is showing, i.e. the block the page's settings belong
     * to and the key its map of configured cell-faces is held under.
     * <p>
     * <b>It is not always {@link #position()}.</b> For the plugin's own screen the two are the same - that menu
     * belongs to the plugin. For an Extension Addon's screen the menu is opened on the <b>addon</b>, while the
     * page draws and configures the plugin standing next to it ({@code TransferAddonBlockEntity}), and the
     * server keys everything it sends by that plugin's position
     * ({@code TransferNetworking.FaceModes}). A page that asked {@code position()} for the map therefore looked
     * up a key nothing is ever stored under, which is why a stored plugin drew no configured face - and why
     * every setting the player made was sent with the wrong position and refused.
     * <p>
     * The plugin's own position is derivable on both sides: a placed plugin's menu is opened on it, and a stored
     * one stands in a cell next to the addon that holds it, so the six neighbours are asked.
     */
    public BlockPos transferPluginPos() {
        return holdsTransferPlugin(position) ? position : menuBlockPos();
    }

    /**
     * Position of the transfer plugin of this addon, or this menu's own position while there is none.
     * <p>
     * A stored plugin is a <b>block</b> of the world ({@code oritechaddonsone:transfer_addon}) standing next to
     * the addon whose slot holds it, so the cell is one of the addon's six neighbours. The neighbours are
     * checked rather than assumed, because a menu whose block holds no transfer plugin has no plugin position
     * to report and has to keep its own.
     */
    private BlockPos menuBlockPos() {
        for (var face : Direction.values()) {
            var candidate = position.relative(face);
            if (holdsTransferPlugin(candidate)) return candidate;
        }
        return position;
    }
}
