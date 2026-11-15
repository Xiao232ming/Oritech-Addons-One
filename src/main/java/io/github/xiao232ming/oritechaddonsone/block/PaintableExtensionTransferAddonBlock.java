package io.github.xiao232ming.oritechaddonsone.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import rearth.oritech.util.ColorableMachine.ColorVariant;
import rearth.oritech.util.PaintableBlock;

/**
 * The extension transfer addon, recolourable by Oritech's paint cartridge. The plain transfer addon stays
 * orange on purpose and keeps extending {@link TransferAddonBlock} / {@link PluginAddonBlock} directly.
 * <p>
 * The colour property is added by this subclass because {@link Block#createBlockStateDefinition} runs
 * inside the {@link Block} constructor; see {@link PaintableExtensionAddonBlock}.
 */
public class PaintableExtensionTransferAddonBlock extends ExtensionTransferAddonBlock implements PaintableBlock {

    public PaintableExtensionTransferAddonBlock(Properties properties, AddonSettings addonSettings) {
        super(properties, addonSettings);
        registerDefaultState(defaultBlockState().setValue(COLOR, ColorVariant.ORANGE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }
}
