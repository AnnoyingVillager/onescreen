package dev.onescreen.editor;

import dev.onescreen.OneScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScreenReloadPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<ScreenReloadPayload> TYPE = new Type<>(OneScreen.id("reload_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenReloadPayload> CODEC = StreamCodec.of(
		(buffer, payload) -> buffer.writeBlockPos(payload.pos()),
		buffer -> new ScreenReloadPayload(buffer.readBlockPos())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}