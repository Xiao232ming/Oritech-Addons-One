package io.github.xiao232ming.oritechaddonsone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
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
     * {@link #openPluginMenu(Level, BlockPos, Player, BlockPos)}.
     */
    public static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        return openPluginMenu(level, pos, player, null);
    }

    /**
     * Opens the menu of the block entity at {@code pos} and tells the client which machine that block serves, or
     * reports that there is nothing to open ({@link InteractionResult#PASS}) - see
     * {@link #openPluginMenu(Level, BlockPos, Player)}.
     * <p>
     * The machine is the one the caller resolved <b>on the server</b>, and it travels with the menu for the same
     * reason the wireless dock's link does (see {@link #writeMenuData}): 传输插件's page draws a 3D model of that
     * machine, and neither the plugin's own block entity nor the extender behind it can answer the question on a
     * client, because the answer is built from controller offsets that are plain save data and never leave the
     * server. {@code null} is a real answer here - it means "this block serves no machine", which the page shows as
     * its "no machine" state.
     */
    public static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player,
            @Nullable BlockPos servedMachine) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity blockEntity)) return InteractionResult.PASS;

        serverPlayer.openMenu(blockEntity, buffer -> writeMenuData(buffer, blockEntity, pos, servedMachine));
        return InteractionResult.SUCCESS;
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
