package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockContent;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;

/**
 * Block entity of the transfer addon that is <b>placed as a block</b> on an Oritech machine extender
 * ({@code oritech:machine_extender}).
 * <p>
 * The plugin is the same item as the one that is put <i>into</i> an Extension Addon, and once it stands on
 * an extender it does for that extender what the stored plugin does for its addon: it makes the six faces of
 * the host configurable on the "Extension Transfer" page and moves the items of the machine behind it. It is
 * therefore an {@link ExtensionAddonBlockEntity} - the GUI, the menu, the per-face settings, the save format
 * and the page's container data are all reused as they are - with three differences:
 * <ul>
 *     <li>the net is drawn from the <b>host</b>, not from the plugin: {@link #transferPageBlock()} and
 *     {@link #transferPageBlockState()} report the extender's state, so the page shows the extender's faces
 *     (a plugin model unfolded into six faces would say nothing about where the items go),</li>
 *     <li>the gold border marks the face of the extender this plugin hangs on and the movement skips it, so
 *     the plugin never tries to trade with itself,</li>
 *     <li>the machine is the one the <b>extender</b> is attached to (see {@link #attachedMachinePos()}), and
 *     the container of a face is the one next to the extender, not next to the plugin.</li>
 * </ul>
 * The plugin's own faces offer nothing: they are not a way into the machine, the extender's faces are (see
 * {@link #getItemLookup(Direction)}).
 * <p>
 * Placed on anything else - a machine, a wall, another block - none of this applies: the plugin keeps
 * Oritech's ordinary addon behaviour, has no GUI and moves nothing.
 */
public class TransferAddonBlockEntity extends ExtensionAddonBlockEntity {

    /**
     * Faces of the extender this plugin hangs on, as a bitmask over {@link Direction#values()}. The base
     * class recomputes it from {@link #scanAttachedTransferFaces()} on the server and publishes it through
     * the menu's container data; on the client this field is the synced copy the menu writes, because the
     * page has to draw the gold border there as well.
     */
    private int attachedHostFaces;

    /**
     * The empty inventory every face of a placed plugin answers with. One instance for all six faces,
     * because NeoForge caches what a face answers with and the answer never changes: the plugin owns no
     * items of its own (see {@link #getItemLookup(Direction)}).
     */
    private final ResourceHandler<ItemResource> emptyStorage = new DelegatingInventoryStorage(() -> null, () -> false);

    public TransferAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.TRANSFER_ADDON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ the host extender

    /**
     * The Oritech machine extender this plugin hangs on, or {@code null} while it hangs on something else (a
     * machine, a wall, ...) or that block's chunk is not loaded.
     * <p>
     * Only the server decides this: the host is found by looking at the neighbour the plugin was placed
     * against, and the client learns everything it needs about it through the menu (see
     * {@link #attachedTransferFaces()} and {@link #transferPageBlockState()}).
     */
    @Nullable
    public BlockEntity attachedExtender() {
        if (level == null || level.isClientSide()) return null;

        var hostPos = hostPos();
        if (!level.isLoaded(hostPos)) return null;

        return level.getBlockEntity(hostPos);
    }

    /**
     * True while this plugin hangs on an Oritech machine extender, i.e. while the whole placed behaviour -
     * the GUI, the page, the movement - applies. The host is checked by block identity, so an addon or a
     * machine that merely happens to be an Oritech addon block does not count.
     */
    public boolean hangsOnExtender() {
        if (level == null || level.isClientSide()) return false;

        var hostPos = hostPos();
        return level.isLoaded(hostPos) && level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER.get());
    }

    /** Position of the block this plugin hangs on, i.e. of the extender while there is one. */
    private BlockPos hostPos() {
        return worldPosition.relative(attachedTowards());
    }

    /** Direction from this plugin towards the block it hangs on, i.e. the extender when there is one. */
    private Direction attachedTowards() {
        return TransferAddonBlock.attachedTowards(getBlockState());
    }

    /**
     * Position of the machine the <b>extender</b> is attached to, or {@code null} while the plugin does not
     * hang on an extender, that extender was not claimed by a machine, or the machine's chunk is not loaded.
     * <p>
     * The machine is resolved exactly like the machine of an addon: Oritech's addon scan writes the position
     * of the machine that claimed the extender into the extender's own block entity, and its controller
     * position is that machine - as long as it is not its own position, which is the "claimed by nobody"
     * value.
     */
    @Nullable
    public BlockPos attachedMachinePos() {
        if (!(attachedExtender() instanceof AddonBlockEntity extender)) return null;

        var machinePos = extender.getControllerPos();
        return machinePos == null || machinePos.equals(extender.getBlockPos()) ? null : machinePos;
    }

    /**
     * The machine this plugin works on. The base class answers "the machine that claimed me", which is
     * Oritech's answer for the plugin's <b>own</b> addon slot (and nothing while it stands on an extender is
     * scanned); what the plugin really works on is the machine behind its host extender. Every inherited user
     * of this position - the machine name in the menu and the energy guard of the storage bonuses - therefore
     * follows the extender, which is what makes them show and protect the right machine.
     */
    @Override
    @Nullable
    public BlockPos connectedMachinePos() {
        return attachedMachinePos();
    }

    // ------------------------------------------------------------------ the page of the host

    /**
     * The block whose faces the transfer page shows: the extender this plugin hangs on, or the plugin itself
     * while it does not hang on one (the page then never appears, see {@link #canTransferItems()}).
     */
    @Override
    public Block transferPageBlock() {
        if (level == null) return super.transferPageBlock();

        var hostPos = hostPos();
        if (!level.isLoaded(hostPos)) return super.transferPageBlock();

        return level.getBlockState(hostPos).getBlock();
    }

    /**
     * State of that block, or {@code null} while the plugin does not hang on an extender. The state is read
     * from the client's own level, where block states are synced, so the net is drawn from the very
     * orientation the extender has in the world.
     */
    @Override
    @Nullable
    public BlockState transferPageBlockState() {
        if (level == null) return super.transferPageBlockState();

        var hostPos = hostPos();
        if (!level.isLoaded(hostPos)) return super.transferPageBlockState();

        return level.getBlockState(hostPos);
    }

    // ------------------------------------------------------------------ the face the plugin hangs on

    /**
     * The face of the extender this plugin hangs on, as the bitmask the page draws its gold border from.
     * <p>
     * The extender is not scanned by this plugin, so there is nothing to look up here: the face follows from
     * the plugin's own placement - it is the face of the extender the plugin was placed against - and is
     * therefore only reported while the plugin really hangs on an extender. On the client the base class's
     * container data already wrote the value into {@link #attachedHostFaces}.
     */
    @Override
    protected int scanAttachedTransferFaces() {
        if (level == null) return 0;
        if (level.isClientSide()) return attachedHostFaces;

        return hangsOnExtender() ? 1 << TransferAddonBlock.attachedFace(getBlockState()).ordinal() : 0;
    }

    // ------------------------------------------------------------------ moving the extender's items

    /**
     * Moves items through the extender for every face whose automation is switched on - the placed form of
     * {@link ExtensionAddonBlockEntity#serverTickTransfer()}.
     * <p>
     * The two sides are the machine the <b>extender</b> is attached to and the container on the extender's
     * face, so the trade happens exactly where the page's net says it does. INPUT fills the machine,
     * OUTPUT empties it and BOTH does both, with up to {@link MachineFaceStorage#ITEMS_PER_TICK} items per
     * face and tick. The face the plugin itself hangs on is skipped (there is no container there, only the
     * plugin), as is the face the machine itself sits on - trading "machine to machine" there would only
     * shuffle the machine's own items through its own inventory.
     * <p>
     * Cheap while nothing is configured, and does nothing at all while the plugin does not hang on an
     * extender, so a plugin that stands on a machine keeps Oritech's plain addon behaviour.
     */
    @Override
    public void serverTickTransfer() {
        // keeps the gold-border face and the very existence of the page in step with the world; the base
        // class's own movement must not run here - the faces and the machine are the extender's
        refreshAttachedTransferFaces();

        if (level == null || level.isClientSide()) return;
        if (!hangsOnExtender()) return;

        var machine = MachineFaceStorage.machineStorageAt(level, attachedMachinePos());
        if (machine == null) return;

        var hostPos = hostPos();
        var skipped = TransferAddonBlock.attachedFace(getBlockState());
        var machinePos = attachedMachinePos();

        for (var face : Direction.values()) {
            if (face == skipped) continue;

            var mode = transferModes().modeOf(face);
            if (mode == TransferMode.NONE || !transferModes().automationOf(face)) continue;

            // the face the machine sits on has no container of its own to trade with
            if (hostPos.relative(face).equals(machinePos)) continue;

            var neighbour = MachineFaceStorage.storageAt(level, hostPos, face);
            if (neighbour == null) continue;

            if (mode.allowsExtract()) MachineFaceStorage.move(machine, neighbour);
            if (mode.allowsInsert()) MachineFaceStorage.move(neighbour, machine);
        }
    }

    // ------------------------------------------------------------------ the plugin's own faces

    /**
     * Nothing. The net, the modes and the movement of a placed plugin are about the faces of the extender it
     * hangs on, so a pipe, a hopper or another mod that looks at the plugin's own faces must see an empty
     * inventory instead of the machine's items - otherwise the same items would be reachable at two
     * different places, with the machine's inventory appearing to sit inside a plugin that only passes them
     * through.
     */
    @Override
    public ResourceHandler<ItemResource> getItemLookup(@Nullable Direction direction) {
        return emptyStorage;
    }

    // ------------------------------------------------------------------ GUI and ticking

    /** Name of the placed plugin's screen; the plugin has no addon type of its own to name it. */
    @Override
    protected String displayNameKey() {
        return "container.oritechaddonsone." + OritechAddonsOne.TRANSFER_ADDON.getId().getPath();
    }
}
