package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.energy.containers.DelegatingEnergyStorage;
import rearth.oritech.api.item.ItemApi;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity.RedstoneControllable;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.OritechConfig;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MachineAddonController.BaseAddonData;
import rearth.oritech.util.ScreenProvider;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.addon.AddonBonusSource;
import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;
import io.github.xiao232ming.oritechaddonsone.block.AddonDetailProvider;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.wireless.AnchorForceLoad;
import io.github.xiao232ming.oritechaddonsone.wireless.ForceLoadedChunks;
import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinks;

/**
 * Block entity of the Extension Addons (shared by type I, type II and type III).
 * <p>
 * It stores up to {@link ExtensionAddonLayout#MAX_SLOTS} stacks of Oritech plugin items and forwards
 * their combined stats to the machine this block is connected to. The forwarding works by letting the
 * machine run its normal addon scan (this block reports neutral stats, so it does not change anything
 * by itself) and then merging the combined plugin stats into the machine's addon data.
 * {@link #setControllerPos(BlockPos)} is called by Oritech at the end of every addon scan, which is
 * exactly the point where the machine's own data is already computed and our contribution has to be
 * added on top.
 * <p>
 * Only the plugins accepted by the block's {@link ExtensionAddonType} may be inserted. Plugins whose
 * behaviour Oritech decides by block type (quarry, silk touch, fluid, crop filter, ...) are forwarded
 * by replaying the machine's {@code getAdditionalStatFromAddon} hook for every stored plugin, so they
 * work as if they were attached directly. While a machine acceptor plugin is stored, this block also
 * offers an energy input that feeds the machine.
 * <p>
 * Since type II also accepts Oritech's inventory proxy addon, this block can additionally proxy the
 * machine's item inventory to the outside world: see {@link #getInventoryStorage(Direction)},
 * {@link MachineFaceStorage} and {@link ProxyFaceBindings}. Which face proxies which machine slot is
 * configured on the GUI's Item Proxy page and stored here, so it survives a save and is server
 * authoritative.
 */
public class ExtensionAddonBlockEntity extends AddonBlockEntity
        implements Container, MenuProvider, EnergyApi.BlockProvider, AddonBonusSource, ItemApi.BlockProvider {

    private final ExtensionAddonType type;
    /**
     * Index of the reserved single item slot of the wireless page, right behind the plugin storage. The
     * plugin slots keep their indices {@code 0 .. getContainerSize() - 1}, and the reserved item rides
     * along in the normal container and save format, so nothing about the plugin storage changes.
     */
    public static final int RESERVED_SLOT = ExtensionAddonLayout.MAX_SLOTS;
    /** Size of the backing storage: the plugin storage plus the reserved single item slot. */
    public static final int STORAGE_SIZE = RESERVED_SLOT + 1;

    /**
     * Storage is always {@link ExtensionAddonLayout#MAX_SLOTS} plugin slots (plus
     * {@linkplain #RESERVED_SLOT one} for the wireless page), the config only decides how many of these
     * slots are usable. That way lowering the configured amount never destroys stored plugins.
     */
    private final NonNullList<ItemStack> items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);

    /** Feeds the connected machine while an acceptor plugin is inserted. */
    private final DelegatingEnergyStorage delegatedStorage =
            new DelegatingEnergyStorage(this::getMainStorage, this::isEnergyInputActive);

    /**
     * Addon data this block wrote into its machine last, used to tell whether the client has to be told
     * about a change (the machine only sends its addon data when a GUI is opened).
     */
    @Nullable
    private BaseAddonData lastApplied;

    /** Last redstone state we forwarded to the machine. */
    private boolean lastRedstonePowered;
    /** True while we forwarded "a control unit is controlling this machine", so it can be released. */
    private boolean redstoneApplied;

    public ExtensionAddonBlockEntity(BlockPos pos, BlockState state) {
        this(OritechAddonsOne.EXTENSION_ADDON_ENTITY.get(), pos, state);
    }

    /**
     * Constructor for subclasses that use their own block entity type (the wireless extension addons).
     * The plugin type is always read from the owning block.
     */
    protected ExtensionAddonBlockEntity(BlockEntityType<?> entityType, BlockPos pos, BlockState state) {
        super(entityType, pos, state);
        this.type = state.getBlock() instanceof AddonDetailProvider provider
                ? provider.getType()
                : ExtensionAddonType.TYPE_1;
    }

    // ------------------------------------------------------------------ plugin handling

    /** The plugin type of this block (decided by the block that created it). */
    public ExtensionAddonType pluginType() {
        return type;
    }

    /** Number of usable slots of this block, taken from the mod config. */
    public int configuredSlots() {
        return Config.slots(type);
    }

    /** True only for the Oritech plugins this block accepts. */
    public boolean isPlugin(ItemStack stack) {
        return type.accepts(stack);
    }

    private MachineAddonBlock.AddonSettings settingsOf(ItemStack stack) {
        if (!isPlugin(stack)) return null;
        return ((MachineAddonBlock) ((BlockItem) stack.getItem()).getBlock()).getAddonSettings();
    }

    /**
     * True while an acceptor plugin is stored, which turns this block into an energy input. Any acceptor
     * of any tier counts (Oritech's own one and e.g. the tiered ones from Oritech Things), so they all
     * have the same effect as inserting Oritech's machine acceptor.
     */
    public boolean hasAcceptorPlugin() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (ExtensionAddonType.categoryOf(blockItem.getBlock()) == ExtensionAddonType.StatCategory.ACCEPTOR) {
                return true;
            }
        }
        return false;
    }

    /** True while a control unit (redstone) plugin is stored, which lets redstone control the machine. */
    public boolean hasRedstonePlugin() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (blockItem.getBlock() == BlockContent.MACHINE_REDSTONE_ADDON) return true;
        }
        return false;
    }

    /** True while the reserved slot of the wireless page holds a chunk anchor plugin. */
    public boolean holdsAnchor() {
        var stack = items.get(RESERVED_SLOT);
        return !stack.isEmpty() && stack.getItem() == OritechAddonsOne.CHUNK_ANCHOR_ADDON_ITEM.get();
    }

    // ------------------------------------------------------------------ connected machine (wireless page)

    /**
     * Position of the machine this addon works on, or {@code null} while it is not connected to one.
     * <p>
     * A wired addon is claimed by a machine, which writes its position as the controller position; while
     * that never happened the controller position is this block's own position, which means "nothing".
     * The wireless dock overrides this with the position it was linked to.
     */
    @Nullable
    public BlockPos connectedMachinePos() {
        var controller = getControllerPos();
        return controller == null || controller.equals(worldPosition) ? null : controller;
    }

    /**
     * Translation key of the connected machine's display name, or {@code null} while it is unknown: the
     * addon is not connected, the machine's chunk is not loaded or nothing but air stands there.
     * <p>
     * The name is resolved on the server - where the machine's chunk is loaded whenever the machine is -
     * and sent to the client with the menu, so the GUI shows the right name even when the client has the
     * machine's chunk unloaded and therefore cannot look the block up itself.
     */
    @Nullable
    public String connectedMachineNameKey() {
        return nameKeyAt(level, connectedMachinePos());
    }

    /** Translation key of the block at the given position, or {@code null} while it cannot be resolved. */
    @Nullable
    public static String nameKeyAt(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;

        var state = level.getBlockState(pos);
        return state.isAir() ? null : state.getBlock().getDescriptionId();
    }

    /**
     * True while the chunk of the connected machine is force loaded, i.e. while something keeps that chunk
     * loaded on purpose - see {@link ForceLoadedChunks} for what counts (vanilla {@code /forceload}, the
     * spawn area and NeoForge force load tickets from other mods).
     * <p>
     * It is the one value the GUI's status badge shows for both variants. False while there is no machine at
     * all (nothing is kept loaded then), while the machine is in an unloaded chunk and while the chunk is
     * only loaded because a player is nearby or because this mod looked the target up - looking a block up
     * loads its chunk through vanilla's short lived {@code unknown} ticket, which is not a force load.
     * Read on the server, see {@code ExtensionAddonMenu#targetChunkForceLoaded()}.
     */
    public boolean isTargetChunkForceLoaded() {
        return ForceLoadedChunks.isForceLoaded(level, connectedMachinePos());
    }

    // ------------------------------------------------------------------ storage bonuses

    /**
     * Item slot bonus this addon gives to every slot of its machine: one warehouse addon per stored item,
     * so a stack of {@code n} plugins counts {@code n} times.
     * <p>
     * The plugin is matched by block identity rather than by category: only this mod's block has the
     * effect implemented (see {@code MachineStorageBonuses}), so a foreign block that happens to be
     * categorised as {@link ExtensionAddonType.StatCategory#WAREHOUSE} must not claim it.
     */
    @Override
    public int oritechaddonsone$itemSlotBonus() {
        return AddonStorageBonus.SLOTS_PER_WAREHOUSE_ADDON * countPlugins(OritechAddonsOne.WAREHOUSE_ADDON.get());
    }

    /** Fluid capacity bonus this addon gives to every tank of its machine, see {@link #oritechaddonsone$itemSlotBonus()}. */
    @Override
    public long oritechaddonsone$fluidCapacityBonus() {
        return AddonStorageBonus.CAPACITY_PER_TANK_ADDON * countPlugins(OritechAddonsOne.TANK_ADDON.get());
    }

    /**
     * Number of stored items of the given plugin block (every slot that holds a stack of it, weighted by
     * the stack size).
     */
    private int countPlugins(Block plugin) {
        var count = 0;

        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (blockItem.getBlock() == plugin) count += stack.getCount();
        }

        return count;
    }

    // ------------------------------------------------------------------ redstone input (control unit plugin)

    /**
     * Polled every server tick by {@link ExtensionAddonBlock#getTicker}. Cheap while nothing is to do:
     * without a stored control unit (and without a state we set earlier) it returns immediately.
     */
    public void serverTickRedstone() {
        if (level == null || level.isClientSide()) return;

        // A chunk anchor in the reserved slot works from here whether or not anything changed: this tick
        // runs on both variants (the wired addon ticks every tick, the wireless dock calls it from its own
        // ticker) and by then the saved link is loaded, which onLoad may be too early for. The lookup is
        // one item slot, and the reconcile behind it is a no-op while the receipt is unchanged.
        if (holdsAnchor()) {
            AnchorForceLoad.register(this);
        }

        var hasPlugin = hasRedstonePlugin();
        if (!hasPlugin && !redstoneApplied) return;

        var powered = isPoweredByRedstone();
        if (powered == lastRedstonePowered && hasPlugin == redstoneApplied) return;

        lastRedstonePowered = powered;
        applyRedstoneSignal(powered);
    }

    /** Redstone signal that has to be honoured: at this block, or at the machine it is attached to. */
    private boolean isPoweredByRedstone() {
        if (level == null) return false;
        return level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(getControllerPos());
    }

    /**
     * Forwards the redstone signal at this block to the connected machine, exactly like Oritech's own
     * control unit plugin does from its {@code neighborChanged} hook: the machine implements
     * {@link RedstoneControllable} and turns itself off while it is powered.
     * <p>
     * Only input control is forwarded. The control unit's mode (input control / comparator output) is
     * stored in its own block entity, which does not exist while it is kept as an item inside this
     * block, so a stored control unit always behaves like the default mode {@code INPUT_CONTROL}.
     */
    public void applyRedstoneSignal(boolean powered) {
        if (level == null || level.isClientSide()) return;
        if (!(level.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller)) return;
        if (!(controller instanceof RedstoneControllable controllable)) {
            OritechAddonsOne.LOGGER.debug("[diag] redstone: {} machine {} is not controllable",
                    worldPosition, getControllerPos());
            return;
        }
        OritechAddonsOne.LOGGER.debug("[diag] redstone: {} powered={} plugin={} applied={} -> {}",
                worldPosition, powered, hasRedstonePlugin(), redstoneApplied, getControllerPos());

        if (hasRedstonePlugin()) {
            redstoneApplied = true;
            controllable.onRedstoneEvent(powered);
            return;
        }

        // Without a stored control unit this block never disabled the machine, so it must not hand it
        // back either: the machine re-scans every addon whenever one is added, removed or changed, and
        // an addon without a control unit would otherwise cancel the redstone control that another
        // addon (the one holding the control unit) had set up.
        if (!redstoneApplied) return;

        releaseRedstoneControl(controller, controllable);
    }

    /**
     * Hands the machine back after the stored control unit went away, either because it was taken out of
     * the inventory or because this block was mined while it was the one disabling the machine.
     */
    private void releaseRedstoneControl(MachineAddonController controller, RedstoneControllable controllable) {
        redstoneApplied = false;

        // Another Extension Addon still holds a control unit: that one owns the redstone state now, so
        // handing the machine back here would switch it on behind its back.
        if (hasOtherControlUnit(controller)) return;

        // A control unit that is attached to the machine directly owns the state, so leave it untouched.
        if (hasAttachedRedstoneAddon(controller)) return;
        controllable.onRedstoneEvent(false);
    }

    /** True while another Extension Addon attached to the same machine holds a control unit. */
    private boolean hasOtherControlUnit(MachineAddonController controller) {
        for (var pos : controller.getConnectedAddons()) {
            if (pos.equals(worldPosition)) continue;
            if (level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity other && other.hasRedstonePlugin()) {
                return true;
            }
        }

        // Wireless extension addons linked to the same machine count as well.
        for (var pos : WirelessLinks.docksOf(level, getControllerPos())) {
            if (level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity other && other.hasRedstonePlugin()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called right before this block is removed: if this addon was the one that disabled the connected
     * machine, the machine has to be released, otherwise it would stay switched off forever.
     */
    public void releaseRedstoneOnRemoval() {
        if (!redstoneApplied) return;
        if (level == null || level.isClientSide()) return;
        if (!(level.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller)) return;
        if (!(controller instanceof RedstoneControllable controllable)) return;

        releaseRedstoneControl(controller, controllable);
    }

    /** True while a vanilla control unit plugin block is attached to the machine directly. */
    private boolean hasAttachedRedstoneAddon(MachineAddonController controller) {
        for (var pos : controller.getConnectedAddons()) {
            if (level.getBlockEntity(pos) instanceof RedstoneAddonBlockEntity) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ inventory proxy (item proxy page)

    /**
     * Which face of this block proxies which slot of the machine inventory. Empty while no inventory
     * proxy addon is stored, which is what turns every face into "not proxying".
     */
    private final ProxyFaceBindings proxyFaces = new ProxyFaceBindings();

    /**
     * One storage per face, created on demand and kept so the capability wrapper Oritech builds for us
     * ({@code NeoforgeItemApiImpl.ContainerStorageWrapper}) always sees the same object. The storages do
     * not cache anything themselves - they resolve the machine inventory, the proxy binding and the transfer
     * mode on every call - so keeping them is only about identity, not about staleness.
     */
    private final java.util.EnumMap<Direction, MachineFaceStorage> faceStorages =
            new java.util.EnumMap<>(Direction.class);

    /** Number of inventory proxy addons stored in this block (a stack of them counts per item). */
    public int inventoryProxyCount() {
        return countPlugins(BlockContent.MACHINE_INVENTORY_PROXY_ADDON);
    }

    /**
     * True while this block holds at least one inventory proxy addon, i.e. while it may proxy items to the
     * outside at all. Type II accepts that addon, so this is what gates every part of the feature.
     */
    public boolean hasInventoryProxy() {
        return inventoryProxyCount() > 0;
    }

    /** True while the Item Proxy page has anything to offer: an inventory proxy addon is stored. */
    public boolean canProxyItems() {
        return hasInventoryProxy();
    }

    /** The per-face bindings of this block; never {@code null}, empty while nothing is configured. */
    public ProxyFaceBindings proxyFaces() {
        return proxyFaces;
    }

    /** Maximum number of configurable faces: one per stored inventory proxy addon. */
    public int maxProxyFaces() {
        return inventoryProxyCount();
    }

    // ------------------------------------------------------------------ item transfer (transfer page)

    /**
     * What each face of this block does with the machine's items - the Extension Transfer feature. Empty
     * while no transfer addon is stored, which is what turns every face into "transfers nothing".
     */
    private final TransferFaceModes transferFaces = new TransferFaceModes();

    /**
     * Faces of this block a placed transfer addon hangs on, as a bitmask over {@link Direction#values()}.
     * A block that can host one reports it through {@link #scanAttachedTransferFaces()}; the wired addons and
     * the wireless dock have nothing to scan, so this stays {@code 0} on all of them. The page draws a gold
     * border around these faces and {@link #hasTransferAddon()} counts them, which is what lets the transfer
     * page show a face that is already taken by the plugin itself.
     */
    private int attachedTransferFaces;

    /** Number of transfer addons stored in this block (a stack of them counts per item). */
    public int transferAddonCount() {
        return countPlugins(OritechAddonsOne.TRANSFER_ADDON.get());
    }

    /**
     * True while this block has a transfer addon at all - one stored inside, or one placed on a block that
     * hosts placed plugins - i.e. while the "Extension Transfer" page has anything to offer. Unlike the
     * inventory proxy there is no per-addon limit here: all six faces may transfer at the same time.
     */
    public boolean hasTransferAddon() {
        return transferAddonCount() > 0 || attachedTransferFaces != 0;
    }

    /**
     * Faces of this block a placed transfer addon hangs on, as a bitmask over {@link Direction#values()};
     * {@code 0} while none does. The page draws its gold border from this.
     */
    public int attachedTransferFaces() {
        return attachedTransferFaces;
    }

    /**
     * The block whose six faces the transfer page unfolds into its net: this block's own faces for every
     * addon and dock, because that is where their transfer faces are.
     * <p>
     * It is an overridable accessor and not {@code getBlockState().getBlock()} inside the page, because a
     * placed transfer addon shows the faces of the <b>host</b> it hangs on - the net, the gold border and the
     * modes are about that block, not about the plugin (see {@code TransferAddonBlockEntity}).
     */
    public Block transferPageBlock() {
        return getBlockState().getBlock();
    }

    /**
     * State of {@link #transferPageBlock()}, or {@code null} while it cannot be resolved. The page needs it
     * (not only the block) because the orientation decides which texture each face is drawn with.
     */
    @Nullable
    public BlockState transferPageBlockState() {
        return getBlockState();
    }

    /**
     * Faces of this block a placed transfer addon hangs on. Neither the wired addons nor the wireless dock
     * host one - a plugin standing on them is an ordinary Oritech addon - so both inherit this "nothing" and
     * only a block entity that really can host a placed plugin overrides it (see
     * {@code TransferAddonBlockEntity}).
     */
    protected int scanAttachedTransferFaces() {
        return 0;
    }

    /**
     * Keeps {@link #attachedTransferFaces} in step with the world, called once per server tick: whatever
     * {@link #scanAttachedTransferFaces()} costs, nothing anywhere else. Losing the last attached plugin
     * drops the settings, exactly like taking the last stored one out of the container does.
     */
    protected void refreshAttachedTransferFaces() {
        var mask = scanAttachedTransferFaces();
        if (mask == attachedTransferFaces) return;

        attachedTransferFaces = mask;
        reconcileTransferModes();
    }

    /** True while this block may transfer the machine's items at all; see {@link #hasTransferAddon()}. */
    public boolean canTransferItems() {
        return hasTransferAddon();
    }

    /** The per-face transfer modes of this block; never {@code null}, empty while nothing is configured. */
    public TransferFaceModes transferModes() {
        return transferFaces;
    }

    /**
     * Sets what one face does with the machine's items and whether it does it on its own, or clears the face
     * again with {@link TransferMode#NONE}. Refused on the client and while no transfer addon is stored.
     */
    public boolean setTransferConfig(Direction face, TransferMode mode, boolean automation) {
        if (level == null || level.isClientSide() || face == null || mode == null) return false;
        if (mode != TransferMode.NONE && !canTransferItems()) return false;

        transferFaces.set(face, mode, automation);
        setChanged();
        return true;
    }

    // ------------------------------------------------------------------ automation of the transfer faces

    /**
     * Moves items for every face whose automation is switched on. Polled once per server tick by
     * {@link ExtensionAddonBlock#getTicker} (and by the wireless dock's own ticker).
     * <p>
     * Cheap while nothing is automated: without a configured face - the common case - it returns immediately,
     * and a face whose automation is off simply keeps offering its inventory to pipes instead of moving items
     * itself.
     */
    public void serverTickTransfer() {
        if (level == null || level.isClientSide()) return;

        // The faces of the block may have changed since the last tick (a plugin that hangs on this block was
        // placed or broken), so this runs before the early return below: on the first tick after a change
        // nothing is configured yet, but the page has to appear or disappear.
        refreshAttachedTransferFaces();

        if (transferFaces.isEmpty() || !canTransferItems()) return;

        for (var face : Direction.values()) {
            var mode = transferFaces.modeOf(face);
            if (mode == TransferMode.NONE || !transferFaces.automationOf(face)) continue;

            var machine = MachineFaceStorage.machineStorage(this);
            if (machine == null) continue;

            // The face the machine itself sits on has no container to trade with: moving "machine to
            // neighbour" there would shuffle the machine's own items through its own inventory, so it is
            // skipped. All the other faces are free.
            if (worldPosition.relative(face).equals(connectedMachinePos())) continue;

            var neighbour = MachineFaceStorage.storageAt(level, worldPosition, face);
            if (neighbour == null) continue;

            // The mode names the direction as seen from the machine, so "input" fills the machine from the
            // container on that side and "output" empties the machine into it. The machine itself is passed
            // along as the owner of the machine side, so both directions respect which of its slots are
            // inputs and which are outputs (see MachineSlotRoles) instead of moving anything anywhere.
            if (mode.allowsExtract()) MachineFaceStorage.move(machine, this, neighbour, null);
            if (mode.allowsInsert()) MachineFaceStorage.move(neighbour, null, machine, this);
        }
    }

    /**
     * Drops every mode while the block no longer holds a transfer addon. Called from
     * {@link #contentsChanged}: a block that lost its last transfer addon must stop feeding or emptying the
     * machine through its faces.
     */
    private void reconcileTransferModes() {
        if (level == null || level.isClientSide()) return;
        if (transferFaces.isEmpty()) return;
        if (hasTransferAddon()) return;

        transferFaces.clear();
        setChanged();
    }

    /**
     * Slot count of the machine inventory one face would proxy, or {@code 0} while there is no machine
     * (not connected, chunk unloaded, block entity gone). Used to refuse a binding the server cannot honour.
     */
    public int proxySlotCount() {
        var storage = MachineFaceStorage.machineStorage(this);
        return storage == null ? 0 : storage.getSlotCount();
    }

    /**
     * The slot layout of the machine inventory, as used by the picker of the Item Proxy page: one
     * {@code int[]} of {@code {container index, x, y}} per visible slot, exactly like Oritech's own
     * inventory proxy screen reads them. Empty while the machine cannot be resolved, which the page shows
     * as "no machine".
     */
    public List<int[]> proxyPickerSlots() {
        var machine = connectedMachinePos();
        if (machine == null || level == null || !level.isLoaded(machine)) return List.of();

        if (!(level.getBlockEntity(machine) instanceof ScreenProvider screen)) return List.of();

        var slots = new ArrayList<int[]>(screen.getGuiSlots().size());
        for (var slot : screen.getGuiSlots()) {
            slots.add(new int[]{slot.index(), slot.x(), slot.y()});
        }
        return List.copyOf(slots);
    }

    /**
     * Points one face at one slot of the machine inventory. Refused while the face is not configurable:
     * there has to be a free inventory proxy addon for it, or the face was already configured before.
     */
    public boolean bindProxyFace(Direction face, int slot) {
        if (level == null || level.isClientSide() || face == null || slot < 0) return false;
        if (!canProxyItems()) return false;
        if (!proxyFaces.isConfigured(face) && proxyFaces.configuredFaces() >= maxProxyFaces()) {
            OritechAddonsOne.LOGGER.debug("[proxy] {} refused face {}: all {} slot(s) configured",
                    worldPosition, face, maxProxyFaces());
            return false;
        }

        proxyFaces.bind(face, slot);
        setChanged();
        return true;
    }

    /** Removes the binding of one face, so that face stops proxying anything. */
    public boolean unbindProxyFace(Direction face) {
        if (level == null || level.isClientSide() || face == null) return false;
        if (!proxyFaces.isConfigured(face)) return false;

        proxyFaces.unbind(face);
        setChanged();
        return true;
    }

    /**
     * Drops every binding while the block no longer holds an inventory proxy addon. Called from
     * {@link #contentsChanged}: a block that lost its last proxy addon must not keep offering the faces of
     * a machine it can no longer be configured for.
     */
    private void reconcileProxyBindings() {
        if (level == null || level.isClientSide()) return;
        if (proxyFaces.isEmpty()) return;
        if (hasInventoryProxy()) return;

        proxyFaces.clear();
        setChanged();
    }

    /** Polled while a pipe or hopper reads/writes through one of our faces; today only persists the state. */
    public void onProxyUsed() {
        if (level != null && !level.isClientSide()) setChanged();
    }

    /**
     * Inventory a face of this block offers to the outside world (Oritech's {@code ItemApi.BlockProvider}
     * contract). The storage reports the machine's slots and then either proxies the one slot that face is
     * bound to or, with a transfer mode set, moves items in the direction that mode allows; with no binding,
     * no mode, no machine or no matching addon it reports an empty inventory, so a pipe simply sees "nothing
     * here" instead of an error.
     */
    @Override
    public ItemApi.InventoryStorage getInventoryStorage(Direction direction) {
        var face = direction == null ? Direction.NORTH : direction;
        // One object per face forever: NeoForge caches what a face answers with, and the handler reads the
        // binding and the mode on every call (see MachineFaceStorage).
        return faceStorages.computeIfAbsent(face, key -> new MachineFaceStorage(this, key));
    }

    // ------------------------------------------------------------------ energy input (acceptor plugin)

    protected boolean isEnergyInputActive() {
        return hasAcceptorPlugin()
                && isMachineAddonUsed()
                && getControllerEntity() instanceof MachineAddonController;
    }

    /** True while Oritech considers this block a connected addon (always false for the wireless ones). */
    protected boolean isMachineAddonUsed() {
        var state = getBlockState();
        return state.hasProperty(MachineAddonBlock.ADDON_USED) && state.getValue(MachineAddonBlock.ADDON_USED);
    }

    /** True while the block at our position is one of the blocks this entity belongs to. */
    protected boolean isOwnBlock() {
        return getBlockState().getBlock() instanceof ExtensionAddonBlock;
    }

    /** Block state property that mirrors {@link #hasRedstonePlugin()} (see {@link #updateControlUnitState()}). */
    protected BooleanProperty controlUnitProperty() {
        return ExtensionAddonBlock.HAS_CONTROL_UNIT;
    }

    private EnergyApi.EnergyStorage getMainStorage() {
        return getControllerEntity() instanceof MachineAddonController controller
                ? controller.getStorageForAddon()
                : null;
    }

    protected BlockEntity getControllerEntity() {
        return level == null ? null : level.getBlockEntity(getControllerPos());
    }

    /**
     * Energy storage of this block. It always exists so capability caches stay valid, but it only
     * reports capacity / accepts energy while an acceptor plugin is inside and a machine is connected.
     * <p>
     * Oritech's own energy network reaches it through this interface; the NeoForge energy capability is
     * provided by Oritech's bridge ({@code NeoforgeEnergyApiImpl}) for every block entity type that was
     * registered with {@code EnergyApi.BLOCK.registerBlockEntity} (see
     * {@link OritechAddonsOne#OritechAddonsOne(net.neoforged.bus.api.IEventBus, net.neoforged.fml.ModContainer)}).
     */
    @Override
    public EnergyApi.EnergyStorage getEnergyStorage(@Nullable Direction direction) {
        return delegatedStorage;
    }

    /** Combined stats of all plugins currently inserted. */
    private record CombinedStats(int count, float speedProduct, float efficiencyProduct,
                                 float speedDelta, float efficiencyDelta,
                                 long addedCapacity, long addedInsert, int chambers, int burstTicks) {
        boolean isEmpty() {
            return count == 0;
        }
    }

    private CombinedStats combinedStats() {
        var speedProduct = 1f;
        var efficiencyProduct = 1f;
        var speedDelta = 0f;
        var efficiencyDelta = 0f;
        var capacity = 0L;
        var insert = 0L;
        var chambers = 0;
        var burstTicks = 0;
        var pluginCount = 0;

        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            var settings = settingsOf(stack);
            if (settings == null) continue;

            // Stacked plugins count once per item, so a stack of 2 speed plugins is twice as strong
            // as a single one.
            var amount = stack.getCount();

            pluginCount += amount;
            speedProduct *= (float) Math.pow(settings.speedMultiplier(), amount);
            efficiencyProduct *= (float) Math.pow(settings.efficiencyMultiplier(), amount);
            speedDelta += amount * (1f - settings.speedMultiplier());
            efficiencyDelta += amount * (1f - settings.efficiencyMultiplier());
            capacity += amount * settings.addedCapacity();
            insert += amount * settings.addedInsert();
            chambers += amount * settings.chamberCount();
            burstTicks += amount * settings.burstTicks();
        }

        return new CombinedStats(pluginCount, speedProduct, efficiencyProduct, speedDelta, efficiencyDelta,
                capacity, insert, chambers, burstTicks);
    }

    // ------------------------------------------------------------------ forwarding to the machine

    @Override
    public void setControllerPos(BlockPos pos) {
        super.setControllerPos(pos);
        // Called by Oritech while writing the addon list, i.e. right after the machine recomputed its data.
        applyCombinedStats();
    }

    /**
     * Forgets the addon data this block merged into its machine. Used when the block stops working on that
     * machine (a wireless dock that was moved away or broken), so that the next merge is a fresh one.
     */
    protected void forgetAppliedStats() {
        lastApplied = null;
    }

    /** Adds the combined plugin stats on top of the data the machine computed for its other addons. */
    protected void applyCombinedStats() {
        // Called exactly once per dock and per addon recomputation of the machine (see
        // MachineAddonControllerMixin for the wireless docks and setControllerPos for the wired ones), so
        // several docks of one machine simply accumulate on top of each other like several addons do.
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!isOwnBlock()) return;
        if (!(serverLevel.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller)) {
            return;
        }

        // Keep the synced block state and the machine's redstone state in sync. Doing it here covers
        // placement, world loads and plugin changes, because every addon scan ends up in this method.
        updateControlUnitState();
        lastRedstonePowered = isPoweredByRedstone();
        applyRedstoneSignal(lastRedstonePowered);

        // Plugins whose behaviour Oritech keys on the block type (quarry, silk touch, fluid, crop
        // filter, steam boiler, hunter, ...) are replayed through the machine's own addon hook, so
        // they take effect exactly as if they were attached to the machine directly.
        forwardSpecialBehaviours(controller);

        // A stored control unit has to be visible to the machine's GUI. Oritech shows the redstone panel
        // of a machine when it finds a control unit in its connected addons, and that list is synced to
        // the client when the GUI is opened - a wireless dock is not one of the machine's own addons, so
        // it announces itself here while it really stores a control unit (see
        // UpgradeableOritechScreenHandlerMixin, which accepts this block as a control unit too).
        var connectedAddons = controller.getConnectedAddons();
        if (hasRedstonePlugin()) {
            if (!connectedAddons.contains(worldPosition)) connectedAddons.add(worldPosition.immutable());
        } else {
            connectedAddons.remove(worldPosition);
        }

        var stats = combinedStats();
        if (stats.isEmpty()) {
            lastApplied = null;
            return;
        }

        var base = controller.getBaseAddonData();
        var additive = OritechConfig.additiveAddons.get();
        var merged = merge(base, stats, additive);
        var previous = lastApplied;

        controller.setBaseAddonData(merged);
        controller.updateEnergyContainer();
        lastApplied = merged;

        // Oritech only sends the machine's addon data to the client when its GUI is opened, and the machine
        // recomputes - i.e. drops our contribution - right before that. The client builds the speed and
        // efficiency panel from that copy, so push our result whenever it really changed (plugins inserted
        // or removed, a dock linked or unlinked, ...).
        if (!merged.equals(previous) && controller instanceof NetworkedBlockEntity networked) {
            networked.sendUpdate(SyncType.GUI_OPEN);
        }

        OritechAddonsOne.LOGGER.debug(
                "Extension addon at {} merged {} plugin(s) into {} (additive={}): speed {} -> {}, efficiency {} -> {}",
                worldPosition, stats.count(), getControllerPos(), additive,
                base.speed(), merged.speed(), base.efficiency(), merged.efficiency());
    }

    /**
     * Replays {@link MachineAddonController#getAdditionalStatFromAddon} once per stored plugin, using a
     * synthetic addon entry that carries the plugin's block and block state. Oritech's machine
     * implementations read the block type from that entry, so the plugins behave like real addons.
     */
    private void forwardSpecialBehaviours(MachineAddonController controller) {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            if (!isPlugin(stack)) continue;

            var pluginBlock = (MachineAddonBlock) ((BlockItem) stack.getItem()).getBlock();
            var pluginState = pluginBlock.defaultBlockState();

            for (int i = 0; i < stack.getCount(); i++) {
                controller.getAdditionalStatFromAddon(
                        new MachineAddonController.AddonBlock(pluginBlock, pluginState, worldPosition, this));
            }
        }
    }

    private static BaseAddonData merge(BaseAddonData base, CombinedStats stats, boolean additive) {
        float speed;
        float efficiency;

        if (additive) {
            // Additive mode accumulates (1 - multiplier) per addon and inverts the result afterwards.
            // Undo that transform to get the accumulated delta, add ours, then apply it again.
            var speedDelta = 1f / base.speed() - 1f;
            var efficiencyDelta = base.efficiency() > 1f ? 1f - base.efficiency() : 1f / base.efficiency() - 1f;

            speedDelta += stats.speedDelta();
            efficiencyDelta += stats.efficiencyDelta();

            speed = 1f / (1f + speedDelta);
            efficiency = 1f / (1f + efficiencyDelta);
            if (efficiencyDelta < 0f) efficiency = 1f + Math.abs(efficiencyDelta);
        } else {
            speed = base.speed() * stats.speedProduct();
            efficiency = base.efficiency() * stats.efficiencyProduct();
        }

        return new BaseAddonData(speed, efficiency,
                base.energyBonusCapacity() + stats.addedCapacity(),
                base.energyBonusTransfer() + stats.addedInsert(),
                base.extraChambers() + stats.chambers(),
                base.maxBurstTicks() + stats.burstTicks());
    }

    /** Asks the connected machine to re-scan its addons after this block's contents changed. */
    protected void refreshController() {
        if (level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller) {
            serverLevel.getServer().execute(controller::initAddons);
        }
    }

    private void contentsChanged() {
        OritechAddonsOne.LOGGER.debug("[diag] contents changed on {} (slot 0 = {})", worldPosition, items.get(0));
        setChanged();
        // a block that lost its last inventory proxy addon must not keep any face configured
        reconcileProxyBindings();
        // same for the transfer modes of a block that lost its last transfer addon
        reconcileTransferModes();
        // keep the synced "has a control unit" state (and the machine) up to date right away
        updateControlUnitState();
        serverTickRedstone();
        refreshController();
    }

    /**
     * Mirrors {@link #hasRedstonePlugin()} into the block state. Oritech decides whether a machine shows
     * its redstone panel on the client by looking at the blocks in the machine's addon slots, and a block
     * entity inventory is not synced to the client, so the flag is kept in the (synced) block state.
     */
    private void updateControlUnitState() {
        if (level == null || level.isClientSide()) return;

        var state = getBlockState();
        var property = controlUnitProperty();
        if (property == null || !state.hasProperty(property)) return;

        var hasControlUnit = hasRedstonePlugin();
        if (state.getValue(property) != hasControlUnit) {
            level.setBlock(worldPosition, state.setValue(property, hasControlUnit), Block.UPDATE_ALL);
        }
    }

    /**
     * Mirrors the stored control unit into the synced block state of every extension addon of a machine,
     * once that machine's addon scan is complete.
     * <p>
     * The flag cannot be left to {@link #updateControlUnitState()} alone: Oritech's
     * {@code MachineAddonController#writeAddons} captures the addon's block state when the scan starts and
     * writes it back <em>after</em> calling {@code setControllerPos} - i.e. after
     * {@link #applyCombinedStats()} wrote the flag - so that write is thrown away and the state the block
     * had before the scan survives. Re-applying the flag at the end of the scan is therefore the only
     * point where it holds; it also repairs addons whose state was saved by an older version of this mod,
     * where the block still carried the property's default {@code true}.
     */
    public static void refreshControlUnitStates(Level level, Iterable<BlockPos> addons) {
        for (var pos : addons) {
            if (level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity addon) {
                addon.updateControlUnitState();
            }
        }
    }

    // ------------------------------------------------------------------ inventory

    @Override
    public int getContainerSize() {
        return configuredSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!items.get(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (isReservedSlot(slot)) {
                setChanged();
                refreshAnchor();
            } else {
                contentsChanged();
            }
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        var removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            if (isReservedSlot(slot)) {
                setChanged();
                refreshAnchor();
            } else {
                contentsChanged();
            }
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (isReservedSlot(slot)) {
            // The reserved item is not a plugin: it changes neither the machine's stats nor its storage,
            // so the machine must not be asked to recompute its addons because of it. One item of it may
            // still have an effect of its own: the chunk anchor force loads the connected machine's chunk.
            setChanged();
            refreshAnchor();
            return;
        }
        contentsChanged();
    }

    /**
     * Tells the chunk anchor that the reserved slot changed, so the force load of the connected machine is
     * updated at once. Called for every change of that slot, because removing the anchor is as important as
     * inserting it; a slot that holds anything else (or nothing) simply answers "release".
     */
    protected void refreshAnchor() {
        AnchorForceLoad.register(this);
    }

    /**
     * Announces a chunk anchor that was saved in the reserved slot. The force loads themselves are runtime
     * state, so after a server start the anchor has to report itself again; {@code onLoad} runs after the
     * saved contents - and, for a wireless dock, after the saved link - were read.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        AnchorForceLoad.register(this);
    }

    /** True for the index of the reserved single item slot of the wireless page. */
    public static boolean isReservedSlot(int slot) {
        return slot == RESERVED_SLOT;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < getContainerSize() && type.acceptsInSlot(slot, stack);
    }

    /** Capacity of one slot (type III can be configured above the vanilla stack limit). */
    @Override
    public int getMaxStackSize() {
        return Config.slotCapacity(type);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Saves the contents in a count preserving way: {@code ItemStack.CODEC} only allows counts up to 99,
     * while type III slots can hold far more. Every stack is therefore stored with count 1 (plus
     * components, through the vanilla container helper) and its real count in a parallel int array.
     * Data written without that array (plain container format) still loads normally.
     */
    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        var singles = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        var counts = new int[items.size()];

        for (int slot = 0; slot < items.size(); slot++) {
            var stack = items.get(slot);
            singles.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            counts[slot] = stack.getCount();
        }

        ContainerHelper.saveAllItems(nbt, singles, registries);
        nbt.putIntArray("counts", counts);

        // the per-face inventory proxy bindings of the Item Proxy page and the transfer modes of the
        // Extension Transfer page
        proxyFaces.save(nbt);
        transferFaces.save(nbt);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        ContainerHelper.loadAllItems(nbt, items, registries);

        // a stale binding or mode is harmless: it only resolves to an inventory while the machine is there
        proxyFaces.load(nbt);
        transferFaces.load(nbt);

        var counts = nbt.getIntArray("counts");
        if (counts.length == 0) return;

        var capacity = getMaxStackSize();
        for (int slot = 0; slot < items.size() && slot < counts.length; slot++) {
            var stack = items.get(slot);
            if (stack.isEmpty() || counts[slot] <= 0) {
                items.set(slot, ItemStack.EMPTY);
            } else {
                items.set(slot, stack.copyWithCount(Math.min(counts[slot], capacity)));
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        // Each variant names itself, so a GUI title always says which block is open: the wired and wireless
        // addons by their type (see displayNameKey), a placed plugin by its own key.
        return Component.translatable(displayNameKey());
    }

    /** Language key of this block's GUI title; overridable for a block that is not tied to an addon type. */
    protected String displayNameKey() {
        return "container.oritechaddonsone." + type.id();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ExtensionAddonMenu(containerId, inventory, this);
    }
}
