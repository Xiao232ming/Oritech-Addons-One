package io.github.xiao232ming.oritechaddonsone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Adds the Extension Plugins to Oritech's own "machines" creative tab. */
@EventBusSubscriber(modid = OritechAddonsOne.MODID)
public class ModEvents {

    private static final ResourceKey<CreativeModeTab> ORITECH_MACHINES = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("oritech", "machine_group"));

    @SubscribeEvent
    static void addToOritechTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ORITECH_MACHINES)) {
            event.accept(OritechAddonsOne.EXTENSION_PLUGIN_1_ITEM.get());
            event.accept(OritechAddonsOne.EXTENSION_PLUGIN_2_ITEM.get());
            event.accept(OritechAddonsOne.EXTENSION_PLUGIN_3_ITEM.get());
        }
    }
}
