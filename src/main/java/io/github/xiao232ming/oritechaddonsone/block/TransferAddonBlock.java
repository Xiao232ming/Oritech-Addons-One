package io.github.xiao232ming.oritechaddonsone.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;

/**
 * 扩展传输插件 - the transfer addon as a placed block.
 * <p>
 * Inside an Extension Addon - either variant - the plugin makes the machine reachable through the addon's
 * own faces; that behaviour lives on the addon's block entity and needs nothing from this class. Placed in
 * the world, the plugin does something of its own: hung on Oritech's <b>machine extender</b> it becomes the
 * transfer page of that extender, so the six faces of the extender can be configured while standing next to
 * it - the extender itself has no screen of its own.
 * <p>
 * What that page then configures is the extender's net: {@link TransferAddonBlockEntity} reports the
 * extender's block state as the block to draw, marks the face this plugin hangs on in gold, and moves the
 * items of the machine behind the extender between that machine and the containers around the extender.
 * Placed on anything else - a machine, a wall - the plugin keeps Oritech's own addon behaviour, opens no GUI
 * and moves nothing.
 * <p>
 * The plugin block itself is a normal Oritech addon that needs support, so it can also be put in a machine's
 * addon slot, where it is one of Oritech's plugins and this class stays out of the way.
 */
public class TransferAddonBlock extends PluginAddonBlock {

    public TransferAddonBlock(Properties properties, AddonSettings addonSettings) {
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
        return new TransferAddonBlockEntity(pos, state);
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
            if (blockEntity instanceof TransferAddonBlockEntity plugin) {
                plugin.serverTickTransfer();
            }
        };
    }

    /**
     * Right-clicking a placed transfer addon that hangs on a machine extender opens the plugin's own screen:
     * the net it shows is the extender's, its gold border is the face this plugin hangs on, and everything set
     * there is applied to the extender's faces. Anything else - no extender, or a plugin standing on a machine
     * - keeps Oritech's own behaviour.
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var opened = openPluginMenu(level, pos, player);
        return opened == InteractionResult.PASS ? super.useWithoutItem(state, level, pos, player, hit) : opened;
    }

    /**
     * The same as {@link #useWithoutItem}: an item in the hand must not swallow the click that opens the
     * screen, exactly like on the wired addon.
     */
    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        var opened = openPluginMenu(level, pos, player);
        return opened == InteractionResult.PASS ? super.useItemOn(stack, state, level, pos, player, hand, hit)
                : opened;
    }

    /**
     * Opens the plugin's own screen while it hangs on a machine extender, and reports {@code PASS} - i.e.
     * "leave this click to Oritech" - for every other placement. The host is read from the block entity, so
     * the same question ("do I hang on an extender?") is answered in one place.
     */
    private static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof TransferAddonBlockEntity plugin)) return InteractionResult.PASS;
        if (!plugin.hangsOnExtender()) return InteractionResult.PASS;

        return PluginAddonMenus.openPluginMenu(level, pos, player);
    }
}
