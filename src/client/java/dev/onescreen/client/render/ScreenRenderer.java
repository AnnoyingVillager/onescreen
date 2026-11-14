package dev.onescreen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onescreen.OneScreen;
import dev.onescreen.block.ScreenBlock;
import dev.onescreen.block.ScreenBlockEntity;
import dev.onescreen.client.debug.ClientScreenDebugState;
import dev.onescreen.client.media.MediaSnapshot;
import dev.onescreen.client.media.MediaState;
import dev.onescreen.client.media.ScreenMediaManager;
import dev.onescreen.screen.ScreenFitGeometry;
import dev.onescreen.screen.ScreenGeometry;
import dev.onescreen.screen.ScreenSettings;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity, ScreenRenderState> {
	private static final Identifier PLACEHOLDER_TEXTURE = OneScreen.id("textures/block/screen_front.png");
	private static final RenderType PLACEHOLDER_RENDER_TYPE = RenderTypes.entityCutout(PLACEHOLDER_TEXTURE);
	private static final float SURFACE_OFFSET = 0.4365F;
	private static final int FULL_BRIGHT = 0x00F000F0;
	private static final int ERROR_TINT = 0xFFFF7777;

	@Override
	public ScreenRenderState createRenderState() {
		return new ScreenRenderState();
	}

	@Override
	public void extractRenderState(
		final ScreenBlockEntity blockEntity,
		final ScreenRenderState state,
		final float partialTicks,
		final Vec3 cameraPosition,
		final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		ScreenSettings settings = blockEntity.getSettings();
		state.facing = blockEntity.getBlockState().getValue(ScreenBlock.FACING);
		state.alignment = settings.alignment();
		state.fitMode = settings.fitMode();
		state.width = settings.width();
		state.height = settings.height();
		state.renderDistance = settings.renderDistance();
		state.hasUrl = settings.hasUrl();

		ScreenMediaManager mediaManager = ScreenMediaManager.get();
		mediaManager.track(blockEntity);
		MediaSnapshot media = mediaManager.snapshot(blockEntity);
		state.mediaTexture = media.texture();
		state.mediaRenderType = state.mediaTexture == null ? null : RenderTypes.entityCutout(state.mediaTexture, false);
		state.mediaWidth = media.mediaWidth();
		state.mediaHeight = media.mediaHeight();
		state.mediaState = media.state();
	}

	@Override
	public void submit(
		final ScreenRenderState state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState camera
	) {
		RenderType mediaRenderType = state.mediaRenderType;
		if (mediaRenderType == null) {
			if (!ClientScreenDebugState.isEnabled()) {
				return;
			}
			submitNodeCollector.submitCustomGeometry(poseStack, PLACEHOLDER_RENDER_TYPE, (pose, buffer) -> {
				submitQuad(state, pose, buffer, state.width, state.height, 0.0F, 0.0F, 1.0F, 1.0F, tint(state));
			});
			return;
		}

		submitNodeCollector.submitCustomGeometry(poseStack, mediaRenderType, (pose, buffer) -> {
			ScreenFitGeometry.Mapping mapping = ScreenFitGeometry.calculate(
				state.fitMode,
				state.width,
				state.height,
				state.mediaWidth,
				state.mediaHeight
			);
			submitQuad(
				state,
				pose,
				buffer,
				mapping.width(),
				mapping.height(),
				mapping.u0(),
				mapping.v0(),
				mapping.u1(),
				mapping.v1(),
				-1
			);
		});
	}

	private static int tint(final ScreenRenderState state) {
		return state.mediaState == MediaState.ERROR ? ERROR_TINT : -1;
	}

	private static void submitQuad(
		final ScreenRenderState state,
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float quadWidth,
		final float quadHeight,
		final float u0,
		final float v0,
		final float u1,
		final float v1,
		final int color
	) {
		Direction facing = state.facing;
		Vec3 right = ScreenGeometry.right(facing);
		Vec3 up = ScreenGeometry.up(facing);
		float horizontalOffset = ScreenGeometry.horizontalOffset(state.width, state.alignment);
		float verticalOffset = ScreenGeometry.verticalOffset(state.height, state.alignment);
		float halfWidth = quadWidth * 0.5F;
		float halfHeight = quadHeight * 0.5F;
		Vec3 center = new Vec3(
			0.5D - facing.getStepX() * SURFACE_OFFSET,
			0.5D - facing.getStepY() * SURFACE_OFFSET,
			0.5D - facing.getStepZ() * SURFACE_OFFSET
		).add(right.scale(horizontalOffset)).add(up.scale(verticalOffset));
		Vec3 rightOffset = right.scale(halfWidth);
		Vec3 upOffset = up.scale(halfHeight);

		Vec3 bottomLeft = center.subtract(rightOffset).subtract(upOffset);
		Vec3 bottomRight = center.add(rightOffset).subtract(upOffset);
		Vec3 topRight = center.add(rightOffset).add(upOffset);
		Vec3 topLeft = center.subtract(rightOffset).add(upOffset);

		addVertex(buffer, pose, bottomLeft, u0, v1, facing, color);
		addVertex(buffer, pose, bottomRight, u1, v1, facing, color);
		addVertex(buffer, pose, topRight, u1, v0, facing, color);
		addVertex(buffer, pose, topLeft, u0, v0, facing, color);
	}

	private static void addVertex(
		final VertexConsumer buffer,
		final PoseStack.Pose pose,
		final Vec3 position,
		final float u,
		final float v,
		final Direction facing,
		final int color
	) {
		buffer.addVertex(pose, (float)position.x, (float)position.y, (float)position.z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(FULL_BRIGHT)
			.setNormal(pose, facing.getStepX(), facing.getStepY(), facing.getStepZ());
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return (int)Math.ceil(ScreenSettings.HARD_MAX_RENDER_DISTANCE);
	}

	@Override
	public boolean shouldRender(final ScreenBlockEntity blockEntity, final Vec3 cameraPosition) {
		ScreenSettings settings = blockEntity.getSettings();
		Vec3 center = ScreenGeometry.mediaCenter(
			blockEntity.getBlockPos(),
			blockEntity.getBlockState().getValue(ScreenBlock.FACING),
			settings
		);
		return center.closerThan(cameraPosition, settings.renderDistance() + ScreenGeometry.displayRadius(settings));
	}
}
