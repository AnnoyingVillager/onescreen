package dev.onescreen.block;

import dev.onescreen.registry.ModBlockEntities;
import dev.onescreen.screen.ScreenAlignment;
import dev.onescreen.screen.ScreenFitMode;
import dev.onescreen.screen.ScreenSettings;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class ScreenBlockEntity extends BlockEntity {
	private ScreenSettings settings = ScreenSettings.DEFAULT;
	private int loadRevision;

	public ScreenBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlockEntities.SCREEN, pos, state);
	}

	public ScreenSettings getSettings() {
		return this.settings;
	}

	public float getScreenWidth() {
		return this.settings.width();
	}

	public float getScreenHeight() {
		return this.settings.height();
	}

	public String getUrl() {
		return this.settings.url();
	}

	public int getLoadRevision() {
		return this.loadRevision;
	}

	public void setSettings(final ScreenSettings nextSettings) {
		Objects.requireNonNull(nextSettings, "nextSettings");
		if (this.settings.equals(nextSettings)) {
			return;
		}

		this.settings = nextSettings;
		this.setChanged();
		this.syncToClients();
	}

	public void setUrl(final String url) {
		this.setSettings(this.settings.withUrl(url));
	}

	public void setSize(final float width, final float height) {
		this.setSettings(this.settings.withSize(width, height));
	}

	public void setRenderDistance(final float renderDistance) {
		this.setSettings(this.settings.withRenderDistance(renderDistance));
	}

	public void setAudioDistance(final float audioDistance) {
		this.setSettings(this.settings.withAudioDistance(audioDistance));
	}

	public void setAlignment(final ScreenAlignment alignment) {
		this.setSettings(this.settings.withAlignment(alignment));
	}

	public void setFitMode(final ScreenFitMode fitMode) {
		this.setSettings(this.settings.withFitMode(fitMode));
	}

	public void setVolume(final float volume) {
		this.setSettings(this.settings.withVolume(volume));
	}

	public void setMuted(final boolean muted) {
		this.setSettings(this.settings.withMuted(muted));
	}

	public void setLoop(final boolean loop) {
		this.setSettings(this.settings.withLoop(loop));
	}

	public void setRefreshOnResume(final boolean refreshOnResume) {
		this.setSettings(this.settings.withRefreshOnResume(refreshOnResume));
	}

	public int reload() {
		this.loadRevision = this.loadRevision == Integer.MAX_VALUE ? 1 : this.loadRevision + 1;
		this.setChanged();
		this.syncToClients();
		return this.loadRevision;
	}

	private void syncToClients() {
		if (this.level != null && !this.level.isClientSide()) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.putString("url", this.settings.url());
		output.putFloat("width", this.settings.width());
		output.putFloat("height", this.settings.height());
		output.putFloat("renderDistance", this.settings.renderDistance());
		output.putFloat("audioDistance", this.settings.audioDistance());
		output.putString("alignment", this.settings.alignment().name());
		output.putString("fitMode", this.settings.fitMode().name());
		output.putFloat("volume", this.settings.volume());
		output.putBoolean("muted", this.settings.muted());
		output.putBoolean("loop", this.settings.loop());
		output.putBoolean("refreshOnResume", this.settings.refreshOnResume());
		output.putInt("loadRevision", this.loadRevision);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		ScreenAlignment alignment = ScreenAlignment.parse(input.getStringOr("alignment", ScreenSettings.DEFAULT.alignment().name()))
			.orElse(ScreenSettings.DEFAULT.alignment());
		ScreenFitMode fitMode = ScreenFitMode.parse(input.getStringOr("fitMode", ScreenSettings.DEFAULT.fitMode().name()))
			.orElse(ScreenSettings.DEFAULT.fitMode());
		this.settings = new ScreenSettings(
			input.getStringOr("url", ScreenSettings.DEFAULT.url()),
			input.getFloatOr("width", ScreenSettings.DEFAULT.width()),
			input.getFloatOr("height", ScreenSettings.DEFAULT.height()),
			input.getFloatOr("renderDistance", ScreenSettings.DEFAULT.renderDistance()),
			input.getFloatOr("audioDistance", ScreenSettings.DEFAULT.audioDistance()),
			alignment,
			fitMode,
			input.getFloatOr("volume", ScreenSettings.DEFAULT.volume()),
			input.getBooleanOr("muted", ScreenSettings.DEFAULT.muted()),
			input.getBooleanOr("loop", ScreenSettings.DEFAULT.loop()),
			input.getBooleanOr("refreshOnResume", ScreenSettings.DEFAULT.refreshOnResume())
		);
		this.loadRevision = Math.max(0, input.getIntOr("loadRevision", 0));
	}

	@Override
	public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
