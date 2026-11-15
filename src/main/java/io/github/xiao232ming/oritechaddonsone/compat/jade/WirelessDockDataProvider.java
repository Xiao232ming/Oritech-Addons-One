package io.github.xiao232ming.oritechaddonsone.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;

/**
 * Reads the binding of a wireless extension dock on the server and hands it to Jade.
 * <p>
 * The values are save data of a plain (non networked) block entity, which never reaches a client on its
 * own, so they have to travel as Jade server data. Reading them here - where the linked machine's chunk
 * is loaded whenever the machine exists - is what makes the tooltip correct on dedicated servers and
 * while the client has the machine's chunk unloaded.
 * <p>
 * Since Minecraft 1.21.6 Jade refuses a provider that is both a data provider and a component provider
 * ({@code WailaCommonRegistration#checkDataProvider}), so this half is separate from
 * {@link WirelessDockTooltipProvider} - which is also why both live in their own classes instead of one
 * enum implementing both interfaces (that is still fine on 1.21.1's Jade).
 */
public enum WirelessDockDataProvider implements IServerDataProvider<BlockAccessor> {

    INSTANCE;

    /** Unique id of this provider (also its config key in Jade's plugin list). */
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, "wireless_dock_data");

    // language keys of the wireless page, reused by the tooltip so both views always show the same text
    static final String MACHINE_KEY = "gui.oritechaddonsone.wireless.machine_name";
    static final String COORDS_KEY = "gui.oritechaddonsone.wireless.coords";
    static final String CHUNK_FORCE_LOADED_YES_KEY = "gui.oritechaddonsone.wireless.chunk_force_loaded.yes";
    static final String CHUNK_FORCE_LOADED_NO_KEY = "gui.oritechaddonsone.wireless.chunk_force_loaded.no";
    static final String UNLINKED_KEY = "gui.oritechaddonsone.wireless.unlinked";
    static final String UNKNOWN_KEY = "gui.oritechaddonsone.wireless.unknown";

    // server data tags
    static final String TAG_LINKED = "linked";
    static final String TAG_MACHINE = "machine";
    static final String TAG_MACHINE_NAME = "machine_name";
    static final String TAG_FORCE_LOADED = "force_loaded";

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
    public Identifier getUid() {
        return UID;
    }
}
