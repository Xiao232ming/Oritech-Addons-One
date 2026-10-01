package io.github.xiao232ming.oritechaddonsone.network;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Carries the capacity bonus of a tank in the very payload Oritech uses to sync that tank's contents.
 * <p>
 * Oritech syncs a machine's tank with a {@code @SyncField} on the storage object and sends only the
 * contents (through {@code UpdatableField#getDeltaCodec}). The capacity is <b>not</b> part of that
 * payload: the client builds its tank from the same constructor as the server, so it always reports the
 * capacity Oritech was compiled with. The bonus of this mod's tank addons is computed by
 * {@code MachineStorageBonuses} from the addons attached to the machine, which is server side
 * information, so without this the client reports 8000 mB while the server may happily have stored
 * 16000 mB in it - and every client side consumer of the tank divides by the smaller number. Oritech's
 * world renderers compute the drawn fluid height as {@code amount / capacity}
 * ({@code RefineryRenderer#buildCube}, {@code CentrifugeRenderer#addRenderData},
 * {@code TaintedRefineryRenderer#buildCube}), so a tank above its base capacity is drawn at more than
 * full height and the fluid cuboid grows out of the machine model.
 * <p>
 * Instead of inventing a second packet for the number, the bonus is appended to the tank's own sync
 * payload by this codec, and the client writes it back into the tank while decoding. That makes the
 * capacity and the contents arrive in the <b>same</b> packet, so the client can never briefly - or
 * permanently - see an amount that its capacity does not cover, and no machine class, addon block
 * position or addon inventory has to be mirrored on the client to work it out. The number sent is the
 * one the server actually applied, so the server stays the only authority and nothing the client
 * computes can drift away from it.
 * <p>
 * A tank without a bonus sends {@code 0} and keeps exactly the capacity Oritech gave it, so machines
 * that carry no tank addon are unaffected.
 * <p>
 * The delegates are read through a raw cast because Java's wildcard capture does not allow calling
 * {@code encode} / {@code decode} on a {@code StreamCodec<? extends ByteBuf, T>}.
 */
public final class TankSyncCodec {

    private TankSyncCodec() {
    }

    /**
     * Wraps the codec that syncs a tank's contents so that the tank's capacity bonus travels with it.
     *
     * @param contents  Oritech's own codec for the contents of the tank
     * @param bonus     reads the bonus the server applied to this tank (or {@code 0})
     * @param applyBonus writes a received bonus into this tank, on the client
     * @return the codec Oritech should use for this tank
     */
    public static <T> StreamCodec<ByteBuf, T> withCapacityBonus(
            StreamCodec<? extends ByteBuf, T> contents, LongSupplier bonus, LongConsumer applyBonus) {

        return new StreamCodec<ByteBuf, T>() {

            @Override
            public T decode(ByteBuf buffer) {
                var value = decodeContents(contents, buffer);
                // The bonus is written by the server and applied to the local tank by the client; it is
                // read after the contents so both are complete before Oritech's handleDeltaUpdate runs.
                applyBonus.accept(ByteBufCodecs.VAR_LONG.decode(buffer));
                return value;
            }

            @Override
            public void encode(ByteBuf buffer, T value) {
                encodeContents(contents, buffer, value);
                ByteBufCodecs.VAR_LONG.encode(buffer, bonus.getAsLong());
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T decodeContents(StreamCodec<? extends ByteBuf, T> contents, ByteBuf buffer) {
        return ((StreamCodec<ByteBuf, T>) contents).decode(buffer);
    }

    @SuppressWarnings("unchecked")
    private static <T> void encodeContents(StreamCodec<? extends ByteBuf, T> contents, ByteBuf buffer, T value) {
        ((StreamCodec<ByteBuf, T>) contents).encode(buffer, value);
    }
}
