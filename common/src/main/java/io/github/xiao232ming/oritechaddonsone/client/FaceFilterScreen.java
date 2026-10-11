package io.github.xiao232ming.oritechaddonsone.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.api.screen.widgets.ItemSlotWidget;
import rearth.oritech.api.screen.widgets.LabelWidget;
import rearth.oritech.api.screen.widgets.ToggleWidget;
import rearth.oritech.client.ui.OritechMachineScreen;
import rearth.oritech.client.ui.OritechWidgetScreen;

import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ItemFilterData;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.menu.FaceFilterMenu;
import io.github.xiao232ming.oritechaddonsone.network.FilterNetworking;

/**
 * The page behind one 过滤 button of a transfer face: the per-face item filter, i.e. the twelve item slots and the
 * three switches that decide what may cross that face.
 * <p>
 * <b>A direct port of Oritech's own item filter screen</b> ({@code rearth.oritech.client.ui.ItemFilterScreen}),
 * and it has to stay one: a player who has filtered an Oritech pipe has learned that the grid is 4x3, that an
 * empty cursor stack clears a slot, that a shift-click <em>registers</em> an item without moving it, and that the
 * three switches mean what they mean there. Every one of those answers is the same here, so the geometry is Oritech's
 * too - same grid pitch, same toggle column, same player inventory - and the behaviour was taken from the class
 * itself rather than guessed at.
 * <p>
 * <b>What this page has that Oritech's does not is the direction row.</b> A face here carries <b>two</b> filters -
 * what may <b>enter</b> the machine ({@link TransferMode#INPUT}) and what may <b>leave</b> it
 * ({@link TransferMode#OUTPUT}) - because those are two different questions a smelter answers differently (fed ore,
 * handed ingots). So the two buttons at the top of the page say which of the two is being edited, and clicking one
 * moves the page to it. Oritech's page never needed this: its filter belongs to a block, a block has one filter per
 * face, and the face's direction is the pipe's business. That row is the <b>only</b> difference in layout, and it is
 * why this page is Oritech's 166 pixels tall plus the 20 it adds - which is also why it draws Oritech's
 * {@code BACKGROUND_TALL} rather than the short {@code BACKGROUND}: that texture is the same artwork with every
 * frame moved down exactly those 20 pixels.
 * <p>
 * <b>The page owns no state; the menu owns the filter.</b> Everything drawn here is read out of
 * {@link FaceFilterMenu#data()} on every frame, and every edit is a whole new {@link ItemFilterData} handed back to
 * the menu - the same way {@link ItemFilterData} is built everywhere else in this mod, so a slot can never show
 * something the transfer itself would not answer with.
 * <p>
 * <b>One send path, and it watches instead of sending.</b> Every edit goes through
 * {@link FaceFilterMenu#setData}, which counts them ({@link FaceFilterMenu#revision()}) - but not every edit is a
 * click this screen handled: a shift-click on the player's inventory registers an item from inside
 * {@link FaceFilterMenu#quickMoveStack}, where no screen code runs at all. So this page sends in
 * {@link #containerTick()} whenever that counter moved, and the click handlers themselves send nothing. Two edits
 * cannot race, because each is handled in its own frame and the tick runs on every one of them.
 * <p>
 * <b>A refused edit is visible.</b> The server answers every write with the filter it really holds
 * ({@code FilterNetworking.FilterState}), which reaches this page through {@link #applyAuthoritative(ItemFilterData)}
 * and overwrites the page's own copy - so what the page shows is always what the server has, never what a player
 * hoped for.
 * <p>
 * <b>This branch's graphics API.</b> 26.1.2 draws through {@code GuiGraphicsExtractor}, which does not exist on
 * 1.21.1; everything here goes through {@link GuiGraphics} instead, with {@code renderItem} and
 * {@code renderItemDecorations} in place of the newer {@code item} / {@code itemDecorations}. Oritech's own
 * 1.21.1 widget screen has the same signature ({@link OritechWidgetScreen#renderBg(GuiGraphics, float, int, int)}),
 * so this page is exactly as much of Oritech's screen as the branch allows.
 * <p>
 * The class lives in a client only package and is registered for {@code FACE_FILTER_MENU}, so it is never loaded on
 * a dedicated server.
 */
public class FaceFilterScreen extends OritechWidgetScreen<FaceFilterMenu> {

    /** How many items one filter lists, i.e. the size of the grid - the number the data itself carries. */
    public static final int FILTER_SIZE = ItemFilterData.SLOTS;

    /** Oritech's own panel width, which is also the width of its {@code gui_base} texture. */
    private static final int IMAGE_WIDTH = 176;

    /**
     * Oritech's filter page is 166 tall and this one is exactly <b>20</b> taller, because the direction row is one
     * band of its own above everything Oritech's page draws. Those twenty pixels are not an arbitrary padding: they
     * are the height of Oritech's {@code BACKGROUND_TALL} ({@code gui_base_tall.png}, which exists on this branch
     * too), the same {@code gui_base} artwork with every frame moved down by twenty, so the page and its texture
     * cannot disagree.
     */
    private static final int IMAGE_HEIGHT = 186;

    /** The band the direction row takes, i.e. how far down everything Oritech's page draws is moved. */
    private static final int TOP_BAND = 20;

    /** The grid: Oritech's own 4x3 geometry, moved down by the band. A 20 pixel pitch, 18 pixel slots. */
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 3;
    private static final int GRID_X = 5;
    private static final int GRID_Y = 18 + TOP_BAND;
    private static final int GRID_PITCH = 20;
    private static final int SLOT_SIZE = 18;

    /** The three switches: Oritech's column at x=83, each twenty pixels below the last, moved down by the band. */
    private static final int TOGGLE_X = 83;
    private static final int TOGGLE_Y = 18 + TOP_BAND;
    private static final int TOGGLE_PITCH = 20;

    /**
     * The direction row: <b>two</b> buttons, not one per direction per model - a page edits one face and that face
     * has exactly two filters. The pair is laid out from {@link #DIRECTION_X}, the same two pixel gap four 40 pixel
     * plates would have used, so the row reads as one control instead of a row of unrelated switches.
     * <p>
     * <b>输入 then sits {@link #INPUT_NUDGE} pixels left of where the centred pair would put it</b>, and 输出 stays
     * where the centring put it. The row is therefore deliberately off centre: the two labels are read far more
     * often in the 输入 -> 输出 order, so the button a player reaches for first gets the room, and widening the
     * space between the two is what makes the switch read as a switch rather than as two unrelated tabs.
     */
    private static final int DIRECTION_Y = 5;
    private static final int DIRECTION_WIDTH = 40;
    private static final int DIRECTION_GAP = 2;

    /** How far the 输入 button sits left of the centred pair; 输出 keeps the centred position. */
    private static final int INPUT_NUDGE = 8;
    private static final int DIRECTION_X = (IMAGE_WIDTH - (2 * DIRECTION_WIDTH + DIRECTION_GAP)) / 2;

    private static final String WHITELIST_LABEL = "gui.oritechaddonsone.filter.whitelist";
    private static final String NBT_LABEL = "gui.oritechaddonsone.filter.nbt";
    private static final String COMPONENTS_LABEL = "gui.oritechaddonsone.filter.components";
    private static final String WHITELIST_TOOLTIP = "gui.oritechaddonsone.filter.tooltip.whitelist";
    private static final String BLACKLIST_TOOLTIP = "gui.oritechaddonsone.filter.tooltip.blacklist";
    private static final String NBT_TOOLTIP = "gui.oritechaddonsone.filter.tooltip.nbt";
    private static final String NO_NBT_TOOLTIP = "gui.oritechaddonsone.filter.tooltip.no_nbt";
    private static final String COMPONENTS_TOOLTIP = "gui.oritechaddonsone.filter.tooltip.components";
    private static final String NO_COMPONENTS_TOOLTIP = "gui.oritechaddonsone.filter.tooltip.no_components";
    private static final String INPUT_LABEL = "gui.oritechaddonsone.filter.direction.input";
    private static final String OUTPUT_LABEL = "gui.oritechaddonsone.filter.direction.output";

    /** The 白名单 switch; null only between a rebuild and the first frame, which nothing reads. */
    private ToggleWidget whitelistButton;
    /** The NBT switch, which decides whether custom data has to match as well. */
    private ToggleWidget nbtButton;
    /** The 组件 switch, which brings NBT with it when it is turned on. */
    private ToggleWidget componentButton;
    /** The two buttons of the direction row, in the order they are drawn; kept so the tick can re-read them. */
    private final List<DirectionButton> directionButtons = new ArrayList<>();

    /** The {@link FaceFilterMenu#revision()} this page has already sent, so it never sends the same edit twice. */
    private long lastSent;

    public FaceFilterScreen(FaceFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT, OritechMachineScreen.BACKGROUND_TALL);
    }

    @Override
    protected void buildComponents() {
        this.directionButtons.clear();
        this.addFilterGrid();
        this.addToggleButtons();
        this.addDirectionRow();
        this.addPlayerInventorySlots();
        this.updateButtons();
        // a rebuild (a resize) re-reads the filter; nothing has changed, so nothing is sent
        this.lastSent = this.menu.revision();
    }

    /**
     * One tick of the open page: the widgets are ticked, the switches are re-read from the filter and - the reason
     * this method exists at all - the filter is <b>sent when it changed</b>.
     * <p>
     * Watching {@link FaceFilterMenu#revision()} instead of sending from the click handlers is what makes the
     * shift-click path work: registering an item by shift-clicking happens inside
     * {@link FaceFilterMenu#quickMoveStack}, with no screen code involved, and a screen that only sent on its own
     * clicks would silently drop exactly that edit.
     */
    @Override
    protected void containerTick() {
        super.containerTick();

        for (UIComponent component : this.components) {
            component.tick();
        }

        this.updateButtons();

        if (this.menu.revision() == this.lastSent) return;

        this.lastSent = this.menu.revision();
        var cell = this.menu.cell();
        FilterNetworking.sendSetFilter(this.menu.pos(), this.menu.model(), this.menu.face(),
                cell == null ? Vec3i.ZERO : cell, this.menu.flow(), this.menu.data());
    }

    /**
     * Re-reads the three switches from the filter on screen, exactly like Oritech's own {@code updateButtons()}
     * does on every tick.
     * <p>
     * The <b>tooltip</b> is what tells the player what a switch does: the same switch means 白名单 on one filter and
     * 黑名单 on another, so the label alone is ambiguous and the tooltip is the answer. The pressed state is
     * re-read as well, which is the one thing this page does that Oritech's cannot: a filter the server
     * <b>refused</b> comes back through {@link #applyAuthoritative(ItemFilterData)}, and the switch has to fall back
     * with it instead of keeping a state the server never stored.
     */
    private void updateButtons() {
        var data = this.menu.data();

        this.whitelistButton.setValue(data.useWhitelist());
        this.whitelistButton.withTooltip(Component.translatable(
                data.useWhitelist() ? WHITELIST_TOOLTIP : BLACKLIST_TOOLTIP));

        this.nbtButton.setValue(data.useNbt());
        this.nbtButton.withTooltip(Component.translatable(data.useNbt() ? NBT_TOOLTIP : NO_NBT_TOOLTIP));

        this.componentButton.setValue(data.useComponents());
        this.componentButton.withTooltip(Component.translatable(
                data.useComponents() ? COMPONENTS_TOOLTIP : NO_COMPONENTS_TOOLTIP));

        for (var button : this.directionButtons) {
            button.setValue(this.menu.flow() == button.flow);
        }
    }

    /**
     * The 4x3 grid of listed items, in Oritech's own coordinates, moved down by the direction row's band.
     * <p>
     * Every cell is <b>two</b> widgets: the empty frame Oritech draws under every cell
     * ({@link ItemSlotWidget}) and the cell itself, one pixel inside it and one z-index above it, so the listed item
     * is drawn where a slot's item would be - and the click that reaches the cell is the click that lists something
     * there.
     */
    private void addFilterGrid() {
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int index = row * GRID_COLUMNS + column;
                int x = GRID_X + column * GRID_PITCH;
                int y = GRID_Y + row * GRID_PITCH;

                this.addComponent(new ItemSlotWidget(x, y));
                this.addComponent(new FilterSlotWidget(x - 1, y - 1, index));
            }
        }
    }

    /**
     * The two direction buttons, and nothing else: which of the face's two filters this page edits.
     * <p>
     * Clicking one only moves the page ({@link FaceFilterMenu#setFlow}); it never sends, because the direction is
     * not something the server stores with the filter - it is part of what an edit names, and an edit is only sent
     * once there is one.
     */
    private void addDirectionRow() {
        var input = new DirectionButton(DIRECTION_X - INPUT_NUDGE, TransferMode.INPUT, INPUT_LABEL);
        var output = new DirectionButton(DIRECTION_X + DIRECTION_WIDTH + DIRECTION_GAP, TransferMode.OUTPUT,
                OUTPUT_LABEL);

        this.directionButtons.add(input);
        this.directionButtons.add(output);
        this.addComponent(input);
        this.addComponent(output);
    }

    /**
     * The three switches, in Oritech's column and with Oritech's labels, moved down by the band.
     * <p>
     * They are built from the filter that is on screen right now, so a page that was reskinned (a resized window
     * rebuilds everything) starts on the real answers instead of the defaults.
     */
    private void addToggleButtons() {
        var data = this.menu.data();

        this.whitelistButton = ToggleWidget.of(TOGGLE_X, TOGGLE_Y, Component.translatable(WHITELIST_LABEL),
                data.useWhitelist(), (button, state) -> this.menu.setData(this.menu.data().withWhitelist(state)))
                        .withTextColor(LabelWidget.DARK_TEXT);
        this.nbtButton = ToggleWidget.of(TOGGLE_X, TOGGLE_Y + TOGGLE_PITCH, Component.translatable(NBT_LABEL),
                data.useNbt(), (button, state) -> this.menu.setData(this.menu.data().withNbt(state)))
                        .withTextColor(LabelWidget.DARK_TEXT);
        // the 组件 switch is the one that is not independent: turning it on brings NBT with it, because a
        // component match that skipped the custom data would be the weaker of the two checks. That rule lives in
        // ItemFilterData#withComponents and is deliberately not repeated here.
        this.componentButton = ToggleWidget.of(TOGGLE_X, TOGGLE_Y + 2 * TOGGLE_PITCH,
                Component.translatable(COMPONENTS_LABEL), data.useComponents(),
                (button, state) -> this.menu.setData(this.menu.data().withComponents(state)))
                        .withTextColor(LabelWidget.DARK_TEXT);

        this.addComponent(this.whitelistButton);
        this.addComponent(this.nbtButton);
        this.addComponent(this.componentButton);
    }

    /**
     * The frames of the player's own inventory, and nothing else.
     * <p>
     * Their positions are <b>read out of the menu's slots</b> instead of being written down again, because vanilla
     * draws an item at its slot's own coordinates: a frame this page placed somewhere else would show a perfectly
     * drawn empty grid with the items hovering twenty pixels above it. The menu is the only thing that decides
     * where an item of the player's sits, so it is the only thing that may decide where its frame goes.
     */
    private void addPlayerInventorySlots() {
        for (var slot : this.menu.slots) {
            this.addComponent(new ItemSlotWidget(slot.x, slot.y));
        }
    }

    /**
     * Hands the page the filter the server really holds, which is what the answer to every edit is.
     * <p>
     * It only has to write the menu: every widget on this page reads the filter out of the menu on every frame, so
     * there is no cached copy of the filter anywhere that could go stale. The send counter is moved along with it,
     * because the server's answer is not an edit - sending it straight back would be a packet that asks the server
     * for the thing it just told us.
     */
    public void applyAuthoritative(ItemFilterData data) {
        if (data == null) return;

        this.menu.setData(data);
        this.lastSent = this.menu.revision();
    }

    /**
     * The same answer, for the filter of a named direction of the movement.
     * <p>
     * A player can switch the direction while an answer is on its way, and such an answer belongs to the filter
     * that is no longer on screen - writing it over the one that is would corrupt a filter nobody edited. So it is
     * written to its <b>own</b> direction and the page stays where the player left it, which is the only case where
     * the two methods differ.
     */
    public void applyAuthoritative(TransferMode flow, ItemFilterData data) {
        if (data == null) return;

        var editing = this.menu.flow();
        if (flow == editing || flow == null) {
            this.applyAuthoritative(data);
            return;
        }

        this.menu.setFlow(flow);
        this.menu.setData(data);
        this.menu.setFlow(editing);
        // the same answer as above: the server's answer is not an edit, so it must not be sent back
        this.lastSent = this.menu.revision();
    }

    /**
     * The block the page was opened on, which is what the title widget above the panel draws and what the
     * narration reads - the container itself is nameless on purpose (see {@link FaceFilterMenu#provider}).
     */
    @Override
    public Component getTitle() {
        return this.getTitleState().getBlock().getName();
    }

    /**
     * The block state of the block the menu's position names, i.e. the machine part this page filters - or air
     * while the block is gone, so that a title widget can still be built.
     */
    @Override
    public BlockState getTitleState() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(this.menu.pos()) instanceof ExtensionAddonBlockEntity addon
                ? addon.getBlockState()
                : Blocks.AIR.defaultBlockState();
    }

    /** What one grid cell lists right now, or an empty stack while it lists nothing. */
    private ItemStack displayedStack(int index) {
        return this.menu.data().slot(index);
    }

    /**
     * One cell of the grid: the frame is a separate widget under it, and this is the cell itself.
     * <p>
     * A click puts <b>the stack on the cursor</b> into the cell, or clears the cell when the cursor is empty -
     * Oritech's own answer, and the reason a filter can be emptied again without a second control. The click is
     * always consumed, also for the clearing one: the cell is not a vanilla slot, so a click that fell through
     * would reach the screen behind the page with the player's cursor stack on it.
     */
    private final class FilterSlotWidget extends UIComponent {

        /** The cell of the grid this widget is; the index is what the filter and the packet carry. */
        private final int index;

        private FilterSlotWidget(int x, int y, int index) {
            super(x, y, SLOT_SIZE, SLOT_SIZE);
            this.index = index;
            // above the empty frame it is drawn on, and below the title widget
            this.zIndex = 1;
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            var stack = displayedStack(this.index);
            if (stack.isEmpty()) return;

            graphics.renderItem(stack, this.x + 1, this.y + 1);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, this.x + 1, this.y + 1);
        }

        @Override
        public boolean handleClick(double mouseX, double mouseY, int button) {
            // an empty cursor stack clears the cell, anything else lists it as a single item
            var menu = FaceFilterScreen.this.menu;
            menu.setData(menu.data().withItem(this.index, menu.getCarried()));
            return true;
        }

        @Override
        public boolean hasTooltip() {
            return !displayedStack(this.index).isEmpty();
        }

        @Override
        public List<Component> getTooltip() {
            var stack = displayedStack(this.index);
            return stack.isEmpty() ? super.getTooltip() : Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        }
    }

    /**
     * One button of the direction row: <b>输入</b> and <b>输出</b>, the two filters one face of this mod carries.
     * <p>
     * It is a {@link ToggleWidget} because that is the control the rest of this page - and the rest of Oritech's
     * machine pages - are built from, and a row of them reads as part of the machine rather than as a foreign
     * piece of menu. It overrides the click instead of using the toggle's own, because here the "new value" is not
     * a new setting: the button does not turn a filter into anything, it moves the page onto the other filter, and
     * the pressed state is re-read from {@link FaceFilterMenu#flow()} every tick so it can never show a direction
     * the page is not on.
     */
    private final class DirectionButton extends ToggleWidget {

        /** Which of the face's two filters this button moves the page onto. */
        private final TransferMode flow;

        private DirectionButton(int x, TransferMode flow, String label) {
            // the value is set on every tick; the toggle's own click is overridden and never flips it
            super(x, DIRECTION_Y, DIRECTION_WIDTH, Component.translatable(label), false, (button, state) -> {
            });
            this.flow = flow;
            this.withTextColor(LabelWidget.DARK_TEXT);
        }

        @Override
        public boolean handleClick(double mouseX, double mouseY, int button) {
            // every click is consumed, even one on the direction the page is already on: these are controls of
            // this page, and a click that fell through would reach the panel behind it
            FaceFilterScreen.this.menu.setFlow(this.flow);
            return true;
        }
    }
}