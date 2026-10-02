package io.github.xiao232ming.oritechaddonsone.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/**
 * The packets of the Item Proxy page.
 * <p>
 * The page is a client side presentation, but the binding it edits belongs to the block entity - and a
 * binding is what makes a face really proxy the machine's inventory, so the server has to apply it. Two
 * small packets are enough:
 * <ul>
 *     <li>{@link BindFace} - the player picked a face and a machine inventory slot; the server writes the
 *     binding into the block entity (which saves it),</li>
 *     <li>{@link RequestPicker} - the player opened the picker of one face; the server answers with
 *     {@link PickerSlots}, the slot layout of the machine, so the client can draw it without having the
 *     machine's block entity loaded.</li>
 * </ul>
 * A third packet, {@link ClearFace}, unbinds a face again (right click on the net).
 */
public final class ProxyNetworking {

    private ProxyNetworking() {
    }

    /** Registers the three packets with NeoForge's payload registrar. */
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(BindFace.TYPE, BindFace.CODEC, BindFace::handle);
        registrar.playToServer(RequestPicker.TYPE, RequestPicker.CODEC, RequestPicker::handle);
        registrar.playToServer(ClearFace.TYPE, ClearFace.CODEC, ClearFace::handle);
        registrar.playToClient(PickerSlots.TYPE, PickerSlots.CODEC, PickerSlots::handle);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
    }

    // ------------------------------------------------------------------ client -> server

    /** Binds one face of the addon at {@code pos} to one slot of the machine inventory. */
    public record BindFace(BlockPos pos, int face, int slot) implements CustomPacketPayload {

        public static final Type<BindFace> TYPE = new Type<>(id("proxy_bind_face"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BindFace> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BindFace::pos,
                ByteBufCodecs.VAR_INT, BindFace::face,
                ByteBufCodecs.VAR_INT, BindFace::slot,
                BindFace::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(BindFace packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (player.level().getBlockEntity(packet.pos()) instanceof ExtensionAddonBlockEntity addon) {
                var face = faceOf(packet.face());
                if (face == null) return;

                // The client only sends a slot the server told it about, and the server re-checks that the
                // slot really exists on the machine, so a stale client can never bind a slot that is not
                // there.
                if (packet.slot() < 0 || packet.slot() >= addon.proxySlotCount()) {
                    OritechAddonsOne.LOGGER.debug("[proxy] refused binding {} face {} to slot {}",
                            packet.pos(), face, packet.slot());
                    return;
                }

                addon.bindProxyFace(face, packet.slot());
                OritechAddonsOne.LOGGER.debug("[proxy] {} face {} -> machine slot {} ({} face(s) configured)",
                        packet.pos(), face, packet.slot(), addon.proxyFaces().configuredFaces());
            }
        }
    }

    /** Asks the server for the slot layout of the machine one face of the addon works on. */
    public record RequestPicker(BlockPos pos, int face) implements CustomPacketPayload {

        public static final Type<RequestPicker> TYPE = new Type<>(id("proxy_picker_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPicker> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RequestPicker::pos,
                ByteBufCodecs.VAR_INT, RequestPicker::face,
                RequestPicker::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(RequestPicker packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (player.level().getBlockEntity(packet.pos()) instanceof ExtensionAddonBlockEntity addon) {
                var face = faceOf(packet.face());
                if (face == null) return;

                var slots = addon.proxyPickerSlots();
                OritechAddonsOne.LOGGER.debug("[proxy] picker for {} face {}: {} slot(s)",
                        packet.pos(), face, slots.size());
                context.reply(new PickerSlots(packet.pos(), packet.face(), flattenSlots(slots)));
            }
        }
    }

    /** Removes the binding of one face (right click on the net). */
    public record ClearFace(BlockPos pos, int face) implements CustomPacketPayload {

        public static final Type<ClearFace> TYPE = new Type<>(id("proxy_clear_face"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClearFace> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ClearFace::pos,
                ByteBufCodecs.VAR_INT, ClearFace::face,
                ClearFace::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ClearFace packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (player.level().getBlockEntity(packet.pos()) instanceof ExtensionAddonBlockEntity addon) {
                var face = faceOf(packet.face());
                if (face == null) return;

                addon.unbindProxyFace(face);
                OritechAddonsOne.LOGGER.debug("[proxy] {} face {} unbound ({} face(s) configured)",
                        packet.pos(), face, addon.proxyFaces().configuredFaces());
            }
        }
    }

    // ------------------------------------------------------------------ server -> client

    /**
     * The slot layout of the machine: three ints per slot (container index, x, y), flattened so one
     * packet can carry a machine's whole inventory without a nested codec.
     */
    public record PickerSlots(BlockPos pos, int face, List<Integer> slots) implements CustomPacketPayload {

        public static final Type<PickerSlots> TYPE = new Type<>(id("proxy_picker_slots"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PickerSlots> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PickerSlots::pos,
                ByteBufCodecs.VAR_INT, PickerSlots::face,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), PickerSlots::slots,
                PickerSlots::new);

        public PickerSlots(BlockPos pos, int face, List<Integer> slots) {
            this.pos = pos;
            this.face = face;
            this.slots = List.copyOf(slots);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PickerSlots packet, IPayloadContext context) {
            var face = faceOf(packet.face());
            if (face == null) return;
            ClientHandler.deliver(packet.pos(), face, packet.slots());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Direction of a wire value, or {@code null} while it is out of range (never trust the client). */
    public static Direction faceOf(int index) {
        var values = Direction.values();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    /** Wire value of a direction. */
    public static int faceIndex(Direction face) {
        return face.ordinal();
    }

    /** Flattens slot triplets into the packet's list. */
    public static List<Integer> flattenSlots(List<int[]> slots) {
        var flat = new ArrayList<Integer>(slots.size() * 3);
        for (var slot : slots) {
            flat.add(slot[0]);
            flat.add(slot[1]);
            flat.add(slot[2]);
        }
        return flat;
    }

    /**
     * Client side hook, implemented in the client only package. It is kept behind a mutable holder that
     * defaults to a no-op, so a dedicated server can load this class without ever touching a client class.
     */
    public interface ClientHandler {
        void pickerSlots(BlockPos pos, Direction face, List<Integer> slots);

        /** Hands the layout to the installed handler (no-op on a dedicated server). */
        static void deliver(BlockPos pos, Direction face, List<Integer> slots) {
            clientHandler.pickerSlots(pos, face, slots);
        }
    }

    /** Replaced by the client setup; stays a no-op on a dedicated server. */
    private static ClientHandler clientHandler = (pos, face, slots) -> {
    };

    public static void setClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }
}
