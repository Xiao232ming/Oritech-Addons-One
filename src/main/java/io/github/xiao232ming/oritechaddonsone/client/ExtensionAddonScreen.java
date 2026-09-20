package io.github.xiao232ming.oritechaddonsone.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    /** Colour of the link line in the top right corner of the panel. */
    private static final int LINK_COLOR = 0xFF1E6B1E;
    /** Colour of that line while the addon is not linked to a machine. */
    private static final int UNLINKED_COLOR = 0xFF707070;

    private final ExtensionAddonLayout layout;

    public ExtensionAddonScreen(ExtensionAddonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ExtensionAddonLayout.WIDTH, menu.layout().imageHeight());
        this.layout = menu.layout();
        this.inventoryLabelY = this.imageHeight - ExtensionAddonLayout.LABEL_OFFSET;
    }

    /**
     * Adds the link line of the wireless addons to the top right corner of the panel: which machine this
     * dock is linked to and where that machine stands. The wired addons simply show that they are not
     * linked, so both variants keep the same layout.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        var text = linkText();
        var x = this.imageWidth - 8 - this.font.width(text.getString());
        graphics.text(this.font, text, x, 6,
                this.menu.linkedMachine() == null ? UNLINKED_COLOR : LINK_COLOR);
    }

    /** Text of the link line, either the linked machine with its coordinates or "not linked". */
    private Component linkText() {
        var machine = this.menu.linkedMachine();
        if (machine == null) {
            return Component.translatable("gui.oritechaddonsone.wireless.unlinked");
        }

        var nameKey = this.menu.linkedMachineNameKey();
        var name = nameKey == null
                ? Component.translatable("gui.oritechaddonsone.wireless.machine")
                : Component.translatable(nameKey);
        return Component.translatable("gui.oritechaddonsone.wireless.linked", name,
                machine.getX(), machine.getY(), machine.getZ());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;

        // classic container panel with a light top/left and dark bottom/right bevel
        graphics.fill(xo, yo, xo + this.imageWidth, yo + this.imageHeight, PANEL);
        graphics.fill(xo, yo, xo + this.imageWidth, yo + 2, PANEL_LIGHT);
        graphics.fill(xo, yo, xo + 2, yo + this.imageHeight, PANEL_LIGHT);
        graphics.fill(xo, yo + this.imageHeight - 2, xo + this.imageWidth, yo + this.imageHeight, PANEL_DARK);
        graphics.fill(xo + this.imageWidth - 2, yo, xo + this.imageWidth, yo + this.imageHeight, PANEL_DARK);

        // plugin slots, and for type III a dim icon of the plugin that belongs into that slot
        var fixedSlots = menu.pluginType() == ExtensionAddonType.TYPE_3
                ? ExtensionAddonType.fixedSlotOrder()
                : java.util.List.<Block>of();

        for (int slot = 0; slot < layout.slots(); slot++) {
            int slotX = xo + layout.slotX(slot);
            int slotY = yo + layout.slotY(slot);
            drawSlot(graphics, slotX - 1, slotY - 1);

            if (slot < fixedSlots.size()) {
                // drawn in the background layer, so a real plugin inserted later covers the hint
                graphics.item(new ItemStack(fixedSlots.get(slot)), slotX, slotY);
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, HINT_VEIL);
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
    }

    /** Recessed 18x18 slot frame, drawn like vanilla container backgrounds do. */
    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + 18, SLOT_DARK);
        graphics.fill(x, y + 17, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 17, y, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
    }
}
