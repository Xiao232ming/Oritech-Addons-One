package io.github.xiao232ming.oritechaddonsone.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.CellFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ItemFilterData;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.menu.FaceFilterMenu;

/**
 * The packets of the 过滤 page.
 * <p>
 * <b>Three payloads, and the split is the whole design.</b> {@link OpenFilter} asks the server for one face's two
 * filters and the server answers by opening {@link FaceFilterMenu} with what it really holds - so the page can
 * never start from something the server does not have. {@link SetFilter} carries a <b>whole</b> filter back for
 * <b>one direction of the movement</b>, because a filter is thirteen values that are always edited together and
 * there is no useful half of one; the client is the only editor, so one packet in that direction is enough.
 * {@link FilterState} goes the other way and carries the authoritative answer, which is what makes a
 * <b>refused</b> edit visible: the page snaps back to what the server still holds instead of showing a filter
 * that was never stored.
 * <p>
 * <b>A face is named, not a filter.</b> Every payload carries the position, the model, the face and - for the
 * cell-face model - the cell, and only {@link SetFilter} names a direction on top of that. That is what lets one
 * 过滤 button on the face panel open a page that reaches both filters of that face: the page knows the face, and
 * the direction it edits is part of what it sends rather than of what it addresses.
 * <p>
 * <b>Nothing here is trusted.</b> Every value a client sends is checked on the server before anything is read or
 * written: the position has to hold a block of this mod, the direction has to be one of the two a filter exists
 * for, and a cell has to be in range <b>and</b> part of the machine's own structure - the same three checks the
 * mode packets make, for the same reason.
 */
public final class FilterNetworking {

    private FilterNetworking() {
    }

    /** Registers the packets with NeoForge's payload registrar, on the same version string as the transfer pages. */
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(OpenFilter.TYPE, OpenFilter.CODEC, OpenFilter::handle);
        registrar.playToServer(SetFilter.TYPE, SetFilter.CODEC, SetFilter::handle);
        registrar.playToClient(FilterState.TYPE, FilterState.CODEC, FilterState::handle);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
    }

    /**
     * The face a payload names, as the four values every payload carries the same way: the block that holds the
     * filters, which of the two models it belongs to, which of its faces, and - for the cell-face model - the
     * cell of the machine's structure that face belongs to.
     * <p>
     * It is public because the pages and the screen build it themselves: the 过滤 button of a face panel and
     * every edit the filter page makes have to name the same face the server validates, and a client class that
     * cannot construct the address cannot use the packets at all.
     */
    public record Target(BlockPos pos, int model, Direction face, Vec3i cell) {
    }

    /** The three address parts as the wire carries them: a position, a model, a face and a cell. */
    private static final StreamCodec<RegistryFriendlyByteBuf, Target> TARGET_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Target::pos,
            ByteBufCodecs.VAR_INT, Target::model,
            ByteBufCodecs.VAR_INT, t -> t.face().ordinal(),
            ByteBufCodecs.VAR_INT, t -> t.cell().getX(),
            ByteBufCodecs.VAR_INT, t -> t.cell().getY(),
            ByteBufCodecs.VAR_INT, t -> t.cell().getZ(),
            (pos, model, face, x, y, z) -> new Target(pos, model, face(face), new Vec3i(x, y, z)));

    /**
     * Asks the server to open the 过滤 page of one face. The page that owns the button calls this and nothing
     * else - it names the face and the model, and the server answers with the two filters it really holds.
     */
    public static void sendOpenFilter(BlockPos pos, int model, Direction face, Vec3i cell) {
        PacketDistributor.sendToServer(new OpenFilter(new Target(pos, model, face, cell)));
    }

    /**
     * Stores one whole filter for one direction of the movement of one face.
     * <p>
     * Every edit of the page goes through here, including the ones the menu makes on its own - a shift-click on
     * the player's inventory registers an item in the filter without the screen touching it - which is why the
     * screen sends on a change it can observe rather than on a click it handled itself.
     */
    public static void sendSetFilter(BlockPos pos, int model, Direction face, Vec3i cell, TransferMode flow,
            ItemFilterData data) {
        PacketDistributor.sendToServer(
                new SetFilter(new Target(pos, model, face, cell), flow.ordinal(), data));
    }

    private static Direction face(int ordinal) {
        var directions = Direction.values();
        return ordinal >= 0 && ordinal < directions.length ? directions[ordinal] : Direction.NORTH;
    }

    /**
     * The filter one address names, out of the map that actually holds it.
     * <p>
     * <b>The model decides which map, and it has to be asked.</b> A filter of a block's six faces lives in
     * {@code MachineFaceConfigs.faceFilters} and is found by its face alone, while a filter of one face of one
     * cell of the structure lives in {@code cellFilters} and is found by that cell too. Reading a cell-face
     * filter as if it were a block-face one answers {@code null} every time - the cell address names a face of
     * the controller block that nobody filtered - so the page opened showing an empty filter, and the answer sent
     * back after each edit was silently dropped, while the filter itself worked: it was stored under the cell it
     * belongs to, as {@code SetFilter} correctly did. Every read of a filter by address goes through here so the
     * two cannot drift apart again.
     *
     * @return the filter, or {@code null} while this face of this model was never filtered
     */
    @Nullable
    private static ItemFilterData filterOf(ExtensionAddonBlockEntity owner, Target target, TransferMode flow) {
        return target.model() == FaceFilterMenu.MODEL_CELL
                ? owner.cellFilter(target.cell(), target.face(), flow)
                : owner.faceFilter(target.face(), flow);
    }

    // ------------------------------------------------------------------ client -> server

    /**
     * Asks the server to open the 过滤 page of one face. The client names the face and nothing else - both
     * filters are the server's to hand over, and the direction the page starts on is decided from the face's own
     * mode so the player lands on the one they most likely came to change.
     */
    public record OpenFilter(Target target) implements CustomPacketPayload {

        public static final Type<OpenFilter> TYPE = new Type<>(id("transfer_open_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenFilter> CODEC = StreamCodec.composite(
                TARGET_CODEC, OpenFilter::target,
                OpenFilter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(OpenFilter packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var target = packet.target();
            var owner = owner(player.level(), target);
            if (owner == null) return;

            var input = filterOf(owner, target, TransferMode.INPUT);
            var output = filterOf(owner, target, TransferMode.OUTPUT);

            // a face nobody filtered opens with the default one, which is a whitelist listing nothing - the page
            // shows that, and nothing is stored until the player really edits something
            var inputData = input == null ? ItemFilterData.DEFAULT : input;
            var outputData = output == null ? ItemFilterData.DEFAULT : output;

            // the direction the page starts on: the one the face actually uses. A face set to 输出 is emptied
            // rather than filled, so that is the filter worth opening first
            var mode = owner.transferModes().modeOf(target.face());
            if (mode == TransferMode.NONE) mode = owner.cellFaceModes().modeOf(target.cell(), target.face());
            var flow = mode == TransferMode.OUTPUT ? TransferMode.OUTPUT : TransferMode.INPUT;

            var pos = target.pos();
            var model = target.model();
            var face = target.face();
            var cell = target.cell();
            player.openMenu(FaceFilterMenu.provider(pos, model, face, cell, inputData, outputData, flow), buffer ->
                    FaceFilterMenu.write(buffer, pos, model, face, cell, inputData, outputData, flow));
        }
    }

    /**
     * Stores one whole filter for one direction of the movement of one face, the way the page sent it.
     * <p>
     * The write is refused exactly like the page's own edit would have been ({@code ExtensionAddonBlockEntity#
     * setFaceFilter} and {@code #setCellFilter} do the checking), and whatever the server really holds afterwards
     * is sent back - so a refused edit is visible on the page instead of silently doing nothing.
     */
    public record SetFilter(Target target, int flow, ItemFilterData data) implements CustomPacketPayload {

        public static final Type<SetFilter> TYPE = new Type<>(id("transfer_set_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetFilter> CODEC = StreamCodec.composite(
                TARGET_CODEC, SetFilter::target,
                ByteBufCodecs.VAR_INT, SetFilter::flow,
                ItemFilterData.CODEC, SetFilter::data,
                SetFilter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SetFilter packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var target = packet.target();
            var owner = owner(player.level(), target);
            if (owner == null) return;

            // never trust the client: the direction has to be one a filter exists for, which is what the page
            // itself may only ever show two of
            var direction = TransferMode.byOrdinal(packet.flow());
            if (direction != TransferMode.INPUT && direction != TransferMode.OUTPUT) return;

            var accepted = target.model() == FaceFilterMenu.MODEL_CELL
                    ? owner.setCellFilter(target.cell(), target.face(), direction, packet.data())
                    : owner.setFaceFilter(target.face(), direction, packet.data());

            if (!accepted) {
                OritechAddonsOne.LOGGER.debug("[transfer] refused a filter for {} model {} face {} ({})",
                        target.pos(), target.model(), target.face(), direction);
            }

            // whatever the server holds now is what the page has to show, whether the write went through or not
            var authoritative = filterOf(owner, target, direction);
            if (authoritative != null) {
                player.connection.send(new FilterState(target, direction.ordinal(), authoritative));
            }
        }
    }

    // ------------------------------------------------------------------ server -> client

    /**
     * The filter the server really holds for one face and one direction, sent back after every edit - the page's
     * own copy had better match it, and when it does not it is this one that is right.
     */
    public record FilterState(Target target, int flow, ItemFilterData data) implements CustomPacketPayload {

        public static final Type<FilterState> TYPE = new Type<>(id("transfer_filter_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FilterState> CODEC = StreamCodec.composite(
                TARGET_CODEC, FilterState::target,
                ByteBufCodecs.VAR_INT, FilterState::flow,
                ItemFilterData.CODEC, FilterState::data,
                FilterState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(FilterState packet, IPayloadContext context) {
            // Client side hook, behind a no-op holder so a dedicated server never touches a client class
            ClientHandler.deliver(packet);
        }
    }

    // ------------------------------------------------------------------ shared checks

    /**
     * The block entity a filter packet may address, or {@code null} while the position holds no block of this mod
     * or the face the packet names is a cell of a structure this block does not serve.
     */
    @Nullable
    private static ExtensionAddonBlockEntity owner(Level level, Target target) {
        if (!(level.getBlockEntity(target.pos()) instanceof ExtensionAddonBlockEntity owner)) return null;
        if (target.model() != FaceFilterMenu.MODEL_CELL) return owner;

        // the same check the page's own edit makes: a cell that is in range but not part of the structure is
        // refused, so a filter cannot be written against - or read from - a part that is not there
        if (owner.servedMachinePos() == null) return null;
        if (!CellFaceModes.isCellOffsetInRange(target.cell())) return null;
        if (!owner.machineCellOffsets().contains(target.cell())) return null;

        return owner;
    }

    /**
     * Client side hook, implemented in the client only package. It is kept behind a mutable holder that defaults
     * to a no-op, so a dedicated server can load this class without ever touching a client class.
     */
    public interface ClientHandler {
        void filterState(FilterState packet);

        /** Hands the authoritative filter to the installed handler (no-op on a dedicated server). */
        static void deliver(FilterState packet) {
            clientHandler.filterState(packet);
        }
    }

    /** Replaced by the client setup; stays a no-op on a dedicated server. */
    private static ClientHandler clientHandler = packet -> {
    };

    public static void setClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }
}