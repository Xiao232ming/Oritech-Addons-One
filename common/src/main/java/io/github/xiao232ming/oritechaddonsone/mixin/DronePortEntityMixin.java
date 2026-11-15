package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import dev.architectury.fluid.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.api.item.containers.SimpleInventoryStorage;
import rearth.oritech.block.entity.interaction.DronePortEntity;

/**
 * Keeps the items of a drone package from being duplicated when the receiving drone port cannot hold the
 * whole package. The trigger is a source port with a warehouse addon (80 items per slot, 15 slots) sending
 * to a port without one (64 per slot): the package is larger than the receiver, and Oritech's own checks
 * only ask whether the receiver is empty, never whether the package fits.
 * <p>
 * {@code canAcceptPayload} is where that is answered, so the first hook refuses a package that the
 * receiver cannot hold in full. Without it the sender would create a package that can never be delivered
 * in one go, and Oritech's {@code tryReceivePacket} below has no transaction to undo a partial insert: it
 * inserts stack by stack, notices the totals do not match, logs a warning and returns - the inserted items
 * stay, the whole package stays, and the port retries every 20 ticks. A pipe that drains the port between
 * two attempts therefore turns the same package into items over and over, which is the duplication this
 * mod is fixing.
 * <p>
 * The second hook is the safety net for a package that still arrives too large - the receiving port can
 * lose its warehouse addon while the drone is in the air, so the sender's check cannot know. It records
 * the port's item count around the receive and subtracts everything that really arrived (the difference)
 * from the package, so a partial delivery is delivered exactly once and the rest is retried later. The
 * package is walked in list order because {@code SimpleInventoryStorage#insert} fills the package stacks
 * in that same order: a stack only stays partially inserted when its item has no room left anywhere,
 * which also means every later stack had no room.
 * <p>
 * Both hooks also clear {@code receivingPackage}. Oritech sets it to {@code true} for the duration of the
 * receive and only clears it on success, so a failed receive would leave the port accepting outside
 * inserts (that flag is what {@code DronePortItemInventory#insertToSlot} checks) while a package is still
 * pending - the opposite of what it is for.
 */
@Mixin(DronePortEntity.class)
public abstract class DronePortEntityMixin {

    /** Guard of Oritech's item inventory: while a package is pending, outside inserts are refused. */
    @Shadow
    private boolean receivingPackage;

    /** Item count of the port before the current receive attempt, {@code -1} when not receiving. */
    @Unique
    private long oritechaddonsone$itemsBeforeReceive = -1L;

    /**
     * Refuses a package the receiving port cannot hold in full.
     * <p>
     * The check simulates the very insert the port would do later ({@code insert(..., true)}), so it sees
     * exactly the slot limits the machine reports - including the bonus of a warehouse addon, which is
     * applied to {@code getSlotLimit} by {@code SimpleInventoryStorageMixin}. Oritech only looks at the
     * items when the package is not empty, which is why the empty case is left to the original method.
     */
    @Inject(method = "canAcceptPayload", at = @At("HEAD"), cancellable = true)
    private void oritechaddonsone$requireRoomForWholePackage(List<ItemStack> stacks, FluidStack fluid,
            CallbackInfoReturnable<Boolean> callback) {
        var port = (DronePortEntity) (Object) this;
        var total = 0;

        for (var stack : stacks) {
            if (!stack.isEmpty()) total += stack.getCount();
        }

        if (total == 0) return;

        var inventory = (SimpleInventoryStorage) port.getInventoryForAddon();
        var fits = 0;

        for (var stack : stacks) {
            if (stack.isEmpty()) continue;
            fits += inventory.insert(stack, true);
        }

        if (fits < total) callback.setReturnValue(false);
    }

    @Inject(method = "tryReceivePacket", at = @At("HEAD"))
    private void oritechaddonsone$countItemsBeforeReceive(CallbackInfo callback) {
        this.oritechaddonsone$itemsBeforeReceive = oritechaddonsone$itemCount((DronePortEntity) (Object) this);
    }

    @Inject(method = "tryReceivePacket", at = @At("RETURN"))
    private void oritechaddonsone$consumeDeliveredItems(CallbackInfo callback) {
        var before = this.oritechaddonsone$itemsBeforeReceive;
        this.oritechaddonsone$itemsBeforeReceive = -1L;

        var port = (DronePortEntity) (Object) this;
        var packet = port.getIncomingPacket();

        // a null package means Oritech inserted everything and consumed it; nothing to correct
        if (before < 0L || packet == null) return;

        this.receivingPackage = false;

        var delivered = oritechaddonsone$itemCount(port) - before;
        if (delivered <= 0L) return;

        for (var stack : packet.transferredStacks()) {
            if (delivered <= 0L) break;

            var taken = (int) Math.min(delivered, stack.getCount());
            stack.shrink(taken);
            delivered -= taken;
        }

        if (packet.transferredStacks().stream().allMatch(ItemStack::isEmpty)) {
            port.setIncomingPacket(null);
        }

        port.setChanged();
    }

    /** The number of items the port is holding, i.e. the sum of all its slots. */
    @Unique
    private static long oritechaddonsone$itemCount(DronePortEntity port) {
        var inventory = (SimpleInventoryStorage) port.getInventoryForAddon();
        var total = 0L;

        for (var index = 0; index < inventory.getContainerSize(); index++) {
            total += inventory.getItem(index).getCount();
        }

        return total;
    }
}
