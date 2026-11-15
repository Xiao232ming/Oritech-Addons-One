package io.github.xiao232ming.oritechaddonsone.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

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
 * Everything the tooltip needs is save data of a plain (non networked) block entity, which never reaches
 * a client on its own, so the values travel as Jade server data: {@link #appendServerData} reads them on
 * the server - where the linked machine's chunk is loaded whenever the machine exists - and the client
 * only formats them. That is what makes the tooltip correct on dedicated servers and while the client has
 * the machine's chunk unloaded.
 * <p>
 * The force load state answers for the dock's own chunk, because that is the chunk the anchor plugin in
 * the dock's reserved slot keeps loaded and the value the page's line shows (see
 * {@link WirelessExtensionAddonBlockEntity#isOwnChunkForceLoaded()}).
 */
public enum WirelessDockJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    /** Unique id of this plugin (also its config key in Jade's plugin list). */
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, "wireless_dock");

    // language keys of the wireless page, reused so both views always show the same text
    private static final String MACHINE_KEY = "gui.oritechaddonsone.wireless.machine_name";
    private static final String COORDS_KEY = "gui.oritechaddonsone.wireless.coords";
    private static final String CHUNK_FORCE_LOADED_YES_KEY = "gui.oritechaddonsone.wireless.chunk_force_loaded.yes";
    private static final String CHUNK_FORCE_LOADED_NO_KEY = "gui.oritechaddonsone.wireless.chunk_force_loaded.no";
    private static final String UNLINKED_KEY = "gui.oritechaddonsone.wireless.unlinked";
    private static final String UNKNOWN_KEY = "gui.oritechaddonsone.wireless.unknown";

    // server data tags
    private static final String TAG_LINKED = "linked";
    private static final String TAG_MACHINE = "machine";
    private static final String TAG_MACHINE_NAME = "machine_name";
    private static final String TAG_FORCE_LOADED = "force_loaded";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof WirelessExtensionAddonBlockEntity dock)) return;

        data.putBoolean(TAG_LINKED, dock.isLinked());
        data.putBoolean(TAG_FORCE_LOADED, dock.isOwnChunkForceLoaded());

        var machine = dock.linkedMachine();
        if (machine == null) return;

        data.putIntArray(TAG_MACHINE, new int[] { machine.getX(), machine.getY(), machine.getZ() });

        // resolved on the server: a client whose machine chunk is unloaded cannot look the block up
        var nameKey = dock.connectedMachineNameKey();
        if (nameKey != null) data.putString(TAG_MACHINE_NAME, nameKey);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(TAG_LINKED)) return;

        if (data.getBooleanOr(TAG_LINKED, false)) {
            var nameKey = data.getString(TAG_MACHINE_NAME).orElse(null);
            var name = nameKey != null
                    ? Component.translatable(nameKey)
                    : Component.translatable(UNKNOWN_KEY).withStyle(ChatFormatting.DARK_GRAY);
            tooltip.add(Component.translatable(MACHINE_KEY, name));

            var machine = data.getIntArray(TAG_MACHINE).orElse(null);
            if (machine != null && machine.length == 3) {
                tooltip.add(Component.translatable(COORDS_KEY, machine[0], machine[1], machine[2]));
            }
        } else {
            tooltip.add(Component.translatable(UNLINKED_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }

        var forceLoaded = data.getBooleanOr(TAG_FORCE_LOADED, false);
        tooltip.add(Component.translatable(forceLoaded ? CHUNK_FORCE_LOADED_YES_KEY : CHUNK_FORCE_LOADED_NO_KEY)
                .withStyle(forceLoaded ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
