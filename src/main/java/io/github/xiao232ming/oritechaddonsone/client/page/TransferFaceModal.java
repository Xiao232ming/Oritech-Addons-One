package io.github.xiao232ming.oritechaddonsone.client.page;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jetbrains.annotations.Nullable;

import io.github.xiao232ming.oritechaddonsone.block.entity.TransferFaceModes;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferMode;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;

/**
 * The configuration page the transfer pages open on a click on a face: the same Oritech panel both use - the
 * block's item as its title icon, a prompt line, the three mode plates (输入/输出/输入
 * 输出) with the face's current one sunken, and the 自动化 checkbox.
 * <p>
 * The page itself is drawn by {@link TransferFaceRenderer}, which the cube net page calls directly as its own
 * picker. This class is what sits between that drawing and a page's model: it is the hit tests and the send path,
 * and the page that opens it supplies what the face does and where a change goes ({@link Current}, {@link Sink}) -
 * the cube net page reads its settings from the menu, the 传输插件 page from the map the server sent, so this class
 * can serve both without knowing either model.
 * <p>
 * What a click does is the cube net page's behaviour: a click on a plate sets that mode and leaves the page open,
 * so the plate turns dark and the player can pick another one; the automation checkbox only toggles and keeps the
 * direction; the right mouse button and any click outside a control close the page. A face the plugin itself
 * stands on is not configurable, and the page says so with the same language key the net page uses.
 */
public final class TransferFaceModal {

    private TransferFaceModal() {
    }

    /**
     * Draws the configuration page of one face over the page's panel.
     * <p>
     * <b>The caller owns the data.</b> This modal is shared by the two transfer pages, whose models are different -
     * the cube net page keys a setting by {@link Direction} on one block, the 传输插件 page by cell and direction on a
     * structure (see {@code CellFaceModes}) - so it neither reads nor writes a model itself. It is told which face it
     * shows and what that face currently does ({@link Current}).
     *
     * @param occupied true while the plugin itself stands on this face, which makes it unconfigurable: the plates
     *                 are then dimmed and refuse the click, and the prompt explains why
     */
    public static void render(AddonPageContext context, GuiGraphicsExtractor graphics, Direction face, Current current,
            boolean occupied, double mouseX, double mouseY) {
        TransferFaceRenderer.render(context, graphics, current.mode(), current.automation(), occupied, mouseX, mouseY);
    }

    /**
     * A click while the configuration page is open.
     *
     * @return true while the click belonged to the page, so the page behind it never sees it; false while the
     *         page is not open at all
     */
    public static boolean mouseClicked(AddonPageContext context, Direction face, Current current, Sink sink,
            boolean occupied, double mouseX, double mouseY, int button) {
        return mouseClicked(context, face, current, sink, occupied, mouseX, mouseY, button, (f) -> {
        });
    }

    /**
     * A click while the configuration page is open, with the way to open that face's 过滤 page.
     *
     * @see #mouseClicked(AddonPageContext, Direction, Current, Sink, boolean, double, double, int)
     */
    public static boolean mouseClicked(AddonPageContext context, Direction face, Current current, Sink sink,
            boolean occupied, double mouseX, double mouseY, int button, FilterOpener filter) {
        if (button == 1) {
            TransferPickerState.close();
            return true;
        }

        var placed = AddonPickerPanel.place(context);
        if (!occupied) {
            for (int index = 0; index < TransferFaceStyle.MODES.size(); index++) {
                if (!TransferFaceRenderer.isOverPlate(placed, index, mouseX, mouseY)) continue;

                var mode = TransferFaceStyle.MODES.get(index);
                // the plate of the mode this face already has is disabled: clicking it again does nothing, so
                // the page neither closes nor repeats a mode the server already has
                if (mode == current.mode()) return true;

                // picking a direction keeps the automation switch of the face as it is
                var automation = current.automation();
                sink.send(face, mode, automation);
                return true;
            }

            if (TransferFaceRenderer.isOverAutomation(placed, mouseX, mouseY)) {
                if (current.mode() == TransferMode.NONE) return true;

                sink.send(face, current.mode(), !current.automation());
                return true;
            }

            // The 过滤 button: it opens a page of its own, so the click is taken and the modal is left open -
            // the server replaces this screen with the filter page, and a click that misses it closes the modal
            // like any other click outside a control.
            if (TransferFaceRenderer.isOverFilter(placed, mouseX, mouseY)) {
                filter.open(face);
                return true;
            }
        }

        // anything else - the panel's own background, the prompt, the icon, or the page outside the modal -
        // closes it, like a click outside a modal
        TransferPickerState.close();
        return true;
    }

    /**
     * What the face the modal configures does right now, as the page that opened it reports it: the mode, the
     * automation flag, and where the change is written to.
     */
    public interface Current {
        TransferMode mode();

        boolean automation();
    }

    /** Where a change the player makes in the modal is sent. */
    public interface Sink {
        void send(Direction face, TransferMode mode, boolean automation);
    }

    /**
     * Where the 过滤 button of the modal sends its click, i.e. how a page asks the server to open the filter
     * page of one face.
     * <p>
     * It is its own single-method interface and not a second method on {@link Sink} so that every {@code Sink}
     * stays a lambda: the two pages build those as one expression each, and a second abstract method would
     * turn every one of them into an anonymous class. What each page has to add is exactly what its own model
     * needs - the cube net page sends a face, the 传输插件 page a face <b>and the cell it belongs to</b> - which
     * is the whole reason this is told apart from the mode sink.
     */
    @FunctionalInterface
    public interface FilterOpener {
        void open(Direction face);
    }

    /**
     * The {@link Current} of a page that keys its settings by direction and has them only in the menu - the cube net
     * page, whose model is one block's six faces.
     */
    public static Current current(ExtensionAddonMenu menu, Direction face) {
        return new Current() {
            @Override
            public TransferMode mode() {
                return menu.transferMode(face);
            }

            @Override
            public boolean automation() {
                return menu.transferAutomation(face);
            }
        };
    }

    /**
     * The mode a face has right now, including what the open page set a moment ago - the pending value first, so
     * a plate turns dark in the same frame as its click instead of a tick later.
     */
    public static TransferMode modeOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.mode() : menu.transferMode(face);
    }

    /** True while a face moves its items by itself, including what the open page set a moment ago. */
    public static boolean automationOf(ExtensionAddonMenu menu, Direction face) {
        var pending = pending(menu, face);
        return pending != null ? pending.automation() : menu.transferAutomation(face);
    }

    /** What the open configuration page of this face set a moment ago, or {@code null} while it is closed. */
    @Nullable
    private static TransferPickerState.Pending pending(ExtensionAddonMenu menu, Direction face) {
        var cell = TransferPickerState.openCell();
        if (cell == null) return null;

        return TransferPickerState.isOpen(menu.position(), cell, face)
                ? TransferPickerState.pending()
                : null;
    }

    /**
     * Clears what a face does, without opening the modal: the page's right click on a configured face. It is the
     * same packet a plate sends, with the "not set" mode and automation off, and it drops the pending value so
     * the face does not read as configured until the server has answered.
     */
    public static void clear(BlockPos pos, Direction face) {
        ClientPacketDistributor.sendToServer(new TransferNetworking.SetTransferMode(
                pos, ProxyNetworking.faceIndex(face), TransferFaceModes.pack(TransferMode.NONE, false)));
    }
}
