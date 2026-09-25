package io.github.xiao232ming.oritechaddonsone.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import rearth.oritech.api.screen.Insets;
import rearth.oritech.api.screen.OritechSurface;
import rearth.oritech.api.screen.UIComponent;
import rearth.oritech.api.screen.widgets.BlockPreviewWidget;
import rearth.oritech.api.screen.widgets.BoxWidget;
import rearth.oritech.api.screen.widgets.ButtonWidget;
import rearth.oritech.api.screen.widgets.ItemWidget;
import rearth.oritech.api.screen.widgets.LabelWidget;
import rearth.oritech.api.screen.widgets.OverlayWidget;
import rearth.oritech.api.screen.widgets.ScrollWidget;
import rearth.oritech.api.screen.widgets.SurfaceWidget;
import rearth.oritech.block.base.entity.MultiblockMachineEntity;
import rearth.oritech.block.base.entity.UpgradableMachineBlockEntity;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.client.ui.OritechMachineScreen;
import rearth.oritech.client.ui.OritechScreenHandler;
import rearth.oritech.client.ui.UpgradableOritechScreenHandler;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.ColorHelper;
import rearth.oritech.util.MachineAddonController;
import rearth.oritech.util.TooltipHelper;

import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.client.AddonOverlayHost;

/**
 * Shows the machine's addon stats panel on screens that skipped it: the refinery (and tainted
 * refinery) use a plain {@code OritechScreenHandler} instead of the
 * {@code UpgradableOritechScreenHandler}, so their screens never received the speed / efficiency
 * labels and the addons button that {@code UpgradableOritechScreen} adds - even though the refinery
 * block entity is a full {@link MachineAddonController} and applies the plugins of linked wireless
 * extension addons.
 * <p>
 * This mixin appends exactly that panel to every {@code OritechMachineScreen} whose handler does not
 * provide it itself but whose block entity is an addon controller, so the refinery displays the same
 * numbers as every other machine. The values come from the machine's synced addon data, which
 * already includes the wireless docks' contributions.
 * <p>
 * Inherited members of the screen (the menu, the gui position, the component list) are reached
 * through public accessors and the {@link AddonOverlayHost} duck interface, because this Mixin
 * version only resolves {@code @Shadow} members that are declared in the target class itself.
 */
@Mixin(OritechMachineScreen.class)
public abstract class OritechMachineScreenMixin {

    /** Same colours the upgradable screen uses for its stat labels. */
    @Unique
    private static final int oritechaddonsone$SPEED_COLOR = ColorHelper.argb(0.12941177F, 0.61960787F, 0.7372549F);
    @Unique
    private static final int oritechaddonsone$EFFICIENCY_COLOR = ColorHelper.argb(0.5568628F, 0.7921569F, 0.9019608F);
    @Unique
    private static final int oritechaddonsone$CAPACITY_COLOR = ColorHelper.argb(0.007843138F, 0.1882353F, 0.2784314F);
    @Unique
    private static final int oritechaddonsone$THROUGHPUT_COLOR = ColorHelper.argb(1.0F, 0.7176471F, 0.011764706F);

    /** The addon stats overlay of this screen, or null while it is closed (mirrors the upgradable screen). */
    @Unique
    private OverlayWidget oritechaddonsone$addonOverlay;

    /** The burst label, kept for the per-tick update (mirrors the upgradable screen). */
    @Unique
    private LabelWidget oritechaddonsone$burstLabel;

    /**
     * Appends the addon stats panel to machines whose handler does not add it but whose block entity
     * supports addons (currently the two refineries; their plugins arrive through wireless extension
     * addons, because the refinery itself has no addon slots).
     */
    @Inject(method = "addExtensionContent", at = @At("TAIL"))
    private void oritechaddonsone$addAddonStats(List<UIComponent> content, CallbackInfo callback) {
        var menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (menu instanceof UpgradableOritechScreenHandler) return;                  // already has the panel
        if (!(menu instanceof OritechScreenHandler handler)) return;
        if (!(handler.blockEntity instanceof MachineAddonController controller)) return;

        var baseData = controller.getBaseAddonData();
        int speed = Math.round(1.0F / baseData.speed() * 100.0F / 5.0F) * 5;
        float efficiency = baseData.efficiency();
        String efficiencyText = "100";
        if (efficiency > 1.03F) {
            efficiency = Math.round((efficiency - 1.0F) * 100.0F / 5.0F) * 5;
            efficiencyText = "-" + (int) efficiency;
        } else if (efficiency < 0.97F) {
            efficiency = Math.round((1.0F / efficiency - 1.0F) * 100.0F / 5.0F) * 5;
            efficiencyText = "+" + (int) efficiency;
        }

        content.add(BoxWidget.filled(0, 0, 60, 1, OritechMachineScreen.SEPARATOR_COLOR));

        LabelWidget speedLabel = new LabelWidget(0, 0, 60, 10,
                Component.translatable("title.oritech.machine_speed", speed));
        speedLabel.withTooltip(Component.translatable("tooltip.oritech.machine_speed"));
        speedLabel.withAlignment(LabelWidget.Alignment.CENTER);
        content.add(speedLabel);

        LabelWidget efficiencyLabel = new LabelWidget(0, 0, 60, 10,
                Component.translatable("title.oritech.machine_efficiency", efficiencyText));
        efficiencyLabel.withTooltip(Component.translatable("tooltip.oritech.machine_efficiency"));
        efficiencyLabel.withAlignment(LabelWidget.Alignment.CENTER);
        content.add(efficiencyLabel);

        String burstKey = oritechaddonsone$getBurstStatusKey(controller);
        if (!burstKey.isBlank()) {
            oritechaddonsone$burstLabel = new LabelWidget(0, 0, 60, 10,
                    Component.translatable("title.oritech." + burstKey));
            oritechaddonsone$burstLabel.withTooltip(
                    Component.translatable("title.oritech." + burstKey + ".tooltip", 0));
            oritechaddonsone$burstLabel.withAlignment(LabelWidget.Alignment.CENTER);
            content.add(oritechaddonsone$burstLabel);
        }

        if (baseData.extraChambers() > 0) {
            LabelWidget chambersLabel = new LabelWidget(0, 0, 60, 10,
                    Component.translatable("title.oritech.chambers", baseData.extraChambers()));
            chambersLabel.withTooltip(Component.translatable("tooltip.oritech.chambers"));
            chambersLabel.withAlignment(LabelWidget.Alignment.CENTER);
            content.add(chambersLabel);
        }

        ButtonWidget addonButton = ButtonWidget.panel(
                5, 0, 50, 14,
                Component.translatable("button.oritech.machine.addons").withColor(LabelWidget.DARK_TEXT),
                button -> oritechaddonsone$toggleAddonOverlay(handler, controller))
                .withSurfacePadding(Insets.of(2, 0, 2, 0))
                .withTextColor(LabelWidget.DARK_TEXT);
        content.add(addonButton);
    }

    /** Keeps the burst label in sync while the screen is open (same update the upgradable screen does). */
    @Inject(method = "tickExtra", at = @At("TAIL"))
    private void oritechaddonsone$updateBurstLabel(CallbackInfo callback) {
        if (oritechaddonsone$burstLabel == null) return;
        var menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (!(menu instanceof OritechScreenHandler handler)) return;
        if (!(handler.blockEntity instanceof MachineAddonController controller)) return;

        String burstKey = oritechaddonsone$getBurstStatusKey(controller);
        if (burstKey.isBlank()) return;

        int burstTicks = controller instanceof UpgradableMachineBlockEntity machine
                ? machine.remainingBurstTicks : 0;
        oritechaddonsone$burstLabel.setText(Component.translatable("title.oritech." + burstKey));
        oritechaddonsone$burstLabel.setTooltip(List.of(
                Component.translatable("title.oritech." + burstKey + ".tooltip", burstTicks)));
    }

    /** Opens or closes the addon overlay (a copy of the upgradable screen's overlay). */
    @Unique
    private void oritechaddonsone$toggleAddonOverlay(OritechScreenHandler handler,
                                                     MachineAddonController controller) {
        if (oritechaddonsone$addonOverlay != null) {
            oritechaddonsone$closeAddonOverlay();
            return;
        }

        var screen = (Screen) (Object) this;
        var overlayHost = (AddonOverlayHost) this;
        var containerScreen = (AbstractContainerScreen<?>) (Object) this;

        var overlay = new OverlayWidget(screen.width, screen.height);
        overlay.setPosition(-containerScreen.getGuiLeft(), -containerScreen.getGuiTop());
        overlay.withBackgroundColor(ColorHelper.argb(0.0F, 0.0F, 0.0F, 0.5F));
        overlay.withDismissHandler(this::oritechaddonsone$closeAddonOverlay);
        oritechaddonsone$addonOverlay = overlay;

        var level = handler.playerInventory.player.level();
        int centerX = -containerScreen.getGuiLeft() + screen.width / 2;
        int centerY = -containerScreen.getGuiTop() + screen.height / 2;

        // Rotating 3D preview of the machine with every connected addon around it.
        BlockPreviewWidget preview = new BlockPreviewWidget(centerX - 93, centerY - 125, 186, 100);
        preview.withSurface(OritechSurface.PANEL);
        preview.withPadding(Insets.of(4));
        preview.withRotationSpeed(0.2F);
        Direction facing = handler.machineBlock.getValue(handler.screenData.getBlockFacingProperty());
        for (BlockPos addonPos : controller.getConnectedAddons()) {
            BlockState addonState = level.getBlockState(addonPos);
            BlockEntity addonEntity = level.getBlockEntity(addonPos);
            Vec3i relative = MultiblockMachineEntity.worldToRelativePos(handler.blockPos, addonPos, facing);
            preview.addBlock(addonState, addonEntity, relative);
        }
        for (BlockPos openPos : controller.getOpenAddonSlots()) {
            Vec3i relative = MultiblockMachineEntity.worldToRelativePos(handler.blockPos, openPos, facing);
            preview.addBlock(BlockContent.ADDON_INDICATOR_BLOCK.defaultBlockState(), null, relative);
        }
        preview.addBlock(handler.machineBlock, handler.blockEntity, new Vec3i(0, 0, 0));
        overlay.addChild(preview);

        // Scrollable list of the addons and their stats.
        ScrollWidget scroll = new ScrollWidget(centerX - 100, centerY - 10, 200, 130);
        scroll.withSurface(OritechSurface.PANEL);
        scroll.withPadding(Insets.of(6));
        var addons = controller.getConnectedAddons();
        int yOffset = 0;
        boolean anyEntries = false;

        for (BlockPos addonPos : addons) {
            BlockState addonState = level.getBlockState(addonPos);
            if (addonState.getBlock() instanceof WirelessExtensionAddonBlock) {
                // A wireless extension addon is not a machine addon block, so the stock list skips it;
                // show it as a linked dock instead.
                ItemWidget icon = new ItemWidget(3, yOffset + 3, 20, new ItemStack(addonState.getBlock()));
                icon.withShowOverlay(false);
                icon.withTooltipFromStack(false);
                scroll.addChild(icon);
                LabelWidget nameLabel = new LabelWidget(28, yOffset + 4, 140, 10, addonState.getBlock().getName());
                scroll.addChild(nameLabel);
                yOffset += 28;
                scroll.addChild(BoxWidget.filled(0, yOffset, 192, 1, OritechMachineScreen.SEPARATOR_COLOR));
                yOffset++;
                anyEntries = true;
                continue;
            }

            if (!(addonState.getBlock() instanceof MachineAddonBlock addonBlock)) continue;

            MutableComponent blockName = addonState.getBlock().getName();
            ItemWidget icon = new ItemWidget(3, yOffset + 3, 20, new ItemStack(addonState.getBlock()));
            icon.withShowOverlay(false);
            icon.withTooltipFromStack(false);
            scroll.addChild(icon);
            LabelWidget nameLabel = new LabelWidget(28, yOffset + 4, 140, 10, blockName);
            scroll.addChild(nameLabel);

            var settings = addonBlock.getAddonSettings();
            int statsY = yOffset + 17;
            int statsX = 28;
            float speed = (1.0F - settings.speedMultiplier()) * 100.0F;
            float efficiency = (1.0F - settings.efficiencyMultiplier()) * 100.0F;
            if (speed != 0.0F) {
                LabelWidget label = new LabelWidget(statsX, statsY, 50, 10,
                        Component.translatable("title.oritech.machine_speed", (int) speed));
                label.withColor(oritechaddonsone$SPEED_COLOR);
                label.withTooltip(Component.translatable("tooltip.oritech.machine_speed"));
                scroll.addChild(label);
                statsX += 42;
            }
            if (efficiency != 0.0F) {
                LabelWidget label = new LabelWidget(statsX, statsY, 50, 10,
                        Component.translatable("title.oritech.machine_efficiency", (int) efficiency));
                label.withColor(oritechaddonsone$EFFICIENCY_COLOR);
                label.withTooltip(Component.translatable("tooltip.oritech.machine_efficiency"));
                scroll.addChild(label);
                statsX += 42;
            }
            if (settings.addedCapacity() > 0L) {
                LabelWidget label = new LabelWidget(statsX, statsY, 60, 10,
                        Component.translatable("title.oritech.machine.capacitor_added_capacity",
                                TooltipHelper.getEnergyText(settings.addedCapacity())));
                label.withColor(oritechaddonsone$CAPACITY_COLOR);
                scroll.addChild(label);
                statsX += 62;
            }
            if (settings.addedInsert() > 0L) {
                LabelWidget label = new LabelWidget(statsX, statsY, 60, 10,
                        Component.translatable("title.oritech.machine.capacitor_added_throughput",
                                TooltipHelper.getEnergyText(settings.addedInsert())));
                label.withColor(oritechaddonsone$THROUGHPUT_COLOR);
                scroll.addChild(label);
            }

            yOffset += 28;
            scroll.addChild(BoxWidget.filled(0, yOffset, 192, 1, OritechMachineScreen.SEPARATOR_COLOR));
            yOffset++;
            anyEntries = true;
        }

        if (!anyEntries) {
            scroll.addChild(new LabelWidget(12, 0, 190, 10,
                    Component.translatable("title.oritech.machine.no_addons")));
            yOffset = 15;
        }

        SurfaceWidget background = new SurfaceWidget(0, 0, 194, yOffset);
        background.setSurface(OritechSurface.PANEL_INSET);
        scroll.addChild(background.withZIndex(-1));
        scroll.setContentDimensions(164, yOffset);
        overlay.addChild(scroll);
        overlayHost.oritechaddonsone$addComponent(overlay);
    }

    /** Removes the addon overlay again (also used as the overlay's dismiss handler). */
    @Unique
    private void oritechaddonsone$closeAddonOverlay() {
        if (oritechaddonsone$addonOverlay == null) return;
        ((AddonOverlayHost) this).oritechaddonsone$removeComponent(oritechaddonsone$addonOverlay);
        oritechaddonsone$addonOverlay = null;
    }

    /** Burst state key of the machine, or an empty string while no burst addon is involved. */
    @Unique
    private static String oritechaddonsone$getBurstStatusKey(MachineAddonController controller) {
        if (controller instanceof UpgradableMachineBlockEntity machine) {
            if (machine.isBurstThrottled()) return "burst.throttled";
            if (machine.isActivelyWorking() && machine.isBurstAvailable()) return "burst.active";
            if (machine.isBurstAvailable()) return "burst.ready";
        }
        return "";
    }
}
