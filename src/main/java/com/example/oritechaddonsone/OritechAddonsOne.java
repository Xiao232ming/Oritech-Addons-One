package com.example.oritechaddonsone;


import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.example.oritechaddonsone.item.ExtensionPluginItem;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;

import com.example.oritechaddonsone.block.ExtensionPluginBlock;
import com.example.oritechaddonsone.block.ExtensionPluginType;
import com.example.oritechaddonsone.block.entity.ExtensionPluginBlockEntity;
import com.example.oritechaddonsone.menu.ExtensionPluginLayout;
import com.example.oritechaddonsone.menu.ExtensionPluginMenu;

/**
 * An addon for Oritech (26.1.2) that adds the "Extension Plugin" block.
 * <p>
 * The block is an Oritech machine addon (plugin) that itself holds up to five Oritech plugin items.
 * The stats of all inserted plugins are combined and applied to the machine the block is attached to.
 */
@Mod(OritechAddonsOne.MODID)
public class OritechAddonsOne {
    /** The mod id, must match gradle.properties and the block/item namespace. */
    public static final String MODID = "oritechaddonsone";
    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /**
     * 扩展插件Ⅰ型 - 5 slots for the six stat plugins.
     * <p>
     * It is registered as a regular Oritech {@link MachineAddonBlock} with neutral stats (speed/efficiency x1,
     * no bonus capacity) and without support requirement, so it can be placed in any machine addon slot.
     */
    public static final DeferredBlock<ExtensionPluginBlock> EXTENSION_PLUGIN_1 = BLOCKS.registerBlock(
            ExtensionPluginType.TYPE_1.id(),
            properties -> new ExtensionPluginBlock(properties, addonSettings(), ExtensionPluginType.TYPE_1),
            OritechAddonsOne::blockProperties);

    /** 扩展插件Ⅱ型 - slots for every other plugin (except the Heart of the Machine / inventory proxy). */
    public static final DeferredBlock<ExtensionPluginBlock> EXTENSION_PLUGIN_2 = BLOCKS.registerBlock(
            ExtensionPluginType.TYPE_2.id(),
            properties -> new ExtensionPluginBlock(properties, addonSettings(), ExtensionPluginType.TYPE_2),
            OritechAddonsOne::blockProperties);

    /** 扩展插件Ⅲ型 - one dedicated slot per stat plugin, configurable capacity per slot. */
    public static final DeferredBlock<ExtensionPluginBlock> EXTENSION_PLUGIN_3 = BLOCKS.registerBlock(
            ExtensionPluginType.TYPE_3.id(),
            properties -> new ExtensionPluginBlock(properties, addonSettings(), ExtensionPluginType.TYPE_3),
            OritechAddonsOne::blockProperties);

    /**
     * Block items of all three types. They forward the block's tooltip to the item (the bridge Oritech
     * uses for its own blocks) and use the block name as their item name.
     */
    public static final DeferredItem<ExtensionPluginItem> EXTENSION_PLUGIN_1_ITEM = ITEMS.registerItem(
            ExtensionPluginType.TYPE_1.id(),
            properties -> new ExtensionPluginItem(EXTENSION_PLUGIN_1.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionPluginItem> EXTENSION_PLUGIN_2_ITEM = ITEMS.registerItem(
            ExtensionPluginType.TYPE_2.id(),
            properties -> new ExtensionPluginItem(EXTENSION_PLUGIN_2.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionPluginItem> EXTENSION_PLUGIN_3_ITEM = ITEMS.registerItem(
            ExtensionPluginType.TYPE_3.id(),
            properties -> new ExtensionPluginItem(EXTENSION_PLUGIN_3.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    /** All plugin types share one block entity type, the type is read from the owning block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtensionPluginBlockEntity>> EXTENSION_PLUGIN_ENTITY =
            BLOCK_ENTITIES.register("extension_plugin",
                    () -> new BlockEntityType<>(ExtensionPluginBlockEntity::new,
                            EXTENSION_PLUGIN_1.get(), EXTENSION_PLUGIN_2.get(), EXTENSION_PLUGIN_3.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ExtensionPluginMenu>> EXTENSION_PLUGIN_MENU =
            MENUS.register("extension_plugin", () -> IMenuTypeExtension.create(ExtensionPluginMenu::new));

    /** Own creative tab, so the blocks are always reachable even if Oritech changes its own tabs. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("extension_plugins",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.oritechaddonsone"))
                    .icon(() -> new ItemStack(EXTENSION_PLUGIN_1_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(EXTENSION_PLUGIN_1_ITEM.get());
                        output.accept(EXTENSION_PLUGIN_2_ITEM.get());
                        output.accept(EXTENSION_PLUGIN_3_ITEM.get());
                    })
                    .build());

    private static MachineAddonBlock.AddonSettings addonSettings() {
        return MachineAddonBlock.AddonSettings.getDefaultSettings().withNeedsSupport(false);
    }

    private static BlockBehaviour.Properties blockProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion();
    }

    public OritechAddonsOne(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(this::registerCapabilities);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(this::onCommonSetup);

        LOGGER.info("Oritech Addons One loaded: {}, {} and {} registered",
                EXTENSION_PLUGIN_1.getId(), EXTENSION_PLUGIN_2.getId(), EXTENSION_PLUGIN_3.getId());
    }

    /**
     * Resolves the plugin sets once after registration and logs them, which makes it easy to check
     * which plugins each type accepts.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {

            var type2 = ExtensionPluginType.type2Plugins().stream()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .sorted()
                    .toList();

            LOGGER.debug("Extension Plugin type I accepts {} plugins; type II accepts {} plugins: {}",
                    ExtensionPluginType.type1Plugins().size(), type2.size(), type2);

            var type3Order = ExtensionPluginType.fixedSlotOrder().stream()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .toList();
            LOGGER.debug("Extension Plugin type III: {} fixed slots, capacity {} each: {}",
                    type3Order.size(), Config.slotCapacity(ExtensionPluginType.TYPE_3), type3Order);

            for (var pluginType : ExtensionPluginType.values()) {
                var layout = ExtensionPluginLayout.of(Config.slots(pluginType));
                LOGGER.debug("Extension Plugin {}: {} slots ({}x{}, panel {}x{})",
                        pluginType.id(), layout.slots(), layout.columns(), layout.rows(),
                        layout.imageWidth(), layout.imageHeight());
            }
        });
    }


    /**
     * Registers the energy capability of the Extension Plugins. The handler only becomes active while a
     * machine acceptor plugin is inserted, so the block can then be used as an energy input of the
     * machine it is attached to.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, EXTENSION_PLUGIN_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getEnergyLookup(side));
    }
}
