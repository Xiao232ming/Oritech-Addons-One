package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Inventory;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * Screen of the Extension Addons.
 * <p>
 * The panel is drawn procedurally instead of using a background texture, because the amount of plugin
 * slots is configurable (1-36): the layout is derived from the menu and extra rows are added as needed.
 * <p>
 * This class lives in a client only package and is only referenced from
 * {@link OritechAddonsOneClient}, so it is never loaded on a dedicated server.
 */
public class ExtensionAddonScreen extends AbstractContainerScreen<ExtensionAddonMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    /** Dark veil drawn over the type III slot hints so they read as a dim background icon. */
    private static final int HINT_VEIL = 0x99000000;
    /** z the GUI stores items at (see {@code GuiGraphics#renderItem}). */
    private static final float ITEM_Z = 150.0F;
    /** z of the pushed back type III hint icons: behind the veil, still behind real items. */
    private static final float HINT_ICON_Z = 50.0F;
    /** z of the veil: in front of the hint icons, behind real items and item decorations. */
    private static final int HINT_VEIL_Z = 60;

    private final ExtensionAddonLayout layout;

    public ExtensionAddonScreen(ExtensionAddonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        this.imageWidth = ExtensionAddonLayout.WIDTH;
        this.imageHeight = layout.imageHeight();
        this.inventoryLabelY = this.imageHeight - ExtensionAddonLayout.LABEL_OFFSET;
    }

    /**
     * Draws the panel, all slot frames and the type III plugin hints.
     * <p>
     * This runs before the slots themselves are rendered, so the dim plugin hints of type III end up
     * behind any plugin that is actually inserted.
     * <p>
     * Note on the layering: on 1.21.1 the GUI depth buffer decides what covers what. The background is
     * drawn at {@code z=0}, items at {@code z=150} (see {@code GuiGraphics#renderItem}), item count
     * decorations at {@code z=200} and tooltips at {@code z=400} - larger {@code z} is closer to the
     * viewer. A veil drawn at {@code z=0} therefore stays *behind* the hint icon and never dims it.
     * The hint icon is thus pushed back to {@code z=50} and the veil drawn at {@code z=60}: the veil
     * covers the icon, while a real plugin inserted later (z=150) still covers the veil.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int xo = this.leftPos;
        int yo = this.topPos;

        // classic container panel with a light top/left and dark bottom/right bevel
        graphics.fill(xo, yo, xo + this.imageWidth, yo + this.imageHeight, PANEL);
        graphics.fill(xo, yo, xo + this.imageWidth, yo + 2, PANEL_LIGHT);
        graphics.fill(xo, yo, xo + 2, yo + this.imageHeight, PANEL_LIGHT);
        graphics.fill(xo, yo + this.imageHeight - 2, xo + this.imageWidth, yo + this.imageHeight, PANEL_DARK);
        graphics.fill(xo + this.imageWidth - 2, yo, xo + this.imageWidth, yo + this.imageHeight, PANEL_DARK);

        // plugin slots, and for type III the icon of the plugin that belongs into that slot (the grid has
        // one column per category and one row per tier, so every slot may have its own icon)
        var hints = menu.pluginType() == ExtensionAddonType.TYPE_3
                ? ExtensionAddonType.type3Slots()
                : java.util.List.<ExtensionAddonType.Type3Slot>of();

        for (int slot = 0; slot < layout.slots(); slot++) {
            int slotX = xo + layout.slotX(slot);
            int slotY = yo + layout.slotY(slot);
            drawSlot(graphics, slotX - 1, slotY - 1);

            var hint = slot < hints.size() ? hints.get(slot).reference() : null;
            if (hint != null) {
                // 150 (the z items normally use) minus 100 -> the hint sits behind the veil below
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, HINT_ICON_Z - ITEM_Z);
                graphics.renderItem(new ItemStack(hint), slotX, slotY);
                graphics.pose().popPose();
            }
        }

        // player inventory (3 rows of 9) and the hotbar - these frames came from the background
        // texture before, so they have to be drawn here as well.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, xo + 7 + column * 18, yo + layout.playerRowsY() + row * 18 - 1);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, xo + 7 + column * 18, yo + layout.hotbarY() - 1);
        }

        if (hints.isEmpty()) return;

        // Draw the veils over the hint icons and flush right away.
        // GuiGraphics#flush() only ends the batch of the *last* render type used, and renderItem flushes
        // its own item batch while drawing, so without this flush the veils would stay in a pending
        // batch and end up somewhere else in the frame. The overlay render type has NO_DEPTH_TEST and
        // COLOR_WRITE, so it covers the icons (which are pushed back to z=50) and still lets the real
        // plugins rendered afterwards (z=150) draw over it.
        for (int slot = 0; slot < layout.slots() && slot < hints.size(); slot++) {
            if (hints.get(slot).reference() == null) continue;
            int slotX = xo + layout.slotX(slot);
            int slotY = yo + layout.slotY(slot);
            graphics.fill(RenderType.guiOverlay(), slotX, slotY, slotX + 16, slotY + 16, HINT_VEIL_Z, HINT_VEIL);
        }
        graphics.flush();
    }

    /** Recessed 18x18 slot frame, drawn like vanilla container backgrounds do. */
    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + 18, SLOT_DARK);
        graphics.fill(x, y + 17, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 17, y, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
    }
}
