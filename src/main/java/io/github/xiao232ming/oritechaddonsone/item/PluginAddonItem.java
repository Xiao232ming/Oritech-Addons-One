package io.github.xiao232ming.oritechaddonsone.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * Block item of the warehouse addon and the tank addon.
 * <p>
 * Both plugins are neutral Oritech addons: Oritech's own tooltip therefore has nothing to say about
 * them, and the effect they have is implemented by this mod ({@code MachineStorageBonuses}). The two
 * lines that describe that effect are added here, in the same way and with the same language keys the
 * other items of this mod use: {@code tooltip.oritechaddonsone.<block>.desc} for the effect of one
 * plugin and {@code ...<block>.stack} for the note that several of them add up.
 * <p>
 * The description is deliberately shown without holding Ctrl - it is the only place that states what
 * these two blocks do - and the block's own (empty) tooltip is not asked for, because it would only add
 * Oritech's "hold Ctrl" hint for information that is not there.
 */
public class PluginAddonItem extends BlockItem {

    public PluginAddonItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        var key = "tooltip.oritechaddonsone." + BuiltInRegistries.BLOCK.getKey(getBlock()).getPath();

        tooltipComponents.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(key + ".stack").withStyle(ChatFormatting.DARK_GRAY));
    }
}
