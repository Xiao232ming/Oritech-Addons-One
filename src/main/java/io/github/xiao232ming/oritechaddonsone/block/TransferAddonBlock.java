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
import rearth.oritech.init.BlockContent;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * 传输插件 - the transfer preview plugin as a placed block.
 * <p>
 * It is the second transfer plugin of this mod and does the same thing as {@link ExtensionTransferAddonBlock}: a machine's
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
 * What makes this plugin different from {@link ExtensionTransferAddonBlock} is only its page: instead of unfolding the host
 * into a cube net, the page renders the machine it serves as a rotatable 3D model and the player picks the faces on
 * that model (see {@code TransferAddonPage}). Everything the page does with a picked face - the mode, the
 * automation switch - is the same code the transfer page uses.
 * <p>
 * The plugin block itself is a normal Oritech addon that needs support, so it can also be put in a machine's addon
 * slot, where it is one of Oritech's plugins and this class stays out of the way.
 */
public class TransferAddonBlock extends PluginAddonBlock {

    public TransferAddonBlock(Properties properties, AddonSettings addonSettings) {
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
     * The face of the host this plugin hangs on - the face of the machine or of the extender. It is the opposite of
     * {@link #attachedTowards(BlockState)}, because that one points from the plugin to the host, and it is only a
     * geometric fact about the placement, not a statement about what the page does with that face.
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
            if (blockEntity instanceof TransferAddonBlockEntity plugin) {
                plugin.serverTickTransfer();
            }
        };
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
     * plugin is not one of the two placements that have a screen: {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} is not a
     * success, so the click is neither consumed nor answered here, and {@link #useWithoutItem} makes the real
     * decision. Answering {@code SUCCESS} instead - which is what {@link PluginAddonMenus#openItemMenu} does for a
     * plugin that is attached - would consume the click for every placement, including a plugin that merely stands on
     * a wall.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // nothing to do with this plugin as an addon: the click belongs to the empty hand hook and to Oritech
        if (!canOpenScreen(state, level, pos)) return super.useItemOn(stack, state, level, pos, player, hand, hit);

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
        if (!canOpenScreen(state, level, pos)) return super.useWithoutItem(state, level, pos, player, hit);

        return openPluginMenu(level, pos, player);
    }

    /**
     * True while the player's click should open the plugin's own screen instead of reaching Oritech: the plugin
     * hangs on one of the two hosts it can serve.
     * <p>
     * <b>The two sides have to agree, and neither of them may need a value the other cannot see.</b> Oritech keeps
     * the position of the machine that claimed an addon in that addon's own block entity as a controller
     * <em>offset</em>, and that offset is save data of a plain (not networked) block entity: it is written on the
     * server and never reaches a client, where {@code getControllerPos()} therefore answers the plugin's own
     * position. The plugin's own answer to "do I serve a machine?" (see
     * {@link TransferAddonBlockEntity#servedMachinePos()}) is built on exactly that value and on the
     * extender's own controller position, so on the client it is always {@code null} - for a plugin that really is
     * in use as much as for one that serves nothing.
     * <p>
     * The client therefore asks the neighbouring block instead, which is synced: the block state of a machine
     * extender, or the {@code addon_used} flag Oritech's addon scan writes into this plugin once a machine claimed
     * it. Both are true for a plugin a machine really claimed, in both placements, and both are false for a plugin
     * on a wall. The server ends up with the same answer, because it reads the same two synced values first.
     */
    private static boolean canOpenScreen(BlockState state, Level level, BlockPos pos) {
        // a plugin standing on an extender: the extender itself has no screen, so this plugin is the only way to
        // configure it - also while no machine claimed that extender yet, which is the case the page reports as
        // "no machine" instead of leaving the click unanswered
        if (isAttachedToExtender(state, level, pos)) return true;

        return state.hasProperty(MachineAddonBlock.ADDON_USED) && state.getValue(MachineAddonBlock.ADDON_USED);
    }

    /**
     * True while the block this plugin was placed against is an Oritech machine extender. The neighbour is the one
     * the plugin hangs on (see {@link #attachedTowards(BlockState)}), which is where the machine extender sits when
     * the plugin is placed on one - on either side.
     */
    private static boolean isAttachedToExtender(BlockState state, Level level, BlockPos pos) {
        var hostPos = pos.relative(attachedTowards(state));
        if (!level.isLoaded(hostPos)) return false;

        return level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER);
    }

    /**
     * Opens the plugin's own screen for one of the two placements that have one, and reports {@code PASS} - i.e.
     * "leave this click to Oritech" - for every other placement.
     * <p>
     * Because the block interaction runs on both sides and only the server can open a menu, the client must not make
     * its answer depend on the machine lookup: it is what made a placed plugin impossible to open (see
     * {@link #canOpenScreen}). The client uses the synced placement instead and treats an attached plugin as
     * openable, and on the client {@link PluginAddonMenus#openItemMenu} consumes the click for this plugin without
     * opening anything (the server owns menus and tells the client about the one it opened), so all the client's call
     * decides is that the click is consumed here.
     * <p>
     * The <b>server</b> still resolves the machine, and the machine travels to the client with the menu (see
     * {@link PluginAddonMenus#openItemMenu(Level, BlockPos, Player, BlockPos)}): a plugin standing on an extender no
     * machine ever claimed opens the screen with no machine in it, which the page reports as "no machine" - the
     * player sees why nothing is configurable instead of a click that silently does nothing. A plugin that hangs on
     * an extender whose machine is currently unloaded keeps its screen too; the page shows the same "no machine"
     * state until the chunk comes back.
     */
    private static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TransferAddonBlockEntity plugin)) return InteractionResult.PASS;

        // resolved here, on the server, and sent along with the menu: the client cannot look it up (see
        // canOpenScreen) and the page needs it to build the model. The item hook's answer is asked and converted
        // because this hook is the one 1.21.1 expects an InteractionResult from (see PluginAddonMenus#openItemMenu)
        var opened = PluginAddonMenus.openItemMenu(level, pos, player, plugin.servedMachinePos());

        // The page draws what every cell-face of that machine does, and that map is the server's: it cannot travel
        // with the menu, whose container data is a fixed set of slots and cannot hold one setting per face of every
        // cell of a structure (see TransferNetworking.FaceModes). It is sent right after the menu, so the page has
        // it before the first frame it draws - and again after every change the player makes.
        if (opened == ItemInteractionResult.SUCCESS) {
            TransferNetworking.sendFaceModes(level, pos);
        }

        return opened.result();
    }
}
