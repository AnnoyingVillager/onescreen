package dev.onescreen.editor;

import dev.onescreen.OneScreen;
import dev.onescreen.screen.ScreenAlignment;
import dev.onescreen.screen.ScreenFitMode;
import dev.onescreen.screen.ScreenSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScreenSettingsUpdatePayload(
	BlockPos pos,
	String url,
	float width,
	float height,
	float renderDistance,
	float audioDistance,
	ScreenAlignment alignment,
	ScreenFitMode fitMode,
	float rotationDegrees,
	boolean hideFrame,
	boolean loop,
	boolean refreshOnResume
) implements CustomPacketPayload {
	public static final Type<ScreenSettingsUpdatePayload> TYPE =
		new Type<>(OneScreen.id("update_screen_settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenSettingsUpdatePayload> CODEC = StreamCodec.of(
		(buffer, payload) -> {
			buffer.writeBlockPos(payload.pos());
			buffer.writeUtf(payload.url(), ScreenSettings.MAX_URL_LENGTH);
			buffer.writeFloat(payload.width());
			buffer.writeFloat(payload.height());
			buffer.writeFloat(payload.renderDistance());
			buffer.writeFloat(payload.audioDistance());
			buffer.writeEnum(payload.alignment());
			buffer.writeEnum(payload.fitMode());
			buffer.writeFloat(payload.rotationDegrees());
			buffer.writeBoolean(payload.hideFrame());
			buffer.writeBoolean(payload.loop());
			buffer.writeBoolean(payload.refreshOnResume());
		},
		buffer -> new ScreenSettingsUpdatePayload(
			buffer.readBlockPos(),
			buffer.readUtf(ScreenSettings.MAX_URL_LENGTH),
			buffer.readFloat(),
			buffer.readFloat(),
			buffer.readFloat(),
			buffer.readFloat(),
			ScreenAlignment.byOrdinal(buffer.readVarInt()),
			ScreenFitMode.byOrdinal(buffer.readVarInt()),
			buffer.readFloat(),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readBoolean()
		)
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
