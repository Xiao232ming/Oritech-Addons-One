package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.HashSet;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.MachineAddonController.AddonBlock;

import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.wireless.AddonEnergyGuard;
import io.github.xiao232ming.oritechaddonsone.wireless.WirelessLinks;

/**
 * Makes the plugins of the wireless extension addons part of every addon recomputation of a machine.
 * <p>
 * Oritech recomputes a machine's addon data in {@code initAddons}: {@code gatherAddonStats} resets the
 * data to the stats of the machine's own addons, {@code writeAddons} lets those addons merge their own
 * plugins in and {@code updateEnergyContainer} then recalculates the energy container from that data -
 * including {@code energy = min(energy, capacity)}, which throws away everything above the capacity.
 * <p>
 * A wireless dock is not one of the machine's own addons, so this is where its plugins are added: at the
 * end of {@code gatherAddonStats}, i.e. exactly where a wired addon contributes and before the container
 * is recalculated, so a dock can neither drop stored energy nor see its stats counted twice. Every scan
 * passes through here once, which also makes several docks of one machine accumulate in a defined order
 * (each one merges on top of the previous one).
 * <p>
 * {@code initAddons} also rebuilds the machine's addon list ({@code getConnectedAddons}) from the blocks
 * it found around the machine, which drops every wireless dock from that list again. The list is synced
 * to the client when a GUI is opened and feeds both the addon overlay of a machine screen and the
 * redstone panel check - so the second hook puts the linked docks back after every recomputation and
 * pushes the result to the clients. This is what makes the docks of a machine visible in the refinery
 * UI (which got its own addon panel through {@code OritechMachineScreenMixin}) and keeps the addon list
 * of every other machine in sync with its wireless docks.
 */
@Mixin(MachineAddonController.class)
public interface MachineAddonControllerMixin {

    @Inject(method = "gatherAddonStats", at = @At("RETURN"))
    private void oritechaddonsone$applyWirelessDocks(List<AddonBlock> addons, CallbackInfo callback) {
        var controller = (MachineAddonController) (Object) this;
        var level = controller.getWorldForAddon();
        if (level == null || level.isClientSide()) return;

        WirelessExtensionAddonBlockEntity.applyAllDocks(level, controller.getPosForAddon());
    }

    /**
     * Remembers which addons of the machine cannot be read before this scan recomputes its addon data.
     * Has to run before {@code initAddons} clears {@code getConnectedAddons()}, because that list is
     * where the addons of an unloaded chunk are still known (see {@link AddonEnergyGuard}).
     */
    @Inject(method = "initAddons(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private void oritechaddonsone$trackUnreadableAddons(CallbackInfo callback) {
        AddonEnergyGuard.refresh((MachineAddonController) (Object) this);
    }

    /**
     * Keeps the machine's stored energy while its addon data is incomplete: {@code updateEnergyContainer}
     * ends in {@code energy = min(energy, capacity)}, and a capacity that is too small because an addon
     * could not be read would silently delete the energy the player stored with that addon.
     */
    @Inject(method = "updateEnergyContainer", at = @At("HEAD"))
    private void oritechaddonsone$captureStoredEnergy(CallbackInfo callback) {
        AddonEnergyGuard.capture((MachineAddonController) (Object) this);
    }

    @Inject(method = "updateEnergyContainer", at = @At("RETURN"))
    private void oritechaddonsone$keepStoredEnergy(CallbackInfo callback) {
        AddonEnergyGuard.restore((MachineAddonController) (Object) this);
    }

    /**
     * Puts the wireless docks back into the machine's addon list, which {@code initAddons} just rebuilt
     * from the blocks it found around the machine (a dock is not one of those). Without this the list a
     * client receives on GUI open would never contain a dock, so the addon overlay would not show it and
     * the redstone panel would not appear for a dock that stores a control unit.
     */
    @Inject(method = "initAddons(Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void oritechaddonsone$relistWirelessDocks(CallbackInfo callback) {
        var controller = (MachineAddonController) (Object) this;
        var level = controller.getWorldForAddon();
        if (level == null || level.isClientSide()) return;

        var machinePos = controller.getPosForAddon();
        var connected = controller.getConnectedAddons();

        // The runtime index first, then the machine's own addon list. The second source is what makes the
        // list survive the moments the index is cold: right after a world load the machine recomputes its
        // addons before its docks have announced themselves, and a dock whose chunk is unloaded never
        // announces itself at all. Without that fallback the docks would drop out of the list here, which
        // both loses their plugins from the speed / efficiency panel and lets the capacity fall back to the
        // default - the latter is what made a machine's stored energy look reset in an opened GUI.
        var docks = new HashSet<BlockPos>(WirelessLinks.docksOf(level, machinePos));
        docks.addAll(WirelessLinks.docksFromAddonList(level, machinePos, connected));

        var changed = false;
        for (var dockPos : docks) {
            if (connected.contains(dockPos)) continue;
            if (!(level.getBlockEntity(dockPos) instanceof WirelessExtensionAddonBlockEntity dock)) continue;
            if (!dock.isLinkedTo(machinePos)) continue;

            connected.add(dockPos.immutable());
            changed = true;
        }

        if (changed && controller instanceof NetworkedBlockEntity networked) {
            networked.sendUpdate(SyncType.GUI_OPEN);
        }
    }
}
