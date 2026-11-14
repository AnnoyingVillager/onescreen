package dev.onescreen.client.media;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MediaSnapshot(
	MediaState state,
	@Nullable Identifier texture,
	int mediaWidth,
	int mediaHeight,
	@Nullable String error
) {
	public static final MediaSnapshot EMPTY = new MediaSnapshot(MediaState.IDLE, null, 0, 0, null);

	public boolean hasTexture() {
		return this.texture != null;
	}
}
