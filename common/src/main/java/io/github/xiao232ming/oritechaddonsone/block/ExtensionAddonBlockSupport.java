package io.github.xiao232ming.oritechaddonsone.block;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import io.github.xiao232ming.oritechaddonsone.Config;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;

/**
 * Code the wired and the wireless extension addon blocks have in common.
 * <p>
 * The two cannot share a superclass: the wired addon has to stay a {@code MachineAddonBlock} (it is a
 * real Oritech addon), while the wireless dock is a plain block on purpose (a machine must never claim
 * it). Everything that does not depend on that difference lives here, so both variants behave
 * identically: the redstone forwarding of a neighbour update, the comparator output and the drop of the
 * stored plugins when the block is mined. The Ctrl gated tooltip is shared by the two block items, see
 * {@link io.github.xiao232ming.oritechaddonsone.item.AddonTooltips}.
 */
final class ExtensionAddonBlockSupport {

    private ExtensionAddonBlockSupport() {
    }

    /**
     * Forwards a redstone change at an extension addon block to its block entity, like Oritech's own
     * control unit plugin does. Both block entities are {@link ExtensionAddonBlockEntity} subclasses, so
     * one lookup covers both block variants.
     */
    static void applySignalAt(Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity addon) {
            addon.applyRedstoneSignal(level.hasNeighborSignal(pos));
        }
    }

    /**
     * Comparator output of a stored control unit. Both block variants read the same block entity API,
     * so the two overrides share this one lookup.
     */
    static int analogOutput(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ExtensionAddonBlockEntity addon ? addon.redstoneOutput() : 0;
    }

    /**
     * Drops everything stored in the block and clears it. Both variants drop every slot - including slots
     * disabled by the config and the reserved item slot of the wireless page - in valid stack chunks, so
     * nothing is lost when the block is mined.
     */
    static void dropStored(Level level, BlockPos pos, ExtensionAddonBlockEntity blockEntity) {
        for (int slot = 0; slot < ExtensionAddonBlockEntity.STORAGE_SIZE; slot++) {
            var stack = blockEntity.getItem(slot);
            // Type III slots can hold far more than one stack, drop it in valid chunks.
            while (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
            }
        }
        blockEntity.clearContent();
    }

    /** The detail lines every extension addon type shares, keyed by its tooltip prefix. */
    static void appendTypeDetails(Consumer<Component> consumer, String key, ExtensionAddonType type) {
        consumer.accept(Component.translatable(key + ".insert").withStyle(ChatFormatting.GOLD));

        if (type == ExtensionAddonType.TYPE_1) {
            consumer.accept(Component.translatable(key + ".acceptor").withStyle(ChatFormatting.AQUA));
        } else if (type == ExtensionAddonType.TYPE_2) {
            consumer.accept(Component.translatable(key + ".special").withStyle(ChatFormatting.AQUA));
        } else {
            consumer.accept(Component.translatable(key + ".capacity", Config.slotCapacity(type))
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
