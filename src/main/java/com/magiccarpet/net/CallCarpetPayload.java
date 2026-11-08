package com.magiccarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.magiccarpet.MagicCarpet;

/** Sent when the player presses the call key (C). */
public final class CallCarpetPayload implements CustomPacketPayload {
	public static final CallCarpetPayload INSTANCE = new CallCarpetPayload();
	public static final CustomPacketPayload.Type<CallCarpetPayload> TYPE = new CustomPacketPayload.Type<>(MagicCarpet.id("call_carpet"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CallCarpetPayload> CODEC = StreamCodec.unit(INSTANCE);

	private CallCarpetPayload() {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
