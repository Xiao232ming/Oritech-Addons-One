package io.github.xiao232ming.oritechaddonsone;

import dev.architectury.registry.CreativeTabRegistry;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.item.ItemApi;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.init.BlockEntitiesContent;

import io.github.xiao232ming.oritechaddonsone.addon.AddonStorageBonus;
import io.github.xiao232ming.oritechaddonsone.block.ChunkAnchorAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionAddonType;
import io.github.xiao232ming.oritechaddonsone.block.PluginAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.ExtensionTransferAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.TransferAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.WirelessExtensionAddonBlock;
import io.github.xiao232ming.oritechaddonsone.block.entity.ChunkAnchorAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtenderFaceStorage;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtenderFaceFluidStorage;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.ExtensionTransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.FacePluginStorage;
import io.github.xiao232ming.oritechaddonsone.block.entity.MachineFaceConfigs;
import io.github.xiao232ming.oritechaddonsone.block.entity.MachineSlotRoles;
import io.github.xiao232ming.oritechaddonsone.block.entity.TransferAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.block.entity.WirelessExtensionAddonBlockEntity;
import io.github.xiao232ming.oritechaddonsone.item.ExtensionAddonItem;
import io.github.xiao232ming.oritechaddonsone.item.PluginAddonItem;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonLayout;
import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;
import io.github.xiao232ming.oritechaddonsone.menu.FaceFilterMenu;
import io.github.xiao232ming.oritechaddonsone.network.FilterNetworking;
import io.github.xiao232ming.oritechaddonsone.network.ProxyNetworking;
import io.github.xiao232ming.oritechaddonsone.network.RedstoneNetworking;
import io.github.xiao232ming.oritechaddonsone.network.TransferNetworking;
import io.github.xiao232ming.oritechaddonsone.platform.AddonsPlatform;
import io.github.xiao232ming.oritechaddonsone.wireless.AnchorForceLoad;

/**
 * An addon for Oritech (1.21.1) that adds the "Extension Addon" block.
 * <p>
 * The block is an Oritech machine addon (plugin) that itself holds up to five Oritech plugin items.
 * The stats of all inserted plugins are combined and applied to the machine the block is attached to.
 * <p>
 * <b>加载器中立。</b>这个类在 common 模块里，编译期看不到任何 loader API（common 用 NeoForm vanilla 模式编译）。
 * 注册走 Architectury 的 {@link DeferredRegister}（Fabric 上立即注册，NeoForge 上挂 RegisterEvent），
 * 加载器差异收敛在 {@link AddonsPlatform} SPI 与两个 loader 模块里。每个加载器的入口点先注册配置，
 * 然后调用 {@link #init()}。
 */
public class OritechAddonsOne {
    /** The mod id, must match gradle.properties and the block/item namespace. */
    public static final String MODID = "oritechaddonsone";
    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(MODID, Registries.BLOCK);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MODID, Registries.ITEM);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(MODID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(MODID, Registries.MENU);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(MODID, Registries.CREATIVE_MODE_TAB);

    /**
     * 扩展插件Ⅰ型 - 5 slots for the six stat plugins.
     * <p>
     * It is registered as a regular Oritech {@link MachineAddonBlock} with neutral stats (speed/efficiency x1,
     * no bonus capacity) and without support requirement, so it can be placed in any machine addon slot.
     */
    public static final RegistrySupplier<ExtensionAddonBlock> EXTENSION_ADDON_1 = BLOCKS.register(
            ExtensionAddonType.TYPE_1.id(),
            () -> new ExtensionAddonBlock(blockProperties(), addonSettings(), ExtensionAddonType.TYPE_1));

    /** 扩展插件Ⅱ型 - slots for every other plugin (except the inventory proxy). */
    public static final RegistrySupplier<ExtensionAddonBlock> EXTENSION_ADDON_2 = BLOCKS.register(
            ExtensionAddonType.TYPE_2.id(),
            () -> new ExtensionAddonBlock(blockProperties(), addonSettings(), ExtensionAddonType.TYPE_2));

    /** 扩展插件Ⅲ型 - one dedicated slot per stat plugin, configurable capacity per slot. */
    public static final RegistrySupplier<ExtensionAddonBlock> EXTENSION_ADDON_3 = BLOCKS.register(
            ExtensionAddonType.TYPE_3.id(),
            () -> new ExtensionAddonBlock(blockProperties(), addonSettings(), ExtensionAddonType.TYPE_3));

    /**
     * 无线扩展坞 - the same three addons as full blocks. They are not machine addons: they are linked to
     * a machine with Oritech's target designator and apply their plugins from wherever they stand.
     */
    public static final RegistrySupplier<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_1 = BLOCKS.register(
            wirelessId(ExtensionAddonType.TYPE_1),
            () -> new WirelessExtensionAddonBlock(wirelessBlockProperties(), ExtensionAddonType.TYPE_1));

    /** 无线扩展坞Ⅱ型 - see {@link #WIRELESS_EXTENSION_ADDON_1}. */
    public static final RegistrySupplier<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_2 = BLOCKS.register(
            wirelessId(ExtensionAddonType.TYPE_2),
            () -> new WirelessExtensionAddonBlock(wirelessBlockProperties(), ExtensionAddonType.TYPE_2));

    /** 无线扩展坞Ⅲ型 - see {@link #WIRELESS_EXTENSION_ADDON_1}. */
    public static final RegistrySupplier<WirelessExtensionAddonBlock> WIRELESS_EXTENSION_ADDON_3 = BLOCKS.register(
            wirelessId(ExtensionAddonType.TYPE_3),
            () -> new WirelessExtensionAddonBlock(wirelessBlockProperties(), ExtensionAddonType.TYPE_3));

    /**
     * 仓库插件 - a stat plugin of its own category: every installed one raises the stack limit of
     * <b>every</b> item slot of the machine by {@code Config#warehouseAddonSlots()}.
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
    public static final RegistrySupplier<PluginAddonBlock> WAREHOUSE_ADDON = BLOCKS.register(
            "warehouse_addon",
            () -> new PluginAddonBlock(blockProperties(), pluginAddonSettings()));

    /**
     * 储罐插件 - the fluid counterpart of {@link #WAREHOUSE_ADDON}: every installed one raises the
     * capacity of <b>every</b> fluid tank of the machine by
     * {@code Config#tankAddonCapacity()}.
     * <p>
     * Its model is the same speed addon pedestal with a miniature Oritech fluid tank on top of it, see
     * {@code models/block/tank_addon.json}.
     */
    public static final RegistrySupplier<PluginAddonBlock> TANK_ADDON = BLOCKS.register(
            "tank_addon",
            () -> new PluginAddonBlock(blockProperties(), pluginAddonSettings()));

    /**
     * 锚点插件 - the chunk anchor: while it is attached to a machine, the chunk it stands in is force
     * loaded, so that piece of the world keeps running while no player is nearby.
     * <p>
     * It has no stats at all (neutral like the other two plugins of this mod), the effect is applied by
     * {@code AnchorForceLoad}. The same item can also be put into the reserved slot of an Extension Addon
     * or a Wireless Extension Dock, where it keeps the chunk of that addon / dock loaded.
     * <p>
     * Unlike the warehouse and tank addons this block uses its own block class and block entity
     * ({@link ChunkAnchorAddonBlock} / {@code ChunkAnchorAddonBlockEntity}), because the anchor has to learn which
     * machine claimed it - the warehouse and tank addons never need to know.
     */
    public static final RegistrySupplier<ChunkAnchorAddonBlock> CHUNK_ANCHOR_ADDON = BLOCKS.register(
            "chunk_anchor_addon",
            () -> new ChunkAnchorAddonBlock(blockProperties(), pluginAddonSettings()));

    /**
     * 扩展传输插件 - the transfer addon: while it sits inside an Extension Addon, the machine that addon works
     * on can be filled from and emptied into through the addon's own six faces, one direction per face (see
     * the "Extension Transfer" page).
     * <p>
     * Placed in the world on Oritech's machine extender, the same plugin does this for the extender instead:
     * right clicking it opens the transfer page of the extender's own six faces, and the items are moved
     * between the machine behind the extender and the containers around it. That placed form has behaviour
     * of its own, so it uses its own block entity type ({@link #EXTENSION_TRANSFER_ADDON_ENTITY}).
     * <p>
     * Like the warehouse and tank addons it is a neutral {@link PluginAddonBlock}, and the Extension Addon
     * Type II accepts it because that type takes every Oritech addon that is not one of the stat plugins.
     * Its model and texture are Oritech's inventory proxy addon for now, see
     * {@code models/block/extension_transfer_addon.json}.
     */
    public static final RegistrySupplier<ExtensionTransferAddonBlock> EXTENSION_TRANSFER_ADDON = BLOCKS.register(
            "extension_transfer_addon",
            () -> new ExtensionTransferAddonBlock(blockProperties(), pluginAddonSettings()));

    /**
     * 传输插件 - the second transfer plugin, whose <b>function</b> is exactly the one of {@link #EXTENSION_TRANSFER_ADDON} and
     * whose page configures the faces on a rotatable 3D model of the machine it serves instead of on a cube net.
     * <p>
     * It supports both placements: hung on Oritech's machine extender it acts on the machine that extender was
     * claimed by, and hung directly on an Oritech machine it acts on that machine. In both cases a configured face
     * means a face of the <b>machine</b> and drives this plugin's own movement only - pipes, hoppers and other mods
     * connect to the machine itself, which Oritech already offers (see {@code TransferAddonBlockEntity}).
     * Because the machine is a real block of the world, the preview plugin needs its own block entity type
     * ({@link #TRANSFER_ADDON_ENTITY}).
     * <p>
     * Like the transfer addon it is a neutral {@link PluginAddonBlock}, and the Extension Addon Type II accepts it
     * because that type takes every Oritech addon that is not one of the stat plugins. Its model and textures are its
     * own, see {@code models/block/transfer_addon_on.json}.
     */
    public static final RegistrySupplier<TransferAddonBlock> TRANSFER_ADDON = BLOCKS.register(
            "transfer_addon",
            () -> new TransferAddonBlock(blockProperties(), pluginAddonSettings()));

    /**
     * Block items of all three types. They forward the block's tooltip to the item (the bridge Oritech
     * uses for its own blocks) and use the block name as their item name.
     * <p>
     * In 1.21.1 a plain {@link Item.Properties} is enough for the block description prefix: vanilla's
     * {@code BlockItem} already derives its description id from the block.
     */
    public static final RegistrySupplier<ExtensionAddonItem> EXTENSION_ADDON_1_ITEM = ITEMS.register(
            ExtensionAddonType.TYPE_1.id(),
            () -> new ExtensionAddonItem(EXTENSION_ADDON_1.get(), new Item.Properties()));

    public static final RegistrySupplier<ExtensionAddonItem> EXTENSION_ADDON_2_ITEM = ITEMS.register(
            ExtensionAddonType.TYPE_2.id(),
            () -> new ExtensionAddonItem(EXTENSION_ADDON_2.get(), new Item.Properties()));

    public static final RegistrySupplier<ExtensionAddonItem> EXTENSION_ADDON_3_ITEM = ITEMS.register(
            ExtensionAddonType.TYPE_3.id(),
            () -> new ExtensionAddonItem(EXTENSION_ADDON_3.get(), new Item.Properties()));

    public static final RegistrySupplier<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_1_ITEM = ITEMS.register(
            wirelessId(ExtensionAddonType.TYPE_1),
            () -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_1.get(), new Item.Properties()));

    public static final RegistrySupplier<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_2_ITEM = ITEMS.register(
            wirelessId(ExtensionAddonType.TYPE_2),
            () -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_2.get(), new Item.Properties()));

    public static final RegistrySupplier<ExtensionAddonItem> WIRELESS_EXTENSION_ADDON_3_ITEM = ITEMS.register(
            wirelessId(ExtensionAddonType.TYPE_3),
            () -> new ExtensionAddonItem(WIRELESS_EXTENSION_ADDON_3.get(), new Item.Properties()));

    /**
     * Block items of the warehouse and tank addons. They use {@link PluginAddonItem} because these two
     * plugins are neutral Oritech addons: Oritech's own tooltip describes nothing about them, so the item
     * states the effect this mod gives them (see the {@code tooltip.oritechaddonsone.*.desc} keys). The
     * bonus they show is read from the config the effect uses.
     */
    public static final RegistrySupplier<BlockItem> WAREHOUSE_ADDON_ITEM = ITEMS.register(
            "warehouse_addon",
            () -> new PluginAddonItem(WAREHOUSE_ADDON.get(), new Item.Properties(),
                    () -> "+" + Config.warehouseAddonSlots()));

    /** Block item of the tank addon, see {@link #WAREHOUSE_ADDON_ITEM}. */
    public static final RegistrySupplier<BlockItem> TANK_ADDON_ITEM = ITEMS.register(
            "tank_addon",
            () -> new PluginAddonItem(TANK_ADDON.get(), new Item.Properties(),
                    () -> "+" + Config.tankAddonCapacity() + " mB"));

    /**
     * Block item of the anchor plugin. Its description line has no number of its own - the plugin forces a
     * chunk, it does not change a stat - so the item is registered without a green bonus argument and the
     * language key has no placeholder, see {@link PluginAddonItem#PluginAddonItem}.
     */
    public static final RegistrySupplier<BlockItem> CHUNK_ANCHOR_ADDON_ITEM = ITEMS.register(
            "chunk_anchor_addon",
            () -> new PluginAddonItem(CHUNK_ANCHOR_ADDON.get(), new Item.Properties()));

    /**
     * Block item of the transfer addon. Like the anchor it changes no stat, so it is registered without a
     * bonus number and its description key has no placeholder.
     */
    public static final RegistrySupplier<BlockItem> EXTENSION_TRANSFER_ADDON_ITEM = ITEMS.register(
            "extension_transfer_addon",
            () -> new PluginAddonItem(EXTENSION_TRANSFER_ADDON.get(), new Item.Properties()));

    /**
     * Block item of the transfer preview plugin. Like the anchor and the transfer addon it changes no stat, so it is
     * registered without a bonus number and its description key has no placeholder.
     */
    public static final RegistrySupplier<BlockItem> TRANSFER_ADDON_ITEM = ITEMS.register(
            "transfer_addon",
            () -> new PluginAddonItem(TRANSFER_ADDON.get(), new Item.Properties()));

    /** All plugin types share one block entity type, the type is read from the owning block. */
    public static final RegistrySupplier<BlockEntityType<ExtensionAddonBlockEntity>> EXTENSION_ADDON_ENTITY =
            BLOCK_ENTITIES.register("extension_addon",
                    () -> BlockEntityType.Builder.of(ExtensionAddonBlockEntity::new,
                            EXTENSION_ADDON_1.get(), EXTENSION_ADDON_2.get(), EXTENSION_ADDON_3.get()).build(null));

    /** The wireless addons have their own block entity type because they are a different block class. */
    public static final RegistrySupplier<BlockEntityType<WirelessExtensionAddonBlockEntity>> WIRELESS_EXTENSION_ADDON_ENTITY =
            BLOCK_ENTITIES.register("wireless_extension_addon",
                    () -> BlockEntityType.Builder.of(WirelessExtensionAddonBlockEntity::new,
                            WIRELESS_EXTENSION_ADDON_1.get(), WIRELESS_EXTENSION_ADDON_2.get(),
                            WIRELESS_EXTENSION_ADDON_3.get()).build(null));

    /**
     * Block entity type of the warehouse, tank and anchor addons.
     * <p>
     * The block entities are Oritech's ordinary {@link AddonBlockEntity} - the anchor uses
     * {@link ChunkAnchorAddonBlockEntity}, a subclass of it; the type exists because Minecraft validates the
     * block state against the block entity's type when the block entity is created
     * ({@code BlockEntity#validateBlockState}). Oritech's shared {@code oritech:addon_entity} type only
     * lists Oritech's own addon blocks, so this type lists the plugin blocks of this mod instead and is
     * used by {@link PluginAddonBlock#newBlockEntity} as well as by the factory below (which is what
     * creates the block entities when a saved chunk is loaded again).
     * <p>
     * <b>Every new plugin block has to be listed here.</b> A block that is missing from this list throws
     * {@code IllegalStateException: Invalid block entity} as soon as it is placed.
     * <p>
     * The factory cannot be {@code AddonBlockEntity::new}: that two argument constructor passes Oritech's
     * shared type, which would fail the very same validation on load. The three argument constructor
     * takes the type explicitly, which is why the factory is a method of this class
     * ({@link #pluginAddonEntity}) - for the same reason the anchor block entity cannot be created by a
     * plain constructor reference either.
     */
    public static final RegistrySupplier<BlockEntityType<AddonBlockEntity>> PLUGIN_ADDON_ENTITY =
            BLOCK_ENTITIES.register("plugin_addon",
                    () -> BlockEntityType.Builder.<AddonBlockEntity>of(OritechAddonsOne::pluginAddonEntity,
                            WAREHOUSE_ADDON.get(), TANK_ADDON.get(), CHUNK_ANCHOR_ADDON.get(),
                            EXTENSION_TRANSFER_ADDON.get()).build(null));

    /**
     * Block entity type of the transfer addon. It is not part of {@link #PLUGIN_ADDON_ENTITY}: the placed
     * plugin has behaviour of its own (see {@link ExtensionTransferAddonBlockEntity}), so it uses the menu and the
     * face settings of an Extension Addon - a subclass, which is what its registered block entity has to be.
     * <p>
     * It is registered with {@code ItemApi.BLOCK} in {@link #init()} like the other two addon types, because
     * that is what Oritech's bridge wraps into the loader's item storage; the storage it answers with is
     * empty on purpose.
     */
    public static final RegistrySupplier<BlockEntityType<ExtensionTransferAddonBlockEntity>> EXTENSION_TRANSFER_ADDON_ENTITY =
            BLOCK_ENTITIES.register("extension_transfer_addon",
                    () -> BlockEntityType.Builder.of(ExtensionTransferAddonBlockEntity::new, EXTENSION_TRANSFER_ADDON.get())
                            .build(null));

    /**
     * Block entity type of the transfer preview plugin: the same shape as {@link #EXTENSION_TRANSFER_ADDON_ENTITY} - the plugin
     * is an {@code ExtensionAddonBlockEntity} subclass, so it uses the menu and the per-face settings of an Extension
     * Addon - and its own type because it works on a different machine (see {@code TransferAddonBlockEntity}).
     */
    public static final RegistrySupplier<BlockEntityType<TransferAddonBlockEntity>> TRANSFER_ADDON_ENTITY =
            BLOCK_ENTITIES.register("transfer_addon",
                    () -> BlockEntityType.Builder.of(TransferAddonBlockEntity::new, TRANSFER_ADDON.get())
                            .build(null));

    /**
     * 扩展插件的菜单类型。用 Architectury 的 {@code MenuRegistry.ofExtended}（Oritech 自己的 {@code ModScreens}
     * 就是这么写的）：它把"菜单额外数据"这件事映射到两个加载器各自的机制上。
     */
    /**
     * 菜单类型实例本身。先建实例、注册时返回同一个实例，是因为客户端入口点要在注册窗口**之前**
     * 就用它去登记屏幕工厂（Oritech 的 {@code ModScreens} 正是这个写法：静态字段持有实例，
     * 屏幕工厂直接引用实例而不是 {@code RegistrySupplier#get()}）。在 NeoForge 上，
     * mod 构造阶段 {@code RegistrySupplier#get()} 还会抛 "Registry Object not present"。
     */
    public static final MenuType<ExtensionAddonMenu> EXTENSION_ADDON_MENU_TYPE =
            MenuRegistry.ofExtended(ExtensionAddonMenu::new);

    public static final RegistrySupplier<MenuType<ExtensionAddonMenu>> EXTENSION_ADDON_MENU =
            MENUS.register("extension_addon", () -> EXTENSION_ADDON_MENU_TYPE);

    /**
     * The container behind the 过滤 page of a transfer face. Its own type because the filter is a dozen items and
     * three switches rather than a page of settings, and because it is opened for <em>one filter</em> instead of
     * for a whole block - see {@code FaceFilterMenu}.
     */
    /** 见 {@link #EXTENSION_ADDON_MENU_TYPE}：实例先建好，注册时返回它。 */
    public static final MenuType<FaceFilterMenu> FACE_FILTER_MENU_TYPE =
            MenuRegistry.ofExtended(FaceFilterMenu::new);

    public static final RegistrySupplier<MenuType<FaceFilterMenu>> FACE_FILTER_MENU =
            MENUS.register("face_filter", () -> FACE_FILTER_MENU_TYPE);

    /** Own creative tab, so the blocks are always reachable even if Oritech changes its own tabs. */
    public static final RegistrySupplier<CreativeModeTab> TAB = TABS.register("extension_addons",
            () -> CreativeTabRegistry.create(builder -> builder
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
                        output.accept(CHUNK_ANCHOR_ADDON_ITEM.get());
                        output.accept(EXTENSION_TRANSFER_ADDON_ITEM.get());
                        output.accept(TRANSFER_ADDON_ITEM.get());
                    })));

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
     * Factory of {@link #PLUGIN_ADDON_ENTITY}, used when a saved chunk is loaded again: Oritech's ordinary
     * {@link AddonBlockEntity}, or the anchor's own subclass for the anchor block, both created with this
     * mod's own block entity type (see there why the two argument constructor is not usable).
     * <p>
     * It is a method instead of an inline lambda only because a static field cannot refer to itself by
     * simple name inside its own initializer.
     */
    private static AddonBlockEntity pluginAddonEntity(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChunkAnchorAddonBlock) {
            return new ChunkAnchorAddonBlockEntity(pos, state);
        }
        return new AddonBlockEntity(PLUGIN_ADDON_ENTITY.get(), pos, state);
    }

    /**
     * Block properties of every addon of this mod: the very same ones Oritech gives its own addons.
     * <p>
     * Oritech's reference plugin - the synergy matrix addon ({@code oritech:machine_ultimate_addon},
     * "协同矩阵插件") - is registered in Oritech's {@code BlockContent} as
     * {@code Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()}, so it is hardness 5 /
     * blast resistance 6, metal map colour, iron sound and {@code requiresCorrectToolForDrops}.
     * This method and {@link #wirelessBlockProperties()} are deliberately the same expression, which
     * keeps the two mods' plugin blocks indistinguishable in mining behaviour.
     * <p>
     * The wire flag matters: {@code requiresCorrectToolForDrops} means a block only drops when the
     * tool in hand is a correct one for it, and a pickaxe counts as correct exactly for the blocks in
     * {@code minecraft:mineable/pickaxe}. Oritech lists every one of its addons in that tag; this mod
     * has to do the same for its own blocks, see
     * {@code data/minecraft/tags/block/mineable/pickaxe.json}. Without that tag the blocks would
     * break but drop nothing.
     */
    private static BlockBehaviour.Properties blockProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion();
    }

    /**
     * The wireless addons are ordinary full blocks, so they keep the normal occluding properties.
     * See {@link #blockProperties()} for why the copy of {@link Blocks#IRON_BLOCK} has to be paired with
     * the {@code mineable/pickaxe} tag.
     */
    private static BlockBehaviour.Properties wirelessBlockProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK);
    }

    /** Registry path of the wireless variant of a type, e.g. {@code wireless_extension_addon_1}. */
    private static String wirelessId(ExtensionAddonType type) {
        return "wireless_" + type.id();
    }

    /**
     * Drops the runtime caches that describe machines and their hosts, called from each loader's server-stopped
     * hook. They are static and keyed by dimension and position, so without this a second world in the same
     * process would read the first one's settings, and the cached block entities would keep the old levels alive.
     */
    public static void clearServerCaches() {
        MachineFaceConfigs.clear();
        MachineSlotRoles.clear();
        FacePluginStorage.clear();
    }

    /**
     * 共用的初始化入口，由每个加载器的入口点在注册完配置之后调用。
     * <p>
     * 顺序有依赖关系，不能随意调整：先让加载器补上旧注册名的别名，然后按
     * <b>方块 → 物品 → 方块实体 → 菜单 → 创造标签</b> 刷新（BlockItem 的工厂要能取到方块，
     * 方块实体的工厂要能取到方块，创造标签最后）。
     */
    public static void init() {
        // 旧注册名别名：NeoForge 用 DeferredRegister#addAlias，Fabric 没有等价机制（no-op）。
        // 必须在任何注册表 flush 之前。
        AddonsPlatform.INSTANCE.registerLegacyAliases();

        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITIES.register();
        MENUS.register();
        TABS.register();

        // 网络：自定义包走 Oritech 自己的跨加载器网络层，它会处理 S2C 接收器要延迟到客户端初始化等加载器差异
        ProxyNetworking.register();
        TransferNetworking.register();
        FilterNetworking.register();
        RedstoneNetworking.register();

        // 服务器 tick：区块锚点的强制加载对账（Architectury TickEvent）
        AnchorForceLoad.init();

        // 机器延伸器的一个面要成为真正的物品连接。这里的存储是 Oritech 的 ItemApi.InventoryStorage
        // （加载器中立），把它包成加载器自己的存储接口由平台实现负责：
        //   NeoForge → Capabilities.ItemHandler.BLOCK 的 IItemHandler
        //   Fabric   → ItemStorage.SIDED 的 Storage<ItemVariant>
        AddonsPlatform.INSTANCE.registerFaceItemProvider(() -> BlockEntitiesContent.ADDON_ENTITY,
                (blockEntity, side) -> blockEntity instanceof AddonBlockEntity extender
                        ? ExtenderFaceStorage.handlerAt(extender, side)
                        : null);

        // 同一个延伸器的面在挂着扩展传输插件时也回答流体：哪个面是连接、属于哪台机器、什么模式，
        // 全部复用物品侧的判断（见 ExtenderFaceFluidStorage），这里只把结果注册给加载器。
        AddonsPlatform.INSTANCE.registerFaceFluidProvider(() -> BlockEntitiesContent.ADDON_ENTITY,
                ExtenderFaceFluidStorage::storageFor);

        // 让 Oritech 把本模组方块实体的能量存储暴露给 Oritech 自己的能量网络 *以及* 加载器的能量 API：
        // 它的桥只包装在这里注册过的方块实体类型，所以这一句是"装入 acceptor 插件后能当能量输入"的关键。
        EnergyApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) EXTENSION_ADDON_ENTITY.get());
        EnergyApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) WIRELESS_EXTENSION_ADDON_ENTITY.get());

        // 同一座桥的物品侧：这是 Item Proxy 页面能工作的原因。Oritech 的桥把本模组方块实体的
        // ItemApi.BlockProvider（见 ExtensionAddonBlockEntity#getInventoryStorage）包成加载器的物品存储，
        // 于是 Oritech 自己的物品管道（通过 ItemApi 找邻居容器）以及漏斗等都能看到某个面绑定的机器槽位。
        //
        // 放置形态的传输插件也注册了物品，但它自己的存储故意什么都不答：它服务的机器是通过它挂着的
        // 延伸器的面到达的，永远不是通过它自己的面（见 ExtensionTransferAddonBlockEntity#getInventoryStorage）。
        ItemApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) EXTENSION_ADDON_ENTITY.get());
        ItemApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) WIRELESS_EXTENSION_ADDON_ENTITY.get());
        ItemApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) EXTENSION_TRANSFER_ADDON_ENTITY.get());

        // 同一座桥的流体侧：扩展插件里放入蒸汽锅炉插件时，它的面直接暴露机器的锅炉储罐，
        // 于是可以从扩展插件注水、抽蒸汽，和贴着机器放锅炉插件一样（见 getFluidStorage）。
        FluidApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) EXTENSION_ADDON_ENTITY.get());
        FluidApi.BLOCK.registerBlockEntity(() -> (BlockEntityType<?>) WIRELESS_EXTENSION_ADDON_ENTITY.get());

        LOGGER.info("Oritech Addons One loaded: {}, {}, {} and the wireless variants {} registered",
                EXTENSION_ADDON_1.getId(), EXTENSION_ADDON_2.getId(), EXTENSION_ADDON_3.getId(),
                WIRELESS_EXTENSION_ADDON_1.getId());
    }

    /**
     * Resolves the plugin sets once after registration and logs them, which makes it easy to check
     * which plugins each type accepts.
     * <p>
     * <b>不能在 {@link #init()} 里调用</b>：它会读 Oritech 的 {@code BlockContent}（见 {@code ExtensionAddonType}），
     * 而 mod 构造阶段方块注册表已冻结。所以回到原本的时机 —— NeoForge 走
     * {@code FMLCommonSetupEvent#enqueueWork}，Fabric 在初始化末尾（那边注册表是开放的）。
     */
    public static void logSetup() {
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

        // Types I and III accept plugins by category, so log the category of every registered addon block.
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
    }
}
