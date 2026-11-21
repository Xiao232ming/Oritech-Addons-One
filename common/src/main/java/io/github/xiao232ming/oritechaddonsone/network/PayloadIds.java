package io.github.xiao232ming.oritechaddonsone.network;

import net.minecraft.resources.ResourceLocation;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Names the payloads of this mod. One id per packet, in this mod's namespace - every payload declares its own
 * {@code TYPE} here, so the four networking classes no longer carry a private copy of the same three lines.
 */
final class PayloadIds {

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
    }

    private PayloadIds() {
    }
}
