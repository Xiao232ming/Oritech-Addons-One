package io.github.xiao232ming.oritechaddonsone.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.init.BlockContent;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;

/**
 * Which texture each of the six faces of an Extension Addon / Wireless Extension Dock uses, how it has to
 * be sampled, and which face the addon shows its interface on.
 * <p>
 * The Item Proxy page draws the block as an unfolded cube net from the block's <b>own</b> per-face
 * textures, so this is the one place that knows the face to texture mapping. It is derived from the block
 * models and blockstates of this mod - and of Oritech's machine extender, the one foreign block a page of
 * this mod draws (a placed transfer plugin, see {@code ExtensionTransferAddonBlockEntity}):
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
 *     <li>type III uses its own recoloured interface texture ({@code extension_addon_3_port}),</li>
 *     <li>every block whose state carries {@code addon_used} - Oritech's machine extender and this mod's wired
 *     addons, which inherit the property from {@link MachineAddonBlock} - has an <b>off</b> set of models as
 *     well as an on one: while the block is not claimed by a machine the blockstate applies the
 *     {@code *_off} model, which swaps the interface texture for {@code machine_extender_off} (or
 *     {@code extension_addon_3_port_off} on type III) and, on type II, the side texture for
 *     {@code extension_addon_2_side_off}; the other two types reuse their on side texture, because they have
 *     no separate off one. The net mirrors that by picking the whole texture set from the same property, so
 *     it looks like the block does in the world either way,</li>
 *     <li>Oritech's machine extender - the host a placed transfer plugin hangs on, and therefore the block a
 *     placed plugin's transfer page really draws - is a {@code minecraft:block/cube_all} over the very
 *     texture {@link #EXTENDER_PORT} names, so all six of its faces carry that one texture and there is no
 *     interface face to tell apart.</li>
 * </ul>
 * Everything is read from the live block state, so a placed block shows exactly the textures the world
 * shows it with, whatever direction it was placed in. {@link #front()} is the big face the player looks at:
 * the Item Proxy page turns it into the middle cell of its net, which is what keeps the net looking the
 * same for every orientation.
 */
public record FaceTextures(Map<Direction, Face> faces, Direction front) {

    /** One face: the texture it is drawn from and whether it is sampled upside down. */
    public record Face(Identifier texture, boolean flipVertically) {
    }

    // ------------------------------------------------------------------ textures

    /**
     * Texture of Oritech's machine extender: the whole texture of its {@code minecraft:block/cube_all} model
     * and, borrowed, the interface ("port") face of this mod's addons - which is why one constant serves both.
     */
    private static final Identifier EXTENDER_PORT =
            Identifier.fromNamespaceAndPath("oritech", "textures/block/machine_extender.png");
    /** Same, for the extender's {@code addon_used=false} model ({@code machine_extender_off}). */
    private static final Identifier EXTENDER_PORT_OFF =
            Identifier.fromNamespaceAndPath("oritech", "textures/block/machine_extender_off.png");
    private static final Identifier ADDON_1_SIDE =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_1_side.png");
    private static final Identifier ADDON_2_SIDE =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_2_side.png");
    /** Side texture of type II while unused: the only type whose side texture changes with the state. */
    private static final Identifier ADDON_2_SIDE_OFF =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_2_side_off.png");
    private static final Identifier ADDON_3_PORT =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_3_port.png");
    /** Interface texture of type III while unused; its side texture is the same either way. */
    private static final Identifier ADDON_3_PORT_OFF =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_3_port_off.png");
    private static final Identifier ADDON_3_SIDE =
            Identifier.fromNamespaceAndPath("oritechaddonsone", "textures/block/extension_addon_3_side.png");

    // ------------------------------------------------------------------ mapping

    /**
     * The six faces of the given block state.
     *
     * @param block the block to unfold: this mod's addon ({@code extension_addon_N} or
     *              {@code wireless_extension_addon_N}), or the Oritech machine extender a placed transfer
     *              plugin hangs on
     * @param state the block state, or {@code null} while it is unknown (the block's default state is used)
     */
    public static FaceTextures of(Block block, @Nullable BlockState state) {
        var resolved = state == null || state.getBlock() != block ? block.defaultBlockState() : state;

        // A placed transfer plugin reports the extender it hangs on as the block of its page, not itself (see
        // ExtensionTransferAddonBlockEntity#transferPageBlockState), so Oritech's machine extender ends up here even
        // though it is no addon of this mod: its model is a minecraft:block/cube_all over EXTENDER_PORT, one
        // texture on all six faces and no port or side face to distinguish. Which face is the "interface" one
        // is therefore arbitrary; NORTH only keeps the frame the net is built around deterministic.
        // Its blockstate still swaps the whole model on addon_used - machine_extender_off while no machine
        // has claimed the extender - so the one texture follows that property.
        if (block == BlockContent.MACHINE_EXTENDER.get()) {
            var extender = isAddonUsed(resolved) ? EXTENDER_PORT : EXTENDER_PORT_OFF;
            return new FaceTextures(defaultFaces(extender, extender), Direction.NORTH);
        }

        var wired = block instanceof ExtensionAddonBlock;
        var wireless = block instanceof WirelessExtensionAddonBlock;
        if (!wired && !wireless) {
            return new FaceTextures(defaultFaces(EXTENDER_PORT, ADDON_2_SIDE), Direction.NORTH);
        }

        var name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        var isType3 = name.contains("_3");
        // The wired addons inherit Oritech's addon_used property, and the extender carries it too, so the
        // whole texture set - not just a face - follows it: an unused block is drawn from the *_off model.
        // A state without the property (the wireless docks, which switch on their own `linked` flag) keeps
        // the on set. Which set applies is a property of the state and not of the block, because the same
        // block is drawn on and off at different times.
        var used = isAddonUsed(resolved);
        var port = isType3
                ? used ? ADDON_3_PORT : ADDON_3_PORT_OFF
                : used ? EXTENDER_PORT : EXTENDER_PORT_OFF;
        var side = isType3
                ? ADDON_3_SIDE
                : used ? name.contains("_2") ? ADDON_2_SIDE : ADDON_1_SIDE
                        : name.contains("_2") ? ADDON_2_SIDE_OFF : ADDON_1_SIDE;

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
    private static Map<Direction, Face> cubeFaces(Identifier port, Identifier side, Direction front) {
        var faces = new EnumMap<Direction, Face>(Direction.class);
        for (var face : Direction.values()) {
            faces.put(face, new Face(face == front ? port : side, false));
        }
        return faces;
    }

    /**
     * Six faces of a block with no orientation to respect: the port texture in the middle, the side texture
     * on the other five. A {@code cube_all} block such as Oritech's machine extender calls this with the same
     * texture twice, because its six faces really are one texture; an unknown block calls it with the
     * extender port and an addon side, where a readable net is better than none either way.
     */
    private static Map<Direction, Face> defaultFaces(Identifier port, Identifier side) {
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

    /**
     * Oritech's {@code addon_used} flag of the given state: true while a machine has claimed the addon (or
     * while the state does not carry the property at all, such as the wireless docks), false only while the
     * block is really unused. Reading it through one accessor keeps every caller on that convention.
     */
    private static boolean isAddonUsed(BlockState state) {
        return !Boolean.FALSE.equals(value(state, MachineAddonBlock.ADDON_USED));
    }
}
