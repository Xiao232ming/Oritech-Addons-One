package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.client.AddonPanelStyle;

/**
 * The plugin page: the slots an extension addon (or a wireless extension dock) holds its plugins in.
 * <p>
 * It is exactly the part of the GUI that this mod had before the page framework existed - one frame per
 * plugin slot, plus the dim plugin icons of type III - just moved out of the screen and reframed as a
 * page. The frames are painted where {@link AddonPageContext} puts the menu's slots, so the plugin grid
 * still lines up with the slots of {@code ExtensionAddonMenu} pixel for pixel.
 */
public final class PluginAddonPage implements AddonPage {

    /** Id of this page, also the suffix of its language keys. */
    public static final String ID = "plugins";

    /** Language key of the tab label, which is all the tab shows. */
    private static final String LABEL_KEY = "gui.oritechaddonsone.page." + ID;

    /**
     * Icon of the tab: Oritech's machine extender texture ({@code oritech:block/machine_extender}). That is
     * the "port" face of an extension dock - it is the front of the type I and II docks, and the type III
     * port texture of this mod ({@code extension_addon_3_port}) is a recolour of it - so a single icon
     * represents all six blocks.
     */
    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath("oritech", "textures/block/machine_extender.png");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component label() {
        return Component.translatable(LABEL_KEY);
    }

    @Override
    public Identifier icon() {
        return ICON;
    }

    /**
     * Draws the plugin grid.
     * <p>
     * This runs in the background layer, before the slots themselves are rendered, so the dim plugin
     * hints of type III end up behind any plugin that is actually inserted.
     */
    @Override
    public void render(AddonPageContext context, GuiGraphicsExtractor graphics, float partialTick) {
        var layout = context.layout();

        // one dedicated slot per stat plugin for type III, so every slot may have its own icon
        var fixedSlots = context.menu().pluginType() == ExtensionAddonType.TYPE_3
                ? ExtensionAddonType.fixedSlotOrder()
                : List.<Block>of();

        for (int slot = 0; slot < layout.slots(); slot++) {
            int slotX = context.slotX(slot);
            int slotY = context.slotY(slot);
            AddonPanelStyle.drawSlot(graphics, slotX - 1, slotY - 1);

            if (slot < fixedSlots.size()) {
                // drawn in the background layer, so a real plugin inserted later covers the hint
                graphics.item(new ItemStack(fixedSlots.get(slot)), slotX, slotY);
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, AddonPanelStyle.HINT_VEIL);
            }
        }
    }
}
