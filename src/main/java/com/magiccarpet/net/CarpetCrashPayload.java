package com.magiccarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.magiccarpet.MagicCarpet;

/** Sent by the pilot's game when the carpet really hits something, with the speed (blocks per tick) at impact. */
public record CarpetCrashPayload(float speed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CarpetCrashPayload> TYPE = new CustomPacketPayload.Type<>(MagicCarpet.id("carpet_crash"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CarpetCrashPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.FLOAT, CarpetCrashPayload::speed, CarpetCrashPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
