package io.github.xiao232ming.oritechaddonsone.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import rearth.oritech.util.ColorableMachine.ColorVariant;
import rearth.oritech.util.PaintableBlock;

/**
 * An Extension Addon that Oritech's paint cartridge can recolour.
 * <p>
 * The colour property has to be added by a subclass rather than by a flag in {@link ExtensionAddonBlock}:
 * {@link Block#createBlockStateDefinition} runs inside the {@link Block} constructor, so an instance field
 * assigned after {@code super(...)} is still at its default value at that point. Same split as Oritech's own
 * {@code PaintableMachineAddonBlock} / {@code MachineAddonBlock}.
 */
public class PaintableExtensionAddonBlock extends ExtensionAddonBlock implements PaintableBlock {

    public PaintableExtensionAddonBlock(Properties properties, AddonSettings addonSettings, ExtensionAddonType type) {
        super(properties, addonSettings, type);
        registerDefaultState(defaultBlockState().setValue(COLOR, ColorVariant.ORANGE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }
}
