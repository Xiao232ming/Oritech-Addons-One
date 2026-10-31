package io.github.xiao232ming.oritechaddonsone.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import rearth.oritech.api.networking.NetworkManager;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/** The packet of the Redstone Control page: a new mode or monitored slot for the stored control unit. */
public final class RedstoneNetworking {

    private RedstoneNetworking() {
    }

    public static void register() {
        NetworkManager.registerToServer(Configure.TYPE, Configure.CODEC, Configure::handle);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
    }

    public record Configure(BlockPos pos, int mode, int slot) implements CustomPacketPayload {

        public static final Type<Configure> TYPE = new Type<>(id("redstone_configure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Configure::pos,
                ByteBufCodecs.VAR_INT, Configure::mode,
                ByteBufCodecs.VAR_INT, Configure::slot,
                Configure::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Configure packet, Player player, RegistryAccess registryAccess) {
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            if (!(serverPlayer.level().getBlockEntity(packet.pos()) instanceof ExtensionAddonBlockEntity addon)) {
                return;
            }
            if (addon.pluginType() != ExtensionAddonType.TYPE_2 || !addon.hasRedstonePlugin()) return;

            var modes = rearth.oritech.block.entity.addons.RedstoneAddonBlockEntity.RedstoneMode.values();
            if (packet.mode() < 0 || packet.mode() >= modes.length) return;

            addon.setRedstoneConfig(modes[packet.mode()], Math.max(0, packet.slot()));
            OritechAddonsOne.LOGGER.debug("[redstone] {} mode {} slot {}",
                    packet.pos(), modes[packet.mode()], packet.slot());
        }
    }
}
