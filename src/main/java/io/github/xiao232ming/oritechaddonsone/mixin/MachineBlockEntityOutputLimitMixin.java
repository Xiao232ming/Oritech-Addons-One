package io.github.xiao232ming.oritechaddonsone.mixin;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.util.MachineAddonController;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;

/**
 * Raises the limit the <b>machine itself</b> applies to one of its own slots when it writes a product
 * into it, so the warehouse addon really raises what a machine can deposit and not only what a player
 * can put in.
 * <p>
 * {@code SimpleInventoryStorageMixin} raises what the storage accepts ({@code getSlotLimit},
 * {@code insertToSlot}, {@code setItem}) and {@code SlotMixin} raises what the machine screen accepts,
 * but the machine's own output path never asks either of them: {@code craftItem} writes the product
 * straight into the output stack, and the gate in front of it, {@code canOutputRecipe}, ends in
 * {@link MachineBlockEntity#canAddToSlot}, which compares against {@code ItemStack#getMaxStackSize()} -
 * the item's own maximum of 64. {@code getOutputInventory()} also hands that method a vanilla
 * {@code SimpleContainer} built from copies of the output slots, so the comparison cannot see the raised
 * limit of the storage behind it.
 * <p>
 * The result was a machine whose output slot reported 80 (storage and screen both did) while the machine
 * stopped producing at 64: with a 64 stack in the slot, {@code canAddToSlot} said no,
 * {@code canOutputRecipe} said no, and the machine sat idle next to an output slot that still had 16
 * items of room. Measured on a pulverizer with one warehouse addon, before this change:
 * {@code prefill=63 -> canOutputRecipe=true}, {@code prefill=64 -> canOutputRecipe=false}, even though
 * the same slot reported a limit of 80 through both the storage and the screen.
 * <p>
 * Only the one {@code getMaxStackSize()} call of that one comparison is redirected, and only while the
 * machine's own inventory carries a bonus: with no addon the original call is made unchanged, so an
 * unmodified machine behaves exactly as before. The replacement is the limit the storage itself reports
 * ({@code getSlotLimit}, i.e. {@code 64 + bonus}) and deliberately not the item's own maximum again,
 * because the storage is what the rest of the machine obeys and the two must not disagree - the
 * warehouse addon is documented as growing every item slot of the machine, output slots included, which
 * for the products a machine can make is 80, the same number the input slots get.
 */
@Mixin(MachineBlockEntity.class)
public abstract class MachineBlockEntityOutputLimitMixin {

    @Redirect(
            method = "canAddToSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int oritechaddonsone$useRaisedOutputLimit(ItemStack slot) {
        if (!(this instanceof MachineAddonController controller)) return slot.getMaxStackSize();

        var inventory = controller.getInventoryForAddon();
        if (!(inventory instanceof AddonStorageBonus storage)) return slot.getMaxStackSize();

        var bonus = storage.oritechaddonsone$slotBonus();
        return bonus > 0 ? 64 + bonus : slot.getMaxStackSize();
    }
}
