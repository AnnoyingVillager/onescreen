package dev.onescreen.client.render;

import dev.onescreen.client.media.MediaState;
import dev.onescreen.screen.ScreenAlignment;
import dev.onescreen.screen.ScreenFitMode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class ScreenRenderState extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState {
	public Direction facing = Direction.NORTH;
	public ScreenAlignment alignment = ScreenAlignment.CENTER;
	public ScreenFitMode fitMode = ScreenFitMode.FIT;
	public float width = 4.0F;
	public float height = 2.25F;
	public float renderDistance = 96.0F;
	public boolean hasUrl;
	public @Nullable Identifier mediaTexture;
	public @Nullable RenderType mediaRenderType;
	public int mediaWidth;
	public int mediaHeight;
	public MediaState mediaState = MediaState.IDLE;
}
