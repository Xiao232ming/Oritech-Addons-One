package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rearth.oritech.block.entity.interaction.DronePortEntity;

/**
 * Keeps the items of a drone package from being duplicated when the receiving drone port cannot hold the
 * whole package. The trigger is a source port with a warehouse addon (80 items per slot, 15 slots) sending
 * to a port without one (64 per slot): the package is larger than the receiver, and Oritech's own checks
 * only ask whether the receiver is empty, never whether the package fits.
 * <p>
 * {@code canAcceptPayload} is where that is answered, so the first hook refuses a package that the
 * receiver cannot hold in full. Without it the sender would create a package that can never be delivered:
 * {@code tryReceivePacket} requires every stack to be inserted at once, aborts the transaction when it is
 * not, and then retries every 20 ticks - forever, because nothing in the port ever shrinks the package.
 * The item would be locked in transit and, since 26.1.2 does not save the pending package, silently
 * deleted on the next world load.
 * <p>
 * The second hook is the safety net for a package that still arrives too large - the receiving port can
 * lose its warehouse addon while the drone is in the air, so the sender's check cannot know. Oritech's
 * 1.21.1 implementation of this method is not transactional and leaves the items it managed to insert
 * behind while retrying the whole package, which is the duplication this mod is fixing; 26.1.2 rolls the
 * transaction back instead, so the counter here only sees what really stayed in the inventory and the
 * package is left untouched. Either way the amount that actually arrived is subtracted from the package,
 * so a partial delivery is delivered exactly once and the rest is retried later.
 * <p>
 * Both hooks also clear {@code receivingPackage}. Oritech sets it to {@code true} for the duration of the
 * receive and only clears it on success, so a failed receive would leave the port accepting outside
 * inserts while a package is still pending - the opposite of what that flag is for.
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
     * The simulation runs in a transaction that is never committed, exactly like the fluid check Oritech
     * itself does in this method: closing the transaction without {@code commit()} reverts every insert,
     * so only the reported amounts are used. Oritech only reaches its own item check when the package is
     * not empty, which is why the empty case is left to the original method.
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

        var inventory = port.getInventoryForAddon();
        var fits = 0;

        try (var transaction = Transaction.openRoot()) {
            for (var stack : stacks) {
                if (stack.isEmpty()) continue;
                fits += inventory.insert(ItemResource.of(stack), stack.getCount(), transaction);
            }
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
        var inventory = port.getInventoryForAddon();
        var total = 0L;

        for (var index = 0; index < inventory.size(); index++) {
            total += inventory.getAmountAsLong(index);
        }

        return total;
    }
}
