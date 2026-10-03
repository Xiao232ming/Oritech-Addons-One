package io.github.xiao232ming.oritechaddonsone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/**
 * Opening the screen of a block that is shown by {@code ExtensionAddonMenu} - the plugin grid, the wireless
 * page and the two face pages of this mod.
 * <p>
 * It exists as one shared place because more than one block renders that menu: the wired
 * {@link ExtensionAddonBlock} and the wireless dock open it for themselves, and a transfer addon placed on
 * an Oritech machine extender opens its own block entity with the very same menu (see
 * {@link TransferAddonBlock}). Copying the buffer layout into every opener would mean three places that have
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
     */
    public static InteractionResult openPluginMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity blockEntity)) return InteractionResult.PASS;

        // The slot count is configurable, so the client needs it to build the matching layout.
        var slots = blockEntity.getContainerSize();
        serverPlayer.openMenu(blockEntity, buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(slots);
            // wired addons are never linked, but the menu reads this field for both variants
            buffer.writeBoolean(false);
            // name of the machine this addon is attached to, resolved here on the server (see
            // ExtensionAddonBlockEntity#connectedMachineNameKey)
            var nameKey = blockEntity.connectedMachineNameKey();
            buffer.writeUtf(nameKey == null ? "" : nameKey);
        });
        return InteractionResult.SUCCESS;
    }
}
