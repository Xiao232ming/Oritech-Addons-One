package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("oritech", "textures/block/machine_extender.png");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component label() {
        return Component.translatable(LABEL_KEY);
    }

    @Override
    public ResourceLocation icon() {
        return ICON;
    }

    /**
     * Draws the plugin grid.
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
    public void render(AddonPageContext context, GuiGraphics graphics, float partialTick) {
        var layout = context.layout();

        // one icon per slot for type III: the grid has one column per stat category and one row per tier,
        // so every slot may have its own icon
        var hints = context.menu().pluginType() == ExtensionAddonType.TYPE_3
                ? ExtensionAddonType.type3Slots()
                : List.<ExtensionAddonType.Type3Slot>of();

        for (int slot = 0; slot < layout.slots(); slot++) {
            int slotX = context.slotX(slot);
            int slotY = context.slotY(slot);
            AddonPanelStyle.drawSlot(graphics, slotX - 1, slotY - 1);

            var hint = slot < hints.size() ? hints.get(slot).reference() : null;
            if (hint != null) {
                // 150 (the z items normally use) minus 100 -> the hint sits behind the veil below
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, AddonPanelStyle.HINT_ICON_Z - AddonPanelStyle.ITEM_Z);
                graphics.renderItem(new ItemStack(hint), slotX, slotY);
                graphics.pose().popPose();
            }
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
            int slotX = context.slotX(slot);
            int slotY = context.slotY(slot);
            graphics.fill(RenderType.guiOverlay(), slotX, slotY, slotX + 16, slotY + 16,
                    AddonPanelStyle.HINT_VEIL_Z, AddonPanelStyle.HINT_VEIL);
        }
        graphics.flush();
    }
}
