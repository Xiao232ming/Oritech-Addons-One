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
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

/**
 * Block item of the warehouse addon, the tank addon and the chunk anchor.
 * <p>
 * All three are neutral Oritech addons: Oritech's own tooltip therefore has nothing to say about
 * them, and the effect they have is implemented by this mod ({@code MachineStorageBonuses} for the
 * storage plugins, {@code AnchorForceLoad} for the anchor). The one line that describes that effect is
 * added here, with the language key the other items of this mod use:
 * {@code tooltip.oritechaddonsone.<block>.desc}.
 * <p>
 * The item is Ctrl gated exactly like every other Oritech plugin. NeoForge's {@code BlockItem}
 * forwards {@link #appendHoverText} to the block, and Oritech's {@code MachineAddonBlock#appendHoverText}
 * prints the description lines of the plugin while Ctrl is held and otherwise only its
 * "hold Ctrl for more information" hint - so the base call alone is the whole gate, and the one line of
 * this mod is appended next to it while Ctrl is held.
 * <p>
 * The value of that line is an argument of the language key and is coloured here, not in the language
 * file: Oritech prints the changed stat of its own plugins as a green component
 * ({@code TooltipHelper#getFormattedValueChangeTooltip} builds the green literal appended by
 * {@code MachineAddonBlock#appendHoverText} on 1.21.1 and by {@code #addToTooltip} on 26.1.2), and
 * {@code TooltipHelper#addMachineTooltip} colours the amount of a stat line by passing it as a styled
 * translation argument - the same way this line does it.
 * <p>
 * A plugin without a number - the anchor - is registered through the two argument constructor: its line
 * then has no green argument at all and its language key has no placeholder.
 */
public class PluginAddonItem extends BlockItem {

    /**
     * The value the description line shows, e.g. {@code +16} or {@code +8000 mB}, or {@code null} for a
     * plugin that changes no stat. The registration passes it in, built from the very constant the machine
     * effect uses, so the tooltip cannot drift away from what the plugin really adds.
     */
    @Nullable
    private final String bonus;

    /** Item of a plugin that shows a number, e.g. the warehouse addon. */
    public PluginAddonItem(Block block, Properties properties, String bonus) {
        super(block, properties);
        this.bonus = bonus;
    }

    /** Item of a plugin without a number, e.g. the chunk anchor. */
    public PluginAddonItem(Block block, Properties properties) {
        this(block, properties, null);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        // Oritech's own plugin tooltip: the description while Ctrl is held, the "hold Ctrl" hint
        // otherwise. The hint is not repeated here, the block already prints it.
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        if (!isControlDown()) return;

        var key = "tooltip.oritechaddonsone." + BuiltInRegistries.BLOCK.getKey(getBlock()).getPath();

        if (bonus == null) {
            // A plugin without a number: the language key is a plain sentence.
            tooltipComponents.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
            return;
        }

        // The number is the %s of the language key and stays green: a styled argument keeps its own
        // colour over the grey of the surrounding line, exactly like Oritech's own coloured numbers.
        tooltipComponents.add(Component.translatable(key + ".desc",
                Component.literal(bonus).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));
    }

    /** True while the player holds Ctrl, like Oritech's own addon items check. */
    private static boolean isControlDown() {
        // Tooltips are only built on the client; the guard keeps dedicated servers from touching
        // client only classes.
        if (!FMLEnvironment.dist.isClient()) return false;
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }
}
