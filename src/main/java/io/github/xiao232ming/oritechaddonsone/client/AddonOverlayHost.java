package io.github.xiao232ming.oritechaddonsone.client;

import rearth.oritech.api.screen.UIComponent;

/**
 * Duck interface installed on {@code OritechWidgetScreen} through {@code OritechWidgetScreenMixin},
 * which gives other client mixins of this mod access to the screen's component list. The screen's own
 * {@code addComponent} / {@code removeComponent} are protected, so they cannot be shadowed from a mixin
 * that targets a subclass (Mixin only resolves shadows against the target class itself) - but the
 * methods can be reached through this interface, because every screen instance has it after the
 * widget screen mixin is applied.
 */
public interface AddonOverlayHost {

    /** Adds a component to the screen (see {@code OritechWidgetScreen#addComponent}). */
    void oritechaddonsone$addComponent(UIComponent component);

    /** Removes a component from the screen (see {@code OritechWidgetScreen#removeComponent}). */
    void oritechaddonsone$removeComponent(UIComponent component);
}
