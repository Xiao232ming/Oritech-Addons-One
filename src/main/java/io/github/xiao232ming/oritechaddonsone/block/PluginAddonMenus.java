package io.github.xiao232ming.oritechaddonsone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Opening the screen of a block that is shown by {@code ExtensionAddonMenu} - the plugin grid, the wireless
 * page and the two face pages of this mod.
 * <p>
 * It exists as one shared place because more than one block renders that menu: the wired
 * {@link ExtensionAddonBlock} and the wireless dock open it for themselves, and a transfer addon placed on
 * an Oritech machine extender opens its own block entity with the very same menu (see
 * {@link ExtensionTransferAddonBlock}). Copying the buffer layout into every opener would mean three places that have
 * to agree on what the client constructor reads, so the layout lives here once.
 */
public final class PluginAddonMenus {

    private PluginAddonMenus() {
    }

    /**
     * Opens the menu of the block entity at {@code pos} for this player, or reports that there is nothing to
     * open ({@link InteractionResult#PASS}) - a block that is shown by this menu but has no block entity, or
     * a client side call, which is not the side that opens menus.
     * <p>
     * Nothing is opened on the client: the server is the side that decides, and vanilla tells the client
     * about the menu it opened.
     * <p>
     * No machine is sent along; a caller that has one writes it through
     * {@link #openItemMenu(Level, BlockPos, Player, BlockPos)}.
     */
    public static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        return openItemMenu(level, pos, player).result();
    }

    /**
     * The same as {@link #openPluginMenu} in the form the {@code useItemOn} hook of a block has to answer
     * with: a click that opened the screen is a success, and a click that has nothing to open is passed on to
     * the default block interaction (Oritech's own addon behaviour for a plugin).
     * <p>
     * It exists because 1.21.1 splits the two hooks: {@code useWithoutItem} answers with an
     * {@link InteractionResult} and {@code useItemOn} with an {@link ItemInteractionResult}, and both open
     * the same menu. Having the two hooks ask this one method keeps them from drifting apart.
     * <p>
     * <b>The client answers for the transfer preview plugin itself.</b> Only the server opens menus, but the client is
     * the side that decides whether a click was consumed at all, and 1.21.1 lets the click fall through to the item in
     * the hand when it was not: for 传输插件 - whose item is its own block item - that fall-through means "try to place
     * a block", i.e. nothing happens at all for an empty hand and for an item in the hand alike. The client therefore
     * consumes the click here, exactly like the wired addon's own hook does
     * ({@link ExtensionAddonBlock#useWithoutItem} answers with {@link InteractionResult#SUCCESS} on both sides). The
     * question it cannot answer - whether the plugin really serves a machine - is not asked here: the block's hooks
     * gate on the synced placement (see {@link TransferAddonBlock#canOpenScreen}), and the
     * server stays the side that opens the menu and that refuses everything for a plugin which serves nothing.
     * <p>
     * No machine is passed explicitly; this overload resolves it from the block entity itself, so a caller cannot
     * forget it (see {@link #resolveServedMachine}).
     */
    public static ItemInteractionResult openItemMenu(Level level, BlockPos pos, Player player) {
        return openItemMenu(level, pos, player, null);
    }

    /**
     * The same as {@link #openItemMenu(Level, BlockPos, Player)}, and additionally tells the client which machine
     * that block serves.
     * <p>
     * The machine is the one the caller resolved <b>on the server</b>, and it travels with the menu for the same
     * reason the wireless dock's link does (see {@link #writeMenuData}): 传输插件's page draws a 3D model of that
     * machine, and neither the plugin's own block entity nor the extender behind it can answer the question on a
     * client, because the answer is built from controller offsets that are plain save data and never leave the
     * server. {@code null} is a real answer here - it means "this block serves no machine", which the page shows as
     * its "no machine" state.
     * <p>
     * <b>A {@code null} from the caller is not taken as "no machine" without asking the block.</b> The caller's value
     * wins when it has one, and otherwise {@link #resolveServedMachine} asks the block entity - which is what makes
     * every opener of this menu agree. That matters because there is more than one: 传输插件's own block reaches this
     * method through two hooks, and the second of them used to pass nothing; the page then showed "no machine" for a
     * plugin whose machine was loaded all along.
     */
    public static ItemInteractionResult openItemMenu(Level level, BlockPos pos, Player player,
            @Nullable BlockPos servedMachine) {
        if (level.isClientSide()) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TransferAddonBlockEntity) return ItemInteractionResult.SUCCESS;

            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity blockEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        serverPlayer.openMenu(blockEntity,
                buffer -> writeMenuData(buffer, blockEntity, pos,
                        servedMachine != null ? servedMachine : resolveServedMachine(blockEntity, pos)));
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * The machine a block serves, as the block entity itself reports it, or {@code null} while it serves none.
     * <p>
     * Only 传输插件 can answer this and only on the server: it is built from the controller offset Oritech writes
     * into the block it claimed, which is plain save data that never leaves the server (see
     * {@code TransferAddonBlockEntity#servedMachinePos()}). Every other addon and the dock serve no machine of
     * their own through this menu, so for them the answer is {@code null} - which the page reads as its "no machine"
     * state, exactly as it should.
     * <p>
     * It exists so that a caller which has no machine to pass still sends the right one, instead of sending
     * {@code null} and making the client draw an empty page for a block that does serve something.
     */
    @Nullable
    private static BlockPos resolveServedMachine(ExtensionAddonBlockEntity blockEntity, BlockPos pos) {
        return blockEntity instanceof TransferAddonBlockEntity plugin ? plugin.servedMachinePos() : null;
    }

    /**
     * Writes the layout every opener of this menu has to send, in the one place the client constructor reads it
     * (see {@code ExtensionAddonMenu(int, Inventory, RegistryFriendlyByteBuf)}).
     * <p>
     * It is a method rather than duplicated calls so a block that opens this menu for itself cannot drift from the
     * client's expectation. What is sent is only what the client cannot look up in its own level:
     * <ul>
     *     <li>the position and the slot count of the block entity,</li>
     *     <li>the machine a wireless dock is linked to, or nothing (the wired addons and the plugins are never
     *     linked),</li>
     *     <li>the machine the block serves - 传输插件's page draws it - which the caller resolved on the server,</li>
     *     <li>the name of the connected machine, resolved on the server as well: a client whose copy of that chunk
     *     is unloaded cannot look the block up itself.</li>
     * </ul>
     */
    public static void writeMenuData(RegistryFriendlyByteBuf buffer, ExtensionAddonBlockEntity blockEntity,
            BlockPos pos, @Nullable BlockPos servedMachine) {
        // The slot count is configurable, so the client needs it to build the matching layout.
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(blockEntity.getContainerSize());
        // wired addons are never linked, but the menu reads this field for both variants
        writeOptionalPos(buffer, null);
        // the machine 传输插件's page renders, or nothing while this block serves none
        writeOptionalPos(buffer, servedMachine);
        // name of the machine this addon is attached to, resolved here on the server (see
        // ExtensionAddonBlockEntity#connectedMachineNameKey)
        var nameKey = blockEntity.connectedMachineNameKey();
        buffer.writeUtf(nameKey == null ? "" : nameKey);
    }

    /**
     * Opens the menu of a wireless extension dock: it writes the same layout as
     * {@link #writeMenuData(RegistryFriendlyByteBuf, ExtensionAddonBlockEntity, BlockPos, BlockPos)} and the one
     * field only a dock has in front of it - the machine it is linked to.
     * <p>
     * The dock is the reason the layout lives here: it opened its menu with its own copy of the buffer before, so
     * every field added for a page had to be added twice. Both openers now write the shared part with the same
     * code, and only the link stays the dock's own.
     * <p>
     * It answers with an {@link InteractionResult} and not with an {@link ItemInteractionResult} because the dock's
     * two hooks already convert: {@code useItemOn} asks this method whether it opened anything and answers
     * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} when it did not (see {@code WirelessExtensionAddonBlock}).
     */
    public static InteractionResult openDockMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof WirelessExtensionAddonBlockEntity dock)) return InteractionResult.PASS;

        serverPlayer.openMenu(dock, buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(dock.getContainerSize());
            // the GUI shows which machine this dock is linked to, so the link goes along with the menu
            writeOptionalPos(buffer, dock.linkedMachine());
            // and the machine this dock serves, which 传输插件's page draws while one of those is stored
            writeOptionalPos(buffer, dock.servedMachinePos());
            // then the name of the linked machine, resolved here on the server: a client that has its chunk
            // unloaded cannot look the block up itself (see connectedMachineNameKey)
            var nameKey = dock.connectedMachineNameKey();
            buffer.writeUtf(nameKey == null ? "" : nameKey);
        });
        return InteractionResult.SUCCESS;
    }

    /** Writes an optional block position as a presence flag plus the position, as the menu expects it. */
    private static void writeOptionalPos(RegistryFriendlyByteBuf buffer, @Nullable BlockPos pos) {
        buffer.writeBoolean(pos != null);
        if (pos != null) {
            buffer.writeBlockPos(pos);
        }
    }
}
