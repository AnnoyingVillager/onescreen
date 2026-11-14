package dev.onescreen.registry;

import dev.onescreen.OneScreen;
import dev.onescreen.block.ScreenBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<ScreenBlockEntity> SCREEN = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		OneScreen.id("screen"),
		new BlockEntityType<>(ScreenBlockEntity::new, Set.of(ModBlocks.SCREEN))
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
	}
}