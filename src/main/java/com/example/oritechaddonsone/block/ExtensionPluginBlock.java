package com.example.oritechaddonsone.block;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;

import com.example.oritechaddonsone.Config;
import com.example.oritechaddonsone.block.entity.ExtensionPluginBlockEntity;
import com.example.oritechaddonsone.menu.ExtensionPluginLayout;

/**
 * The Extension Plugin block: an Oritech machine plugin that looks like a slab (half block)
 * and holds up to five stacks of other Oritech plugins in its own inventory.
 * <p>
 * It can be placed in two orientations, like a slab can:
 * <ul>
 *     <li>clicking the <b>side</b> of a block places it standing upright (vertical half block,
 *     rotated to the player's look direction),</li>
 *     <li>clicking the <b>top</b> or <b>bottom</b> of a block places it lying flat
 *     (lower or upper half block).</li>
 * </ul>
 * <p>
 * In 1.21.1 the tooltip of a block is not picked up by its block item automatically, so the Ctrl
 * gated description lives in {@link com.example.oritechaddonsone.item.ExtensionPluginItem} and only
 * the detail lines ({@link #appendDetails}) are provided here.
 */
public class ExtensionPluginBlock extends MachineAddonBlock {

    /** Used for the standing orientation: which half of the block the slab occupies. */
    public static final EnumProperty<Direction> HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How the slab is oriented. */
    public enum Placement implements StringRepresentable {
        /** Standing upright against a side; {@link #HORIZONTAL_FACING} decides which half. */
        VERTICAL("vertical"),
        /** Lying flat, occupying the lower half of the block. */
        BOTTOM("bottom"),
        /** Lying flat, occupying the upper half of the block. */
        TOP("top");

        private final String name;

        Placement(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final EnumProperty<Placement> PLACEMENT = EnumProperty.create("placement", Placement.class);

    private final ExtensionPluginType type;

    public ExtensionPluginBlock(Properties properties, AddonSettings addonSettings, ExtensionPluginType type) {
        super(properties, addonSettings);
        this.type = type;
    }

    /** Which Extension Plugin this block is (decides slot count, accepted plugins and GUI). */
    public ExtensionPluginType getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PLACEMENT);
        builder.add(HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState();
        var clickedFace = context.getClickedFace();

        if (clickedFace.getAxis().isHorizontal()) {
            // Placed against a side: stand the slab up, on the side the player is looking at
            // (i.e. against the machine in front of them).
            return state.setValue(PLACEMENT, Placement.VERTICAL)
                    .setValue(HORIZONTAL_FACING, context.getHorizontalDirection());
        }

        // Placed on top of or below a block: lay the slab flat, like a vanilla slab.
        return state.setValue(PLACEMENT, clickedFace == Direction.DOWN ? Placement.TOP : Placement.BOTTOM);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (state.getValue(PLACEMENT) != Placement.VERTICAL) return state;
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (state.getValue(PLACEMENT) != Placement.VERTICAL) return state;
        return state.setValue(HORIZONTAL_FACING, mirror.mirror(state.getValue(HORIZONTAL_FACING)));
    }

    /** Standing: full height, half of the block in the horizontal plane. Flat: half height. */
    private static VoxelShape slabShape(BlockState state) {
        return switch (state.getValue(PLACEMENT)) {
            case BOTTOM -> Block.box(0, 0, 0, 16, 8, 16);
            case TOP -> Block.box(0, 8, 0, 16, 16, 16);
            case VERTICAL -> switch (state.getValue(HORIZONTAL_FACING)) {
                case NORTH -> Block.box(0, 0, 0, 16, 16, 8);
                case SOUTH -> Block.box(0, 0, 8, 16, 16, 16);
                case WEST -> Block.box(0, 0, 0, 8, 16, 16);
                case EAST -> Block.box(8, 0, 0, 16, 16, 16);
                default -> Shapes.block();
            };
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return slabShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return slabShape(state);
    }

    @Override
    public Class<? extends BlockEntity> getBlockEntityType() {
        return ExtensionPluginBlockEntity.class;
    }

    /**
     * Picks up redstone changes at this block, like Oritech's own control unit plugin does: while a
     * control unit plugin is stored inside, the signal at this block controls the connected machine
     * (it is turned off while powered).
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtensionPluginBlockEntity blockEntity) {
            blockEntity.applyRedstoneSignal(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return openPluginMenu(level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return openPluginMenu(level, pos, player) == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ExtensionPluginBlockEntity blockEntity) {
            // The slot count is configurable, so the client needs it to build the matching layout.
            var slots = blockEntity.getContainerSize();
            serverPlayer.openMenu(blockEntity, buffer -> {
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtensionPluginBlockEntity blockEntity) {
            for (int slot = 0; slot < ExtensionPluginLayout.MAX_SLOTS; slot++) {
                var stack = blockEntity.getItem(slot);
                // Type III slots can hold far more than one stack, drop it in valid chunks.
                while (!stack.isEmpty()) {
                    Block.popResource(level, pos, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
                }
            }
            blockEntity.clearContent();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** All detail lines of this plugin type, shown while Ctrl is held. */
    public void appendDetails(Consumer<Component> consumer) {
        var key = "tooltip.oritechaddonsone." + type.id();

        consumer.accept(Component.translatable(key + ".insert").withStyle(ChatFormatting.GOLD));

        if (type == ExtensionPluginType.TYPE_1) {
            consumer.accept(Component.translatable(key + ".acceptor").withStyle(ChatFormatting.AQUA));
        } else if (type == ExtensionPluginType.TYPE_2) {
            consumer.accept(Component.translatable(key + ".special").withStyle(ChatFormatting.AQUA));
        } else {
            consumer.accept(Component.translatable(key + ".capacity", Config.slotCapacity(type))
                    .withStyle(ChatFormatting.AQUA));
        }

        consumer.accept(Component.translatable(key + ".splicer").withStyle(ChatFormatting.DARK_RED));
    }
}
