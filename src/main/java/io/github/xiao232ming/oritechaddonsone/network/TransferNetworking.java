package io.github.xiao232ming.oritechaddonsone.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.CellFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
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
    public record SetCellFaceMode(BlockPos pos, int entry) implements CustomPacketPayload {

        public static final Type<SetCellFaceMode> TYPE = new Type<>(id("transfer_cell_face_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCellFaceMode> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetCellFaceMode::pos,
                ByteBufCodecs.VAR_INT, SetCellFaceMode::entry,
                SetCellFaceMode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SetCellFaceMode packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (!(player.level().getBlockEntity(packet.pos())
                    instanceof TransferAddonBlockEntity plugin)) {
                // INFO on purpose: this is the one branch that makes a configuration look like it was never
                // stored, and it is invisible in the default log otherwise - the position the client addressed
                // did not hold a transfer plugin (gone, other block, or the wrong position altogether)
                OritechAddonsOne.LOGGER.info("[transfer] dropped a cell-face setting for {}: no transfer plugin there",
                        packet.pos());
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

            if (!plugin.setCellFaceConfig(entry.cell(), entry.face(), entry.mode(), entry.automation())) {
                OritechAddonsOne.LOGGER.info(
                        "[transfer] refused {} for {} cell {} face {} (machine {} - cell in range {} / part of it {})",
                        entry.mode(), packet.pos(), entry.cell(), entry.face(), plugin.servedMachinePos(),
                        CellFaceModes.isCellOffsetInRange(entry.cell()),
                        plugin.machineCellOffsets().contains(entry.cell()));
                return;
            }

            // the page draws the whole map, so every player whose page is open on this plugin gets the authoritative
            // copy back - including the entry a clear just removed
            sendFaceModes(player.level(), packet.pos());

            OritechAddonsOne.LOGGER.info("[transfer] stored {} on {} cell {} face {} (automation {}, {} face(s) now)",
                    entry.mode(), packet.pos(), entry.cell(), entry.face(), entry.automation(),
                    plugin.cellFaceModes().configuredFaces());
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

    /** Sends the whole cell-face map of {@code plugin} to one player. */
    public static void sendFaceModes(ServerPlayer player, BlockPos pos, TransferAddonBlockEntity plugin) {
        var packed = new ArrayList<Integer>();
        for (var entry : plugin.cellFaceModes().packedEntries()) {
            packed.add(CellFaceModes.pack(entry));
        }
        player.connection.send(new FaceModes(pos, packed));
    }

    /**
     * Sends the whole cell-face map of the block entity at {@code pos} to <b>every player whose page is open on it</b>.
     * <p>
     * Not just the player whose change it was: the map is one setting per cell-face of one machine, so two players
     * looking at the same plugin have to see the same map - otherwise the second one's counter is wrong and their next
     * click would overwrite a setting they never saw. "Their page is open on it" is asked through the menu they have
     * open: only this mod's own addon menu ({@code ExtensionAddonMenu}) can be showing this page, and that menu knows
     * the block it belongs to, so a player who is merely standing nearby is not sent anything.
     * <p>
     * <b>The menu can belong to the plugin itself or to the block that holds it</b>, and both have to be recognised:
     * a plugin that hangs on an extender is the block whose screen shows the page, so its own menu is opened on
     * {@code pos}, while a plugin stored in an Extension Addon's slots is drawn by that addon's menu, which is opened
     * on the addon's position instead. Asking only for the first kind silently skipped the second - which is the very
     * case of a stored plugin, and the reason its page drew an empty map.
     * <p>
     * Does nothing on the client and nothing while the block entity is not a transfer plugin.
     */
    public static void sendFaceModes(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof TransferAddonBlockEntity plugin)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (var player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof ExtensionAddonMenu menu)) continue;
            if (!menu.position().equals(pos) && !menu.holdsTransferPlugin(pos)) continue;

            sendFaceModes(player, pos, plugin);
            // INFO so that "the page stayed empty" can be told apart from "the map never went out": an empty
            // page after this line means the client had the data and did not draw it
            OritechAddonsOne.LOGGER.info("[transfer] sent {} configured face(s) of {} to {}",
                    plugin.cellFaceModes().configuredFaces(), pos, player.getName().getString());
        }
    }

    /** Sends the whole cell-face map of the block entity at {@code pos} to one player. */
    public static void sendFaceModes(ServerPlayer player, BlockPos pos) {
        if (!(player.level().getBlockEntity(pos) instanceof TransferAddonBlockEntity plugin)) return;
        sendFaceModes(player, pos, plugin);
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
