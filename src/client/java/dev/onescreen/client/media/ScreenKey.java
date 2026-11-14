package dev.onescreen.client.media;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ScreenKey(ResourceKey<Level> dimension, BlockPos pos) {
	public ScreenKey {
		Objects.requireNonNull(dimension, "dimension");
		pos = Objects.requireNonNull(pos, "pos").immutable();
	}
}
