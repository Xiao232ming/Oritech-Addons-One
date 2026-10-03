package io.github.xiao232ming.oritechaddonsone.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;

/**
 * The packet of the Extension Transfer page.
 * <p>
 * The page is a client side presentation, but what a face does is a property of the block entity - a mode is
 * what makes a face really feed or empty the machine for pipes, hoppers and other mods - so the server has to
 * apply it. One packet is enough: the player picked a face of the addon and the mode it should transfer with.
 * Everything the page <em>draws</em> comes back through the menu's container data (see
 * {@code ExtensionAddonMenu#transferMode}), which is what makes the colours of the net follow the server.
 */
public final class TransferNetworking {

    private TransferNetworking() {
    }

    /** Registers the packet with NeoForge's payload registrar. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetTransferMode.TYPE, SetTransferMode.CODEC, SetTransferMode::handle);
    }

    /** Sets what one face of the addon at {@code pos} does: the mode and its automation flag (packed). */
    public record SetTransferMode(BlockPos pos, int face, int value) implements CustomPacketPayload {

        public static final Type<SetTransferMode> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, "transfer_set_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetTransferMode> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetTransferMode::pos,
                ByteBufCodecs.VAR_INT, SetTransferMode::face,
                ByteBufCodecs.VAR_INT, SetTransferMode::value,
                SetTransferMode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SetTransferMode packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (!(player.level().getBlockEntity(packet.pos()) instanceof ExtensionAddonBlockEntity addon)) return;

            // never trust the client: the face index and the mode are both ranges the server checks itself
            var face = ProxyNetworking.faceOf(packet.face());
            if (face == null) return;

            var mode = TransferFaceModes.modeOf(packet.value());
            var automation = TransferFaceModes.automationOf(packet.value());
            if (!addon.setTransferConfig(face, mode, automation)) {
                OritechAddonsOne.LOGGER.debug("[transfer] refused {} for {} face {} (no transfer addon?)",
                        packet.value(), packet.pos(), face);
                return;
            }

            OritechAddonsOne.LOGGER.debug("[transfer] {} face {} -> {} (automation {}, {} face(s) configured)",
                    packet.pos(), face, mode, automation, addon.transferModes().configuredFaces());
        }
    }
}
