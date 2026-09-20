package io.github.xiao232ming.oritechaddonsone.block.entity;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import rearth.oritech.api.transfer.energy.DelegatingEnergyStorage;
import rearth.oritech.api.transfer.energy.EnergyProvider;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity;
import rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity.RedstoneControllable;
import rearth.oritech.block.entity.interaction.AddonSplicerBlockEntity;
import rearth.oritech.config.OritechConfig;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MachineAddonController.BaseAddonData;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinks;

/**
 * Block entity of the Extension Addons (shared by type I and type II).
 * <p>
 * It stores up to {@link ExtensionAddonType#slots()} stacks of Oritech plugin items and forwards
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
 */
public class ExtensionAddonBlockEntity extends AddonBlockEntity implements Container, MenuProvider, EnergyProvider {

    private final ExtensionAddonType type;
    /**
     * Storage is always {@link ExtensionAddonLayout#MAX_SLOTS} large, the config only decides how many
     * of these slots are usable. That way lowering the configured amount never destroys stored plugins.
     */
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(ExtensionAddonLayout.MAX_SLOTS, ItemStack.EMPTY);

    /** Feeds the connected machine while an acceptor plugin is inserted. */
    private final EnergyHandler delegatedStorage = new DelegatingEnergyStorage(this::getMainStorage, this::isEnergyInputActive);

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

        var owner = state.getBlock();
        this.type = owner instanceof ExtensionAddonBlock wired ? wired.getType()
                : owner instanceof WirelessExtensionAddonBlock wireless ? wireless.getType()
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

    /** True while a machine acceptor plugin is stored, which turns this block into an energy input. */
    public boolean hasAcceptorPlugin() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (blockItem.getBlock() == BlockContent.MACHINE_ACCEPTOR_ADDON.get()) return true;
        }
        return false;
    }

    /** True while a control unit (redstone) plugin is stored, which lets redstone control the machine. */
    public boolean hasRedstonePlugin() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            var stack = items.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (blockItem.getBlock() == BlockContent.CONTROL_UNIT_ADDON.get()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ redstone input (control unit plugin)

    /**
     * Polled every server tick by {@link ExtensionAddonBlock#getTicker}. Cheap while nothing is to do:
     * without a stored control unit (and without a state we set earlier) it returns immediately.
     */
    public void serverTickRedstone() {
        if (level == null || level.isClientSide()) return;

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
        if (!(controller instanceof RedstoneControllable controllable)) return;

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

    private EnergyHandler getMainStorage() {
        return getControllerEntity() instanceof MachineAddonController controller ? controller.getStorageForAddon() : null;
    }

    protected BlockEntity getControllerEntity() {
        return level == null ? null : level.getBlockEntity(getControllerPos());
    }
    /**
     * Energy handler of this block. It always exists so capability caches stay valid, but it only
     * reports capacity / accepts energy while an acceptor plugin is inside and a machine is connected.
     */
    @Override
    public EnergyHandler getEnergyLookup(@Nullable Direction direction) {
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
        // Never attach to an addon splicer: the splicer must not be able to use (or consume) this
        // block. The mixin in io.github.xiao232ming.oritechaddonsone.mixin additionally stops the splicer from
        // shrinking anything while one of these blocks is connected to it.
        if (level != null && level.getBlockEntity(pos) instanceof AddonSplicerBlockEntity) return;

        super.setControllerPos(pos);
        // Called by Oritech while writing the addon list, i.e. right after the machine recomputed its data.
        applyCombinedStats();
    }

    /** Adds the combined plugin stats on top of the data the machine computed for its other addons. */
    protected void applyCombinedStats() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!isOwnBlock()) return;
        if (!(serverLevel.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller)) return;

        // Keep the synced block state and the machine's redstone state in sync. Doing it here covers
        // placement, world loads and plugin changes, because every addon scan ends up in this method.
        updateControlUnitState();
        lastRedstonePowered = isPoweredByRedstone();
        applyRedstoneSignal(lastRedstonePowered);

        // Plugins whose behaviour Oritech keys on the block type (quarry, silk touch, fluid, crop
        // filter, steam boiler, hunter, ...) are replayed through the machine's own addon hook, so
        // they take effect exactly as if they were attached to the machine directly.
        forwardSpecialBehaviours(controller);

        var stats = combinedStats();
        if (stats.isEmpty()) return;

        var base = controller.getBaseAddonData();
        var additive = OritechConfig.additiveAddons.get();
        var merged = merge(base, stats, additive);

        controller.setBaseAddonData(merged);
        controller.updateEnergyContainer();

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
        setChanged();
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
        if (!removed.isEmpty()) contentsChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        var removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) contentsChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        contentsChanged();
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
     * components) and its real count in a parallel list. Old plain "Items" data still loads through
     * {@link ContainerHelper}.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        var singles = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        var counts = new ArrayList<Integer>(items.size());

        for (int slot = 0; slot < items.size(); slot++) {
            var stack = items.get(slot);
            singles.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            counts.add(stack.getCount());
        }

        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), singles);
        output.store("counts", Codec.INT.listOf(), counts);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        var counts = input.read("counts", Codec.INT.listOf()).orElse(List.of());
        if (counts.isEmpty()) {
            // Data written by an older version (plain ContainerHelper format).
            ContainerHelper.loadAllItems(input, items);
            return;
        }

        var saved = input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        var capacity = getMaxStackSize();

        for (int slot = 0; slot < items.size(); slot++) {
            var stack = slot < saved.size() ? saved.get(slot) : ItemStack.EMPTY;
            var count = slot < counts.size() ? counts.get(slot) : 0;

            if (stack.isEmpty() || count <= 0) {
                items.set(slot, ItemStack.EMPTY);
            } else {
                items.set(slot, stack.copyWithCount(Math.min(count, capacity)));
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.oritechaddonsone." + type.id());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ExtensionAddonMenu(containerId, inventory, this);
    }
}
