package io.github.xiao232ming.oritechaddonsone.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Jade tooltip of the wireless extension dock: the machine the dock is linked to, its coordinates and
 * the force load state, i.e. exactly the three lines the wireless page of the dock's own GUI shows.
 * <p>
 * The text is built from the very translation keys of that page, so both views can never drift apart:
 * <ul>
 *     <li>{@code gui.oritechaddonsone.wireless.machine_name} - machine name, {@code ...unknown} while it
 *     cannot be resolved (unloaded chunk, machine replaced by air),</li>
 *     <li>{@code gui.oritechaddonsone.wireless.coords} - the linked coordinates,</li>
 *     <li>{@code gui.oritechaddonsone.wireless.chunk_force_loaded.yes / .no} - the force load state.</li>
 * </ul>
 * An unlinked dock shows {@code gui.oritechaddonsone.wireless.unlinked} plus the force load line, just
 * like the page does.
 * <p>
 * The values come from {@link WirelessDockDataProvider}, which reads them on the server; this half only
 * formats them.
 * <p>
 * The force load state answers for the dock's own chunk, because that is the chunk the anchor plugin in
 * the dock's reserved slot keeps loaded and the value the page's line shows.
 */
public enum WirelessDockTooltipProvider implements IBlockComponentProvider {

    INSTANCE;

    /** Unique id of this provider (also its config key in Jade's plugin list). */
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, "wireless_dock");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(WirelessDockDataProvider.TAG_LINKED)) return;

        if (data.getBooleanOr(WirelessDockDataProvider.TAG_LINKED, false)) {
            var nameKey = data.getString(WirelessDockDataProvider.TAG_MACHINE_NAME).orElse(null);
            var name = nameKey != null
                    ? Component.translatable(nameKey)
                    : Component.translatable(WirelessDockDataProvider.UNKNOWN_KEY).withStyle(ChatFormatting.DARK_GRAY);
            tooltip.add(Component.translatable(WirelessDockDataProvider.MACHINE_KEY, name));

            var machine = data.getIntArray(WirelessDockDataProvider.TAG_MACHINE).orElse(null);
            if (machine != null && machine.length == 3) {
                tooltip.add(Component.translatable(WirelessDockDataProvider.COORDS_KEY, machine[0], machine[1], machine[2]));
            }
        } else {
            tooltip.add(Component.translatable(WirelessDockDataProvider.UNLINKED_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }

        var forceLoaded = data.getBooleanOr(WirelessDockDataProvider.TAG_FORCE_LOADED, false);
        tooltip.add(Component.translatable(forceLoaded
                        ? WirelessDockDataProvider.CHUNK_FORCE_LOADED_YES_KEY
                        : WirelessDockDataProvider.CHUNK_FORCE_LOADED_NO_KEY)
                .withStyle(forceLoaded ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
