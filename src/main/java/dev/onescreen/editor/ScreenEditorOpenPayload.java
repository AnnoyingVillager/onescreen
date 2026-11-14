package dev.onescreen.editor;

import dev.onescreen.OneScreen;
import dev.onescreen.screen.ScreenAlignment;
import dev.onescreen.screen.ScreenFitMode;
import dev.onescreen.screen.ScreenSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScreenEditorOpenPayload(
	BlockPos pos,
	String url,
	float width,
	float height,
	float renderDistance,
	float audioDistance,
	ScreenAlignment alignment,
	ScreenFitMode fitMode,
	boolean loop,
	boolean refreshOnResume,
	float maxRenderDistance,
	float maxAudioDistance
) implements CustomPacketPayload {
	public static final Type<ScreenEditorOpenPayload> TYPE =
		new Type<>(OneScreen.id("open_screen_editor"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenEditorOpenPayload> CODEC = StreamCodec.of(
		(buffer, payload) -> {
			buffer.writeBlockPos(payload.pos());
			buffer.writeUtf(payload.url(), ScreenSettings.MAX_URL_LENGTH);
			buffer.writeFloat(payload.width());
			buffer.writeFloat(payload.height());
			buffer.writeFloat(payload.renderDistance());
			buffer.writeFloat(payload.audioDistance());
			buffer.writeEnum(payload.alignment());
			buffer.writeEnum(payload.fitMode());
			buffer.writeBoolean(payload.loop());
			buffer.writeBoolean(payload.refreshOnResume());
			buffer.writeFloat(payload.maxRenderDistance());
			buffer.writeFloat(payload.maxAudioDistance());
		},
		buffer -> new ScreenEditorOpenPayload(
			buffer.readBlockPos(),
			buffer.readUtf(ScreenSettings.MAX_URL_LENGTH),
			buffer.readFloat(),
			buffer.readFloat(),
			buffer.readFloat(),
			buffer.readFloat(),
			ScreenAlignment.byOrdinal(buffer.readVarInt()),
			ScreenFitMode.byOrdinal(buffer.readVarInt()),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readFloat(),
			buffer.readFloat()
		)
	);

	public static ScreenEditorOpenPayload fromScreen(
		final BlockPos pos,
		final ScreenSettings settings,
		final float maxRenderDistance,
		final float maxAudioDistance
	) {
		return new ScreenEditorOpenPayload(
			pos.immutable(),
			settings.url(),
			settings.width(),
			settings.height(),
			settings.renderDistance(),
			settings.audioDistance(),
			settings.alignment(),
			settings.fitMode(),
			settings.loop(),
			settings.refreshOnResume(),
			maxRenderDistance,
			maxAudioDistance
		);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
