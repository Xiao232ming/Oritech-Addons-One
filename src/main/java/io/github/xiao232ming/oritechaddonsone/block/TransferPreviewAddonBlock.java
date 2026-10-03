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

import rearth.oritech.block.blocks.addons.MachineAddonBlock;

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
     * The plugin with an item in the hand. 1.21.1 keeps the two hooks apart: this one answers with an
     * {@link ItemInteractionResult} and the empty hand hook ({@link #useWithoutItem}) with an
     * {@link InteractionResult}, and this one only lets that hook run when it gives the click back with
     * {@link ItemInteractionResult#PASS_TO_DEFAULT_BLOCK_INTERACTION} - the value the branch's
     * {@code BlockBehaviour#useItemOn} itself answers with, and the only one of the six that the interaction code
     * accepts as "try the default block interaction" (see {@code MultiPlayerGameMode#performUseItemOn} and
     * {@code ServerPlayerGameMode#useItemOn}, which check for exactly that constant).
     * <p>
     * Giving the click back is therefore what opens the plugin for an empty hand, and it has to happen whenever this
     * plugin is not serving a machine: {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} is not a success, so the click is
     * neither consumed nor answered here, and {@link #useWithoutItem} makes the real decision. Answering
     * {@code SUCCESS} instead - which is what {@link PluginAddonMenus#openItemMenu} does for a plugin that is attached
     * - would consume the click for every placement, including a plugin that merely stands on a wall.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // nothing to do with this plugin as an addon: the click belongs to the empty hand hook and to Oritech
        if (!isClaimedByAHost(state)) return super.useItemOn(stack, state, level, pos, player, hand, hit);

        return PluginAddonMenus.openItemMenu(level, pos, player);
    }

    /**
     * Right-clicking a placed preview plugin that serves a machine opens the plugin's own screen: the 3D preview of
     * that machine and the six faces to configure. Anything else - a plugin standing on a wall, one with no host -
     * keeps Oritech's own behaviour.
     * <p>
     * This hook runs on the client as well, and it has to answer there: the screen a placed plugin opens is part of
     * the plugin, and the client is the side that decides whether the click was consumed at all - a click the client
     * passes on is offered to the item in the hand instead, which for this plugin's own block item means "try to place
     * a block" and therefore "nothing happens".
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        // on the client this consumes the click; the server is still the side that opens the menu (see openPluginMenu)
        if (!isClaimedByAHost(state)) return super.useWithoutItem(state, level, pos, player, hit);

        return openPluginMenu(level, pos, player);
    }

    /**
     * Opens the plugin's own screen while it serves a machine, and hands the click to Oritech - the branch's
     * {@link InteractionResult#PASS} - for every other placement.
     * <p>
     * The two sides answer the "do I serve a machine?" question differently, because they <b>can</b>: the server
     * resolves the machine from the world, the client cannot. Oritech keeps the position of the machine that claimed
     * an addon in the addon's own block entity as a controller <em>offset</em>, and that offset is save data of a
     * plain (not networked) block entity - it is written on the server and never reaches a client. On the client
     * {@code getControllerPos()} therefore answers the plugin's own position, every host lookup built on it fails and
     * the client would answer "I serve nothing" for a plugin that is really in use; {@code opensOnMachine} is exactly
     * that lookup, which is why it is no longer what the hooks ask.
     * <p>
     * Because the block interaction runs on both sides and only the server can open a menu, the client must not make
     * its answer depend on that lookup: it is what made a placed plugin impossible to open. The client uses the one
     * piece of the answer that <b>is</b> synced - the {@code addon_used} flag of the block state, which Oritech's addon
     * scan writes into the plugin for both hosts (a machine that claimed the plugin, or an extender a machine
     * claimed) - and then treats an attached plugin as openable. The server stays authoritative: it opens the menu
     * only while {@link TransferPreviewAddonBlockEntity#servedMachinePos()} really resolves, so a plugin that hangs on
     * neither a machine nor an extender keeps Oritech's ordinary addon click, its faces answer nothing and nothing
     * is moved, whatever the client believed.
     * <p>
     * Opening the screen on the client is not a duplicate of the server's own open: on the client
     * {@link PluginAddonMenus#openPluginMenu} opens nothing at all (the server owns menus and tells the client about
     * the one it opened), so all the client's call decides is that the click is consumed here.
     */
    private static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TransferPreviewAddonBlockEntity plugin)) return InteractionResult.PASS;
        if (plugin.servedMachinePos() == null) return InteractionResult.PASS;

        return PluginAddonMenus.openPluginMenu(level, pos, player);
    }

    /**
     * True while Oritech has attached this plugin to a host, i.e. while its {@code addon_used} flag is set. It is the
     * only half of the "I serve a machine" answer a client can read: Oritech's addon scan sets the flag for a plugin a
     * machine claimed directly and for a plugin hung on an extender a machine claimed, and it is a block state, so
     * both sides see the same value.
     */
    private static boolean isClaimedByAHost(BlockState state) {
        return state.hasProperty(MachineAddonBlock.ADDON_USED) && state.getValue(MachineAddonBlock.ADDON_USED);
    }
}
