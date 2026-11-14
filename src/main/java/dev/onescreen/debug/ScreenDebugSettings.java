package dev.onescreen.debug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.onescreen.OneScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-scoped rendering diagnostics. Debug material is hidden unless explicitly enabled.
 */
public final class ScreenDebugSettings extends SavedData {
	private static final Codec<ScreenDebugSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("show_material", false).forGetter(ScreenDebugSettings::isEnabled)
	).apply(instance, ScreenDebugSettings::new));
	private static final SavedDataType<ScreenDebugSettings> TYPE = new SavedDataType<>(
		OneScreen.id("screen_debug"),
		ScreenDebugSettings::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private boolean enabled;

	public ScreenDebugSettings() {
		this(false);
	}

	private ScreenDebugSettings(final boolean enabled) {
		this.enabled = enabled;
	}

	public static ScreenDebugSettings get(final MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(final boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		this.setDirty();
	}
}