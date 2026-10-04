package io.github.xiao232ming.oritechaddonsone.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.CellFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.MachineFaceConfigs;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The packets of the two transfer pages.
 * <p>
 * <b>Two models, two payloads, one type.</b> The Extension Transfer page (the cube net) keys its settings by
 * {@link net.minecraft.core.Direction} and still writes them through {@link SetTransferMode}, which carries the packed
 * value {@link TransferFaceModes} uses. 传输插件 keys its settings by <b>cell and direction</b> and uses
 * {@link SetCellFaceMode}, which carries the cell offset as well - and because the client page has to <em>draw</em> a
 * map the server owns, it also needs the map itself, which no fixed set of container data slots can carry for a
 * structure of unknown size: that is {@link FaceModes}, a server to client packet with one int per configured
 * cell-face.
 * <ul>
 *     <li>{@link SetCellFaceMode} - the player configured one face of one cell of the machine's structure; the server
 *     validates the cell against its own part list and writes the setting,</li>
 *     <li>{@link FaceModes} - the whole map, sent to the player whose page it is: when the menu opens and after every
 *     accepted change.</li>
 * </ul>
 */
public final class TransferNetworking {

    private TransferNetworking() {
    }

    /** Registers the packets with NeoForge's payload registrar. */
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(SetTransferMode.TYPE, SetTransferMode.CODEC, SetTransferMode::handle);
        registrar.playToServer(SetCellFaceMode.TYPE, SetCellFaceMode.CODEC, SetCellFaceMode::handle);
        registrar.playToClient(FaceModes.TYPE, FaceModes.CODEC, FaceModes::handle);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
    }

    // ------------------------------------------------------------------ client -> server

    /**
     * Sets what one face of the addon at {@code pos} does: the mode and its automation flag (packed). This is the
     * <b>cube net</b> page's packet, whose model is one block's six directions.
     */
    public record SetTransferMode(BlockPos pos, int face, int value) implements CustomPacketPayload {

        public static final Type<SetTransferMode> TYPE = new Type<>(id("transfer_set_mode"));
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

    /**
     * Sets what one <b>face of one cell</b> of the machine structure at {@code pos} does: the cell offset, the face
     * and the packed mode and automation flag, all three in one int (see {@link CellFaceModes#pack}).
     * <p>
     * The cell is sent exactly as the page measured it - relative to the machine's controller block - and the server
     * re-checks it against its own part list, because a client could name any cell at all: the whole point of the
     * check is that a modified client cannot make the plugin trade with a container that is not next to a face of the
     * structure it serves.
     */
    public record SetCellFaceMode(BlockPos pos, BlockPos machine, int entry) implements CustomPacketPayload {

        public static final Type<SetCellFaceMode> TYPE = new Type<>(id("transfer_cell_face_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCellFaceMode> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetCellFaceMode::pos,
                BlockPos.STREAM_CODEC, SetCellFaceMode::machine,
                ByteBufCodecs.VAR_INT, SetCellFaceMode::entry,
                SetCellFaceMode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SetCellFaceMode packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // The block this setting is written to is the one the page was opened on: the transfer plugin
            // itself while it hangs on an extender, or - the common case - the Extension Addon that holds it in
            // a plugin slot. A stored plugin is an <b>item</b>, so its own block entity does not exist in the
            // world and the addon is the block that has to carry the setting.
            if (!(player.level().getBlockEntity(packet.pos())
                    instanceof ExtensionAddonBlockEntity owner)) {
                // INFO on purpose: this is the one branch that makes a configuration look like it was never
                // stored, and it is invisible in the default log otherwise - the position the client addressed
                // did not hold a block of this mod at all
                var found = player.level().getBlockEntity(packet.pos());
                OritechAddonsOne.LOGGER.info(
                        "[transfer] dropped a cell-face setting for {}: no transfer-capable block there (block {}, entity {})",
                        packet.pos(), player.level().getBlockState(packet.pos()).getBlock(),
                        found == null ? "none" : found.getClass().getSimpleName());
                return;
            }

            // never trust the client: the entry names the cell, the face and the mode, and every one of the three is
            // re-checked here - the ranges by unpack, the cell against this machine's own cells by the block entity
            var entry = CellFaceModes.unpack(packet.entry());
            if (entry == null) {
                OritechAddonsOne.LOGGER.info("[transfer] dropped a cell-face setting for {}: unreadable entry {}",
                        packet.pos(), packet.entry());
                return;
            }

            if (!owner.setCellFaceConfig(entry.cell(), entry.face(), entry.mode(), entry.automation())) {
                OritechAddonsOne.LOGGER.info(
                        "[transfer] refused {} for {} cell {} face {} (machine {} - cell in range {} / part of it {})",
                        entry.mode(), packet.pos(), entry.cell(), entry.face(), owner.servedMachinePos(),
                        CellFaceModes.isCellOffsetInRange(entry.cell()),
                        owner.machineCellOffsets().contains(entry.cell()));
                return;
            }

            // the page draws the whole map of the machine, so every player whose page shows a plugin of that
            // machine gets the authoritative copy back - including the entry a clear just removed
            sendFaceModes(player.level(), packet.machine());

            OritechAddonsOne.LOGGER.info(
                    "[transfer] stored {} on {} cell {} face {} (machine {}, automation {}, {} face(s) now)",
                    entry.mode(), packet.pos(), entry.cell(), entry.face(), packet.machine(), entry.automation(),
                    owner.cellFaceModes().configuredFaces());
        }
    }

    // ------------------------------------------------------------------ server -> client

    /**
     * The whole cell-face map of one plugin, one int per configured cell-face
     * ({@link CellFaceModes#pack(CellFaceModes.Entry)}).
     * <p>
     * <b>Why a packet and not menu data.</b> The menu's container data is a fixed set of slots, which fits a setting
     * per {@link net.minecraft.core.Direction} and cannot fit a setting per face of every cell of a structure whose
     * size is only known at runtime - a 3x3x3 machine has a hundred and sixty-two cell-faces and Oritech's part lists
     * are not bounded by six. The client page has to draw all of them, so it is told all of them, and the packet is
     * the one mechanism this mod already uses for exactly this kind of variable data (see
     * {@link ProxyNetworking.PickerSlots}).
     * <p>
     * The list is <b>complete</b>, never a delta: a change replaces what the client had, which is what makes a clear
     * (an entry that is no longer there) expressible without a second packet type.
     */
    public record FaceModes(BlockPos pos, List<Integer> entries) implements CustomPacketPayload {

        public static final Type<FaceModes> TYPE = new Type<>(id("transfer_cell_face_modes"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FaceModes> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, FaceModes::pos,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), FaceModes::entries,
                FaceModes::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(FaceModes packet, IPayloadContext context) {
            var entries = new ArrayList<CellFaceModes.Entry>(packet.entries().size());
            for (var packed : packet.entries()) {
                var entry = CellFaceModes.unpack(packed);
                if (entry != null) entries.add(entry);
            }

            // Client side hook, behind a no-op holder so a dedicated server never touches a client class
            ClientHandler.deliver(packet.pos(), entries);
        }
    }

    /**
     * Sends the whole cell-face map of one machine to one player, keyed by that machine.
     * <p>
     * The key is the <b>machine</b> and not the block that sent it: the settings describe the machine and are
     * shared by every transfer plugin serving it ({@code MachineFaceConfigs}), so all of their pages draw the
     * same map - which is the point of keying it by the machine in the first place.
     */
    public static void sendFaceModes(ServerPlayer player, BlockPos machine, CellFaceModes settings) {
        var packed = new ArrayList<Integer>(settings.configuredFaces());
        for (var entry : settings.packedEntries()) {
            packed.add(CellFaceModes.pack(entry));
        }
        player.connection.send(new FaceModes(machine, packed));
    }

    /**
     * Sends a machine's cell-face map to <b>every player whose page shows one of its plugins</b>.
     * <p>
     * Not just the player whose change it was: the map is one setting per cell-face of one machine, so two
     * players looking at it have to see the same map - otherwise the second one's counter is wrong and their
     * next click would overwrite a setting they never saw. "Their page shows one of its plugins" is asked
     * through the menu they have open: only this mod's own addon menu ({@code ExtensionAddonMenu}) can be
     * showing this page, and it reports the machine its block serves, so a player who is merely standing nearby
     * is not sent anything.
     * <p>
     * The map itself is taken from the machine's shared settings, not from the block that triggered the send, so
     * a plugin that only knows half of the configuration still sends the whole of it.
     * <p>
     * Does nothing on the client.
     */
    public static void sendFaceModes(Level level, BlockPos machine) {
        if (level == null || level.isClientSide() || machine == null) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        var settings = MachineFaceConfigs.cellFaces(level, machine);
        for (var player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof ExtensionAddonMenu menu)) continue;
            if (!machine.equals(menu.transferMachinePos())) continue;

            sendFaceModes(player, machine, settings);
            // INFO so that "the page stayed empty" can be told apart from "the map never went out": an empty
            // page after this line means the client had the data and did not draw it
            OritechAddonsOne.LOGGER.info("[transfer] sent {} configured face(s) of machine {} to {}",
                    settings.configuredFaces(), machine, player.getName().getString());
        }
    }

    /** Sends a machine's cell-face map to one player, if there is a block of this mod serving it at {@code pos}. */
    public static void sendFaceModes(ServerPlayer player, BlockPos machine) {
        if (!(player.level().getBlockEntity(machine) instanceof ExtensionAddonBlockEntity owner)) return;
        sendFaceModes(player, machine, owner.cellFaceModes());
    }

    /**
     * Client side hook, implemented in the client only package. It is kept behind a mutable holder that defaults to a
     * no-op, so a dedicated server can load this class without ever touching a client class.
     */
    public interface ClientHandler {
        void faceModes(BlockPos pos, List<CellFaceModes.Entry> entries);

        /** Hands the map to the installed handler (no-op on a dedicated server). */
        static void deliver(BlockPos pos, List<CellFaceModes.Entry> entries) {
            clientHandler.faceModes(pos, entries);
        }
    }

    /** Replaced by the client setup; stays a no-op on a dedicated server. */
    private static ClientHandler clientHandler = (pos, entries) -> {
    };

    public static void setClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }
}
