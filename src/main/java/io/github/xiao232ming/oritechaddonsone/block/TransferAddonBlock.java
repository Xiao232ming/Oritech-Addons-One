package io.github.xiao232ming.oritechaddonsone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * 扩展传输插件 - the transfer addon as a placed block.
 * <p>
 * Inside an Extension Addon - either variant - the plugin makes the machine reachable through the addon's own
 * faces; that behaviour lives on the addon's block entity and needs nothing from this class. Placed in the
 * world, the plugin does something of its own: hung on a <b>dock</b> (a wireless extension addon block) it
 * becomes a way to reach that dock's screen, so the same "Extension Transfer" page can be configured while
 * standing next to the dock instead of opening the dock itself.
 * <p>
 * What that page then configures is the dock - the modes are stored on the dock's block entity, the gold
 * border of the net marks the face this plugin hangs on, and the items are moved through the dock (its linked
 * machine on the one side, the containers around the dock on the other). Placed on anything else - a machine,
 * a wall - the plugin keeps Oritech's own addon behaviour and this class stays out of the way.
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

    /** The block a placed transfer addon hangs on, i.e. the one on the other side of {@link #attachedTowards}. */
    public static BlockPos supportPos(BlockState state, BlockPos pos) {
        return pos.relative(attachedTowards(state));
    }

    /**
     * The dock block entity this placed transfer addon hangs on, or {@code null} while it hangs on something
     * else (a machine, a wall, ...) or that chunk is not loaded. Only a dock hosts a placed plugin in this
     * sense: the wired addons are attached to their machine themselves.
     */
    @Nullable
    public static WirelessExtensionAddonBlockEntity attachedDock(Level level, BlockState state, BlockPos pos) {
        var support = supportPos(state, pos);
        if (!level.isLoaded(support)) return null;

        return level.getBlockEntity(support) instanceof WirelessExtensionAddonBlockEntity dock ? dock : null;
    }

    /**
     * True while the given neighbour block is a transfer addon hanging on the block whose neighbour it is -
     * the test a dock uses to find the plugins attached to it (see
     * {@code WirelessExtensionAddonBlockEntity#scanAttachedTransferFaces}). The plugin must really face that
     * block, not merely stand next to it.
     *
     * @param neighbourState the block state next to the host
     * @param neighbourFace  the direction from the host to that neighbour
     */
    public static boolean isAttachedTo(BlockState neighbourState, Direction neighbourFace) {
        if (!neighbourState.is(OritechAddonsOne.TRANSFER_ADDON.get())) return false;

        // neighbourFace points from the host to the plugin, so the plugin is attached towards its opposite
        return attachedTowards(neighbourState) == neighbourFace.getOpposite();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var opened = openDockMenu(state, level, pos, player);
        return opened != null ? opened : super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        var opened = openDockMenu(state, level, pos, player);
        return opened != null ? opened : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Right-clicking a placed transfer addon that hangs on a dock opens that dock's own screen: the net it
     * shows is the dock's, its gold border is the face this plugin hangs on, and everything set there is
     * applied by the dock. Anything else - no dock, or a plugin standing on a machine - is left to Oritech.
     */
    @Nullable
    private static InteractionResult openDockMenu(BlockState state, Level level, BlockPos pos, Player player) {
        var dock = attachedDock(level, state, pos);
        if (dock == null) return null;

        return WirelessExtensionAddonBlock.openDockMenu(level, dock.getBlockPos(), player);
    }
}
