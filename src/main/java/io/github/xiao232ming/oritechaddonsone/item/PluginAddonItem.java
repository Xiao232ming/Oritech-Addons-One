package io.github.xiao232ming.oritechaddonsone.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.block.Block;

/**
 * Block item of the warehouse addon and the tank addon.
 * <p>
 * Both plugins are neutral Oritech addons: Oritech's own tooltip therefore has nothing to say about
 * them, and the effect they have is implemented by this mod ({@code MachineStorageBonuses}). The one
 * line that describes that effect is added here, with the language key the other items of this mod use:
 * {@code tooltip.oritechaddonsone.<block>.desc}.
 * <p>
 * The item is Ctrl gated exactly like every other Oritech plugin. Vanilla's {@code BlockItem} does not
 * forward to the block, so - exactly like Oritech's own block items ({@code BlockContent#AddBlockItems})
 * - the tooltip provider of the block is called from here: Oritech's
 * {@code MachineAddonBlock#addToTooltip} prints the description lines of the plugin while Ctrl is held
 * and otherwise only its "hold Ctrl for more information" hint. The line of this mod is appended next to
 * it while Ctrl is held.
 */
public class PluginAddonItem extends BlockItem {

    public PluginAddonItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);

        // Oritech's own plugin tooltip: the description while Ctrl is held, the "hold Ctrl" hint
        // otherwise. The hint is not repeated here, the block already prints it.
        if (getBlock() instanceof TooltipProvider provider) {
            provider.addToTooltip(context, tooltip, flag, stack);
        }

        if (!isControlDown()) return;

        var key = "tooltip.oritechaddonsone." + BuiltInRegistries.BLOCK.getKey(getBlock()).getPath();
        tooltip.accept(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** True while the player holds Ctrl, like Oritech's own addon items check. */
    private static boolean isControlDown() {
        var minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.hasControlDown();
    }
}
