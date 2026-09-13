package com.example.oritechaddonsone.item;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.block.Block;

/**
 * Block item of the Extension Plugins.
 * <p>
 * A block that implements {@link TooltipProvider} does not automatically contribute to the tooltip of
 * its block item - vanilla's {@code BlockItem} has no such bridge. Oritech solves this with the same
 * custom item for its own blocks, and this class does the same, so
 * {@link com.example.oritechaddonsone.block.ExtensionPluginBlock#addToTooltip} actually runs when the
 * item is hovered.
 */
public class ExtensionPluginItem extends BlockItem {

    public ExtensionPluginItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);

        if (getBlock() instanceof TooltipProvider provider) {
            provider.addToTooltip(context, tooltip, flag, stack);
        }
    }
}
