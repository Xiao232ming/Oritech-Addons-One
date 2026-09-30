package io.github.xiao232ming.oritechaddonsone;


import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
import io.github.xiao232ming.oritechaddonsone.item.ExtensionAddonItem;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;

import io.github.xiao232ming.oritechaddonsone.addon.StorageBonusHolder;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.PluginAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * An addon for Oritech (26.1.2) that adds the "Extension Addon" block.
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
    public static final DeferredBlock<ExtensionAddonBlock> EXTENSION_ADDON_1 = BLOCKS.registerBlock(
            ExtensionAddonType.TYPE_1.id(),
            properties -> new ExtensionAddonBlock(properties, addonSettings(), ExtensionAddonType.TYPE_1),
            OritechAddonsOne::blockProperties);

    /** 扩展插件Ⅱ型 - slots for every other plugin (except the Heart of the Machine / inventory proxy). */
    public static final DeferredBlock<ExtensionAddonBlock> EXTENSION_ADDON_2 = BLOCKS.registerBlock(
            ExtensionAddonType.TYPE_2.id(),
            properties -> new ExtensionAddonBlock(properties, addonSettings(), ExtensionAddonType.TYPE_2),
            OritechAddonsOne::blockProperties);

    /** 扩展插件Ⅲ型 - one dedicated slot per stat plugin, configurable capacity per slot. */
    public static final DeferredBlock<ExtensionAddonBlock> EXTENSION_ADDON_3 = BLOCKS.registerBlock(
            ExtensionAddonType.TYPE_3.id(),
            properties -> new ExtensionAddonBlock(properties, addonSettings(), ExtensionAddonType.TYPE_3),
            OritechAddonsOne::blockProperties);

    /**
     * 无线扩展坞 - the same three addons as full blocks. They are not machine addons: they are linked to
     * a machine with Oritech's target designator and apply their plugins from wherever they stand.
     */
    public static final DeferredBlock<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_1 = BLOCKS.registerBlock(
            wirelessId(ExtensionAddonType.TYPE_1),
            properties -> new WirelessExtensionAddonBlock(properties, ExtensionAddonType.TYPE_1),
            OritechAddonsOne::wirelessBlockProperties);

    /** 无线扩展坞Ⅱ型 - see {@link #WIRELESS_EXTENSION_ADDON_1}. */
    public static final DeferredBlock<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_2 = BLOCKS.registerBlock(
            wirelessId(ExtensionAddonType.TYPE_2),
            properties -> new WirelessExtensionAddonBlock(properties, ExtensionAddonType.TYPE_2),
            OritechAddonsOne::wirelessBlockProperties);

    /** 无线扩展坞Ⅲ型 - see {@link #WIRELESS_EXTENSION_ADDON_1}. */
    public static final DeferredBlock<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_3 = BLOCKS.registerBlock(
            wirelessId(ExtensionAddonType.TYPE_3),
            properties -> new WirelessExtensionAddonBlock(properties, ExtensionAddonType.TYPE_3),
            OritechAddonsOne::wirelessBlockProperties);

    /**
     * 仓库插件 - a stat plugin of its own: every installed one raises the stack limit of <b>every</b>
     * item slot of the machine by {@link StorageBonusHolder#SLOTS_PER_WAREHOUSE_ADDON}.
     * <p>
     * It is a plain Oritech {@link MachineAddonBlock} with neutral stats, so Oritech itself treats it like
     * any other plugin (it occupies an addon slot and contributes nothing to the six stats). The effect
     * is applied by this mod, see {@code MachineStorageBonuses}. The model and the texture are the ones of
     * Oritech's machine speed addon until a dedicated one exists.
     * <p>
     * It is a {@link PluginAddonBlock} because it needs its own block entity type, see
     * {@link #PLUGIN_ADDON_ENTITY}.
     */
    public static final DeferredBlock<PluginAddonBlock> WAREHOUSE_ADDON = BLOCKS.registerBlock(
            "warehouse_addon",
            properties -> new PluginAddonBlock(properties, addonSettings()),
            OritechAddonsOne::blockProperties);

    /**
     * 储罐插件 - the fluid counterpart of {@link #WAREHOUSE_ADDON}: every installed one raises the
     * capacity of <b>every</b> fluid tank of the machine by
     * {@link StorageBonusHolder#CAPACITY_PER_TANK_ADDON}.
     */
    public static final DeferredBlock<PluginAddonBlock> TANK_ADDON = BLOCKS.registerBlock(
            "tank_addon",
            properties -> new PluginAddonBlock(properties, addonSettings()),
            OritechAddonsOne::blockProperties);

    /**
     * Block items of all three types. They forward the block's tooltip to the item (the bridge Oritech
     * uses for its own blocks) and use the block name as their item name.
     */
    public static final DeferredItem<ExtensionAddonItem> EXTENSION_ADDON_1_ITEM = ITEMS.registerItem(
            ExtensionAddonType.TYPE_1.id(),
            properties -> new ExtensionAddonItem(EXTENSION_ADDON_1.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionAddonItem> EXTENSION_ADDON_2_ITEM = ITEMS.registerItem(
            ExtensionAddonType.TYPE_2.id(),
            properties -> new ExtensionAddonItem(EXTENSION_ADDON_2.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionAddonItem> EXTENSION_ADDON_3_ITEM = ITEMS.registerItem(
            ExtensionAddonType.TYPE_3.id(),
            properties -> new ExtensionAddonItem(EXTENSION_ADDON_3.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_1_ITEM = ITEMS.registerItem(
            wirelessId(ExtensionAddonType.TYPE_1),
            properties -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_1.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_2_ITEM = ITEMS.registerItem(
            wirelessId(ExtensionAddonType.TYPE_2),
            properties -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_2.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_3_ITEM = ITEMS.registerItem(
            wirelessId(ExtensionAddonType.TYPE_3),
            properties -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_3.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    /**
     * Block items of the warehouse and tank addons. They are plain block items: the blocks are ordinary
     * Oritech plugins whose stats Oritech's own tooltip already describes, and both are neutral.
     */
    public static final DeferredItem<BlockItem> WAREHOUSE_ADDON_ITEM = ITEMS.registerItem(
            "warehouse_addon",
            properties -> new BlockItem(WAREHOUSE_ADDON.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    /** Block item of the tank addon, see {@link #WAREHOUSE_ADDON_ITEM}. */
    public static final DeferredItem<BlockItem> TANK_ADDON_ITEM = ITEMS.registerItem(
            "tank_addon",
            properties -> new BlockItem(TANK_ADDON.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    /** All plugin types share one block entity type, the type is read from the owning block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtensionAddonBlockEntity>> EXTENSION_ADDON_ENTITY =
            BLOCK_ENTITIES.register("extension_addon",
                    () -> new BlockEntityType<>(ExtensionAddonBlockEntity::new,
                            EXTENSION_ADDON_1.get(), EXTENSION_ADDON_2.get(), EXTENSION_ADDON_3.get()));

    /** The wireless addons have their own block entity type because they are a different block class. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessExtensionAddonBlockEntity>> WIRELESS_EXTENSION_ADDON_ENTITY =
            BLOCK_ENTITIES.register("wireless_extension_addon",
                    () -> new BlockEntityType<>(WirelessExtensionAddonBlockEntity::new,
                            WIRELESS_EXTENSION_ADDON_1.get(), WIRELESS_EXTENSION_ADDON_2.get(),
                            WIRELESS_EXTENSION_ADDON_3.get()));

    /**
     * Block entity type of the warehouse and tank addons.
     * <p>
     * The block entities are Oritech's ordinary {@link AddonBlockEntity}; the type exists because
     * Minecraft validates the block state against the block entity's type when the block entity is
     * created ({@code BlockEntity#validateBlockState}). Oritech's shared {@code oritech:addon} type only
     * lists Oritech's own addon blocks, so this type lists the two plugin blocks instead and is used by
     * {@link PluginAddonBlock#newBlockEntity} as well as by the factory below (which is what creates the
     * block entities when a saved chunk is loaded again).
     * <p>
     * The factory cannot be {@code AddonBlockEntity::new}: that two argument constructor passes Oritech's
     * shared type, which would fail the very same validation on load. The three argument constructor
     * takes the type explicitly, which is why the factory is a method of this class
     * ({@link #pluginAddonEntity}).
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AddonBlockEntity>> PLUGIN_ADDON_ENTITY =
            BLOCK_ENTITIES.register("plugin_addon",
                    () -> new BlockEntityType<AddonBlockEntity>(OritechAddonsOne::pluginAddonEntity,
                            WAREHOUSE_ADDON.get(), TANK_ADDON.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ExtensionAddonMenu>> EXTENSION_ADDON_MENU =
            MENUS.register("extension_addon", () -> IMenuTypeExtension.create(ExtensionAddonMenu::new));

    /** Own creative tab, so the blocks are always reachable even if Oritech changes its own tabs. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("extension_addons",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.oritechaddonsone"))
                    .icon(() -> new ItemStack(EXTENSION_ADDON_1_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(EXTENSION_ADDON_1_ITEM.get());
                        output.accept(EXTENSION_ADDON_2_ITEM.get());
                        output.accept(EXTENSION_ADDON_3_ITEM.get());
                        output.accept(WIRELESS_EXTENSION_ADDON_1_ITEM.get());
                        output.accept(WIRELESS_EXTENSION_ADDON_2_ITEM.get());
                        output.accept(WIRELESS_EXTENSION_ADDON_3_ITEM.get());
                        output.accept(WAREHOUSE_ADDON_ITEM.get());
                        output.accept(TANK_ADDON_ITEM.get());
                    })
                    .build());

    private static MachineAddonBlock.AddonSettings addonSettings() {
        return MachineAddonBlock.AddonSettings.getDefaultSettings().withNeedsSupport(false);
    }

    /**
     * Factory of {@link #PLUGIN_ADDON_ENTITY}: Oritech's ordinary {@link AddonBlockEntity}, created with
     * this mod's own block entity type (see there why the two argument constructor is not usable).
     * <p>
     * It is a method instead of an inline lambda only because a static field cannot refer to itself by
     * simple name inside its own initializer.
     */
    private static AddonBlockEntity pluginAddonEntity(BlockPos pos, BlockState state) {
        return new AddonBlockEntity(PLUGIN_ADDON_ENTITY.get(), pos, state);
    }

    private static BlockBehaviour.Properties blockProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion();
    }

    /** The wireless addons are ordinary full blocks, so they keep the normal occluding properties. */
    private static BlockBehaviour.Properties wirelessBlockProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK);
    }

    /** Registry path of the wireless variant of a type, e.g. {@code wireless_extension_addon_1}. */
    private static String wirelessId(ExtensionAddonType type) {
        return "wireless_" + type.id();
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

        LOGGER.info("Oritech Addons One loaded: {}, {}, {} and the wireless variants {} registered",
                EXTENSION_ADDON_1.getId(), EXTENSION_ADDON_2.getId(), EXTENSION_ADDON_3.getId(),
                WIRELESS_EXTENSION_ADDON_1.getId());
    }

    /**
     * Resolves the plugin sets once after registration and logs them, which makes it easy to check
     * which plugins each type accepts.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {

            var type2 = ExtensionAddonType.type2Plugins().stream()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .sorted()
                    .toList();

            LOGGER.debug("Extension Addon type I accepts {} plugins; type II accepts {} plugins: {}",
                    ExtensionAddonType.type1Plugins().size(), type2.size(), type2);

            var type3Order = ExtensionAddonType.fixedSlotOrder().stream()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .toList();
            LOGGER.debug("Extension Addon type III: {} fixed slots, capacity {} each: {}",
                    type3Order.size(), Config.slotCapacity(ExtensionAddonType.TYPE_3), type3Order);

            for (var pluginType : ExtensionAddonType.values()) {
                var layout = ExtensionAddonLayout.of(Config.slots(pluginType));
                LOGGER.debug("Extension Addon {}: {} slots ({}x{}, panel {}x{})",
                        pluginType.id(), layout.slots(), layout.columns(), layout.rows(),
                        layout.imageWidth(), layout.imageHeight());
            }
        });
    }


    /**
     * Registers the energy capability of the Extension Addons. The handler only becomes active while a
     * machine acceptor plugin is inserted, so the block can then be used as an energy input of the
     * machine it is attached to.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, EXTENSION_ADDON_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getEnergyLookup(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, WIRELESS_EXTENSION_ADDON_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getEnergyLookup(side));
    }
}
