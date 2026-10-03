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

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferPreviewAddonBlockEntity;

/**
 * 传输插件 - the transfer preview plugin as a placed block.
 * <p>
 * It is the second transfer plugin of this mod and does the same thing as {@link TransferAddonBlock}: a machine's
 * items are fed and emptied through one direction per face. Placed in the world it supports <b>both</b> hosts the
 * plugin can serve, and which one it is standing on is decided by the neighbour it was placed against:
 * <ul>
 *     <li>on Oritech's <b>machine extender</b> ({@code oritech:machine_extender}) it becomes the transfer page of
 *     that extender, because the extender itself has no screen of its own and no inventory: right clicking it opens
 *     the plugin's screen and its faces are configured on the machine behind the extender,</li>
 *     <li>on an Oritech <b>machine</b> it becomes a plugin of that machine - Oritech's addon scan claims it like any
 *     other addon of this mod, which is what makes it work on that machine through its own faces.</li>
 * </ul>
 * Inside an Extension Addon - either variant - the plugin is again exactly the transfer plugin: the behaviour lives
 * on the addon's block entity and needs nothing from this class.
 * <p>
 * What makes this plugin different from {@link TransferAddonBlock} is only its page: instead of unfolding the host
 * into a cube net, the page renders the machine it serves as a rotatable 3D model and the player picks the faces on
 * that model (see {@code TransferPreviewAddonPage}). Everything the page does with a picked face - the mode, the
 * automation switch, the occupied-face refusal - is the same code the transfer page uses.
 * <p>
 * The plugin block itself is a normal Oritech addon that needs support, so it can also be put in a machine's addon
 * slot, where it is one of Oritech's plugins and this class stays out of the way.
 */
public class TransferPreviewAddonBlock extends PluginAddonBlock {

    public TransferPreviewAddonBlock(Properties properties, AddonSettings addonSettings) {
        super(properties, addonSettings);
    }

    /**
     * The direction a placed preview plugin is attached in, i.e. the one from the block towards whatever it hangs on.
     * These plugin blocks are Oritech addons that need support, so their state is vanilla's
     * {@link FaceAttachedHorizontalDirectionalBlock} one: up from a floor, down from a ceiling, and towards the wall
     * for a wall mounted one.
     */
    public static Direction attachedTowards(BlockState state) {
        return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state).getOpposite();
    }

    /**
     * The face of the host this plugin hangs on - the face of the machine or of the extender - and therefore the face
     * the page marks in gold and refuses. It is the opposite of {@link #attachedTowards(BlockState)}, because that
     * one points from the plugin to the host.
     */
    public static Direction attachedFace(BlockState state) {
        return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransferPreviewAddonBlockEntity(pos, state);
    }

    /**
     * Server ticker of the placed plugin. It only gives the plugin's own block entity its transfer, and it returns
     * nothing on the client - the transfer is a display only concern there, because everything the GUI draws comes
     * from the menu's container data.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TransferPreviewAddonBlockEntity plugin) {
                plugin.serverTickTransfer();
            }
        };
    }

    /**
     * The plugin is about to be broken, so it stops offering the machine's inventory on the faces it configured.
     * Telling the capability caches here is the one moment that can be done for a broken plugin: this hook runs before
     * the block is removed, i.e. while the chunk is still fully alive and before any chunk bookkeeping starts, unlike
     * {@code BlockEntity#setRemoved()} - which the block entity deliberately does not use for it (see
     * {@link TransferPreviewAddonBlockEntity#invalidateFaceCapabilities()}).
     * <p>
     * Without this a pipe that cached a storage would keep treating the plugin as a connection that answers nothing.
     * The plugin's own tick cannot cover the case either, because a broken plugin has no block entity left to tick,
     * and a plugin that unloads with its chunk is covered by NeoForge's chunk-wide invalidation.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TransferPreviewAddonBlockEntity plugin) {
            plugin.invalidateFaceCapabilities();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Right-clicking a placed preview plugin that serves a machine opens the plugin's own screen: the 3D preview of
     * that machine and the six faces to configure. Anything else - a plugin standing on a wall, one with no host -
     * keeps Oritech's own behaviour.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return opensOnMachine(level, pos)
                ? PluginAddonMenus.openPluginMenu(level, pos, player)
                : super.useWithoutItem(state, level, pos, player, hit);
    }

    /**
     * The same as {@link #useWithoutItem}: an item in the hand must not swallow the click that opens the screen,
     * exactly like on the wired addon. 1.21.1 splits the two hooks - one answers with an {@code InteractionResult},
     * the other with an {@code ItemInteractionResult} - so this one goes through the shared
     * {@link PluginAddonMenus#openItemMenu} form.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!opensOnMachine(level, pos)) return super.useItemOn(stack, state, level, pos, player, hand, hit);

        return PluginAddonMenus.openItemMenu(level, pos, player);
    }

    /**
     * True while the placed plugin at {@code pos} serves a machine, i.e. while the click may open the plugin's own
     * screen. Reading the host from the block entity keeps that one question ("do I serve a machine?") in a single
     * place for both interaction hooks: it is true for both placements this plugin supports - an extender the machine
     * claimed, and a machine the plugin is attached to - and false for a plugin that merely stands on a wall.
     */
    private static boolean opensOnMachine(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TransferPreviewAddonBlockEntity plugin
                && plugin.servedMachinePos() != null;
    }
}
