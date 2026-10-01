package io.github.xiao232ming.oritechaddonsone.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.api.screen.widgets.LabelWidget;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusDisplay;

/**
 * The two storage lines of the machine addon panel.
 * <p>
 * They are the panel counterpart of the warehouse / tank addons: the item line shows how much stack
 * capacity those plugins add to every slot of the machine, the fluid line how much capacity they add to
 * every tank. Both are rendered like Oritech's own stat lines (emoji plus value, centred, with a tooltip)
 * and each only appears while it really is greater than zero, so a machine without such a plugin looks
 * exactly as it did before.
 * <p>
 * The numbers come from the machine's synced display fields
 * ({@link StorageBonusDisplay}, written on the server by {@code MachineStorageBonuses}), never from the
 * local storages: the client does not compute the bonus, it only shows what the server published.
 * <p>
 * Both panel paths this mod extends use this helper: the panel that {@code OritechMachineScreenMixin}
 * adds to the machines without an addon page of their own, and the one Oritech's upgradable screen
 * builds, which {@code UpgradableOritechScreenMixin} extends.
 */
public final class StorageBonusPanel {

    /** Label and tooltip of the added item slot capacity. */
    public static final String ITEM_SLOTS_TITLE = "title.oritechaddonsone.machine.storage_item_slots";
    public static final String ITEM_SLOTS_TOOLTIP = "tooltip.oritechaddonsone.machine.storage_item_slots";

    /** Label and tooltip of the added fluid capacity. */
    public static final String FLUID_CAPACITY_TITLE = "title.oritechaddonsone.machine.storage_fluid_capacity";
    public static final String FLUID_CAPACITY_TOOLTIP = "tooltip.oritechaddonsone.machine.storage_fluid_capacity";

    private StorageBonusPanel() {
    }

    /** Appends the lines of the given machine to the panel content, skipping everything that is zero. */
    public static void addLines(List<UIComponent> content, BlockEntity blockEntity) {
        if (!(blockEntity instanceof StorageBonusDisplay display)) return;

        int slots = display.oritechaddonsone$shownItemSlotBonus();
        if (slots > 0) {
            content.add(line(ITEM_SLOTS_TITLE, ITEM_SLOTS_TOOLTIP, slots));
        }

        long capacity = display.oritechaddonsone$shownFluidCapacityBonus();
        if (capacity > 0L) {
            content.add(line(FLUID_CAPACITY_TITLE, FLUID_CAPACITY_TOOLTIP, capacity));
        }
    }

    /** One stat line, built exactly like Oritech's own addon panel lines. */
    private static LabelWidget line(String titleKey, String tooltipKey, Object value) {
        var label = new LabelWidget(0, 0, 60, 10, Component.translatable(titleKey, value));
        label.withTooltip(Component.translatable(tooltipKey));
        label.withAlignment(LabelWidget.Alignment.CENTER);
        return label;
    }
}
