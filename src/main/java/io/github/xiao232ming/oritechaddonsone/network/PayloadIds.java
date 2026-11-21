package io.github.xiao232ming.oritechaddonsone.network;

import net.minecraft.resources.Identifier;

import io.github.xiao232ming.oritechaddonsone.OritechAddonsOne;

/**
 * Names the payloads of this mod. One id per packet, in this mod's namespace - every payload declares its own
 * {@code TYPE} here, so the four networking classes no longer carry a private copy of the same three lines.
 */
final class PayloadIds {

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OritechAddonsOne.MODID, path);
    }

    private PayloadIds() {
    }
}
