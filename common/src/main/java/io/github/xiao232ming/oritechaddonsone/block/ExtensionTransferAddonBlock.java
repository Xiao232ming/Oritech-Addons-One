package io.github.xiao232ming.oritechaddonsone.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionTransferAddonBlockEntity;

/**
 * 扩展传输插件 - the transfer addon as a placed block.
 * <p>
 * Inside an Extension Addon - either variant - the plugin makes the machine reachable through the addon's
 * own faces; that behaviour lives on the addon's block entity and needs nothing from this class. Placed in
 * the world, the plugin does something of its own: hung on Oritech's <b>machine extender</b> it becomes the
 * transfer page of that extender, so the six faces of the extender can be configured while standing next to
 * it - the extender itself has no screen of its own.
 * <p>
 * What that page then configures is the extender's net: {@link ExtensionTransferAddonBlockEntity} reports the
 * extender's block state as the block to draw, marks the face this plugin hangs on in gold, and moves the
 * items of the machine behind the extender between that machine and the containers around the extender.
 * Placed on anything else - a machine, a wall - the plugin keeps Oritech's own addon behaviour, opens no GUI
 * and moves nothing.
 * <p>
 * The plugin block itself is a normal Oritech addon that needs support, so it can also be put in a machine's
 * addon slot, where it is one of Oritech's plugins and this class stays out of the way.
 */
public class ExtensionTransferAddonBlock extends PluginAddonBlock {

    public ExtensionTransferAddonBlock(Properties properties, AddonSettings addonSettings) {
        super(properties, addonSettings);
    }

    /**
     * The direction a placed transfer addon is attached in, i.e. the one from the block towards whatever it
     * hangs on. These plugin blocks are Oritech addons that need support, so their state is vanilla's
     * {@link FaceAttachedHorizontalDirectionalBlock} one: up from a floor, down from a ceiling, and towards
     * the wall for a wall mounted one.
     */
    public static Direction attachedTowards(BlockState state) {
        return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state).getOpposite();
    }

    /**
     * The face of the host this plugin hangs on - the one of the extender's six faces the transfer page
     * marks in gold. It is the opposite of {@link #attachedTowards(BlockState)}, because that one points from
     * the plugin to the host.
     */
    public static Direction attachedFace(BlockState state) {
        return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExtensionTransferAddonBlockEntity(pos, state);
    }

    /**
     * Server ticker of the placed plugin. It only gives the plugin's own block entity its transfer, and it
     * returns nothing on the client - the transfer is a display only concern there, because everything the
     * GUI draws comes from the menu's container data.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ExtensionTransferAddonBlockEntity plugin) {
                plugin.serverTickTransfer();
            }
        };
    }

    /**
     * The plugin is about to be broken, so the extender it hangs on stops offering the machine on the faces
     * this plugin configured. Telling the capability caches here is the one moment that can be done for a
     * broken plugin: this hook runs before the block is removed, i.e. while the chunk is still fully alive
     * and before any chunk bookkeeping starts, unlike {@code BlockEntity#setRemoved()} - which the block
     * entity deliberately does not use for it (see {@link ExtensionTransferAddonBlockEntity#invalidateHostCapabilities()}).
     * <p>
     * Without this a pipe that cached a handler would keep treating the extender as a connection that answers
     * nothing. The plugin's own tick cannot cover the case either, because a broken plugin has no block
     * entity left to tick, and an extender that unloads with its chunk is covered by NeoForge's chunk-wide
     * invalidation.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtensionTransferAddonBlockEntity plugin) {
            plugin.invalidateHostCapabilities();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Right-clicking a placed transfer addon that hangs on a machine extender opens the plugin's own screen:
     * the net it shows is the extender's, its gold border is the face this plugin hangs on, and everything set
     * there is applied to the extender's faces. Anything else - no extender, or a plugin standing on a machine
     * - keeps Oritech's own behaviour.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return opensOnExtender(level, pos)
                ? PluginAddonMenus.openPluginMenu(level, pos, player)
                : super.useWithoutItem(state, level, pos, player, hit);
    }

    /**
     * The same as {@link #useWithoutItem}: an item in the hand must not swallow the click that opens the
     * screen, exactly like on the wired addon.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!opensOnExtender(level, pos)) return super.useItemOn(stack, state, level, pos, player, hand, hit);

        return PluginAddonMenus.openItemMenu(level, pos, player);
    }

    /**
     * True while the placed plugin at {@code pos} hangs on a machine extender, i.e. while the click may open
     * the plugin's own screen. Reading the host from the block entity keeps that one question ("do I hang on
     * an extender?") in a single place for both interaction hooks.
     */
    private static boolean opensOnExtender(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ExtensionTransferAddonBlockEntity plugin && plugin.hangsOnExtender();
    }
}
