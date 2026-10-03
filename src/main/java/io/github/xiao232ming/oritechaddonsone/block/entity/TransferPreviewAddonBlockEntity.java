package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.EnumMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.TransferPreviewAddonBlock;

/**
 * Block entity of 传输插件 (the transfer preview plugin), the second transfer plugin of this mod.
 * <p>
 * Its <b>function</b> is exactly the one of {@link TransferAddonBlockEntity}: it makes the machine it serves
 * reachable through the faces of a host, one {@link TransferMode} plus the automation flag per face (see
 * {@link TransferFaceModes}), applied on the server and offered to pipes, hoppers and other mods. What differs is
 * where it may be placed and what its page shows:
 * <ul>
 *     <li>hung on Oritech's <b>machine extender</b> it acts on the machine that extender was claimed by, and the
 *     connections are the extender's faces - exactly like {@link TransferAddonBlockEntity},</li>
 *     <li>hung <b>directly on an Oritech machine</b> it acts on that machine and its connections are its own six
 *     faces, because the machine's own faces belong to Oritech: the plugin is the one block that can add a
 *     connection there (see {@link MachinePluginStorage}).</li>
 * </ul>
 * The page of this plugin draws neither of those as a cube net: it renders the machine it serves as a rotatable
 * 3D model and lets the player pick the faces on that model (see
 * {@code io.github.xiao232ming.oritechaddonsone.client.page.TransferPreviewAddonPage}). Everything the page
 * sends is the same packet the transfer page sends and everything it draws comes from the same container data,
 * so both plugins really behave identically - only the way a face is chosen differs.
 * <p>
 * The plugin is therefore an {@link ExtensionAddonBlockEntity} with four differences:
 * <ul>
 *     <li>the machine it works on is {@link #servedMachinePos()}: the machine behind its host extender, or the
 *     machine it is attached to itself. Every inherited user of {@code connectedMachinePos()} - the machine name
 *     in the menu, the force load badge and the energy guard of the storage bonuses - therefore follows the host,</li>
 *     <li>its own faces answer with the machine inventory ({@link #getItemLookup(Direction)}), so the pipe and
 *     hopper side of the feature exists in both placements,</li>
 *     <li>the page it shows is its own ({@code ExtensionAddonMenu#previewOnly()}), and that page draws the
 *     machine it serves,</li>
 *     <li>an <b>occupied</b> face - the face of the machine this plugin hangs on, or a face another plugin of
 *     this mod already stands on - is refused and marked, so two plugins can never configure the same face and no
 *     mode can describe a connection that is physically blocked.</li>
 * </ul>
 * Placed on a wall - i.e. on anything that is neither an Oritech machine nor an extender - it keeps Oritech's
 * ordinary addon behaviour: no GUI, no movement, an empty answer on every face.
 */
public class TransferPreviewAddonBlockEntity extends ExtensionAddonBlockEntity {

    /**
     * Faces this plugin refuses to configure, as a bitmask over {@link Direction#values()}: the face of the
     * machine it hangs on (the machine block is there) and every face another plugin of this mod stands on.
     * <p>
     * The base class recomputes it from {@link #scanAttachedTransferFaces()} on the server and publishes it
     * through the menu's container data; on the client this field is the synced copy the menu writes, because the
     * page has to mark those faces there as well.
     */
    private int occupiedFaces;

    /**
     * Faces this plugin last told NeoForge's capability caches about, as a bitmask over {@link Direction#values()},
     * or {@code -1} while that has not happened yet.
     * <p>
     * It exists so the invalidation can be moved out of the two lifecycle hooks that are not allowed to touch the
     * level (see {@link #invalidateFaceCapabilities()}): the mask is compared on every server tick, and the
     * plugin's position is invalidated exactly when it really changed. The bits stand for "this plugin answers
     * with an inventory on this face": a face whose mode transfers something and which is not occupied.
     */
    private int publishedFaceMask = -1;

    /**
     * Number of times this plugin told NeoForge's capability caches that it may answer differently. Only a
     * diagnostic counter, so that the log can say whether an invalidation happened at all without printing a line
     * for every one of them.
     */
    private int faceInvalidations;

    /**
     * The empty inventory every face answers with while this plugin does not serve a machine. One instance for all
     * six faces, because NeoForge caches what a face answers with and the answer never changes: without a machine
     * the plugin owns no items of its own (see {@link #getItemLookup(Direction)}).
     */
    private final ResourceHandler<ItemResource> emptyStorage = new DelegatingInventoryStorage(() -> null, () -> false);

    /**
     * Handler of every face of this plugin, one object per face forever - NeoForge caches what a face answers with,
     * so the instance must never be replaced (see {@link MachinePluginStorage}).
     */
    private final EnumMap<Direction, ResourceHandler<ItemResource>> faceStorages = new EnumMap<>(Direction.class);

    public TransferPreviewAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.TRANSFER_PREVIEW_ADDON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ which machine this plugin serves

    /** Position of the block this plugin hangs on, i.e. of the machine or of the extender. */
    private BlockPos attachedHostPos() {
        return worldPosition.relative(TransferPreviewAddonBlock.attachedTowards(getBlockState()));
    }

    /**
     * The machine the <b>host</b> this plugin hangs on reports, or {@code null} while that host is not an addon of
     * a machine at all: the machine behind the extender, or the machine the plugin itself was claimed by.
     * <p>
     * This is the one place the two placements are told apart, and it deliberately asks the <em>host</em> instead
     * of the plugin: Oritech's addon scan walks through an extender and claims the plugin standing on it like any
     * other addon, so it writes the position of the <b>machine behind the extender</b> into the plugin's own
     * controller position - never the extender's. A plugin that compared its own controller position with the
     * block it hangs on therefore answers "no" for the extender placement, which is what used to make right
     * clicking such a plugin do nothing at all (see {@link #hangsOnExtender()}).
     * <p>
     * Server only, like every user of it: a controller offset is plain block entity save data that never reaches
     * a client - the client's copy stays zero, so {@code getControllerPos()} answers the block's own position
     * there - and the extender's controller position is not synced either. The client learns the machine the page
     * draws through the menu instead (see {@code ExtensionAddonMenu#transferPreviewMachinePos()}).
     */
    @Nullable
    private BlockPos hostMachinePos() {
        if (level == null || level.isClientSide()) return null;

        var hostPos = attachedHostPos();
        if (!level.isLoaded(hostPos)) return null;

        if (level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER.get())) {
            if (!(level.getBlockEntity(hostPos) instanceof AddonBlockEntity extender)) return null;

            var machinePos = extender.getControllerPos();
            return machinePos == null || machinePos.equals(extender.getBlockPos()) ? null : machinePos;
        }

        var controller = getControllerPos();
        if (controller == null || !controller.equals(hostPos)) return null;

        return level.getBlockEntity(hostPos) instanceof MachineAddonController ? hostPos : null;
    }

    /**
     * True while this plugin hangs on an Oritech machine extender that a machine claimed, i.e. while the extender
     * placement really applies.
     * <p>
     * The host is checked by block identity, so an addon or a machine that merely happens to be an Oritech addon
     * block does not count, and the extender's own controller position has to point at a machine as well: an
     * extender no machine ever claimed has no machine behind it, so a plugin hanging on it serves nothing.
     */
    public boolean hangsOnExtender() {
        if (level == null || level.isClientSide()) return false;

        var hostPos = attachedHostPos();
        if (!level.isLoaded(hostPos)) return false;
        if (!level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER.get())) return false;

        return hostMachinePos() != null;
    }

    /**
     * True while this plugin hangs directly on an Oritech machine, i.e. on a block that claimed it as one of its
     * addons - the second placement this plugin supports.
     * <p>
     * The machine is the neighbour the plugin was placed against <b>and</b> the block Oritech wrote into the
     * plugin's controller position: a plugin standing on a wall next to something it was never claimed by serves
     * nothing, and a plugin whose extender was broken does not silently start serving whatever block is behind it
     * either. An extender never counts here even when it was claimed by a machine - that placement is
     * {@link #hangsOnExtender()}.
     */
    public boolean hangsOnMachine() {
        if (level == null || level.isClientSide()) return false;

        var hostPos = attachedHostPos();
        if (level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER.get())) return false;

        var controller = getControllerPos();
        if (controller == null || !controller.equals(hostPos)) return false;

        return hostMachinePos() != null;
    }

    /**
     * The machine this plugin works on, or {@code null} while it serves none: the machine the host extender was
     * claimed by, or the machine the plugin itself is attached to.
     * <p>
     * Extends the base class's answer - that one is the plugin's own controller position - by the extender case: while
     * the plugin hangs on an extender, Oritech's addon scan writes the position of the machine <b>behind</b> that
     * extender into the plugin, so the base class's answer is the machine that really claimed the plugin; the
     * extender is only asked because it is the block whose faces the plugin works through (see
     * {@link #hostMachinePos()}).
     * <p>
     * <b>This is the server's answer.</b> It is built from controller offsets, which are plain block entity save data
     * and never reach a client, so a client gets {@code null} here for a plugin that really serves a machine. The
     * capability provider and the automation read it on the server, where it is authoritative, and the machine the
     * page draws is sent to the client with the menu instead
     * ({@code ExtensionAddonMenu#transferPreviewMachinePos()}).
     */
    @Override
    @Nullable
    public BlockPos servedMachinePos() {
        return hostMachinePos();
    }

    /**
     * The machine this plugin works on. The base class answers "the machine that claimed me", which is right for a
     * plugin in an addon slot and right for this plugin as long as it hangs on a machine directly; while it hangs
     * on an <b>extender</b> it is the machine behind that extender, because that is the machine whose items the
     * extender's faces move.
     */
    @Override
    @Nullable
    public BlockPos connectedMachinePos() {
        return servedMachinePos();
    }

    // ------------------------------------------------------------------ the page of the plugin

    /**
     * The block whose faces the page configures: the plugin itself. Both placements configure the plugin's own
     * faces - the extender placement deliberately keeps {@link ExtenderFaceStorage}'s answer separate from the
     * preview plugin, so an extender's faces stay {@link TransferAddonBlockEntity}'s - and the plugin is also the
     * block whose block state decides whether it is really connected to a machine (the {@code addon_used} flag,
     * which the blockstate swaps the model on).
     */
    @Override
    public Block transferPageBlock() {
        return getBlockState().getBlock();
    }

    /** State of {@link #transferPageBlock()}, which is always this plugin's own state. */
    @Override
    public BlockState transferPageBlockState() {
        return getBlockState();
    }

    // ------------------------------------------------------------------ the faces the plugin refuses

    /**
     * The faces the page marks and refuses, as the bitmask the page draws its gold border from: the face of the
     * machine this plugin hangs on - the machine block is there - and every face another plugin of this mod stands
     * on.
     * <p>
     * Both halves are a property of a <b>placement</b>, not of this plugin alone, so nothing is reported while the
     * plugin serves no machine: a plugin standing on a wall keeps Oritech's ordinary addon behaviour, where every
     * face is free and no face describes a connection to a machine.
     * <p>
     * On the server the mask is recomputed from the world every tick; on the client it is the copy the base class's
     * container data wrote into {@link #occupiedFaces}, because the page has to draw the same borders there.
     */
    @Override
    protected int scanAttachedTransferFaces() {
        if (level == null) return 0;
        if (level.isClientSide()) return occupiedFaces;

        if (servedMachinePos() == null) return 0;

        var mask = 1 << TransferPreviewAddonBlock.attachedFace(getBlockState()).ordinal();
        for (var face : Direction.values()) {
            if (occupiedByPlugin(face)) mask |= 1 << face.ordinal();
        }
        return mask;
    }

    /**
     * True while a transfer plugin of this mod stands on the given face of this plugin, i.e. while a pipe could not
     * be there and a mode on that face could not describe a connection. Only the plugins of this mod count: an
     * Oritech addon on a neighbouring face is not something this plugin can configure anyway, and treating it as
     * occupied would only take a face away from the player.
     */
    private boolean occupiedByPlugin(Direction face) {
        if (level == null) return false;

        var neighbourPos = worldPosition.relative(face);
        if (!level.isLoaded(neighbourPos)) return false;

        return level.getBlockEntity(neighbourPos) instanceof TransferPreviewAddonBlockEntity other
                && TransferPreviewAddonBlock.attachedFace(other.getBlockState()) == face.getOpposite();
    }

    // ------------------------------------------------------------------ moving the machine's items

    /**
     * Moves items through this plugin for every face whose automation is switched on - the placed form of
     * {@link ExtensionAddonBlockEntity#serverTickTransfer()}.
     * <p>
     * The two sides are the machine this plugin serves and the container outside the plugin's face, so the trade
     * happens exactly where the rendered model says it does. INPUT fills the machine, OUTPUT empties it and BOTH
     * does both, with up to {@link MachineFaceStorage#ITEMS_PER_TICK} items per face and tick. The face the plugin
     * hangs on is skipped (the machine is there, not a container), which is also the face no mode can be set on.
     * <p>
     * Cheap while nothing is configured, and it does nothing at all while the plugin serves no machine, so a plugin
     * that stands on a wall keeps Oritech's plain addon behaviour.
     * <p>
     * This deliberately does <b>not</b> call the base class's own movement: that one trades with the containers
     * around the addon and with the machine that claimed the addon, which is the wrong pair for the extender
     * placement.
     */
    @Override
    public void serverTickTransfer() {
        // keeps the refused faces and the very existence of the page in step with the world; the base class's own
        // movement must not run here - the faces and the machine are resolved by this plugin
        refreshAttachedTransferFaces();

        if (level == null || level.isClientSide()) return;

        // The one moment this plugin may be told that it answers differently: a server tick of this very block
        // entity, which runs after the world is ticking again and never inside the removal, unload or save path
        // the block entity also goes through (see invalidateFaceCapabilities).
        publishFaceCapabilityMask();

        var machinePos = servedMachinePos();
        var machine = MachineFaceStorage.machineStorageAt(level, machinePos);
        if (machine == null) return;

        // The machine's own block entity, so that the machine's slot roles can be respected - an INPUT face fills
        // the input slots only and an OUTPUT face empties the output slots only, never the other way round (see
        // MachineSlotRoles). A handler alone does not carry that knowledge.
        var machineEntity = level.isLoaded(machinePos) ? level.getBlockEntity(machinePos) : null;
        var skipped = TransferPreviewAddonBlock.attachedFace(getBlockState());

        for (var face : Direction.values()) {
            if (face == skipped) continue;

            var mode = transferModes().modeOf(face);
            if (mode == TransferMode.NONE || !transferModes().automationOf(face)) continue;

            var neighbour = MachineFaceStorage.storageAt(level, worldPosition, face);
            if (neighbour == null) continue;

            if (mode.allowsExtract()) MachineFaceStorage.move(machine, machineEntity, neighbour, null);
            if (mode.allowsInsert()) MachineFaceStorage.move(neighbour, null, machine, machineEntity);
        }
    }

    /**
     * Sets what one face of this plugin does and tells the capability caches that the answer for that face may have
     * changed: a face that was configured and now is not (or the other way round) turns this plugin from "no
     * inventory here" into an item connection, or back.
     * <p>
     * A face that is <b>occupied</b> is refused: the machine block is in the face this plugin hangs on and another
     * transfer plugin is in a face one of them already stands on, so no pipe, hopper or other mod can ever be
     * there and a mode on it could not describe a connection. The page marks those faces and does not offer them
     * either; refusing them here as well is what keeps a mode written by an older version, by a modified client or
     * by a half-rolled-back page from leaving a face configured that nothing can ever use.
     * <p>
     * Called from the preview page's packet, on the server, on this very block entity - which is what makes the
     * plugin the right place to do it: it knows the machine it serves and the faces that are physically blocked.
     */
    @Override
    public boolean setTransferConfig(Direction face, TransferMode mode, boolean automation) {
        // The occupied faces only exist while this plugin really serves a machine: placed on a wall it keeps
        // Oritech's own addon behaviour, where the face it is attached to says nothing about a machine and every
        // face has to stay configurable.
        if (mode != TransferMode.NONE && servedMachinePos() != null && isOccupied(face)) {
            OritechAddonsOne.LOGGER.debug(
                    "[transfer] refused {} on {} face {}: the face is occupied by a machine or another plugin",
                    mode, worldPosition, face);
            return false;
        }

        if (!super.setTransferConfig(face, mode, automation)) return false;

        invalidateFaceCapabilities();
        return true;
    }

    /** True while the given face is one of the faces {@link #scanAttachedTransferFaces()} refuses. */
    private boolean isOccupied(@Nullable Direction face) {
        if (face == null) return false;

        return (scanAttachedTransferFaces() & 1 << face.ordinal()) != 0;
    }

    // ------------------------------------------------------------------ the plugin's own faces

    /**
     * The machine's inventory, offered on every face of this plugin whose mode transfers something - this is the
     * pipe and hopper side of the feature in <b>both</b> placements (see {@link MachinePluginStorage}).
     * <p>
     * Without a machine - a plugin on a wall, an extender no machine claimed, an unloaded chunk - the answer is an
     * empty inventory instead of an error, so a pipe simply sees "nothing here". One handler object per face is
     * created once and kept forever, and it reads the mode, the machine and the machine's slot roles on every
     * single call, so the GUI, the automation and this handler can never disagree.
     */
    @Override
    public ResourceHandler<ItemResource> getItemLookup(@Nullable Direction direction) {
        if (direction == null || servedMachinePos() == null) return emptyStorage;

        return faceStorages.computeIfAbsent(direction, face -> MachinePluginStorage.handlerAt(this, face));
    }

    // ------------------------------------------------------------------ this plugin's capability cache

    /**
     * Tells NeoForge's capability caches that this plugin may answer differently from now on, and is public so that
     * the block can call it on the way out (see {@link TransferPreviewAddonBlock#playerWillDestroy}).
     * <p>
     * The handler a configured face answers with stays the same object and reads the mode and the machine on every
     * call, so it needs no invalidation of its own; what does need one is the jump between "this face offers
     * nothing" and "this face offers the machine's inventory". That jump is the only thing a pipe cannot notice by
     * itself - the {@code BlockCapabilityCache} of Oritech's own item pipes, for one, keeps a {@code null} answer
     * until the level invalidates the position - so this plugin invalidates its own position whenever a face
     * appears, disappears or is configured.
     * <p>
     * <b>It must never run while the block entity is being removed or unloaded.</b> NeoForge moves the invalidation
     * of a position into {@code BlockEntity#setRemoved} / {@code clearRemoved}, which both run from
     * {@code LevelChunk#removeBlockEntity} and {@code LevelChunk#setBlockEntity} - i.e. exactly while a chunk is
     * unloaded or saved. Reading a neighbouring block there looks a second chunk up in the middle of that, and
     * invalidating a capability re-enters the capability caches of every pipe and hopper that listens on the
     * position. Both are hazard enough on their own; on a world save the chunk map keeps saving while the chunk is
     * still marked unsaved, so anything that dirties it again from here turns "Saving world" into a loop. This
     * plugin therefore never overrides those two hooks and reaches this method from
     * {@link #publishFaceCapabilityMask()} (a server tick) or from
     * {@link #setTransferConfig(Direction, TransferMode, boolean)} (the page's packet) only.
     */
    public void invalidateFaceCapabilities() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        faceInvalidations++;
        serverLevel.invalidateCapabilities(worldPosition);
    }

    /**
     * The faces of this plugin that currently answer with the machine's inventory, as a bitmask over
     * {@link Direction#values()}: every face that is not occupied and whose mode transfers something. {@code 0}
     * while the plugin serves no machine, which is also what every face answers with then.
     */
    private int capabilityMask() {
        if (servedMachinePos() == null) return 0;

        var mask = 0;
        for (var face : Direction.values()) {
            // the mode is the cheap test and the common answer is "not configured", so the world is only asked
            // about a face that really carries a mode
            if (transferModes().modeOf(face) == TransferMode.NONE) continue;
            if (isOccupied(face)) continue;

            mask |= 1 << face.ordinal();
        }
        return mask;
    }

    /**
     * Compares the faces this plugin answers on with the ones its capability caches were last told about and
     * invalidates its own position when they differ. Called once per server tick.
     * <p>
     * This is the safe replacement for the invalidation the removal hooks cannot do: a plugin that is placed,
     * broken, unloaded or reconfigured changes the mask, and the difference is published on the next tick of the
     * loaded plugin instead of inside the chunk bookkeeping. The first tick after the block entity is loaded
     * publishes it as well, because the mask starts at {@code -1} - a world that was saved with a plugin already
     * configured has to be able to answer from the very first tick.
     * <p>
     * Cheap in the normal case: no plugin state changes, no mask changes, no level call at all beyond the block
     * state the mask is built from - and no log line.
     */
    private void publishFaceCapabilityMask() {
        if (!(level instanceof ServerLevel)) return;

        var mask = capabilityMask();
        if (mask == publishedFaceMask) return;

        publishedFaceMask = mask;
        OritechAddonsOne.LOGGER.debug(
                "[transfer] preview plugin capability mask of {} is now {} ({} invalidation(s), {})",
                worldPosition, Integer.toBinaryString(mask), faceInvalidations + 1,
                mask == 0 ? "no face offers an inventory" : "a pipe asking may now connect");

        invalidateFaceCapabilities();
    }

    /*
     * Nothing is overridden for setRemoved() / clearRemoved() on purpose: those two hooks run while the chunk is
     * being unloaded or saved, and the invalidation that tells the pipes about this plugin is done from the server
     * tick and from TransferPreviewAddonBlock#playerWillDestroy() instead (see invalidateFaceCapabilities()).
     * BlockEntity#clearRemoved already invalidates this block entity's own position, which is all NeoForge asks a
     * block entity to do there; a chunk that unloads invalidates its whole position range through NeoForge's own
     * ChunkEvent.Unload hook.
     */

    // ------------------------------------------------------------------ GUI and ticking

    /** Name of the plugin's screen; the plugin has no addon type of its own to name it. */
    @Override
    protected String displayNameKey() {
        return "container.oritechaddonsone." + OritechAddonsOne.TRANSFER_PREVIEW_ADDON.getId().getPath();
    }
}
