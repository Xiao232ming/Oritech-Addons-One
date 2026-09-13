package com.example.oritechaddonsone.block.entity;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

import com.example.oritechaddonsone.Config;
import com.example.oritechaddonsone.OritechAddonsOne;
import com.example.oritechaddonsone.block.ExtensionPluginBlock;
import com.example.oritechaddonsone.block.ExtensionPluginType;
import com.example.oritechaddonsone.menu.ExtensionPluginLayout;
import com.example.oritechaddonsone.menu.ExtensionPluginMenu;

/**
 * Block entity of the Extension Plugins (shared by type I and type II).
 * <p>
 * It stores up to {@link ExtensionPluginType#slots()} stacks of Oritech plugin items and forwards
 * their combined stats to the machine this block is connected to. The forwarding works by letting the
 * machine run its normal addon scan (this block reports neutral stats, so it does not change anything
 * by itself) and then merging the combined plugin stats into the machine's addon data.
 * {@link #setControllerPos(BlockPos)} is called by Oritech at the end of every addon scan, which is
 * exactly the point where the machine's own data is already computed and our contribution has to be
 * added on top.
 * <p>
 * Only the plugins accepted by the block's {@link ExtensionPluginType} may be inserted. Plugins whose
 * behaviour Oritech decides by block type (quarry, silk touch, fluid, crop filter, ...) are forwarded
 * by replaying the machine's {@code getAdditionalStatFromAddon} hook for every stored plugin, so they
 * work as if they were attached directly. While a machine acceptor plugin is stored, this block also
 * offers an energy input that feeds the machine.
 */
public class ExtensionPluginBlockEntity extends AddonBlockEntity implements Container, MenuProvider, EnergyProvider {

    private final ExtensionPluginType type;
    /**
     * Storage is always {@link ExtensionPluginLayout#MAX_SLOTS} large, the config only decides how many
     * of these slots are usable. That way lowering the configured amount never destroys stored plugins.
     */
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(ExtensionPluginLayout.MAX_SLOTS, ItemStack.EMPTY);

    /** Feeds the connected machine while an acceptor plugin is inserted. */
    private final EnergyHandler delegatedStorage = new DelegatingEnergyStorage(this::getMainStorage, this::isEnergyInputActive);

    public ExtensionPluginBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.EXTENSION_PLUGIN_ENTITY.get(), pos, state);
        this.type = state.getBlock() instanceof ExtensionPluginBlock block ? block.getType() : ExtensionPluginType.TYPE_1;
    }

    // ------------------------------------------------------------------ plugin handling

    /** The plugin type of this block (decided by the block that created it). */
    public ExtensionPluginType pluginType() {
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
     * Forwards the redstone signal at this block to the connected machine, exactly like Oritech's own
     * control unit plugin does from its {@code neighborChanged} hook: the machine implements
     * {@link RedstoneControllable} and turns itself off while it is powered.
     * <p>
     * Only input control is forwarded. The control unit's mode (input control / comparator output) is
     * stored in its own block entity, which does not exist while it is kept as an item inside this
     * block, so a stored control unit always behaves like the default mode {@code INPUT_CONTROL}.
     */
    public void applyRedstoneSignal(boolean powered) {
        if (level == null) return;
        if (!(level.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller)) return;
        if (!(controller instanceof RedstoneControllable controllable)) return;

        if (hasRedstonePlugin()) {
            controllable.onRedstoneEvent(powered);
            return;
        }

        // No control unit inside: release the machine, so taking the plugin out does not leave it
        // disabled forever. A control unit that is attached to the machine directly owns the state, so
        // it is left untouched in that case.
        if (hasAttachedRedstoneAddon(controller)) return;
        controllable.onRedstoneEvent(false);
    }

    /** True while a vanilla control unit plugin block is attached to the machine directly. */
    private boolean hasAttachedRedstoneAddon(MachineAddonController controller) {
        for (var pos : controller.getConnectedAddons()) {
            if (level.getBlockEntity(pos) instanceof RedstoneAddonBlockEntity) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ energy input (acceptor plugin)

    private boolean isEnergyInputActive() {
        return hasAcceptorPlugin()
                && getBlockState().getValue(MachineAddonBlock.ADDON_USED)
                && getControllerEntity() instanceof MachineAddonController;
    }

    private EnergyHandler getMainStorage() {
        return getControllerEntity() instanceof MachineAddonController controller ? controller.getStorageForAddon() : null;
    }

    private BlockEntity getControllerEntity() {
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
        // block. The mixin in com.example.oritechaddonsone.mixin additionally stops the splicer from
        // shrinking anything while one of these blocks is connected to it.
        if (level != null && level.getBlockEntity(pos) instanceof AddonSplicerBlockEntity) return;

        super.setControllerPos(pos);
        // Called by Oritech while writing the addon list, i.e. right after the machine recomputed its data.
        applyCombinedStats();
    }

    /** Adds the combined plugin stats on top of the data the machine computed for its other addons. */
    private void applyCombinedStats() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(level.getBlockState(worldPosition).getBlock() instanceof ExtensionPluginBlock)) return;
        if (!(serverLevel.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller)) return;

        // Keep the machine's redstone state in sync with the signal at this block. Doing it here covers
        // placement, world loads and plugin changes, because every addon scan ends up in this method.
        applyRedstoneSignal(serverLevel.hasNeighborSignal(worldPosition));

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
                "Extension plugin at {} merged {} plugin(s) into {} (additive={}): speed {} -> {}, efficiency {} -> {}",
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
    private void refreshController() {
        if (level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(getControllerPos()) instanceof MachineAddonController controller) {
            serverLevel.getServer().execute(controller::initAddons);
        }
    }

    private void contentsChanged() {
        setChanged();
        refreshController();
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
        return new ExtensionPluginMenu(containerId, inventory, this);
    }
}
