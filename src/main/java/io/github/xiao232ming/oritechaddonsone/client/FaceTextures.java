package io.github.xiao232ming.oritechaddonsone.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;

/**
 * Which texture each of the six faces of an Extension Addon / Wireless Extension Dock uses, how it has to
 * be sampled, and which face the addon shows its interface on.
 * <p>
 * The Item Proxy page draws the block as an unfolded cube net from the block's <b>own</b> per-face
 * textures, so this is the one place that knows the face to texture mapping. It is derived from the block
 * models and blockstates of this mod:
 * <ul>
 *     <li>the standing (vertical) addon of every type is the same machine extender slab: the interface
 *     texture is on the big face the player looks at, and the side texture on the other big face - the half
 *     the slab occupies, standing against the machine - and on the four cut edges. The slab occupies the half
 *     of the block it was placed against ({@link ExtensionAddonBlock#HORIZONTAL_FACING} points at the machine
 *     in front of the player), so the face carrying the interface is the opposite of that facing,</li>
 *     <li>the flat (bottom/top) addon is the same slab laid down: only the big face the player sees carries
 *     the interface - up for a bottom placement, down for a top one, whose blockstate flips the model
 *     ({@code "x": 180}) - and the other five the side texture; the model samples the side textures from the
 *     <b>lower</b> half of the 16x16 texture ({@code "uv": [0, 8, 16, 16]}), so the horizontal faces are
 *     flipped vertically,</li>
 *     <li>the wireless dock is a full cube whose model is rotated to {@code facing}: its interface faces
 *     that direction and the side texture covers the other five,</li>
 *     <li>type III uses its own recoloured interface texture ({@code extension_addon_3_port}).</li>
 * </ul>
 * Everything is read from the live block state, so a placed block shows exactly the textures the world
 * shows it with, whatever direction it was placed in. {@link #front()} is the big face the player looks at:
 * the Item Proxy page turns it into the middle cell of its net, which is what keeps the net looking the
 * same for every orientation.
 */
public record FaceTextures(Map<Direction, Face> faces, Direction front) {

    /** One face: the texture it is drawn from and whether it is sampled upside down. */
    public record Face(ResourceLocation texture, boolean flipVertically) {
    }

    // ------------------------------------------------------------------ textures

    private static final ResourceLocation EXTENDER_PORT =
            ResourceLocation.fromNamespaceAndPath("oritech", "textures/block/machine_extender.png");
    private static final ResourceLocation ADDON_1_SIDE =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_1_side.png");
    private static final ResourceLocation ADDON_2_SIDE =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_2_side.png");
    private static final ResourceLocation ADDON_3_PORT =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_3_port.png");
    private static final ResourceLocation ADDON_3_SIDE =
            ResourceLocation.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_3_side.png");

    // ------------------------------------------------------------------ mapping

    /**
     * The six faces of the given block state.
     *
     * @param block the addon block ({@code extension_addon_N} or {@code wireless_extension_addon_N})
     * @param state the block state, or {@code null} while it is unknown (the block's default state is used)
     */
    public static FaceTextures of(Block block, @Nullable BlockState state) {
        var resolved = state == null || state.getBlock() != block ? block.defaultBlockState() : state;

        var wired = block instanceof ExtensionAddonBlock;
        var wireless = block instanceof WirelessExtensionAddonBlock;
        if (!wired && !wireless) {
            return new FaceTextures(defaultFaces(EXTENDER_PORT, ADDON_2_SIDE), Direction.NORTH);
        }

        var name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        var isType3 = name.contains("_3");
        var port = isType3 ? ADDON_3_PORT : EXTENDER_PORT;
        var side = isType3 ? ADDON_3_SIDE : name.contains("_2") ? ADDON_2_SIDE : ADDON_1_SIDE;

        if (wireless) {
            // a full cube whose model is rotated to `facing`: the interface faces that direction
            var facing = value(resolved, BlockStateProperties.FACING);
            var front = facing == null ? Direction.NORTH : facing;
            return new FaceTextures(cubeFaces(port, side, front), front);
        }

        var placement = value(resolved, ExtensionAddonBlock.PLACEMENT);
        if (placement == ExtensionAddonBlock.Placement.VERTICAL) {
            // The model puts the interface on its outward half and the side texture on the half that stands
            // against the machine, so the face the player looks at is the one that carries the interface -
            // and that is the front of the net.
            var facing = value(resolved, ExtensionAddonBlock.HORIZONTAL_FACING);
            var front = (facing == null ? Direction.NORTH : facing).getOpposite();
            return new FaceTextures(cubeFaces(port, side, front), front);
        }

        // flat: the interface is on the big face the player sees - up, or down when the blockstate flipped
        // the model for a top placement - and the sides are sampled from the lower half of their texture
        var front = placement == ExtensionAddonBlock.Placement.TOP ? Direction.DOWN : Direction.UP;
        var faces = new EnumMap<Direction, Face>(Direction.class);
        for (var face : Direction.values()) {
            faces.put(face, new Face(face == front ? port : side, face.getAxis().isHorizontal()));
        }
        return new FaceTextures(faces, front);
    }

    /** Six faces of a cube whose model points its interface texture at {@code front}. */
    private static Map<Direction, Face> cubeFaces(ResourceLocation port, ResourceLocation side, Direction front) {
        var faces = new EnumMap<Direction, Face>(Direction.class);
        for (var face : Direction.values()) {
            faces.put(face, new Face(face == front ? port : side, false));
        }
        return faces;
    }

    /** Every face uses the same texture (unknown block: better a readable net than none). */
    private static Map<Direction, Face> defaultFaces(ResourceLocation port, ResourceLocation side) {
        var faces = new EnumMap<Direction, Face>(Direction.class);
        for (var face : Direction.values()) {
            faces.put(face, new Face(face == Direction.NORTH ? port : side, false));
        }
        return faces;
    }

    /** The face of the given direction; never {@code null} for a complete mapping. */
    public Face face(Direction direction) {
        var face = faces.get(direction);
        return face == null ? new Face(EXTENDER_PORT, false) : face;
    }

    /** Value of a block state property, or {@code null} while the state does not carry it. */
    @Nullable
    private static <T extends Comparable<T>> T value(BlockState state, Property<T> property) {
        return state.hasProperty(property) ? state.getValue(property) : null;
    }
}
