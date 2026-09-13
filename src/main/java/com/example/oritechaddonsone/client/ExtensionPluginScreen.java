package com.example.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Inventory;

import com.example.oritechaddonsone.block.ExtensionPluginType;
import com.example.oritechaddonsone.menu.ExtensionPluginLayout;
import com.example.oritechaddonsone.menu.ExtensionPluginMenu;

/**
 * Screen of the Extension Plugins.
 * <p>
 * The panel is drawn procedurally instead of using a background texture, because the amount of plugin
 * slots is configurable (1-36): the layout is derived from the menu and extra rows are added as needed.
 * <p>
 * This class lives in a client only package and is only referenced from
 * {@link OritechAddonsOneClient}, so it is never loaded on a dedicated server.
 */
public class ExtensionPluginScreen extends AbstractContainerScreen<ExtensionPluginMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    /** Dark veil drawn over the type III slot hints so they read as a dim background icon. */
    private static final int HINT_VEIL = 0x99000000;

    private final ExtensionPluginLayout layout;

    public ExtensionPluginScreen(ExtensionPluginMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        this.imageWidth = ExtensionPluginLayout.WIDTH;
        this.imageHeight = layout.imageHeight();
        this.inventoryLabelY = this.imageHeight - ExtensionPluginLayout.LABEL_OFFSET;
    }

    /**
     * Draws the panel, all slot frames and the type III plugin hints.
     * <p>
     * This runs before the slots themselves are rendered, so the dim plugin hints of type III end up
     * behind any plugin that is actually inserted.
     * <p>
     * Note on the draw order: on 1.21.1 {@link GuiGraphics} collects its geometry per render type and
     * flushes those batches by render type instead of by call order, so a plain {@code fill} directly
     * after {@code renderItem} still ends up *below* the icon. The hint icons are therefore drawn
     * first and the batch is flushed ({@link GuiGraphics#flush()}) before the dark veils are added:
     * the veils then land in a later batch and cover the icons, while the real plugins rendered
     * afterwards still cover the veils.
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

        // plugin slots, and for type III a dim icon of the plugin that belongs into that slot
        var fixedSlots = menu.pluginType() == ExtensionPluginType.TYPE_3
                ? ExtensionPluginType.fixedSlotOrder()
                : java.util.List.<Block>of();

        for (int slot = 0; slot < layout.slots(); slot++) {
            int slotX = xo + layout.slotX(slot);
            int slotY = yo + layout.slotY(slot);
            drawSlot(graphics, slotX - 1, slotY - 1);

            if (slot < fixedSlots.size()) {
                graphics.renderItem(new ItemStack(fixedSlots.get(slot)), slotX, slotY);
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

        if (fixedSlots.isEmpty()) return;

        // see the note above: flush the icons first, then dim them with the veil
        graphics.flush();
        for (int slot = 0; slot < layout.slots() && slot < fixedSlots.size(); slot++) {
            int slotX = xo + layout.slotX(slot);
            int slotY = yo + layout.slotY(slot);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, HINT_VEIL);
        }
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
