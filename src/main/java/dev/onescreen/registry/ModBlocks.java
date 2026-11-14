package dev.onescreen.registry;

import dev.onescreen.OneScreen;
import dev.onescreen.block.ScreenBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

public final class ModBlocks {
	public static final ResourceKey<net.minecraft.world.level.block.Block> SCREEN_KEY =
		ResourceKey.create(Registries.BLOCK, OneScreen.id("screen"));
	public static final ResourceKey<Item> SCREEN_ITEM_KEY =
		ResourceKey.create(Registries.ITEM, OneScreen.id("screen"));
	public static final ResourceKey<CreativeModeTab> SCREEN_TAB_KEY =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, OneScreen.id("screen"));

	public static final ScreenBlock SCREEN = Registry.register(
		BuiltInRegistries.BLOCK,
		SCREEN_KEY,
		new ScreenBlock(BlockBehaviour.Properties.of()
			.setId(SCREEN_KEY)
			.strength(1.5F, 6.0F)
			.sound(SoundType.GLASS)
			.noCollision()
			.noOcclusion())
	);

	public static final BlockItem SCREEN_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		SCREEN_ITEM_KEY,
		new BlockItem(SCREEN, new Item.Properties()
			.setId(SCREEN_ITEM_KEY)
			.useBlockDescriptionPrefix())
	);

	public static final CreativeModeTab SCREEN_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		SCREEN_TAB_KEY,
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.onescreen"))
			.icon(() -> new ItemStack(SCREEN_ITEM))
			.displayItems((parameters, output) -> output.accept(SCREEN_ITEM))
			.build()
	);

	private ModBlocks() {
	}

	public static void initialize() {
	}
}