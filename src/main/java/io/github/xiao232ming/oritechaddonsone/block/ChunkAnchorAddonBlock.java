package io.github.xiao232ming.oritechaddonsone.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import io.github.xiao232ming.oritechaddonsone.block.entity.ChunkAnchorAddonBlockEntity;

/**
 * 锚点插件 - the chunk anchor plugin.
 * <p>
 * It is a normal Oritech plugin that can be attached to any face of a machine and contributes nothing to
 * the machine's stats; its whole effect is that the chunk containing the machine it is attached to stays
 * loaded, see {@code AnchorForceLoad}. The same item can instead be put into the reserved slot of an
 * Extension Addon or a Wireless Extension Dock, where it force loads the chunk of the machine that addon
 * is connected to.
 * <p>
 * Behaviour is identical to {@link PluginAddonBlock} (the warehouse and tank addons) - same base class,
 * same settings, same bounding shape - the only difference is the block entity, which is what learns which
 * machine claimed the anchor.
 */
public class ChunkAnchorAddonBlock extends PluginAddonBlock {

    public ChunkAnchorAddonBlock(Properties properties, AddonSettings addonSettings) {
        super(properties, addonSettings);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkAnchorAddonBlockEntity(pos, state);
    }

    /**
     * Server ticker: handing the block entity to its own {@link BlockEntityTicker} implementation is
     * Oritech's own pattern for its redstone addon (see {@code RedstoneAddonBlock#getTicker}), and it keeps
     * the anchor from being ticked on the client - the anchor block entity implements the ticker for
     * {@link BlockEntity}, so it can be called without knowing the exact type parameter of the registry.
     */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<BlockEntity>) (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BlockEntityTicker<?> ticker) {
                ((BlockEntityTicker<BlockEntity>) ticker).tick(tickLevel, pos, tickState, blockEntity);
            }
        };
    }
}
