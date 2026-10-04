package io.github.xiao232ming.oritechaddonsone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.DelegatingInventoryStorage;

import io.github.xiao232ming.oritechaddonsone.Config;

/**
 * The item inventory one <b>face</b> of an Extension Addon / Wireless Extension Dock offers to the outside
 * world. A face answers for exactly one of two features, read on every single call:
 * <ul>
 *     <li>its <b>transfer mode</b> (the Extension Transfer page, see {@link TransferFaceModes}): the machine's
 *     whole inventory, where {@link TransferMode#INPUT} accepts items, {@link TransferMode#OUTPUT} offers
 *     them and {@link TransferMode#BOTH} does both - each of them restricted to the machine's own slot roles
 *     (see {@link MachineSlotRoles}), so an INPUT face fills the machine's input slots and only those, an
 *     OUTPUT face empties its output slots and only those, and BOTH never inserts into an output slot nor
 *     extracts from an input one,</li>
 *     <li>its <b>proxy binding</b> (the Item Proxy page, see {@code ProxyFaceBindings}): Oritech's own
 *     inventory proxy mechanism, i.e. the machine's inventory with every slot but the bound one answering
 *     "empty".</li>
 * </ul>
 * A face with both configured transfers items - the newer, whole-inventory behaviour wins over the
 * single-slot one; taking the mode away falls back to the binding. A face with neither offers nothing.
 * <p>
 * <b>One object per face, forever.</b> The capability wrapper Oritech builds for us
 * ({@code NeoforgeItemApiImpl.ContainerStorageWrapper}) caches the storage a face answers with, so this
 * class must never be replaced by a different instance; everything that can change - the mode, the binding,
 * the machine - is therefore resolved fresh inside each call instead of being cached in a field.
 * <p>
 * <b>Fail safe:</b> the machine inventory is resolved on every call too, so a machine that was broken,
 * unloaded, replaced or never present simply yields {@code null} - the
 * {@link DelegatingInventoryStorage} then reports zero slots and accepts/offers nothing instead of
 * crashing.
 * <p>
 * The class also carries the two helpers the <b>automation</b> of a face is built from - looking a
 * neighbour's inventory up ({@link #storageAt}) and moving one stack between two handlers ({@link #move}).
 * They are static and need no face of their own, which is what lets the placed transfer addon move items
 * between a machine and the containers around the extender it hangs on (see
 * {@code ExtensionTransferAddonBlockEntity#serverTickTransfer}) with the very same logic a face of this block uses.
 */
public final class MachineFaceStorage extends DelegatingInventoryStorage {

    /**
     * How many items automation moves per face and tick, <b>per direction</b> - read from the config
     * ({@code Config#transferItemsPerTick()}, {@code transferItemsPerTick} in
     * {@code config/oritechaddonsone-common.toml}, default 64).
     * <p>
     * A hopper moves one item per tick, an Oritech item pipe up to a stack, so the default is one full stack
     * per direction and per face - and a face that is fed and emptied at the same time gets that budget for
     * each of the two directions rather than sharing one.
     * <p>
     * The value is read on every call instead of being cached, so an edited config file takes effect on the
     * next automation step without a restart; the call is a field read of an already loaded value.
     */
    public static int itemsPerTick() {
        return Config.transferItemsPerTick();
    }

    private final BlockEntity owner;
    /** Face this storage belongs to, resolved against the block entity on every call. */
    private final Direction face;

    public MachineFaceStorage(BlockEntity owner, Direction face) {
        super(() -> machineStorage(owner), () -> offers(owner, face));
        this.owner = owner;
        this.face = face;
    }

    /**
     * The inventory storage of the machine this block works on, or {@code null} while there is none.
     * <p>
     * This is the same call Oritech's own inventory proxy addon makes
     * ({@code getInventoryStorage(null)} on the machine's block entity), i.e. exactly what an Oritech item
     * pipe sees - and it follows the block's own idea of "the machine I work on", so a wireless dock
     * transfers to the machine it is linked to and a wired addon to the machine that claimed it.
     */
    @Nullable
    public static ItemApi.InventoryStorage machineStorage(BlockEntity owner) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return null;
        return machineStorageAt(addon.getLevel(), addon.connectedMachinePos());
    }

    /**
     * The inventory storage of the block at {@code machinePos} - the machine an addon works on - or
     * {@code null} while there is none (no position, unloaded chunk, block entity gone, a block without an
     * inventory).
     * <p>
     * It exists next to {@link #machineStorage(BlockEntity)} because the machine of a placed transfer addon
     * is not the machine of the block that holds the plugin: the plugin is asked for the extender's
     * controller position instead of its own, and this is where that position is turned into a storage.
     */
    @Nullable
    public static ItemApi.InventoryStorage machineStorageAt(@Nullable Level level, @Nullable BlockPos machinePos) {
        if (level == null || machinePos == null || !level.isLoaded(machinePos)) return null;

        return level.getBlockEntity(machinePos) instanceof ItemApi.BlockProvider provider
                ? provider.getInventoryStorage(null)
                : null;
    }

    /**
     * Position of the machine an addon works on, or {@code null} while this block does not work on one. The
     * one place the machine position is derived from the addon, so the inventory
     * ({@link #machineStorage(BlockEntity)}) and the slot roles of that inventory
     * ({@link MachineSlotRoles}) can never be looked up for two different machines.
     */
    @Nullable
    static BlockPos machinePos(BlockEntity owner) {
        return owner instanceof ExtensionAddonBlockEntity addon ? addon.connectedMachinePos() : null;
    }

    /**
     * Level the machine an addon works on lives in, or {@code null} while this block does not work on one.
     * A placed transfer addon is in the same world as the machine behind its extender, so the addon's own
     * level is the machine's level in both cases.
     */
    @Nullable
    static Level machineLevel(BlockEntity owner) {
        return owner instanceof ExtensionAddonBlockEntity addon ? addon.getLevel() : null;
    }

    /**
     * The item storage of the block outside one face of the block at {@code pos} - the container automation
     * moves items with - or {@code null} while there is none (air, a machine without an inventory, an
     * unloaded chunk). The side is asked for as the neighbour's own face pointing back at that block, which
     * is what Oritech's own item pipe asks with (see {@code ItemPipeInterfaceEntity}, which does
     * {@code ItemApi.BLOCK.find(world, sourcePos, direction)} with the direction pointing from the neighbour
     * back at the pipe).
     */
    @Nullable
    public static ItemApi.InventoryStorage storageAt(@Nullable Level level, BlockPos pos, Direction face) {
        if (level == null) return null;

        var neighbourPos = pos.relative(face);
        if (!level.isLoaded(neighbourPos)) return null;

        // The side that points back at us is asked first, exactly as a pipe would - and only first, because not
        // every block offers its inventory per side: a block that registers its handler without sides answers
        // null to a directional query, and refusing that block would mean the automation finds no container
        // although there is one right behind the face.
        var side = ItemApi.BLOCK.find(level, neighbourPos, face.getOpposite());
        if (side != null) return side;

        return ItemApi.BLOCK.find(level, neighbourPos, null);
    }

    /**
     * Moves up to {@link #itemsPerTick()} items from {@code from} to {@code to} and reports nothing: it is the
     * four argument form without owners, i.e. for two storages whose machines are not known here - a caller that
     * has the machine's block entity passes it so the machine's slot roles can be respected.
     */
    public static void move(ItemApi.InventoryStorage from, ItemApi.InventoryStorage to) {
        move(from, null, to, null);
    }

    /**
     * The full form of {@link #move(ItemApi.InventoryStorage, ItemApi.InventoryStorage)}, for a caller that
     * knows which machines it is moving between - the automation of a face, which holds the machine and the
     * container next to their storages.
     * <p>
     * Oritech's {@code ItemApi.InventoryStorage} has no transaction: both sides are therefore asked first and
     * really moved afterwards. The target is asked how much it would take with a simulated insert, the source
     * with a simulated extract, and only then is exactly that amount taken out and put in. Whatever the target
     * ends up refusing (it can only refuse because something else filled it in between) is handed straight back
     * to the source, so no item can be lost even then.
     * <p>
     * The owners are what make the machine's slot roles ({@link MachineSlotRoles}) enforceable here: an item
     * is only taken out of a slot the source may give away and only put into a slot the target may take it
     * in - never out of a machine's input slot and never into one of its output slots. A {@code null} owner
     * is a container with no roles to respect: a chest, a pipe, another mod's inventory.
     * <p>
     * It delegates to the form the automation uses, i.e. one dose of up to {@link #itemsPerTick()} items
     * ({@link #move(ItemApi.InventoryStorage, BlockEntity, ItemApi.InventoryStorage, BlockEntity, int)}).
     */
    public static void move(ItemApi.InventoryStorage from, @Nullable BlockEntity fromOwner,
            ItemApi.InventoryStorage to, @Nullable BlockEntity toOwner) {
        move(from, fromOwner, to, toOwner, itemsPerTick());
    }

    /**
     * The form the <b>automation of a face</b> calls: it moves as many items as the two sides and the budget
     * allow, in one step, instead of one stack per step.
     * <p>
     * <b>Filling a slot up to its maximum is exactly what this makes possible.</b> A slot that already holds
     * some of the item has room for "its maximum minus what is in there", and a step that may only insert one
     * stack's worth stops at that difference: the slot creeps towards its maximum instead of being filled. A
     * dose takes the budget as its target and hands it to the target's slots in order, so the first slot that
     * accepts the item is topped up to its maximum and whatever is left over flows into the next one - the face
     * set to INPUT fills until the machine's input slots are full, and the face set to OUTPUT empties the output
     * slots as soon as something is in them. The same dose walks <b>every</b> source slot, so a machine whose
     * items sit in several output slots is emptied in one tick instead of one slot per tick; the rate per slot and
     * per direction is the configured one ({@code Config#transferItemsPerTick()}).
     * <p>
     * Nothing can be lost: Oritech's storage has no transaction, so the target is asked with a simulated insert
     * first and only the amount it really takes is extracted; whatever a target refuses in between is handed
     * straight back. Every source slot is served by one dose, so the rate below is a rate per slot and per
     * direction, not a rate for the whole face. A dose that moves nothing ends the loop, so a target that refuses
     * everything cannot spin.
     *
     * @param amount total number of items this call may move, i.e. the cap of one dose
     */
    public static int move(ItemApi.InventoryStorage from, @Nullable BlockEntity fromOwner,
            ItemApi.InventoryStorage to, @Nullable BlockEntity toOwner, int amount) {
        var budget = Math.max(1, Math.min(amount, itemsPerTick()));
        var total = 0;

        while (budget > 0) {
            var moved = moveDose(from, fromOwner, to, toOwner, budget, true);
            if (moved <= 0) {
                // Nothing travelled while the machine's slot roles were respected. Those roles are a reading of
                // the machine's own slot layout, and a reading can be wrong in a way that blocks <em>every</em>
                // slot: a machine whose whole inventory falls inside one of the two ranges forbids the other
                // direction outright, and its faces would then move nothing at all. The dose is therefore
                // retried once without them - the machine's own handler still decides what a slot accepts, so
                // the worst case is the behaviour this mod had before the roles existed, and the best case is
                // that the items travel after all.
                moved = moveDose(from, fromOwner, to, toOwner, budget, false);
                if (moved <= 0) return total;
            }

            budget -= moved;
            total += moved;
        }

        return total;
    }

    /**
     * Moves one dose: every source slot the roles let go of, each up to {@code amount}, into the target's
     * accepting slots.
     * <p>
     * <b>Every source slot, not just the first one.</b> A machine can have several output slots, and serving only
     * one of them per call meant the second slot's items waited for the first slot to be emptied - a machine with
     * four full output slots needed four passes to push them all out, which is exactly the "one slot lags behind"
     * the automation is supposed to avoid. The dose therefore walks the source slots in order and moves what each
     * of them holds, so items in <em>any</em> output slot leave in the same tick.
     * <p>
     * The other side of the same coin is {@link #insertInto}, which spreads a dose over the target's accepting
     * slots in order: a partially filled slot of the same item is topped up to its maximum first and the rest
     * flows into the following slots, so every input slot of a machine is reachable in one dose as well. The
     * target is asked with a simulated insert of exactly the dose, which is what makes that answer the real
     * capacity of those slots.
     * <p>
     * Nothing can be lost: the simulated extract means only what the source really gives is taken, and whatever
     * a target refuses in between is handed straight back.
     *
     * @return number of items moved, or {@code 0} while nothing could be moved
     */
    private static int moveDose(ItemApi.InventoryStorage from, @Nullable BlockEntity fromOwner,
            ItemApi.InventoryStorage to, @Nullable BlockEntity toOwner, int amount, boolean useRoles) {
        // A side that could not be resolved is nothing to move between, and both handlers are looked up per
        // tick: a machine that is broken, replaced or unloaded in between leaves a null here, which used to
        // reach the target and crash the server on the tick.
        if (from == null || to == null) return 0;
        if (!from.supportsExtraction() || !to.supportsInsertion()) return 0;

        var moved = 0;

        for (int slot = 0; slot < from.getSlotCount(); slot++) {
            if (useRoles && !MachineSlotRoles.allowsExtractAt(fromOwner, slot)) continue;

            var stack = from.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            // dry run: how much of the dose would the target take, and how much can this slot give?
            var offered = stack.copyWithCount(Math.min(stack.getCount(), amount));
            var wanted = acceptedBy(to, toOwner, offered, useRoles);
            if (wanted <= 0) continue;

            var takeable = from.extractFromSlot(offered.copyWithCount(wanted), slot, true);
            if (takeable <= 0) continue;

            var extracted = from.extractFromSlot(offered.copyWithCount(takeable), slot, false);
            if (extracted <= 0) continue;

            var inserted = insertInto(to, toOwner, offered.copyWithCount(extracted), false, useRoles);
            if (inserted < extracted) {
                // the target changed its mind in between: give the refused items back to where they came from
                from.insert(offered.copyWithCount(extracted - inserted), false);
            }

            moved += inserted;
        }

        return moved;
    }

    /**
     * How much of {@code offered} the target would take right now, counting only the slots a machine target
     * may take it in. It is the same simulated insert {@link #move} has always made, just with the machine's
     * slot roles respected; a target that is not a machine - a chest, a pipe, another mod's inventory - has
     * no roles and is asked exactly as before.
     */
    private static int acceptedBy(ItemApi.InventoryStorage to, @Nullable BlockEntity toOwner, ItemStack offered,
            boolean useRoles) {
        return insertInto(to, toOwner, offered, true, useRoles);
    }

    /**
     * Inserts into the target's accepting slots, in slot order, up to the offered amount in total: a slot that
     * already holds part of the item is filled to its maximum first, and the rest goes to the following ones.
     * <p>
     * The slots Oritech reserved for a machine's outputs are skipped while the target is a machine whose roles
     * are known; the candidate slots are tried one at a time because a role is a property of a slot while
     * Oritech's storage only offers the whole-inventory insert as an alternative.
     */
    private static int insertInto(ItemApi.InventoryStorage to, @Nullable BlockEntity toOwner, ItemStack offered,
            boolean simulate) {
        return insertInto(to, toOwner, offered, simulate, true);
    }

    /**
     * The same insert with the machine's slot roles switchable: the retry of {@link #move} that runs after a
     * dose moved nothing passes {@code false} to fall back to the plain handler.
     */
    private static int insertInto(ItemApi.InventoryStorage to, @Nullable BlockEntity toOwner, ItemStack offered,
            boolean simulate, boolean useRoles) {
        if (to == null) return 0;

        var roles = !useRoles || toOwner == null ? null : MachineSlotRoles.of(toOwner);
        if (roles == null) return to.insert(offered, simulate);

        var inserted = 0;
        for (var slot = 0; slot < to.getSlotCount() && inserted < offered.getCount(); slot++) {
            if (!MachineSlotRoles.allowsInsertAt(toOwner, slot)) continue;
            inserted += to.insertToSlot(offered.copyWithCount(offered.getCount() - inserted), slot, simulate);
        }
        return inserted;
    }

    /** The mode this face transfers with, or {@link TransferMode#NONE} while it transfers nothing. */
    private TransferMode transferMode() {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return TransferMode.NONE;
        if (!addon.canTransferItems()) return TransferMode.NONE;
        return addon.transferModes().modeOf(face);
    }

    /** Slot this face proxies, or {@code null} while the proxy feature does not configure it. */
    @Nullable
    private Integer proxySlot() {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return null;
        if (!addon.canProxyItems()) return null;
        return addon.proxyFaces().slotOf(face);
    }

    /**
     * True while this face offers the machine's inventory at all: one of the two features configures it.
     * That the machine can be resolved as well is what the parent's own {@code canUseBackend} check adds, so
     * every call below still answers "nothing" while the machine is gone.
     */
    private static boolean offers(BlockEntity owner, Direction face) {
        if (!(owner instanceof ExtensionAddonBlockEntity addon)) return false;
        if (addon.transferModes().isConfigured(face) && addon.canTransferItems()) return true;
        if (addon.proxyFaces().isConfigured(face) && addon.canProxyItems()) return true;
        return false;
    }

    /** True while this face moves whole inventories with a mode instead of proxying a single slot. */
    private boolean transfers() {
        return transferMode() != TransferMode.NONE;
    }

    /**
     * Slot count of the machine inventory. It is the machine's own slot count in both behaviours - like
     * Oritech's inventory proxy addon - so a pipe sees a normal inventory; in the proxy behaviour only the
     * bound slot then accepts or offers items.
     */
    @Override
    public int getSlotCount() {
        return offers(owner, face) ? super.getSlotCount() : 0;
    }

    /** Index of the slot the proxy behaviour works on, or {@code -1} while the face transfers whole stacks. */
    private int proxyIndex() {
        if (transfers()) return -1;
        var configured = proxySlot();
        return configured == null ? -1 : configured;
    }

    /** True while the given slot may be read or written through this face. */
    private boolean covers(int index) {
        if (!offers(owner, face)) return false;
        var proxy = proxyIndex();
        return proxy < 0 || index == proxy;
    }

    /** True while items may be put into the machine through this face. */
    private boolean allowsInsert() {
        var mode = transferMode();
        // no transfer mode means the proxy behaviour, which is bidirectional on its one slot
        return mode == TransferMode.NONE || mode.allowsInsert();
    }

    /** True while items may be taken out of the machine through this face. */
    private boolean allowsExtract() {
        var mode = transferMode();
        return mode == TransferMode.NONE || mode.allowsExtract();
    }

    /**
     * True while a pipe may push into this face at all. An output-only face answers {@code false}, which is
     * what tells a pipe not to try, and the parent already answers {@code false} while the machine is gone.
     */
    @Override
    public boolean supportsInsertion() {
        return allowsInsert() && super.supportsInsertion();
    }

    /** True while a pipe may pull out of this face at all; see {@link #supportsInsertion()}. */
    @Override
    public boolean supportsExtraction() {
        return allowsExtract() && super.supportsExtraction();
    }

    /**
     * The stack of one machine slot, or nothing while this face does not cover it: a proxy face covers only
     * its bound slot, a transfer face the whole inventory.
     */
    @Override
    public ItemStack getStackInSlot(int index) {
        return covers(index) ? super.getStackInSlot(index) : ItemStack.EMPTY;
    }

    /** Slot limit of one machine slot, or {@code 0} while this face does not cover it; see {@link #covers}. */
    @Override
    public int getSlotLimit(int index) {
        return covers(index) ? super.getSlotLimit(index) : 0;
    }

    /**
     * Index-free insert of a pipe or a hopper. The mode gates it (an OUTPUT face takes nothing) and the
     * slots are walked here, because the machine's slot roles ({@link MachineSlotRoles}) decide which ones
     * may be filled at all - an INPUT face fills the machine's input slots and never a slot Oritech reserved
     * for its results. Walking the slots keeps the indexed overload the single place both gates live in.
     */
    @Override
    public int insert(ItemStack inserted, boolean simulate) {
        if (!allowsInsert()) return 0;

        var proxy = proxyIndex();
        if (proxy >= 0) return insertToSlot(inserted, proxy, simulate);

        var supported = machineStorage(owner);
        if (supported == null) return 0;

        var insertedTo = 0;
        for (var index = 0; index < supported.getSlotCount() && insertedTo < inserted.getCount(); index++) {
            insertedTo += insertToSlot(inserted.copyWithCount(inserted.getCount() - insertedTo), index, simulate);
        }
        return insertedTo;
    }

    /**
     * Index-free extract, gated like {@link #insert(ItemStack, boolean)}: an OUTPUT face offers the
     * machine's own output slots and never its ingredients.
     */
    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        if (!allowsExtract()) return 0;

        var proxy = proxyIndex();
        if (proxy >= 0) return extractFromSlot(extracted, proxy, simulate);

        var supported = machineStorage(owner);
        if (supported == null) return 0;

        var extractedFrom = 0;
        for (var index = 0; index < supported.getSlotCount() && extractedFrom < extracted.getCount(); index++) {
            extractedFrom += extractFromSlot(extracted.copyWithCount(extracted.getCount() - extractedFrom), index, simulate);
        }
        return extractedFrom;
    }

    /**
     * Indexed insert of a pipe or a hopper. Two gates run here: the face's mode (an OUTPUT face takes
     * nothing) and the machine's slot roles ({@link MachineSlotRoles}) - an INPUT face fills the machine's
     * input slots and never a slot Oritech reserved for its results. The item proxy behaviour, which has no
     * mode, is only gated by the role of its one bound slot, which is what keeps a face bound to an output
     * slot from pushing items into it.
     */
    @Override
    public int insertToSlot(ItemStack inserted, int index, boolean simulate) {
        if (!allowsInsert() || !covers(index) || !MachineSlotRoles.allowsInsertAt(owner, index)) return 0;
        return super.insertToSlot(inserted, index, simulate);
    }

    /** Indexed extract, gated like {@link #insertToSlot(ItemStack, int, boolean)}. */
    @Override
    public int extractFromSlot(ItemStack extracted, int index, boolean simulate) {
        if (!allowsExtract() || !covers(index) || !MachineSlotRoles.allowsExtractAt(owner, index)) return 0;
        return super.extractFromSlot(extracted, index, simulate);
    }

    /**
     * A pipe that pulled an item out or pushed one in asks us to persist the change. The backing storage
     * belongs to the machine, whose own handler already marks itself dirty, so only our block entity has
     * to be told that the face was used (it holds no items itself, but this is where a future state would
     * be flushed).
     */
    @Override
    public void update() {
        if (owner instanceof ExtensionAddonBlockEntity addon) addon.onProxyUsed();
    }
}
