package io.github.xiao232ming.oritechaddonsone.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Base of this mod's plain Oritech plugins that are placed as real addon blocks - the warehouse addon
 * and the tank addon.
 * <p>
 * Everything else is inherited from {@link MachineAddonBlock}: these blocks occupy an addon slot of a
 * machine like any Oritech plugin, they are found by Oritech's addon scan (which looks for a
 * {@code MachineAddonBlock} with an {@link AddonBlockEntity}) and they save and load like one.
 * <p>
 * Only the block entity creation has to be replaced. Oritech's {@link MachineAddonBlock#newBlockEntity}
 * builds its block entity reflectively through the two argument constructor of
 * {@link AddonBlockEntity}, and that constructor hardcodes Oritech's own {@code oritech:addon} type.
 * Since Minecraft validates the block state against the type in the block entity constructor (see
 * {@code BlockEntity#validateBlockState}), placing one of these blocks crashed with
 * {@code Invalid block entity oritech:addon ... got Block{oritechaddonsone:warehouse_addon}}: the
 * shared Oritech type lists only Oritech's own addon blocks as valid. The override below passes this
 * mod's own {@link OritechAddonsOne#PLUGIN_ADDON_ENTITY}, which was registered with both plugin blocks
 * in its valid-block list.
 */
public class PluginAddonBlock extends MachineAddonBlock {

    public PluginAddonBlock(Properties properties, AddonSettings addonSettings) {
        super(properties, addonSettings);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AddonBlockEntity(OritechAddonsOne.PLUGIN_ADDON_ENTITY.get(), pos, state);
    }
}
