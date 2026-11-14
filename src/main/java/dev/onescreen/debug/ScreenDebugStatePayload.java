package dev.onescreen.debug;

import dev.onescreen.OneScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScreenDebugStatePayload(boolean enabled) implements CustomPacketPayload {
	public static final Type<ScreenDebugStatePayload> TYPE = new Type<>(OneScreen.id("screen_debug_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenDebugStatePayload> CODEC = StreamCodec.of(
		(buffer, payload) -> buffer.writeBoolean(payload.enabled()),
		buffer -> new ScreenDebugStatePayload(buffer.readBoolean())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}