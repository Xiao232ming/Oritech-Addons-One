package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MultiblockMachineController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;

/**
 * Block entity of 传输插件 (the transfer preview plugin), the second transfer plugin of this mod.
 * <p>
 * Its <b>function</b> is the transfer side of {@link ExtensionTransferAddonBlockEntity}: one {@link TransferMode} plus the
 * automation flag per face (see {@link TransferFaceModes}), applied on the server. What differs is what a face
 * means and what its page shows:
 * <ul>
 *     <li>a configured face means a face of the <b>machine</b> the plugin serves in both placements, and it drives
 *     this plugin's own <b>automation</b> only - with the container outside that face of the machine (see
 *     {@link #serverTickTransfer()}),</li>
 *     <li><b>pipes, hoppers and other mods never connect here.</b> A machine that wants them already offers its own
 *     faces to them, directly from Oritech, so this plugin deliberately registers no item capability at all (see
 *     {@code OritechAddonsOne#registerCapabilities}): a capability on the plugin would answer for the
 *     <em>plugin's</em> own block, i.e. a second meaning for the same six directions, and none is needed.</li>
 * </ul>
 * Where it may be placed:
 * <ul>
 *     <li>hung on Oritech's <b>machine extender</b> it acts on the machine that extender was claimed by,</li>
 *     <li>hung <b>directly on an Oritech machine</b> it acts on that machine.</li>
 * </ul>
 * The page of this plugin draws neither of those as a cube net: it renders the machine it serves as a rotatable
 * 3D model and lets the player pick the faces on that model (see
 * {@code io.github.xiao232ming.oritechaddonsone.client.page.TransferAddonPage}).
 * <p>
 * The plugin is therefore an {@link ExtensionAddonBlockEntity} with three differences:
 * <ul>
 *     <li>the machine it works on is {@link #servedMachinePos()}: the machine behind its host extender, or the
 *     machine it is attached to itself. Every inherited user of {@code connectedMachinePos()} - the machine name in
 *     the menu, the force load badge and the energy guard of the storage bonuses - therefore follows the host,</li>
 *     <li>its own faces answer with an <b>empty</b> inventory ({@link #getInventoryStorage(Direction)}), so no pipe,
 *     hopper or other mod can reach the machine through the plugin,</li>
 *     <li>the page it shows is its own ({@code ExtensionAddonMenu#previewOnly()}), and that page draws the
 *     machine it serves and treats <b>every</b> cell-face of it alike - including the one the plugin block itself
 *     stands in while it hangs directly on that machine. No face is refused, marked or hidden there; the only thing
 *     that differs about the host's own cell-face is that the automation finds no container in that direction and
 *     therefore trades nothing through it (see {@link #serverTickTransfer()}).</li>
 * </ul>
 * Placed on a wall - i.e. on anything that is neither an Oritech machine nor an extender - it keeps Oritech's
 * ordinary addon behaviour: no GUI, no movement, an empty answer on every face.
 */
public class TransferAddonBlockEntity extends ExtensionAddonBlockEntity {

    /**
     * The empty inventory every face of this plugin answers with. One instance for all six faces, because
     * {@code ItemApi.BlockProvider} is part of what an Extension Addon is and the answer never changes: this plugin
     * never offers the machine's items through its own block (see {@link #getInventoryStorage(Direction)}), so a pipe
     * that looks at it sees "nothing here" rather than an error.
     */
    private final ItemApi.InventoryStorage emptyStorage =
            new DelegatingInventoryStorage(() -> null, () -> false);

    public TransferAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.TRANSFER_ADDON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ which machine this plugin serves

    /** Position of the block this plugin hangs on, i.e. of the machine or of the extender. */
    private BlockPos attachedHostPos() {
        return worldPosition.relative(TransferAddonBlock.attachedTowards(getBlockState()));
    }

    /**
     * The face of the machine this plugin's host block stands in, or {@code null} while that block is not next
     * to the machine's controller cell.
     * <p>
     * Only this plugin can answer it, which is why it overrides the base class's {@code null}: the host
     * direction comes from the plugin block's own placement rules
     * ({@link TransferAddonBlock#attachedTowards(BlockState)}), and the cell-face the host occupies is the one
     * the automation skips - a block of this mod stands there instead of a possible container.
     */
    @Override
    @Nullable
    protected Direction hostFaceOfMachine() {
        var machinePos = servedMachinePos();
        if (machinePos == null || level == null) return null;

        var hostPos = attachedHostPos();
        for (var face : Direction.values()) {
            if (machinePos.relative(face).equals(hostPos)) return face;
        }
        return null;
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
     * draws through the menu instead (see {@code ExtensionAddonMenu#transferMachinePos()}).
     */
    @Nullable
    private BlockPos hostMachinePos() {
        if (level == null || level.isClientSide()) return null;

        var hostPos = attachedHostPos();
        if (!level.isLoaded(hostPos)) return null;

        if (level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER)) {
            if (!(level.getBlockEntity(hostPos) instanceof AddonBlockEntity extender)) return null;

            var machinePos = extender.getControllerPos();
            return machinePos == null || machinePos.equals(extender.getBlockPos()) ? null : machinePos;
        }

        var controller = getControllerPos();
        // Not claimed by any machine (controller position is the plugin's own position).
        if (controller == null || controller.equals(worldPosition)) return null;

        // Placed directly on the controller block.
        if (controller.equals(hostPos)) {
            return level.getBlockEntity(hostPos) instanceof MachineAddonController ? hostPos : null;
        }

        // Placed on a core block of a multiblock machine: the plugin's controller position
        // is the machine's controller, but the block it stands on is one of the core blocks.
        // Resolve the controller entity and check if hostPos is one of its rotated core positions.
        var controllerEntity = level.isLoaded(controller) ? level.getBlockEntity(controller) : null;
        if (controllerEntity instanceof MultiblockMachineController multiblock) {
            var facing = multiblock.getFacingForMultiblock();
            for (var relative : multiblock.getCorePositions()) {
                var corePos = controller.offset(Geometry.rotatePosition(relative, facing));
                if (corePos.equals(hostPos)) {
                    return controller;
                }
            }
        }

        return null;
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
        if (!level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER)) return false;

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
        if (level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER)) return false;

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
     * ({@code ExtensionAddonMenu#transferMachinePos()}).
     */
    @Override
    @Nullable
    public BlockPos servedMachinePos() {
        return hostMachinePos();
    }

    /**
     * The machine this plugin works on. The base class answers "the machine that claimed me", which is right for a
     * plugin in an addon slot and right for this plugin as long as it hangs on a machine directly; while it hangs on
     * an <b>extender</b> it is the machine behind that extender, because that is the machine whose items the
     * extender's faces move.
     */
    @Override
    @Nullable
    public BlockPos connectedMachinePos() {
        return servedMachinePos();
    }

    // ------------------------------------------------------------------ the page of the plugin

    /**
     * The block whose faces the page configures: the plugin itself. Both placements configure the plugin's own faces
     * - the extender placement deliberately keeps {@link ExtenderFaceStorage}'s answer separate from the preview
     * plugin, so an extender's faces stay {@link ExtensionTransferAddonBlockEntity}'s - and the plugin is also the block whose
     * block state decides whether it is really connected to a machine (the {@code addon_used} flag, which the
     * blockstate swaps the model on).
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

    // ------------------------------------------------------------------ moving the machine's items

    /**
     * True while this plugin may move the served machine's items at all, which is exactly while it serves one.
     * <p>
     * <b>The inherited answer does not fit a placed plugin.</b> The base class asks whether the block holds or hosts a
     * transfer plugin of either kind ({@link ExtensionAddonBlockEntity#canTransferItems()}:
     * {@code hasExtensionTransferAddon() || hasTransferAddon()}), and both halves are about plugins that are stored
     * <em>in</em> the block or hang <em>on</em> it: a placed preview plugin stores neither kind, and it hosts none
     * either - it is a leaf, so nothing hangs on it and it never publishes a face of its own as taken. What really
     * decides whether this plugin can transfer is therefore the one thing its page configures and its automation
     * moves: the machine it serves ({@link #servedMachinePos()}), which is not {@code null} for any placement that
     * has a machine behind it.
     * <p>
     * This also repairs the <b>extender</b> placement, where the inherited answer is wrong for a placed plugin: the
     * plugin hangs on an extender rather than on the machine, so the mask of faces a plugin occupies is empty and
     * {@code hasExtensionTransferAddon()} answers {@code false} - the base class would refuse every real mode and the
     * page would not work at all, although the plugin serves a machine perfectly well.
     */
    @Override
    public boolean canTransferItems() {
        return servedMachinePos() != null;
    }

    @Override
    public void serverTickTransfer() {
        // The cell-face loop of the block that holds or hosts this plugin is the base class's: it reads the
        // same settings this plugin's packet writes (see ExtensionAddonBlockEntity#cellFaces). What must not
        // run here is the base class's own movement - that one trades with the containers around the addon,
        // around the extender, which is not what the page configures or shows - so the base tick is called
        // while this plugin's own six directions stay unconfigured
        // (see ExtensionAddonBlockEntity#canTransferItems()).
        super.serverTickTransfer();
    }

    // ------------------------------------------------------------------ GUI and ticking

    /** Name of the plugin's screen; the plugin has no addon type of its own to name it. */
    @Override
    protected String displayNameKey() {
        return "container.oritechaddonsone." + OritechAddonsOne.TRANSFER_ADDON.getId().getPath();
    }
}
