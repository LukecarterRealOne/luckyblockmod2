package com.lucky.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: screen flash + camera shake. */
public record FxPayload(float flash, float shake, int duration, int color) implements CustomPayload {
    public static final CustomPayload.Id<FxPayload> ID =
            new CustomPayload.Id<>(Identifier.of(LuckyBlockMod.MOD_ID, "fx"));

    public static final PacketCodec<RegistryByteBuf, FxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, FxPayload::flash,
            PacketCodecs.FLOAT, FxPayload::shake,
            PacketCodecs.INTEGER, FxPayload::duration,
            PacketCodecs.INTEGER, FxPayload::color,
            FxPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
