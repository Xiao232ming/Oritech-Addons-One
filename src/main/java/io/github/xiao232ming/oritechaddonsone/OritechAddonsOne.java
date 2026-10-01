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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github.xiao232ming.oritechaddonsone.item.ExtensionAddonItem;
import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.PluginAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * An addon for Oritech (1.21.1) that adds the "Extension Addon" block.
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
            blockProperties());

    /** 扩展插件Ⅱ型 - slots for every other plugin (except the inventory proxy). */
    public static final DeferredBlock<ExtensionAddonBlock> EXTENSION_ADDON_2 = BLOCKS.registerBlock(
            ExtensionAddonType.TYPE_2.id(),
            properties -> new ExtensionAddonBlock(properties, addonSettings(), ExtensionAddonType.TYPE_2),
            blockProperties());

    /** 扩展插件Ⅲ型 - one dedicated slot per stat plugin, configurable capacity per slot. */
    public static final DeferredBlock<ExtensionAddonBlock> EXTENSION_ADDON_3 = BLOCKS.registerBlock(
            ExtensionAddonType.TYPE_3.id(),
            properties -> new ExtensionAddonBlock(properties, addonSettings(), ExtensionAddonType.TYPE_3),
            blockProperties());

    /**
     * 无线扩展坞 - the same three addons as full blocks. They are not machine addons: they are linked to
     * a machine with Oritech's target designator and apply their plugins from wherever they stand.
     */
    public static final DeferredBlock<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_1 = BLOCKS.registerBlock(
            wirelessId(ExtensionAddonType.TYPE_1),
            properties -> new WirelessExtensionAddonBlock(properties, ExtensionAddonType.TYPE_1),
            wirelessBlockProperties());

    /** 无线扩展坞Ⅱ型 - see {@link #WIRELESS_EXTENSION_ADDON_1}. */
    public static final DeferredBlock<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_2 = BLOCKS.registerBlock(
            wirelessId(ExtensionAddonType.TYPE_2),
            properties -> new WirelessExtensionAddonBlock(properties, ExtensionAddonType.TYPE_2),
            wirelessBlockProperties());

    /** 无线扩展坞Ⅲ型 - see {@link #WIRELESS_EXTENSION_ADDON_1}. */
    public static final DeferredBlock<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_3 = BLOCKS.registerBlock(
            wirelessId(ExtensionAddonType.TYPE_3),
            properties -> new WirelessExtensionAddonBlock(properties, ExtensionAddonType.TYPE_3),
            wirelessBlockProperties());

    /**
     * 仓库插件 - a stat plugin of its own category: every installed one raises the stack limit of
     * <b>every</b> item slot of the machine by {@link AddonStorageBonus#SLOTS_PER_WAREHOUSE_ADDON}.
     * <p>
     * It is a plain Oritech {@link MachineAddonBlock} with neutral stats, so Oritech itself treats it like
     * any other plugin (it occupies an addon slot and contributes nothing to the six stats). The effect
     * is applied by this mod, see {@code MachineStorageBonuses}. Its model reuses the base and the
     * pedestal of Oritech's machine speed addon and replaces the crystal on top of the pedestal with a
     * miniature vanilla chest, see {@code models/block/warehouse_addon.json}.
     * <p>
     * It is a {@link PluginAddonBlock} because it needs its own block entity type, see
     * {@link #PLUGIN_ADDON_ENTITY}.
     */
    public static final DeferredBlock<PluginAddonBlock> WAREHOUSE_ADDON = BLOCKS.registerBlock(
            "warehouse_addon",
            properties -> new PluginAddonBlock(properties, pluginAddonSettings()),
            blockProperties());

    /**
     * 储罐插件 - the fluid counterpart of {@link #WAREHOUSE_ADDON}: every installed one raises the
     * capacity of <b>every</b> fluid tank of the machine by
     * {@link AddonStorageBonus#CAPACITY_PER_TANK_ADDON}.
     * <p>
     * Its model is the same speed addon pedestal with a miniature Oritech fluid tank on top of it, see
     * {@code models/block/tank_addon.json}.
     */
    public static final DeferredBlock<PluginAddonBlock> TANK_ADDON = BLOCKS.registerBlock(
            "tank_addon",
            properties -> new PluginAddonBlock(properties, pluginAddonSettings()),
            blockProperties());

    /**
     * Block items of all three types. They forward the block's tooltip to the item (the bridge Oritech
     * uses for its own blocks) and use the block name as their item name.
     * <p>
     * In 1.21.1 a plain {@link Item.Properties} is enough for the block description prefix: vanilla's
     * {@code BlockItem} already derives its description id from the block.
     */
    public static final DeferredItem<ExtensionAddonItem> EXTENSION_ADDON_1_ITEM = ITEMS.registerItem(
            ExtensionAddonType.TYPE_1.id(),
            properties -> new ExtensionAddonItem(EXTENSION_ADDON_1.get(), properties),
            new Item.Properties());

    public static final DeferredItem<ExtensionAddonItem> EXTENSION_ADDON_2_ITEM = ITEMS.registerItem(
            ExtensionAddonType.TYPE_2.id(),
            properties -> new ExtensionAddonItem(EXTENSION_ADDON_2.get(), properties),
            new Item.Properties());

    public static final DeferredItem<ExtensionAddonItem> EXTENSION_ADDON_3_ITEM = ITEMS.registerItem(
            ExtensionAddonType.TYPE_3.id(),
            properties -> new ExtensionAddonItem(EXTENSION_ADDON_3.get(), properties),
            new Item.Properties());

    public static final DeferredItem<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_1_ITEM = ITEMS.registerItem(
            wirelessId(ExtensionAddonType.TYPE_1),
            properties -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_1.get(), properties),
            new Item.Properties());

    public static final DeferredItem<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_2_ITEM = ITEMS.registerItem(
            wirelessId(ExtensionAddonType.TYPE_2),
            properties -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_2.get(), properties),
            new Item.Properties());

    public static final DeferredItem<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_3_ITEM = ITEMS.registerItem(
            wirelessId(ExtensionAddonType.TYPE_3),
            properties -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_3.get(), properties),
            new Item.Properties());

    /**
     * Block items of the warehouse and tank addons. They are plain block items: the blocks are ordinary
     * Oritech plugins whose stats Oritech's own tooltip already describes, and both are neutral.
     */
    public static final DeferredItem<BlockItem> WAREHOUSE_ADDON_ITEM = ITEMS.registerItem(
            "warehouse_addon",
            properties -> new BlockItem(WAREHOUSE_ADDON.get(), properties),
            new Item.Properties());

    /** Block item of the tank addon, see {@link #WAREHOUSE_ADDON_ITEM}. */
    public static final DeferredItem<BlockItem> TANK_ADDON_ITEM = ITEMS.registerItem(
            "tank_addon",
            properties -> new BlockItem(TANK_ADDON.get(), properties),
            new Item.Properties());

    /** All plugin types share one block entity type, the type is read from the owning block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtensionAddonBlockEntity>> EXTENSION_ADDON_ENTITY =
            BLOCK_ENTITIES.register("extension_addon",
                    () -> BlockEntityType.Builder.of(ExtensionAddonBlockEntity::new,
                            EXTENSION_ADDON_1.get(), EXTENSION_ADDON_2.get(), EXTENSION_ADDON_3.get()).build(null));

    /** The wireless addons have their own block entity type because they are a different block class. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessExtensionAddonBlockEntity>> WIRELESS_EXTENSION_ADDON_ENTITY =
            BLOCK_ENTITIES.register("wireless_extension_addon",
                    () -> BlockEntityType.Builder.of(WirelessExtensionAddonBlockEntity::new,
                            WIRELESS_EXTENSION_ADDON_1.get(), WIRELESS_EXTENSION_ADDON_2.get(),
                            WIRELESS_EXTENSION_ADDON_3.get()).build(null));

    /**
     * Block entity type of the warehouse and tank addons.
     * <p>
     * The block entities are Oritech's ordinary {@link AddonBlockEntity}; the type exists because
     * Minecraft validates the block state against the block entity's type when the block entity is
     * created ({@code BlockEntity#validateBlockState}). Oritech's shared {@code oritech:addon_entity}
     * type only lists Oritech's own addon blocks, so this type lists the two plugin blocks instead and
     * is used by {@link PluginAddonBlock#newBlockEntity} as well as by the factory below (which is what
     * creates the block entities when a saved chunk is loaded again).
     * <p>
     * The factory cannot be {@code AddonBlockEntity::new}: that two argument constructor passes
     * Oritech's shared type, which would fail the very same validation on load. The three argument
     * constructor takes the type explicitly, which is why the factory is a method of this class
     * ({@link #pluginAddonEntity}).
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AddonBlockEntity>> PLUGIN_ADDON_ENTITY =
            BLOCK_ENTITIES.register("plugin_addon",
                    () -> BlockEntityType.Builder.<AddonBlockEntity>of(OritechAddonsOne::pluginAddonEntity,
                            WAREHOUSE_ADDON.get(), TANK_ADDON.get()).build(null));

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

    /**
     * Settings of this mod's own plugin holders ({@link #EXTENSION_ADDON_1} and friends). They are
     * deliberately placeable without support: an Extension Addon is put into a machine's addon slot and is
     * not attached to one particular face, so it must not fall off when a neighbour changes.
     */
    private static MachineAddonBlock.AddonSettings addonSettings() {
        return MachineAddonBlock.AddonSettings.getDefaultSettings().withNeedsSupport(false);
    }

    /**
     * Settings of {@link #WAREHOUSE_ADDON} and {@link #TANK_ADDON}.
     * <p>
     * Unlike the Extension Addons these two are plain Oritech plugins the player attaches to one face of a
     * machine, so they use Oritech's default settings and therefore {@code needsSupport = true} - exactly
     * like Oritech's own addons. That flag is also what gives them the {@code facing} and {@code face}
     * properties (see {@code MachineAddonBlock#createBlockStateDefinition}) and makes
     * {@code getStateForPlacement} derive them from the clicked face: the block state stores which face
     * the plugin hangs on, and the blockstate file rotates the model to match. Without it the blocks would
     * only have {@code addon_used} and would always stand upright, wherever they were placed.
     * <p>
     * The support requirement is what makes the blocks behave like Oritech's own addons: they are attached
     * to a solid face, they fall off when that block is removed, and the machine's addon scan looks at the
     * face they hang on first.
     * <p>
     * The bounding shape is the tight box of the models, see {@link PluginAddonBlock#PLUGIN_ADDON_SHAPE}:
     * without it the outline and the collision box of a placed plugin would be a full block.
     */
    private static MachineAddonBlock.AddonSettings pluginAddonSettings() {
        return MachineAddonBlock.AddonSettings.getDefaultSettings()
                .withBoundingShape(PluginAddonBlock.PLUGIN_ADDON_SHAPE);
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
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(this::onCommonSetup);

        // Tells Oritech to expose this block entity's energy storage (see
        // ExtensionAddonBlockEntity#getEnergyStorage) to Oritech's own energy network *and* to
        // NeoForge's energy capability. Oritech's bridge is built in NeoforgeEnergyApiImpl and only
        // wraps block entity types that were registered here, so this call is what makes the block
        // usable as an energy input while a machine acceptor plugin is inserted.
        EnergyApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) EXTENSION_ADDON_ENTITY.get());
        EnergyApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) WIRELESS_EXTENSION_ADDON_ENTITY.get());

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

            var type1 = ExtensionAddonType.type1Plugins().stream()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .sorted()
                    .toList();

            var type2 = ExtensionAddonType.type2Plugins().stream()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .sorted()
                    .toList();

            LOGGER.debug("Extension Addon type I / III accept every plugin of the stat categories; Oritech's reference plugins ({}): {}", type1.size(), type1);
            LOGGER.debug("Extension Addon type II accepts {} plugins: {}", type2.size(), type2);

            // Types I and III accept plugins by category, so log the category of every registered addon
            // block. This makes it easy to check that tiered plugins from other addon mods (e.g. Oritech
            // Things) are classified as expected.
            var categorized = new java.util.TreeMap<String, String>();
            for (var block : BuiltInRegistries.BLOCK) {
                var category = ExtensionAddonType.categoryOf(block);
                if (category != null) {
                    categorized.put(BuiltInRegistries.BLOCK.getKey(block).toString(), category.name());
                }
            }
            LOGGER.debug("Extension Addon stat categories ({} blocks): {}", categorized.size(), categorized);

            var type3Slots = ExtensionAddonType.type3Slots();
            LOGGER.debug("Extension Addon type III: {} slots = {} categories x {} tiers, capacity {} each: {}",
                    type3Slots.size(), ExtensionAddonType.StatCategory.values().length,
                    ExtensionAddonType.Type3Slot.rowCount(), Config.slotCapacity(ExtensionAddonType.TYPE_3),
                    type3Slots.stream()
                            .map(slot -> slot.category() + "/tier" + slot.tier() + "="
                                    + (slot.reference() == null ? "-" : BuiltInRegistries.BLOCK.getKey(slot.reference()).toString()))
                            .toList());

            for (var pluginType : ExtensionAddonType.values()) {
                var layout = ExtensionAddonLayout.forType(pluginType, Config.slots(pluginType));
                LOGGER.debug("Extension Addon {}: {} slots ({}x{}, panel {}x{}, columnMajor={})",
                        pluginType.id(), layout.slots(), layout.columns(), layout.rows(),
                        layout.imageWidth(), layout.imageHeight(), layout.columnMajor());
            }
        });
    }
}
