package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
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
import io.github.xiao232ming.oritechaddonsone.block.ExtensionTransferAddonBlock;

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
 *     <li>the gold border marks the face of the extender this plugin hangs on, that face is not
 *     configurable and the movement skips it, so the plugin never tries to trade with itself: the plugin
 *     block stands in that face, so no pipe or hopper can ever be there and a mode on it could not describe
 *     a connection (see {@link #setTransferConfig(Direction, TransferMode, boolean)}),</li>
 *     <li>the machine is the one the <b>extender</b> is attached to (see {@link #attachedMachinePos()}), and
 *     the container of a face is the one next to the extender, not next to the plugin.</li>
 * </ul>
 * The plugin's own faces offer nothing: they are not a way into the machine, the extender's faces are (see
 * {@link #getItemLookup(Direction)}).
 * <p>
 * Placed on anything else - a machine, a wall, another block - none of this applies: the plugin keeps
 * Oritech's ordinary addon behaviour, has no GUI and moves nothing.
 */
public class ExtensionTransferAddonBlockEntity extends ExtensionAddonBlockEntity {

    /**
     * Faces of the extender this plugin hangs on, as a bitmask over {@link Direction#values()}. The base
     * class recomputes it from {@link #scanAttachedTransferFaces()} on the server and publishes it through
     * the menu's container data; on the client this field is the synced copy the menu writes, because the
     * page has to draw the gold border there as well.
     */
    private int attachedHostFaces;

    /**
     * Faces of the extender this plugin last told NeoForge's capability caches about, as a bitmask over
     * {@link Direction#values()}, or {@code -1} while that has not happened yet.
     * <p>
     * It exists so the invalidation can be moved out of the two lifecycle hooks that are not allowed to
     * touch the level (see {@link #invalidateHostCapabilities()}): the mask is compared on every server
     * tick, and the extender's position is invalidated exactly when it really changed. The bits stand for
     * "the extender answers with an inventory on this face": the face the plugin itself occupies never
     * counts, because the plugin block is there and no pipe can stand in it.
     */
    private int publishedHostMask = -1;

    /**
     * Number of times this plugin told NeoForge's capability caches that the extender may answer
     * differently. Only a diagnostic counter, so that the log can say whether an invalidation happened at
     * all without printing a line for every one of them.
     */
    private int hostInvalidations;

    /**
     * The empty inventory every face of a placed plugin answers with. One instance for all six faces,
     * because NeoForge caches what a face answers with and the answer never changes: the plugin owns no
     * items of its own (see {@link #getItemLookup(Direction)}).
     */
    private final ResourceHandler<ItemResource> emptyStorage = new DelegatingInventoryStorage(() -> null, () -> false);

    public ExtensionTransferAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.EXTENSION_TRANSFER_ADDON_ENTITY.get(), pos, state);
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
        return ExtensionTransferAddonBlock.attachedTowards(getBlockState());
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

        return hangsOnExtender() ? 1 << ExtensionTransferAddonBlock.attachedFace(getBlockState()).ordinal() : 0;
    }

    // ------------------------------------------------------------------ moving the extender's items

    /**
     * Moves items through the extender for every face whose automation is switched on - the placed form of
     * {@link ExtensionAddonBlockEntity#serverTickTransfer()}.
     * <p>
     * The two sides are the machine the <b>extender</b> is attached to and the container on the extender's
     * face, so the trade happens exactly where the page's net says it does. INPUT fills the machine,
     * OUTPUT empties it and BOTH does both, with up to {@link MachineFaceStorage#itemsPerTick()} items per
     * face and tick. The face the plugin itself hangs on is skipped (there is no container there, only the
     * plugin), as is the face the machine itself sits on - trading "machine to machine" there would only
     * shuffle the machine's own items through its own inventory.
     * <p>
     * Cheap while nothing is configured, and does nothing at all while the plugin does not hang on an
     * extender, so a plugin that stands on a machine keeps Oritech's plain addon behaviour.
     * <p>
     * This deliberately does <b>not</b> call the base class's own movement: that one trades with the
     * containers around the <em>plugin</em>, while the faces this plugin configures are the extender's (see
     * {@link #invalidateHostCapabilities()} for the one thing the extender needs to be told on top).
     */
    @Override
    public void serverTickTransfer() {
        // keeps the gold-border face and the very existence of the page in step with the world; the base
        // class's own movement must not run here - the faces and the machine are the extender's
        refreshAttachedTransferFaces();

        if (level == null || level.isClientSide()) return;

        // The one moment the extender may be told that it answers differently: a server tick of this very
        // block entity, which runs after the world is ticking again and never inside the removal, unload or
        // save path the block entity also goes through (see invalidateHostCapabilities).
        publishHostCapabilityMask();

        if (!hangsOnExtender()) return;

        var machine = MachineFaceStorage.machineStorageAt(level, attachedMachinePos());
        if (machine == null) return;

        var hostPos = hostPos();
        var skipped = ExtensionTransferAddonBlock.attachedFace(getBlockState());
        var machinePos = attachedMachinePos();

        // The machine's own block entity, known here because the machine sits behind the extender and not
        // behind the plugin: the movement is told about it so that the machine's slot roles can be respected
        // - an INPUT face fills the input slots only and an OUTPUT face empties the output slots only, never
        // the other way round (see MachineSlotRoles). A handler alone does not carry that knowledge.
        var machineEntity = level.isLoaded(machinePos) ? level.getBlockEntity(machinePos) : null;

        for (var face : Direction.values()) {
            if (face == skipped) continue;

            var mode = transferModes().modeOf(face);
            if (mode == TransferMode.NONE || !transferModes().automationOf(face)) continue;

            // the face the machine sits on has no container of its own to trade with
            if (hostPos.relative(face).equals(machinePos)) continue;

            var neighbour = MachineFaceStorage.storageAt(level, hostPos, face);
            if (neighbour == null) continue;

            if (mode.allowsExtract()) MachineFaceStorage.move(machine, machineEntity, neighbour, null);
            if (mode.allowsInsert()) MachineFaceStorage.move(neighbour, null, machine, machineEntity);
        }
    }

    /**
     * Sets what one face of the extender does and tells the capability caches that the answer for that face
     * may have changed: a face that was configured and now is not (or the other way round) turns the
     * extender from "no inventory here" into an item connection, or back.
     * <p>
     * The face the plugin itself hangs on is refused: the plugin block occupies it, so no pipe, hopper or
     * other mod can ever be there and a mode on it could not describe a connection. The transfer page marks
     * that face in gold and does not offer it either (see {@code ExtensionTransferAddonPage#occupiedFace}); refusing
     * it here as well is what keeps a mode written by an older version, by a modified client or by a
     * half-rolled-back page from leaving a face configured that nothing can ever use.
     * <p>
     * Called from the transfer page's packet, on the server, on this very block entity - which is what makes
     * the plugin the right place to do it: it knows the extender it hangs on.
     */
    @Override
    public boolean setTransferConfig(Direction face, TransferMode mode, boolean automation) {
        // The occupied face only exists while the plugin really hangs on an extender: placed on anything
        // else the plugin keeps Oritech's own addon behaviour, where the face it is attached to says
        // nothing about an extender and every face has to stay configurable.
        if (mode != TransferMode.NONE && hangsOnExtender()
                && face == ExtensionTransferAddonBlock.attachedFace(getBlockState())) {
            OritechAddonsOne.LOGGER.debug(
                    "[transfer] refused {} on {} face {}: the plugin itself stands on that face of the extender",
                    mode, worldPosition, face);
            return false;
        }

        if (!super.setTransferConfig(face, mode, automation)) return false;

        invalidateHostCapabilities();
        return true;
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

    // ------------------------------------------------------------------ the extender's capability cache

    /**
     * Tells NeoForge's capability caches that the extender this plugin hangs on may answer differently from
     * now on, and is public so that the block can call it on the way out (see
     * {@link ExtensionTransferAddonBlock#playerWillDestroy}).
     * <p>
     * The handler a configured face answers with stays the same object and reads the plugin, the mode and
     * the machine on every call, so it needs no invalidation of its own; what does need one is the jump
     * between "this face offers nothing" and "this face offers the machine's inventory". That jump is the
     * only thing a pipe cannot notice by itself - the {@code BlockCapabilityCache} of Oritech's own item
     * pipes, for one, keeps a {@code null} answer until the level invalidates the position - so the plugin
     * invalidates the extender's position whenever it appears, disappears or is configured.
     * <p>
     * The invalidation is per <b>position</b>, and deliberately so: {@code Level#invalidateCapabilities} has
     * no notion of a face, and the extender's answer changes for all of them at once - the plugin holds one
     * mode per face of the extender, and whether any plugin hangs there at all is what can turn any of those
     * faces into a connection.
     * <p>
     * <b>It must never run while the block entity is being removed or unloaded.</b> NeoForge moves the
     * invalidation of a position into {@code BlockEntity#setRemoved} / {@code clearRemoved}, which both run
     * from {@code LevelChunk#removeBlockEntity} and {@code LevelChunk#setBlockEntity} - i.e. exactly while a
     * chunk is unloaded or saved. Reading a neighbouring block there ({@code level.getBlockState(hostPos)})
     * looks a second chunk up in the middle of that, and invalidating a capability re-enters the capability
     * caches of every pipe and hopper that listens on the position. Both are hazard enough on their own; on
     * a world save the chunk map keeps saving while the chunk is still marked unsaved, so anything that
     * dirties it again from here turns "Saving world" into a loop. This plugin therefore never overrides
     * those two hooks and reaches this method from {@link #publishHostCapabilityMask()} (a server tick) or
     * from {@link #setTransferConfig(Direction, TransferMode, boolean)} (the page's packet) only.
     * <p>
     * Cheap and harmless while the plugin hangs on something else: the host is checked by block identity
     * first - without reading a block state, so that no chunk is touched - and the extender's own position
     * is a position NeoForge's capability system knows.
     */
    public void invalidateHostCapabilities() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!hangsOnExtender()) return;

        hostInvalidations++;
        serverLevel.invalidateCapabilities(hostPos());
    }

    /**
     * The faces of the extender that currently answer with an inventory, as a bitmask over
     * {@link Direction#values()}: every face that no plugin hangs on and whose transfer page mode transfers
     * something. {@code 0} while the plugin does not hang on an extender, which is also what the extender
     * answers with then.
     * <p>
     * The plugin's own face is dropped even for a mode an older version stored on it, because that is what
     * {@code ExtenderFaceStorage} answers: the plugin block stands in that face and the handler refuses it.
     * Both sides read the same rule, so the mask can never announce a connection the handler would not give.
     */
    private int hostAnswerMask() {
        if (!hangsOnExtender()) return 0;

        var mine = ExtensionTransferAddonBlock.attachedFace(getBlockState());
        var mask = 0;
        for (var face : Direction.values()) {
            if (face == mine) continue;
            // the mode is the cheap test and the common answer is "not configured", so the world is only
            // asked about a face that really carries a mode
            if (transferModes().modeOf(face) == TransferMode.NONE) continue;
            if (occupiedByPlugin(face)) continue;

            mask |= 1 << face.ordinal();
        }
        return mask;
    }

    /**
     * True while a transfer plugin hangs on the given face of the extender, i.e. while some plugin block
     * stands in the cell outside that face. Nothing but a plugin can be there any more, but a face that is
     * still configured in the saved data must not be announced as a connection while it is occupied.
     */
    private boolean occupiedByPlugin(Direction face) {
        var pluginPos = hostPos().relative(face);
        if (level == null || !level.isLoaded(pluginPos)) return false;

        return level.getBlockEntity(pluginPos) instanceof ExtensionTransferAddonBlockEntity other
                && ExtensionTransferAddonBlock.attachedFace(other.getBlockState()) == face;
    }

    /**
     * Compares the faces the extender answers on with the ones its capability caches were last told about
     * and invalidates the extender's position when they differ. Called once per server tick.
     * <p>
     * This is the safe replacement for the invalidation the removal hooks used to do: a plugin that is
     * placed, broken, unloaded or reconfigured changes the mask, and the difference is published on the next
     * tick of the loaded plugin instead of inside the chunk bookkeeping. The first tick after the block
     * entity is loaded publishes it as well, because the mask starts at {@code -1} - a world that was saved
     * with a plugin already hanging on an extender has to be able to answer from the very first tick.
     * <p>
     * Cheap in the normal case: no plugin state changes, no mask changes, no level call at all beyond the
     * block state the mask is built from - and no log line.
     */
    private void publishHostCapabilityMask() {
        if (!(level instanceof ServerLevel)) return;

        var mask = hostAnswerMask();
        if (mask == publishedHostMask) return;

        publishedHostMask = mask;
        OritechAddonsOne.LOGGER.debug(
                "[transfer] extender capability mask of {} is now {} ({} invalidation(s), {})",
                worldPosition, Integer.toBinaryString(mask), hostInvalidations + 1,
                mask == 0 ? "no face offers an inventory" : "a pipe asking may now connect");

        invalidateHostCapabilities();
    }

    /*
     * Nothing is overridden for setRemoved() / clearRemoved() on purpose: those two hooks run while the
     * chunk is being unloaded or saved, and the invalidation that tells the pipes about this plugin is done
     * from the server tick and from ExtensionTransferAddonBlock#playerWillDestroy() instead (see
     * invalidateHostCapabilities()). BlockEntity#clearRemoved already invalidates this block entity's own
     * position, which is all NeoForge asks a block entity to do there; a chunk that unloads invalidates its
     * whole position range through NeoForge's own ChunkEvent.Unload hook.
     */

    // ------------------------------------------------------------------ GUI and ticking

    /** Name of the placed plugin's screen; the plugin has no addon type of its own to name it. */
    @Override
    protected String displayNameKey() {
        return "container.oritechaddonsone." + OritechAddonsOne.TRANSFER_ADDON.getId().getPath();
    }
}
