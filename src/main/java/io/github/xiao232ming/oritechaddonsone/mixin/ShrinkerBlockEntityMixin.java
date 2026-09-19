package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import rearth.oritech.block.entity.interaction.ShrinkerBlockEntity;
import rearth.oritech.util.MachineAddonController;

/**
 * Keeps the addon splicer ({@code ShrinkerBlockEntity}, "插件绞接器") from using the Extension Addons.
 * <p>
 * Oritech's splicer collects the addons attached to it, builds a "shrunk addon" out of their stats and
 * then replaces every one of them with air. In 1.21.1 {@code MachineAddonController#initAddons} passes
 * the very same list to {@code gatherAddonStats} that it later uses to rebuild
 * {@code getConnectedAddons()} and the splicer's shrink loop iterates that field, so removing the
 * Extension Addons from the list here means they are neither consumed nor counted into the machine
 * core, while every other addon on the same splicer keeps working normally.
 */
@Mixin(ShrinkerBlockEntity.class)
public class ShrinkerBlockEntityMixin {

    @Inject(method = "gatherAddonStats", at = @At("HEAD"))
    private void oritechaddonsone$excludeExtensionAddons(List<MachineAddonController.AddonBlock> addons,
            CallbackInfo ci) {
        addons.removeIf(addon -> addon.addonBlock() instanceof ExtensionAddonBlock);
    }
}
