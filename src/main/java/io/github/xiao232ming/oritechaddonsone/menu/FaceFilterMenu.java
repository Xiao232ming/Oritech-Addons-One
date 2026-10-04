package io.github.xiao232ming.oritechaddonsone.menu;

import java.util.HashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ItemFilterData;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;

/**
 * The container behind the 过滤 page: it holds <b>the two filters of one transfer face</b> - what may enter the
 * machine through it and what may leave it - and the player's own inventory so the cursor stack can fill the
 * twelve slots, exactly what Oritech's item filter screen is built on.
 * <p>
 * <b>One menu per face, holding both directions.</b> A face carries two filters ({@link TransferMode#INPUT} and
 * {@link TransferMode#OUTPUT}), and the page that opens it is <b>one button</b> - so the page has to reach both,
 * and the direction selector at its top is what switches between them. A menu per filter instead would have
 * needed a second button on the small face panel, and a face set to 输入 + 输出 would then have two entry points
 * where every other face has one.
 * <p>
 * <b>The filter travels in the opening buffer, not through container data.</b> It is a dozen items plus three
 * switches, it is edited by clicking, and it is written straight back to the server on every change - so there is
 * nothing a sync would have to keep in step, and a slot that would otherwise have to carry an item stack per grid
 * cell is avoided. The client is therefore the only editor, and the server only ever sees a whole filter.
 * <p>
 * <b>The menu owns no slot of the filter.</b> The twelve grid cells are drawn by the screen and read straight out
 * of {@link #data()}; only the player's inventory is a real slot list here, because that is what a cursor stack
 * and a shift-click need.
 * <p>
 * <b>Which direction the page opens on is the face's own.</b> A face set to 输出 opens on the filter that decides
 * what leaves the machine, so the page starts on the one the player most likely came to change; the selector then
 * moves to the other one. This is a page-local choice and is not sent anywhere - only the filter that is actually
 * edited travels.
 */
public class FaceFilterMenu extends AbstractContainerMenu {

    /** The filter belongs to one of a block's six faces - see {@link FaceFilters}. */
    public static final int MODEL_FACE = 0;
    /** The filter belongs to one face of one cell of the machine's structure - see {@link CellFilters}. */
    public static final int MODEL_CELL = 1;

    private final BlockPos pos;
    private final int model;
    private final Direction face;
    private final Vec3i cell;

    /** What may enter the machine through this face; never {@code null}. */
    private ItemFilterData input;
    /** What may leave the machine through this face; never {@code null}. */
    private ItemFilterData output;

    /** Which of the two the page is editing right now. Page local - see the class comment. */
    private TransferMode flow;

    /** Client side constructor: everything arrives in the opening buffer. */
    public FaceFilterMenu(int syncId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(OritechAddonsOne.FACE_FILTER_MENU.get(), syncId);

        this.pos = buffer.readBlockPos();
        this.model = buffer.readVarInt();
        this.face = readFace(buffer.readVarInt());
        this.cell = new Vec3i(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        this.input = ItemFilterData.CODEC.decode(buffer);
        this.output = ItemFilterData.CODEC.decode(buffer);
        this.flow = readFlow(buffer.readVarInt());

        addPlayerInventory(playerInventory);
    }

    private FaceFilterMenu(int syncId, Inventory playerInventory, BlockPos pos, int model, Direction face, Vec3i cell,
            ItemFilterData input, ItemFilterData output, TransferMode flow) {
        super(OritechAddonsOne.FACE_FILTER_MENU.get(), syncId);

        this.pos = pos;
        this.model = model;
        this.face = face;
        this.cell = cell;
        this.input = input == null ? ItemFilterData.DEFAULT : input;
        this.output = output == null ? ItemFilterData.DEFAULT : output;
        this.flow = flow;

        addPlayerInventory(playerInventory);
    }

    /**
     * The provider that opens this menu for one face, i.e. what {@code ServerPlayer#openMenu} is given.
     * <p>
     * It is a separate object rather than the block entity itself because a block entity implements
     * {@link MenuProvider} once, for one menu, while the filter is chosen per click - and because the two filters
     * to open with are read by the caller, not by the block.
     */
    public static MenuProvider provider(BlockPos pos, int model, Direction face, Vec3i cell,
            ItemFilterData input, ItemFilterData output, TransferMode flow) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                // the page draws its own title, so the container itself has none - the same answer Oritech's own
                // item filter gives
                return Component.empty();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
                return new FaceFilterMenu(syncId, playerInventory, pos, model, face, cell, input, output, flow);
            }
        };
    }

    /** Writes what the client needs to build this menu into the opening buffer. */
    public static void write(RegistryFriendlyByteBuf buffer, BlockPos pos, int model, Direction face, Vec3i cell,
            ItemFilterData input, ItemFilterData output, TransferMode flow) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(model);
        buffer.writeVarInt(face.ordinal());
        buffer.writeVarInt(cell.getX());
        buffer.writeVarInt(cell.getY());
        buffer.writeVarInt(cell.getZ());
        ItemFilterData.CODEC.encode(buffer, input == null ? ItemFilterData.DEFAULT : input);
        ItemFilterData.CODEC.encode(buffer, output == null ? ItemFilterData.DEFAULT : output);
        buffer.writeVarInt((flow == null ? TransferMode.INPUT : flow).ordinal());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // Oritech's own filter screen puts these at y = 84 and y = 142 against the 166 tall background. This
        // page is 20 pixels taller - the direction row at the top pushed everything down - and draws the tall
        // background, whose own inventory frames sit 20 lower as well, so the slots move with them. The slots
        // are the real ones, so the items are drawn where the menu says; the frames are the background's, so
        // both have to move together or the items float 20 pixels above their own slots.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 162));
        }
    }

    /** The block whose page was opened, i.e. where the filters are stored. */
    public BlockPos pos() {
        return pos;
    }

    /** Which model these filters belong to, one of {@link #MODEL_FACE} and {@link #MODEL_CELL}. */
    public int model() {
        return model;
    }

    /** The face these filters belong to; never {@code null}. */
    public Direction face() {
        return face;
    }

    /** The cell this face belongs to, or {@code null} while it is a face of the block rather than of the structure. */
    @Nullable
    public Vec3i cell() {
        return model == MODEL_FACE ? null : cell;
    }

    /** Which of the two filters the page is editing right now; never {@code null}. */
    public TransferMode flow() {
        return flow;
    }

    /** The filter the page is editing right now, i.e. the one of {@link #flow()}; never {@code null}. */
    public ItemFilterData data() {
        return flow == TransferMode.OUTPUT ? output : input;
    }

    /**
     * Counts every local edit of a filter, whatever made it.
     * <p>
     * The page has to watch this rather than sending on the clicks it handled itself, because not every edit is
     * a click it handled: a shift-click on the player's inventory registers an item in the filter from inside
     * {@link #quickMoveStack}, where no screen code runs at all. One counter covers both, and it cannot miss an
     * edit the way a per-click send can.
     */
    private long revision;

    /** The number of local edits so far; a page that sees it change sends the filter it holds. */
    public long revision() {
        return revision;
    }

    /**
     * Replaces the filter of the direction the page is editing, which is what every edit does - the screen never
     * changes a part of it. Sending it to the server is the screen's business, not the menu's.
     */
    public void setData(ItemFilterData data) {
        if (data == null) return;

        if (flow == TransferMode.OUTPUT) {
            output = data;
        } else {
            input = data;
        }
        revision++;
    }

    /** Moves the page onto the other of the two filters. */
    public void setFlow(TransferMode flow) {
        if (flow == TransferMode.INPUT || flow == TransferMode.OUTPUT) this.flow = flow;
    }

    /**
     * A shift-click on the player's inventory registers the item in the first free slot of the filter being
     * edited, and is Oritech's own behaviour rather than a move: the stack <b>stays where it is</b>, because a
     * filter names a kind of item and not a stack of it. An item that is already listed is refused, so one kind
     * cannot fill the whole grid.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        var slotStack = player.getInventory().getItem((slot + 9) % 36);
        if (slotStack.isEmpty()) return ItemStack.EMPTY;

        var displayStack = new ItemStack(slotStack.getItem(), 1);
        displayStack.applyComponents(slotStack.getComponents());

        for (var item : data().items().values()) {
            if (item.is(displayStack.getItem())) return ItemStack.EMPTY;
        }

        var items = new HashMap<>(data().items());
        for (int index = 0; index < ItemFilterData.SLOTS; index++) {
            if (items.containsKey(index)) continue;
            items.put(index, displayStack);
            break;
        }

        var current = data();
        setData(new ItemFilterData(current.useNbt(), current.useWhitelist(), current.useComponents(), items));
        return ItemStack.EMPTY;
    }

    /**
     * The page closes itself once the block it belongs to is gone, exactly like any other container: a filter
     * that outlived its face would write into nothing.
     */
    @Override
    public boolean stillValid(Player player) {
        var level = player.level();
        return level == null || level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity;
    }

    /** The face an ordinal names, or a default one while it names none - which is what a modified client sends. */
    private static Direction readFace(int ordinal) {
        var directions = Direction.values();
        return ordinal >= 0 && ordinal < directions.length ? directions[ordinal] : Direction.NORTH;
    }

    /** The direction of the movement an ordinal names, or {@link TransferMode#INPUT} while it names none. */
    private static TransferMode readFlow(int ordinal) {
        var flow = TransferMode.byOrdinal(ordinal);
        return flow == TransferMode.OUTPUT ? TransferMode.OUTPUT : TransferMode.INPUT;
    }
}