package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import rearth.oritech.api.screen.widgets.BoxWidget;
import rearth.oritech.api.screen.widgets.ItemWidget;
import rearth.oritech.api.screen.widgets.LabelWidget;
import rearth.oritech.api.screen.widgets.ScrollWidget;
import rearth.oritech.api.screen.widgets.SurfaceWidget;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.client.ui.OritechWidgetScreen;
import rearth.oritech.client.ui.UpgradableOritechScreen;
import rearth.oritech.client.ui.UpgradableOritechScreenHandler;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;

/**
 * Lists the machine's wireless extension addons in the stock addon page (the "addons" button), where they
 * are missing without this.
 * <p>
 * Oritech builds that page by walking the machine's connected addons and rendering only those positions
 * whose block is a {@link MachineAddonBlock}. Our wired extension addons pass that check (they extend
 * Oritech's addon block), a wireless dock deliberately does not: it is a plain block that is not attached
 * to the machine, and making it an addon block would also make machines scan it like a real addon, which is
 * exactly what the link is meant to avoid. The stock page therefore skips a dock even though the machine
 * lists it (and the refinery, which gets its page from {@code OritechMachineScreenMixin}, shows it).
 * <p>
 * This mixin appends one row per dock below the rows Oritech rendered and grows the two things that are
 * sized from the running offset - the inset background and the scrollable content - by the same amount, so
 * the rows sit inside the panel instead of below it. Both hooks are {@link Redirect}s on those two calls:
 * the second one is also what hands us the scroll widget the rows are added to, without touching the
 * screen's local variables.
 */
@Mixin(UpgradableOritechScreen.class)
public abstract class UpgradableOritechScreenMixin {

    /** Height one row of the list takes: Oritech's 28 high row plus its separator. */
    @Unique
    private static final int oritechaddonsone$ROW_HEIGHT = 29;

    /** Docks this machine lists and that are readable on the client, in addon list order. */
    @Unique
    private List<BlockPos> oritechaddonsone$wirelessDocks() {
        // The config switch turns the whole feature off: both hooks size themselves from this list, so an
        // empty list leaves Oritech's page exactly as it is.
        if (!Config.showWirelessDocksInAddonPage()) return List.of();

        var menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (!(menu instanceof UpgradableOritechScreenHandler handler)) return List.of();

        var level = handler.worldAccess;
        var controller = handler.addonController;
        if (level == null || controller == null) return List.of();

        var docks = new ArrayList<BlockPos>();
        for (var addonPos : controller.getConnectedAddons()) {
            if (level.getBlockState(addonPos).getBlock() instanceof WirelessExtensionAddonBlock) {
                docks.add(addonPos);
            }
        }
        return docks;
    }

    /**
     * First row below everything Oritech renders. The stock loop advances its offset by one row height per
     * addon it renders and skips every position that is not a {@link MachineAddonBlock} - i.e. exactly our
     * docks - so counting the same way gives the first free row.
     */
    @Unique
    private int oritechaddonsone$firstFreeRow() {
        var menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (!(menu instanceof UpgradableOritechScreenHandler handler)) return 0;

        var level = handler.worldAccess;
        var controller = handler.addonController;
        if (level == null || controller == null) return 0;

        int rows = 0;
        for (var addonPos : controller.getConnectedAddons()) {
            if (level.getBlockState(addonPos).getBlock() instanceof MachineAddonBlock) rows++;
        }
        return rows * oritechaddonsone$ROW_HEIGHT;
    }

    /** Grows the inset background of the list by the rows this mixin appends. */
    @Redirect(method = "toggleAddonOverlay",
            at = @At(value = "NEW",
                    target = "(IIII)Lrearth/oritech/api/screen/widgets/SurfaceWidget;"))
    private SurfaceWidget oritechaddonsone$growAddonListBackground(int x, int y, int width, int height) {
        return new SurfaceWidget(x, y, width,
                height + oritechaddonsone$wirelessDocks().size() * oritechaddonsone$ROW_HEIGHT);
    }

    /**
     * Appends the dock rows to the stock list and grows its content so they can be scrolled to. Runs on the
     * call that sizes the content, which is the last thing Oritech does with the scroll widget and the only
     * place that hands us the widget itself.
     */
    @Redirect(method = "toggleAddonOverlay",
            at = @At(value = "INVOKE",
                    target = "Lrearth/oritech/api/screen/widgets/ScrollWidget;setContentDimensions(II)V"))
    private void oritechaddonsone$appendWirelessDocks(ScrollWidget scroll, int width, int height) {
        var menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        var level = menu instanceof UpgradableOritechScreenHandler handler ? handler.worldAccess : null;
        var docks = oritechaddonsone$wirelessDocks();

        if (level != null) {
            int y = oritechaddonsone$firstFreeRow();
            for (var dockPos : docks) {
                var dockState = level.getBlockState(dockPos);
                if (!(dockState.getBlock() instanceof WirelessExtensionAddonBlock)) continue;

                ItemWidget icon = new ItemWidget(3, y + 3, 20, new ItemStack(dockState.getBlock()));
                icon.withShowOverlay(false);
                icon.withTooltipFromStack(false);
                scroll.addChild(icon);
                scroll.addChild(new LabelWidget(28, y + 4, 140, 10, dockState.getBlock().getName()));

                y += 28;
                scroll.addChild(BoxWidget.filled(0, y, 192, 1, OritechWidgetScreen.SEPARATOR_COLOR));
                y += 1;
            }
        }

        scroll.setContentDimensions(width, height + docks.size() * oritechaddonsone$ROW_HEIGHT);
    }
}
