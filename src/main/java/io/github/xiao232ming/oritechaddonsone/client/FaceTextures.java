package io.github.xiao232ming.oritechaddonsone.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;

/**
 * Which texture each of the six faces of an Extension Addon / Wireless Extension Dock uses, and how it has
 * to be sampled.
 * <p>
 * The Item Proxy page draws the block as an unfolded cube net from the block's <b>own</b> per-face
 * textures, so this is the one place that knows the face to texture mapping. It is derived from the block
 * models and blockstates of this mod:
 * <ul>
 *     <li>the standing (vertical) addon of types I and II is Oritech's machine extender slab - {@code port}
 *     on the front, {@code side} everywhere else - rotated by {@link ExtensionAddonBlock.HORIZONTAL_FACING},</li>
 *     <li>the flat (bottom/top) addon is the same slab laid down: {@code port} on the up face, {@code side}
 *     on the other five; the model samples the side textures from the <b>lower</b> half of the 16x16 texture
 *     ({@code "uv": [0, 8, 16, 16]}), so those faces are flipped vertically,</li>
 *     <li>type III uses its own recoloured port texture ({@code extension_addon_3_port} /
 *     {@code _port_off}),</li>
 *     <li>the wireless dock is a full cube whose model is rotated to {@code facing}: the port texture is on
 *     the face it points at and the side texture on the other five.</li>
 * </ul>
 * Everything is read from the live block state, so a placed block shows exactly the textures the world
 * shows it with.
 */
public record FaceTextures(Map<Direction, Face> faces) {

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
        if (!wired && !wireless) return new FaceTextures(defaultFaces(EXTENDER_PORT, ADDON_2_SIDE));

        var isType3 = name(block).contains("_3");
        var port = isType3 ? ADDON_3_PORT : EXTENDER_PORT;
        var side = isType3 ? ADDON_3_SIDE : name(block).contains("_2") ? ADDON_2_SIDE : ADDON_1_SIDE;

        if (wireless) {
            // a full cube whose model is rotated to `facing`: the port faces that direction
            var facing = stateValue(resolved, BlockStateProperties.FACING);
            return new FaceTextures(cubeFaces(port, side, facing == null ? Direction.NORTH : facing, false));
        }

        // the wired addon: a slab, either standing against the horizontal facing or lying flat
        var placement = resolved.getValue(ExtensionAddonBlock.PLACEMENT);
        if (placement == ExtensionAddonBlock.Placement.VERTICAL) {
            // The blockstate rotates the model by its facing (north: none, east: 90, south: 180, west: 270),
            // so the model's port face is the block's own HORIZONTAL_FACING - which is also the face the
            // Item Proxy page's net turns to the middle of the cross. Deriving it from get2DDataValue()
            // instead named the face opposite the facing (that 2D value plus two is the opposite
            // direction), so the net drew the interface texture on its back cell and the side texture on
            // its front one, i.e. exactly swapped.
            return new FaceTextures(cubeFaces(port, side,
                    resolved.getValue(ExtensionAddonBlock.HORIZONTAL_FACING), false));
        }

        // flat: the port is on the up face, the sides are sampled from the lower half of their texture
        var faces = new EnumMap<Direction, Face>(Direction.class);
        for (var face : Direction.values()) {
            var flipped = face.getAxis().isHorizontal();
            faces.put(face, new Face(face == Direction.UP ? port : side, flipped));
        }
        return new FaceTextures(faces);
    }

    /** Six faces of a cube whose model points its port texture at {@code portFacing}. */
    private static Map<Direction, Face> cubeFaces(ResourceLocation port, ResourceLocation side,
            Direction portFacing, boolean flipSides) {
        var faces = new EnumMap<Direction, Face>(Direction.class);
        for (var face : Direction.values()) {
            var isPort = face == portFacing;
            faces.put(face, new Face(isPort ? port : side, !isPort && flipSides));
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

    /** Registry path of the block, used only to pick the texture variant. */
    private static String name(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /** Value of a block state property, or {@code null} while the state does not carry it. */
    @Nullable
    private static <T extends Comparable<T>> T stateValue(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return state.hasProperty(property) ? state.getValue(property) : null;
    }
}
