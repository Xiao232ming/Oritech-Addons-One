package io.github.xiao232ming.oritechaddonsone.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.transfer.item.DelegatingInventoryStorage;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MultiblockMachineController;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;

/**
 * Block entity of 传输插件, the second transfer plugin of this mod.
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
 *     machine it is attached to itself. Every inherited user of {@code connectedMachinePos()} - the machine name
 *     in the menu, the force load badge and the energy guard of the storage bonuses - therefore follows the host,</li>
 *     <li>its own faces answer with an <b>empty</b> inventory ({@link #getItemLookup(Direction)}), so no pipe,
 *     hopper or other mod can reach the machine through the plugin,</li>
 *     <li>the page it shows is its own ({@code ExtensionAddonMenu#transferOnly()}), and that page draws the
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
     * What each <b>cell-face</b> of the served machine's structure does - the setting this page configures, one entry
     * per individual face of one individual cell (see {@link CellFaceModes}).
     * <p>
     * It is a map of its own and not the inherited {@link TransferFaceModes}, which stays what it is for the cube net
     * page and the Extension Addons: that one keys a setting by {@link Direction} alone and so describes the six faces
     * of <b>one block</b>, while this one names a face of a cell of a structure and has no maximum at all. The two
     * live side by side on this block entity because it inherits the whole addon - the plugin slots, the energy lookup
     * and the cube net page's own model - and only the parts this page owns are moved onto the cell-face model.
     */
    private final CellFaceModes cellFaces = new CellFaceModes();

    /**
     * The empty inventory every face of this plugin answers with. One instance for all six faces, because
     * {@code ItemProvider} is part of what an Extension Addon is and the answer never changes: this plugin never
     * offers the machine's items through its own block (see {@link #getItemLookup(Direction)}), so a pipe that looks
     * at it sees "nothing here" rather than an error.
     */
    private final ResourceHandler<ItemResource> emptyStorage = new DelegatingInventoryStorage(() -> null, () -> false);

    public TransferAddonBlockEntity(BlockPos pos, BlockState state) {
        super(OritechAddonsOne.TRANSFER_ADDON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ which machine this plugin serves

    /** Position of the block this plugin hangs on, i.e. of the machine or of the extender. */
    private BlockPos attachedHostPos() {
        return worldPosition.relative(TransferAddonBlock.attachedTowards(getBlockState()));
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

        if (level.getBlockState(hostPos).is(BlockContent.MACHINE_EXTENDER.get())) {
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
     * {@link ExtensionTransferAddonBlockEntity}, so an extender's faces stay the extension plugin's - and the plugin is also the
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

    /**
     * Moves items through the <b>individual faces of the machine's cells</b> for every cell-face whose automation is
     * switched on - the placed form of {@link ExtensionAddonBlockEntity#serverTickTransfer()}.
     * <p>
     * <b>A configured entry names a face of one cell of the structure</b> ({@link CellFaceModes}), and the container it
     * trades with is the one outside <em>that</em> face of <em>that</em> cell: with a mode on the north face of the
     * cell at offset {@code o}, INPUT pulls from the container north of {@code machinePos.offset(o)} into the machine,
     * OUTPUT pushes the machine's items into it, and BOTH does both. A face of a cell that no other cell of the
     * structure covers is a face of the machine's outer surface, which is the only kind the page can configure (the
     * server re-checks that too, see {@link #setCellFaceConfig}), so an entry always names a real outside face.
     * <p>
     * The trade happens between the machine's inventory and that container, with up to
     * {@link MachineFaceStorage#itemsPerTick()} items per <b>entry</b> and tick - and it keeps going until the
     * budget is used up or the machine has nothing left to take in or out, so an INPUT face really fills the
     * machine while its input slots have room and an OUTPUT face really empties it while its output slots hold
     * something (see {@link MachineFaceStorage#move}). The machine is the owner on its own side of both, so its
     * slot roles are respected ({@link MachineSlotRoles}) - an INPUT face fills the machine's input slots only
     * and an OUTPUT face empties its output slots only. The container outside is passed without an owner, which
     * is what a chest, a pipe or another mod's inventory is.
     * <p>
     * <b>The budget is per configured cell-face and not shared.</b> The model has no maximum, so a machine whose
     * surface is configured all over would move {@code itemsPerTick()} per entry - which is exactly what the page's
     * counter reports and what a player who configured that many faces asked for. What keeps it bounded is the
     * machine's own inventory: every entry moves items into or out of the same inventory, so an empty or full machine
     * starves the rest of the entries instead of the loop doing more work than the items allow. The loop itself is one
     * pass over the configured entries, and the common case is none.
     * <p>
     * <b>One cell-face is skipped, and that is an automation detail rather than a rule about the face.</b> While one
     * of the six faces of the machine's controller cell is the cell the plugin's own host block stands in
     * ({@link #hostFaceOfMachine()}), the neighbour outside that cell-face is a block of this mod - the plugin
     * itself, or the extender it hangs on - and a block of this mod is not a container: nothing can be traded with in
     * that direction, and a move would only shuffle the machine's items through a face it shares with a block of this
     * mod. <b>The page configures that cell-face like any other and the mode is stored normally</b> (see
     * {@link #setCellFaceConfig}); only this loop has nothing to trade with there. Nothing else distinguishes the
     * face: it is shown, configured and saved like every other cell-face of the structure, and no face is refused or
     * marked anywhere.
     * <p>
     * Cheap while nothing is configured, and it does nothing at all while the plugin serves no machine, so a plugin
     * that stands on a wall keeps Oritech's plain addon behaviour.
     * <p>
     * This deliberately does <b>not</b> call the base class's own movement: that one trades with the containers around
     * the addon - around the plugin or around the extender - which is not what the page configures or shows.
     */
    @Override
    public void serverTickTransfer() {
        // Keeps the inherited mask of attached plugin faces in step with the world. This plugin hosts no placed
        // plugin, so the mask is the base class's "nothing" and this call only ever confirms it; it is kept because it
        // is the hook the base class expects a subclass to refresh that state in, and the base class's own movement
        // must not run here - the faces and the machine are resolved by this plugin.
        refreshAttachedTransferFaces();

        if (level == null || level.isClientSide()) return;

        var machinePos = servedMachinePos();
        var machine = MachineFaceStorage.machineStorageAt(level, machinePos);
        if (machine == null) return;

        if (cellFaces.isEmpty()) return;

        // The machine's own block entity, so that the machine's slot roles can be respected - an INPUT face fills
        // the input slots only and an OUTPUT face empties the output slots only, never the other way round (see
        // MachineSlotRoles). A handler alone does not carry that knowledge.
        var machineEntity = level.isLoaded(machinePos) ? level.getBlockEntity(machinePos) : null;
        // the face of the controller's own cell the plugin's host block stands in, or null while it stands somewhere
        // else entirely (the extender placement): that is the one cell-face whose neighbour is a block of this mod
        // rather than a possible container, so it is the one the loop has nothing to trade with
        var hostFace = hostFaceOfMachine();

        for (var entry : cellFaces.packedEntries()) {
            var mode = entry.mode();
            if (mode == TransferMode.NONE || !entry.automation()) continue;

            // the plugin's own block is no container, so nothing stands outside that face of that cell to trade with;
            // the mode itself stays configured there like on any other cell-face
            if (entry.cell().equals(Vec3i.ZERO) && entry.face() == hostFace) continue;

            var neighbour = MachineFaceStorage.storageAt(level, machinePos.offset(entry.cell()), entry.face());
            if (neighbour == null) continue;

            if (mode.allowsExtract()) {
                MachineFaceStorage.move(machine, machineEntity, neighbour, null, MachineFaceStorage.itemsPerTick());
            }
            if (mode.allowsInsert()) {
                MachineFaceStorage.move(neighbour, null, machine, machineEntity, MachineFaceStorage.itemsPerTick());
            }
        }
    }

    /**
     * Sets what one <b>face of one cell</b> of the machine does, i.e. which container outside that face of that cell
     * this plugin trades with on its own (see {@link #serverTickTransfer()}).
     * <p>
     * <b>The server validates the offset, not the client.</b> The cell has to name a cell of the machine the plugin
     * really serves ({@link #machineCellOffsets()}: the controller's own cell plus the multiblock's core positions,
     * rotated by the machine's facing) and it has to be inside the range one entry can express
     * ({@link CellFaceModes#isCellOffsetInRange}), so a modified client can neither configure a cell that is not part
     * of the structure nor make the plugin trade with something arbitrarily far away. The offset arrives exactly as
     * the client measured it, i.e. relative to the machine the page draws.
     * <p>
     * <b>Every cell-face is accepted, the host's own one included.</b> The cell-face the plugin's own host block
     * stands in - while it hangs directly on the machine - is not special here: the page configures it like any other
     * and the mode is stored like any other, so a player may describe a connection on it just as on the five remaining
     * faces of the controller cell. What differs is only that the automation finds no container in that direction and
     * therefore trades nothing there (see {@link #serverTickTransfer()}), which is a fact about the neighbour and
     * nothing this method has to enforce.
     * <p>
     * Called from the transfer page's packet, on the server, on this very block entity - which is what makes the
     * plugin the right place to do it: it knows the machine it serves and the cells that machine is made of. No
     * capability cache has to be told anything: this plugin answers no item capability at all (see
     * {@link #getItemLookup(Direction)}).
     *
     * @param cell the offset of the cell from the served machine's controller block, as the page measured it
     * @return true while the setting was accepted and written
     */
    public boolean setCellFaceConfig(Vec3i cell, Direction face, TransferMode mode, boolean automation) {
        if (level == null || level.isClientSide() || cell == null || face == null || mode == null) return false;
        if (mode != TransferMode.NONE && !canTransferItems()) return false;
        if (mode != TransferMode.NONE && servedMachinePos() == null) return false;
        if (!CellFaceModes.isCellOffsetInRange(cell)) return false;
        if (mode != TransferMode.NONE && !machineCellOffsets().contains(cell)) return false;

        if (!cellFaces.set(cell, face, mode, automation)) return true;

        setChanged();
        return true;
    }

    /** The cell-face settings of this plugin; never {@code null}, empty while nothing is configured. */
    public CellFaceModes cellFaceModes() {
        return cellFaces;
    }

    /**
     * The cells of the served machine's structure, as offsets from its controller block: the controller's own cell
     * plus every cell Oritech's part list names ({@code MultiblockMachineController#getCorePositions()}), each rotated
     * by the machine's facing the way the assembled machine itself rotates them.
     * <p>
     * It is the list the server validates a client's offset against, and it is deliberately the same list the page
     * builds its model from: a one-block machine - or a machine whose block entity is not a multiblock controller at
     * all - is the one cell at the origin.
     * <p>
     * Empty while the plugin serves no machine or the machine's chunk is not loaded, which is what makes every
     * configuration request fail then.
     */
    public List<Vec3i> machineCellOffsets() {
        var machinePos = servedMachinePos();
        if (machinePos == null || level == null || !level.isLoaded(machinePos)) return List.of();

        var cells = new ArrayList<Vec3i>();
        cells.add(Vec3i.ZERO);

        var machineEntity = level.getBlockEntity(machinePos);
        if (machineEntity instanceof MultiblockMachineController multiblock) {
            var facing = multiblock.getFacingForMultiblock();
            for (var relative : multiblock.getCorePositions()) {
                var cell = Geometry.rotatePosition(relative, facing);
                if (!cells.contains(cell)) cells.add(cell);
            }
        }
        return List.copyOf(cells);
    }

    /**
     * The face of the machine the block this plugin hangs on stands in, or {@code null} while that block is not next
     * to the machine's core block at all.
     * <p>
     * It answers a question about the <b>neighbour</b> rather than about the face: hung directly on the machine this
     * is the face the plugin block is the neighbour of, and hung on an extender it is the face the extender sits in.
     * In both cases a block of this mod stands there instead of a possible container, which is why the automation has
     * nothing to trade with in that direction (see {@link #serverTickTransfer()}). The face itself is nothing special:
     * the page configures it like any other and its mode is saved like any other (see {@link #setCellFaceConfig}).
     * <p>
     * The host is resolved on the server only, like every user of {@link #attachedHostPos()}: the client has neither
     * the plugin's controller offset nor the extender's, and it runs no automation anyway.
     */
    @Nullable
    private Direction hostFaceOfMachine() {
        var machinePos = servedMachinePos();
        if (machinePos == null || level == null) return null;

        var hostPos = attachedHostPos();
        for (var face : Direction.values()) {
            if (machinePos.relative(face).equals(hostPos)) return face;
        }
        return null;
    }

    // ------------------------------------------------------------------ the plugin's own faces

    /**
     * <b>Nothing, on every face and in both placements.</b> This plugin never offers the machine's items through its
     * own block: a machine that wants pipes, hoppers or another mod already offers its own faces to them, and those
     * already reach the machine directly - a second connection through the plugin would only be a second meaning for
     * the same six directions, because the page and the automation read a face as a face of the <em>machine</em>
     * while an item capability on the plugin answers for the plugin's own block.
     * <p>
     * The override exists because {@code ItemProvider} is part of what an Extension Addon is (see
     * {@link ExtensionAddonBlockEntity#getItemLookup(Direction)}), and one handler object is kept for all six faces
     * because the answer never changes and NeoForge caches what a face answers with. The configured faces drive this
     * plugin's own movement instead (see {@link #serverTickTransfer()}).
     */
    @Override
    public ResourceHandler<ItemResource> getItemLookup(@Nullable Direction direction) {
        return emptyStorage;
    }

    // ------------------------------------------------------------------ save data

    /**
     * Saves this plugin's own cell-face settings next to everything the inherited addon saves.
     * <p>
     * The inherited {@link TransferFaceModes} is written as well - it is the base class's own field and the cube net
     * page's model - but this plugin never sets an entry in it (see {@link #setCellFaceConfig}), so what it holds is
     * only ever what a world written by an older version had. That is exactly what the migration in
     * {@link CellFaceModes#load(net.minecraft.world.level.storage.ValueInput)} reads, and it is why the old array is
     * left untouched rather than cleared: a world that is opened by the older version again keeps the six settings it
     * can show.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        cellFaces.save(output);
    }

    /** Reads the cell-face settings, migrating the old direction-keyed array (see {@link CellFaceModes#load}). */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cellFaces.load(input);
    }

    // ------------------------------------------------------------------ GUI and ticking

    /** Name of the plugin's screen; the plugin has no addon type of its own to name it. */
    @Override
    protected String displayNameKey() {
        return "container.oritechaddonsone." + OritechAddonsOne.TRANSFER_ADDON.getId().getPath();
    }
}
