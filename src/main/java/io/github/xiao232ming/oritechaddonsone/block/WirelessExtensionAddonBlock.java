package io.github.xiao232ming.oritechaddonsone.block;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinking;

/**
 * The Wireless Extension Addon: a full block that stores the same plugins as the wired Extension Addon
 * and applies them to a machine it is <b>linked</b> to instead of being attached to it.
 * <p>
 * Linking uses Oritech's target designator: right click this block with the designator to save its
 * position, then right click the machine with the same designator. The link is stored in this block and
 * survives world saves; linking to another machine simply moves the link.
 * <p>
 * The block is a normal (non addon) block on purpose: it must not be placeable in a machine's addon slot.
 * Its facing decides which of the six faces carries the connector port texture, the other five faces use
 * the side texture of the matching wired addon.
 */
public class WirelessExtensionAddonBlock extends Block implements EntityBlock, AddonDetailProvider {

    /** Which face carries the connector port. Set from the face the player clicked. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * True while a machine is linked and loaded. The textures switch on this, so the port shows the
     * active colours of the wired addon while the dock is actually connected.
     */
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    /** True while a control unit (redstone) plugin is stored inside, same meaning as on the wired block. */
    public static final BooleanProperty HAS_CONTROL_UNIT = BooleanProperty.create("control_unit");

    private final ExtensionAddonType type;

    public WirelessExtensionAddonBlock(Properties properties, ExtensionAddonType type) {
        super(properties);
        this.type = type;

        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LINKED, false)
                .setValue(HAS_CONTROL_UNIT, false));
    }

    @Override
    public ExtensionAddonType getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        builder.add(LINKED);
        builder.add(HAS_CONTROL_UNIT);
    }

    /** The clicked face becomes the port face, so the port can be aimed in all six directions. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(LINKED, false);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessExtensionAddonBlockEntity(pos, state);
    }

    /**
     * Server ticker: the redstone control of type II and the periodic re-apply of the plugins. A wired
     * addon is polled by the machine, a wireless one has to do this itself.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof WirelessExtensionAddonBlockEntity dock) {
                dock.serverTick();
            }
        };
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity dock) {
            dock.applyRedstoneSignal(level.hasNeighborSignal(pos));
        }
    }

    /**
     * Opening the menu must not swallow the click of Oritech's target designator: vanilla runs the block
     * first and only reaches {@code Item#useOn} (where the designator stores this dock's position) when
     * the block passes on the interaction. With the designator in hand the dock therefore steps aside.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (WirelessLinking.isHoldingLinkDesignator(player)) return InteractionResult.PASS;
        return openPluginMenu(level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (WirelessLinking.isLinkDesignator(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return openPluginMenu(level, pos, player) == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity dock) {
            var slots = dock.getContainerSize();
            serverPlayer.openMenu(dock, buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeVarInt(slots);
            });
        }
        return InteractionResult.SUCCESS;
    }


    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Drop everything stored inside (including slots that are currently disabled by the config),
        // so nothing is lost when the block is mined.
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity dock) {
            // release the linked machine first if this dock was the one holding it switched off, and
            // forget the link so the machine stops expecting plugins from here
            dock.releaseRedstoneOnRemoval();
            dock.clearLink();

            for (int slot = 0; slot < ExtensionAddonLayout.MAX_SLOTS; slot++) {
                var stack = dock.getItem(slot);
                while (!stack.isEmpty()) {
                    Block.popResource(level, pos, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
                }
            }
            dock.clearContent();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** All detail lines of this addon type, shown while Ctrl is held. */
    @Override
    public void appendDetails(Consumer<Component> consumer) {
        var key = "tooltip.oritechaddonsone.wireless_" + type.id();

        consumer.accept(Component.translatable(key + ".insert").withStyle(ChatFormatting.GOLD));

        if (type == ExtensionAddonType.TYPE_1) {
            consumer.accept(Component.translatable(key + ".acceptor").withStyle(ChatFormatting.AQUA));
        } else if (type == ExtensionAddonType.TYPE_2) {
            consumer.accept(Component.translatable(key + ".special").withStyle(ChatFormatting.AQUA));
        } else {
            consumer.accept(Component.translatable(key + ".capacity", Config.slotCapacity(type))
                    .withStyle(ChatFormatting.AQUA));
        }

        consumer.accept(Component.translatable(key + ".link").withStyle(ChatFormatting.LIGHT_PURPLE));
        consumer.accept(Component.translatable(key + ".splicer").withStyle(ChatFormatting.DARK_RED));
    }
}
