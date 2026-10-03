package io.github.xiao232ming.oritechaddonsone;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry aliases that keep worlds saved by the previous naming of the two transfer plugins readable.
 * <p>
 * <b>Why aliases and not a remapping event.</b> This mod used to register the cube net plugin - the one that
 * works with the machine extender - as {@code oritechaddonsone:transfer_addon}, and the plugin that shows the
 * 3D model of the machine as {@code oritechaddonsone:transfer_preview_addon}. The two families have since
 * swapped their ids: the cube net plugin is now {@code extension_transfer_addon} and the model plugin is
 * {@code transfer_addon}. A world that already contains a placed plugin therefore has chunks that name a block
 * id which no longer exists, and item stacks that name an item id which no longer exists.
 * <p>
 * Forge's and old NeoForge's answer to that was {@code MissingMappingsEvent}, which let a mod rewrite the
 * entries of a saved registry snapshot as it was loaded. <b>NeoForge 21.1.200 has no such event either.</b>
 * {@code MissingMappingsEvent} is not present in {@code neoforge-21.1.200} at all (neither is
 * {@code MappingType}), and the registry load does not fire any remapping event: it collects the entries it
 * could not resolve, logs one warning listing them, and leaves them to be dropped when the chunk is written
 * again. The supported replacement is a registry alias, which {@code DeferredRegister} applies to the live
 * registry during registration and which every name based lookup resolves through - so the old name behaves
 * exactly as if it still existed. That is what this class registers.
 * <p>
 * <b>What is covered.</b> Blocks, items and block entity types all resolve through
 * {@code Registry#get(ResourceLocation)}: a chunk's block state palette is read with {@code BlockState.CODEC}
 * ({@code BuiltInRegistries.BLOCK.byNameCodec()}), an item stack with {@code BuiltInRegistries.ITEM}'s name
 * codec, and a block entity's {@code id} tag with {@code BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec()}
 * (see {@code BlockEntity#loadStatic}). A placed plugin therefore loads as the block it now is, keeps its
 * block entity and its saved face settings, and the block drops its own item again, because the loot table
 * follows the block. The aliases are also part of the registry snapshot NeoForge keeps, so they are re-applied
 * on every load rather than only once.
 * <p>
 * <b>What is not covered.</b> The alias only rewrites the path: the old {@code transfer_addon} id now means
 * {@code extension_transfer_addon} and the old {@code transfer_preview_addon} id means
 * {@code transfer_addon}, which is exactly the swap. Nothing else of a saved world refers to either plugin by
 * name - the recipes, advancements and the creative tab all name the new ids.
 */
public final class TransferAddonMigration {

    /** Namespace of every entry this mod registers. */
    private static final String NAMESPACE = OritechAddonsOne.MODID;

    private TransferAddonMigration() {
    }

    /**
     * Registers the aliases of both renamed families.
     * <p>
     * It has to run before the {@code RegisterEvent} of the registries it touches, so it is called from the mod
     * constructor, where the deferred registers are still only being wired to the event bus.
     */
    public static void register() {
        // The cube net plugin: transfer_addon -> extension_transfer_addon, in all three registries.
        alias(OritechAddonsOne.BLOCKS, "transfer_addon", "extension_transfer_addon");
        alias(OritechAddonsOne.ITEMS, "transfer_addon", "extension_transfer_addon");
        alias(OritechAddonsOne.BLOCK_ENTITIES, "transfer_addon", "extension_transfer_addon");

        // The model plugin: transfer_preview_addon -> transfer_addon. Its old block entity type was registered
        // under the same path as its block, which is why the alias repeats for the type.
        alias(OritechAddonsOne.BLOCKS, "transfer_preview_addon", "transfer_addon");
        alias(OritechAddonsOne.ITEMS, "transfer_preview_addon", "transfer_addon");
        alias(OritechAddonsOne.BLOCK_ENTITIES, "transfer_preview_addon", "transfer_addon");
    }

    /**
     * Adds one alias to one deferred register, in this mod's namespace.
     *
     * @param register the deferred register whose registry the alias belongs to
     * @param from     the registry path used before the rename
     * @param to       the registry path used now
     */
    private static void alias(DeferredRegister<?> register, String from, String to) {
        register.addAlias(ResourceLocation.fromNamespaceAndPath(NAMESPACE, from),
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, to));
    }
}
