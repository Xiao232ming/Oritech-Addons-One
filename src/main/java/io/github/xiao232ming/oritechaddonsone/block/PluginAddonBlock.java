package io.github.xiao232ming.oritechaddonsone.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.util.Geometry;

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
 * <p>
 * The outline of the blocks is narrower than a full block, see {@link #PLUGIN_ADDON_SHAPE}.
 */
public class PluginAddonBlock extends MachineAddonBlock {

    /**
     * Outline and collision shape of every plugin block, one box per facing/face pair.
     * <p>
     * The two models are Blockbench exports of Oritech's machine speed addon (see
     * {@code models/block/warehouse_addon.json} and {@code models/block/tank_addon.json}): they share the
     * pedestal of that addon and replace its crystal with a miniature chest or tank, whose footprint
     * (model x 6.5..12.5, z 3.5..9.5) is exactly the footprint of the crystal Oritech put there
     * (x 8..11, z 5..8, same centre). The box below is the symmetric box of those models - the pedestal
     * footprint x/z 4..12 and the full model height y 0..10 in model pixels, i.e. 8x10x8 pixels. Only the
     * small decorative brackets of the pedestal reach further out (to x 3 and 14, z 2 and 13), so the box
     * hugs the bulk of the model without following every corner.
     * <p>
     * A box cannot be reused for all orientations: a plugin attached to a wall hangs in the middle of the
     * block instead of standing on its floor. Since the models are geometrically identical to Oritech's
     * own speed addon, the box is therefore rotated with Oritech's own
     * {@link Geometry#rotateVoxelShape(VoxelShape, Direction, AttachFace)}, the very helper Oritech uses
     * to build the shapes of its own addons. That keeps the outline on the model in every orientation the
     * blockstate rotates it into, and it needs no per-state code: Oritech's
     * {@code MachineAddonBlock#getShape} picks the entry, and {@code getCollisionShape} returns the same
     * shape.
     * <p>
     * Oritech only consults this array while its {@code tightMachineAddonHitboxes} option is enabled -
     * which is its default, and what Oritech's own addons are shaped by. With the option off every addon,
     * Oritech's own included, falls back to a full block.
     */
    public static final VoxelShape[][] PLUGIN_ADDON_SHAPE =
            new VoxelShape[Direction.values().length][AttachFace.values().length];

    static {
        var box = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.625, 0.75);

        for (var facing : Direction.values()) {
            // Only horizontal facings occur: FaceAttachedHorizontalDirectionalBlock never stores UP/DOWN.
            if (!facing.getAxis().isHorizontal()) continue;

            for (var face : AttachFace.values()) {
                PLUGIN_ADDON_SHAPE[facing.ordinal()][face.ordinal()] = Geometry.rotateVoxelShape(box, facing, face);
            }
        }
    }

    public PluginAddonBlock(Properties properties, AddonSettings addonSettings) {
        super(properties, addonSettings);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AddonBlockEntity(OritechAddonsOne.PLUGIN_ADDON_ENTITY.get(), pos, state);
    }
}
