package com.example.oritechaddonsone.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLEnvironment;

import com.example.oritechaddonsone.block.ExtensionPluginBlock;

/**
 * Block item of the Extension Plugins.
 * <p>
 * A block does not automatically contribute to the tooltip of its block item, so Oritech uses custom
 * item classes for its own blocks and this class does the same: it adds the Ctrl gated description of
 * {@link ExtensionPluginBlock#appendDetails} to {@link #appendHoverText}.
 * <p>
 * Just like Oritech's own addon items, only Oritech's "hold Ctrl for more information" line is shown
 * while Ctrl is not held.
 */
public class ExtensionPluginItem extends BlockItem {

    public ExtensionPluginItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        if (!(getBlock() instanceof ExtensionPluginBlock pluginBlock)) return;

        if (!isControlDown()) {
            tooltipComponents.add(Component.translatable("tooltip.oritech.item_extra_info")
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(ChatFormatting.ITALIC));
            return;
        }

        pluginBlock.appendDetails(tooltipComponents::add);
    }

    /** True while the player holds Ctrl, like Oritech's own addon items check. */
    private static boolean isControlDown() {
        // Tooltips are only built on the client; the guard keeps dedicated servers from touching
        // client only classes.
        if (!FMLEnvironment.dist.isClient()) return false;
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }
}
