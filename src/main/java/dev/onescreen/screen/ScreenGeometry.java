package dev.onescreen.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Shared orientation and nine-way anchor math for server audio and client rendering.
 */
public final class ScreenGeometry {
	private ScreenGeometry() {
	}

	public static Vec3 right(final Direction facing) {
		return right(facing, 0.0F);
	}

	public static Vec3 right(final Direction facing, final float rotationDegrees) {
		Vec3 baseRight = baseRight(facing);
		if (rotationDegrees == 0.0F) {
			return baseRight;
		}
		Vec3 baseUp = baseUp(facing);
		double radians = Math.toRadians(rotationDegrees);
		return baseRight.scale(Math.cos(radians)).add(baseUp.scale(Math.sin(radians)));
	}

	public static Vec3 up(final Direction facing) {
		return up(facing, 0.0F);
	}

	public static Vec3 up(final Direction facing, final float rotationDegrees) {
		Vec3 baseUp = baseUp(facing);
		if (rotationDegrees == 0.0F) {
			return baseUp;
		}
		Vec3 baseRight = baseRight(facing);
		double radians = Math.toRadians(rotationDegrees);
		return baseUp.scale(Math.cos(radians)).subtract(baseRight.scale(Math.sin(radians)));
	}

	private static Vec3 baseRight(final Direction facing) {
		if (facing.getAxis().isHorizontal()) {
			return new Vec3(facing.getStepZ(), 0.0D, -facing.getStepX());
		}
		return new Vec3(1.0D, 0.0D, 0.0D);
	}

	private static Vec3 baseUp(final Direction facing) {
		return switch (facing) {
			case UP -> new Vec3(0.0D, 0.0D, -1.0D);
			case DOWN -> new Vec3(0.0D, 0.0D, 1.0D);
			default -> new Vec3(0.0D, 1.0D, 0.0D);
		};
	}

	public static float horizontalOffset(final float width, final ScreenAlignment alignment) {
		float distance = width >= 1.0F ? (width - 1.0F) * 0.5F : (1.0F - width) * 0.5F;
		float sign = width >= 1.0F ? -1.0F : 1.0F;
		return alignment.horizontal() * distance * sign;
	}

	public static float verticalOffset(final float height, final ScreenAlignment alignment) {
		float distance = height >= 1.0F ? (height - 1.0F) * 0.5F : (1.0F - height) * 0.5F;
		float sign = height >= 1.0F ? 1.0F : -1.0F;
		return alignment.vertical() * distance * sign;
	}

	public static Vec3 mediaCenter(final BlockPos pos, final Direction facing, final ScreenSettings settings) {
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 right = right(facing, settings.rotationDegrees());
		Vec3 up = up(facing, settings.rotationDegrees());
		float horizontal = horizontalOffset(settings.width(), settings.alignment());
		float vertical = verticalOffset(settings.height(), settings.alignment());
		return center.add(right.scale(horizontal)).add(up.scale(vertical));
	}

	public static double displayRadius(final ScreenSettings settings) {
		return Math.hypot(settings.width(), settings.height()) * 0.5D;
	}
}