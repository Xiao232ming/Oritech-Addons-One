package io.github.xiao232ming.oritechaddonsone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.client.ui.OritechWidgetScreen;

import io.github.xiao232ming.oritechaddonsone.client.AddonOverlayHost;

/**
 * Opens the component list of every Oritech widget screen to the other client mixins of this mod
 * through the {@link AddonOverlayHost} duck interface. {@code addComponent} / {@code removeComponent}
 * are declared on this class, so they can be shadowed here; a mixin on a subclass (like the machine
 * screen mixin) could not resolve them.
 */
@Mixin(OritechWidgetScreen.class)
public abstract class OritechWidgetScreenMixin implements AddonOverlayHost {

    @Shadow
    protected abstract void addComponent(UIComponent component);

    @Shadow
    protected abstract void removeComponent(UIComponent component);

    @Override
    public void oritechaddonsone$addComponent(UIComponent component) {
        this.addComponent(component);
    }

    @Override
    public void oritechaddonsone$removeComponent(UIComponent component) {
        this.removeComponent(component);
    }
}
