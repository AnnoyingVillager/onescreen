package dev.onescreen.client.editor;

import dev.onescreen.OneScreen;
import dev.onescreen.client.media.MediaPlaybackInfo;
import dev.onescreen.client.media.ScreenMediaManager;
import dev.onescreen.editor.ScreenReloadPayload;
import dev.onescreen.editor.ScreenSettingsUpdatePayload;
import dev.onescreen.screen.ScreenAlignment;
import dev.onescreen.screen.ScreenFitMode;
import dev.onescreen.screen.ScreenSettings;
import dev.onescreen.screen.ScreenUrlValidator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ScreenEditorScreen extends Screen {
	private static final int PANEL_WIDTH = 400;
	private static final int PANEL_HEIGHT = 254;
	private static final int ALIGNMENT_BUTTON_WIDTH = 54;
	private static final int ALIGNMENT_BUTTON_HEIGHT = 14;
	private static final int ALIGNMENT_GAP = 2;
	private static final int FIT_BUTTON_WIDTH = 68;
	private static final int FIT_BUTTON_HEIGHT = 15;
	private static final int FIT_BUTTON_GAP = 4;
	private static final int WIDGET_HEIGHT = 15;
	private static final int PLAYBACK_HEIGHT = 16;
	private static final int CONTROL_BUTTON_WIDTH = 34;
	private static final int CONTROL_BUTTON_GAP = 4;
	private static final int NUMERIC_FIELD_WIDTH = 88;
	private static final int LABEL_COLOR = 0xFFE0E0E0;
	private static final int STATUS_COLOR = 0xFFFF7777;

	private final BlockPos screenPos;
	private final String urlValue;
	private final String widthValue;
	private final String heightValue;
	private final String renderDistanceValue;
	private final String audioDistanceValue;
	private final String rotationDegreesValue;
	private final float maxRenderDistance;
	private final float maxAudioDistance;
	private final Map<ScreenAlignment, Button> alignmentButtons = new EnumMap<>(ScreenAlignment.class);
	private final Map<ScreenFitMode, Button> fitModeButtons = new EnumMap<>(ScreenFitMode.class);
	private ScreenAlignment alignment;
	private ScreenFitMode fitMode;
	private boolean hideFrame;
	private boolean loop;
	private boolean refreshOnResume;
	private EditBox urlField;
	private EditBox widthField;
	private EditBox heightField;
	private EditBox renderDistanceField;
	private EditBox audioDistanceField;
	private EditBox rotationDegreesField;
	private PlaybackSlider playbackSlider;
	private Button refreshButton;
	private Button playButton;
	private Button pauseButton;
	private Button stopButton;
	private @Nullable Component status;
	private int panelLeft;
	private int panelTop;

	public ScreenEditorScreen(
		final BlockPos screenPos,
		final String url,
		final float width,
		final float height,
		final float renderDistance,
		final float audioDistance,
		final ScreenAlignment alignment,
		final ScreenFitMode fitMode,
		final float rotationDegrees,
		final boolean hideFrame,
		final boolean loop,
		final boolean refreshOnResume,
		final float maxRenderDistance,
		final float maxAudioDistance
	) {
		super(Component.translatable("onescreen.editor.title"));
		this.screenPos = screenPos.immutable();
		this.alignment = alignment;
		this.fitMode = fitMode;
		this.hideFrame = hideFrame;
		this.loop = loop;
		this.refreshOnResume = refreshOnResume;
		this.urlValue = url;
		this.widthValue = formatNumber(width);
		this.heightValue = formatNumber(height);
		this.renderDistanceValue = formatNumber(renderDistance);
		this.audioDistanceValue = formatNumber(audioDistance);
		this.rotationDegreesValue = formatNumber(rotationDegrees);
		this.maxRenderDistance = maxRenderDistance;
		this.maxAudioDistance = maxAudioDistance;
	}

	@Override
	protected void init() {
		ScreenMediaManager.get().beginEditing(this.screenPos);
		this.panelLeft = (this.width - PANEL_WIDTH) / 2;
		int centeredTop = Math.max(2, (this.height - PANEL_HEIGHT) / 2);
		this.panelTop = Math.min(centeredTop, this.height - PANEL_HEIGHT - 2);
		int contentLeft = this.panelLeft + 20;

		this.urlField = this.addRenderableWidget(new EditBox(
			this.font,
			contentLeft,
			this.panelTop + 17,
			PANEL_WIDTH - 40,
			WIDGET_HEIGHT,
			Component.translatable("onescreen.editor.url")
		));
		this.urlField.setMaxLength(ScreenSettings.MAX_URL_LENGTH);
		this.urlField.setValue(this.urlValue);
		this.urlField.setHint(Component.translatable("onescreen.editor.url.hint"));

		this.widthField = this.addNumericField("onescreen.editor.width", contentLeft + 88, this.panelTop + 36, this.widthValue);
		this.heightField = this.addNumericField("onescreen.editor.height", contentLeft + 268, this.panelTop + 36, this.heightValue);
		this.renderDistanceField = this.addNumericField("onescreen.editor.render_distance", contentLeft + 88, this.panelTop + 55, this.renderDistanceValue);
		this.audioDistanceField = this.addNumericField("onescreen.editor.audio_distance", contentLeft + 268, this.panelTop + 55, this.audioDistanceValue);
		this.rotationDegreesField = this.addNumericField("onescreen.editor.rotation", contentLeft + 88, this.panelTop + 74, this.rotationDegreesValue);

		Checkbox hideFrameCheckbox = Checkbox.builder(
			Component.translatable("onescreen.editor.hide_frame"),
			this.font
		).pos(contentLeft + 180, this.panelTop + 72).maxWidth(PANEL_WIDTH - 200)
			.selected(this.hideFrame)
			.onValueChange((checkbox, selected) -> this.hideFrame = selected)
			.build();
		hideFrameCheckbox.setTooltip(Tooltip.create(Component.translatable("onescreen.editor.hide_frame.tooltip")));
		this.addRenderableWidget(hideFrameCheckbox);

		int gridWidth = ALIGNMENT_BUTTON_WIDTH * 3 + ALIGNMENT_GAP * 2;
		int gridLeft = (this.width - gridWidth) / 2;
		int gridTop = this.panelTop + 94;
		ScreenAlignment[] ordered = {
			ScreenAlignment.TOP_LEFT, ScreenAlignment.UP, ScreenAlignment.TOP_RIGHT,
			ScreenAlignment.LEFT, ScreenAlignment.CENTER, ScreenAlignment.RIGHT,
			ScreenAlignment.BOTTOM_LEFT, ScreenAlignment.DOWN, ScreenAlignment.BOTTOM_RIGHT
		};
		for (int index = 0; index < ordered.length; index++) {
			ScreenAlignment option = ordered[index];
			int column = index % 3;
			int row = index / 3;
			Button button = this.addRenderableWidget(Button.builder(
				Component.literal(option.symbol()),
				pressed -> {
					this.alignment = option;
					this.refreshAlignmentButtons();
				}
			).bounds(
				gridLeft + column * (ALIGNMENT_BUTTON_WIDTH + ALIGNMENT_GAP),
				gridTop + row * (ALIGNMENT_BUTTON_HEIGHT + ALIGNMENT_GAP),
				ALIGNMENT_BUTTON_WIDTH,
				ALIGNMENT_BUTTON_HEIGHT
			).tooltip(Tooltip.create(Component.translatable(option.translationKey()))).build());
			this.alignmentButtons.put(option, button);
		}
		this.refreshAlignmentButtons();

		int fitRowWidth = FIT_BUTTON_WIDTH * ScreenFitMode.values().length
			+ FIT_BUTTON_GAP * (ScreenFitMode.values().length - 1);
		int fitRowLeft = (this.width - fitRowWidth) / 2;
		int fitRowTop = this.panelTop + 150;
		ScreenFitMode[] fitModes = ScreenFitMode.values();
		for (int index = 0; index < fitModes.length; index++) {
			ScreenFitMode option = fitModes[index];
			Button button = this.addRenderableWidget(Button.builder(
				Component.translatable(option.translationKey()),
				pressed -> {
					this.fitMode = option;
					this.refreshFitModeButtons();
				}
			).bounds(
				fitRowLeft + index * (FIT_BUTTON_WIDTH + FIT_BUTTON_GAP),
				fitRowTop,
				FIT_BUTTON_WIDTH,
				FIT_BUTTON_HEIGHT
			).tooltip(Tooltip.create(Component.translatable(option.translationKey() + ".tooltip"))).build());
			this.fitModeButtons.put(option, button);
		}
		this.refreshFitModeButtons();

		this.playbackSlider = this.addRenderableWidget(new PlaybackSlider(
			contentLeft,
			this.panelTop + 174,
			PANEL_WIDTH - 40,
			PLAYBACK_HEIGHT
		));

		int controlGroupWidth = CONTROL_BUTTON_WIDTH * 4 + CONTROL_BUTTON_GAP * 3;
		int checkboxWidth = PANEL_WIDTH - 40 - controlGroupWidth - 8;
		Checkbox refreshCheckbox = Checkbox.builder(
			Component.translatable("onescreen.editor.refresh_on_resume"),
			this.font
		).pos(contentLeft, this.panelTop + 193).maxWidth(checkboxWidth)
			.selected(this.refreshOnResume)
			.onValueChange((checkbox, selected) -> this.refreshOnResume = selected)
			.build();
		refreshCheckbox.setTooltip(Tooltip.create(Component.translatable("onescreen.editor.refresh_on_resume.tooltip")));
		this.addRenderableWidget(refreshCheckbox);

		Checkbox loopCheckbox = Checkbox.builder(
			Component.translatable("onescreen.editor.loop"),
			this.font
		).pos(contentLeft, this.panelTop + 211).maxWidth(checkboxWidth)
			.selected(this.loop)
			.onValueChange((checkbox, selected) -> this.loop = selected)
			.build();
		this.addRenderableWidget(loopCheckbox);

		int controlLeft = contentLeft + PANEL_WIDTH - 40 - controlGroupWidth;
		int controlTop = this.panelTop + 201;
		this.refreshButton = this.addRenderableWidget(new ScaledGlyphButton(
			controlLeft,
			controlTop,
			CONTROL_BUTTON_WIDTH,
			WIDGET_HEIGHT,
			Component.literal("↻"),
			this.font,
			2.0F,
			pressed -> this.requestReload()
		));
		this.refreshButton.setTooltip(Tooltip.create(Component.translatable("onescreen.editor.control.refresh")));
		this.refreshButton.active = !this.urlValue.isBlank();
		this.playButton = this.addPlaybackButton(
			controlLeft + CONTROL_BUTTON_WIDTH + CONTROL_BUTTON_GAP,
			controlTop,
			"▶",
			"onescreen.editor.control.play",
			() -> ScreenMediaManager.get().play(this.screenPos)
		);
		this.pauseButton = this.addPlaybackButton(
			controlLeft + (CONTROL_BUTTON_WIDTH + CONTROL_BUTTON_GAP) * 2,
			controlTop,
			"⏸",
			"onescreen.editor.control.pause",
			() -> ScreenMediaManager.get().pause(this.screenPos)
		);
		this.stopButton = this.addPlaybackButton(
			controlLeft + (CONTROL_BUTTON_WIDTH + CONTROL_BUTTON_GAP) * 3,
			controlTop,
			"⏹",
			"onescreen.editor.control.stop",
			() -> ScreenMediaManager.get().stop(this.screenPos)
		);

		int buttonTop = this.panelTop + 236;
		this.addRenderableWidget(Button.builder(
			Component.translatable("onescreen.editor.save"),
			button -> this.save()
		).bounds(this.width / 2 - 120, buttonTop, 110, WIDGET_HEIGHT).build());
		this.addRenderableWidget(Button.builder(
			Component.translatable("onescreen.editor.cancel"),
			button -> this.onClose()
		).bounds(this.width / 2 + 10, buttonTop, 110, WIDGET_HEIGHT).build());

		this.setInitialFocus(this.urlField);
	}

	@Override
	public void removed() {
		ScreenMediaManager.get().endEditing(this.screenPos);
		super.removed();
	}
	private EditBox addNumericField(final String narrationKey, final int x, final int y, final String value) {
		EditBox field = this.addRenderableWidget(new EditBox(
			this.font,
			x,
			y,
			NUMERIC_FIELD_WIDTH,
			WIDGET_HEIGHT,
			Component.translatable(narrationKey)
		));
		field.setMaxLength(16);
		field.setValue(value);
		return field;
	}

	private Button addPlaybackButton(
		final int x,
		final int y,
		final String symbol,
		final String tooltipKey,
		final PlaybackAction action
	) {
		Button button = this.addRenderableWidget(Button.builder(
			Component.literal(symbol),
			pressed -> {
				boolean handled = action.run();
				OneScreen.LOGGER.info(
					"Editor playback control {} at {} handled={}",
					tooltipKey,
					this.screenPos,
					handled
				);
			}
		).bounds(x, y, CONTROL_BUTTON_WIDTH, WIDGET_HEIGHT)
			.tooltip(Tooltip.create(Component.translatable(tooltipKey)))
			.build());
		button.active = false;
		return button;
	}

	@FunctionalInterface
	private interface PlaybackAction {
		boolean run();
	}

	private int fieldLabelY(final EditBox field) {
		return field.getY() + (field.getHeight() - this.font.lineHeight) / 2;
	}
	private void refreshAlignmentButtons() {
		for (Map.Entry<ScreenAlignment, Button> entry : this.alignmentButtons.entrySet()) {
			String symbol = entry.getKey().symbol();
			entry.getValue().setMessage(Component.literal(entry.getKey() == this.alignment ? "[" + symbol + "]" : symbol));
		}
	}

	private void refreshFitModeButtons() {
		for (Map.Entry<ScreenFitMode, Button> entry : this.fitModeButtons.entrySet()) {
			Component label = Component.translatable(entry.getKey().translationKey());
			entry.getValue().setMessage(
				entry.getKey() == this.fitMode ? Component.literal("[").append(label).append("]") : label
			);
		}
	}

	private void save() {
		final float width;
		final float height;
		final float renderDistance;
		final float audioDistance;
		final float rotationDegrees;
		try {
			width = parseNumber(this.widthField.getValue(), ScreenSettings.MIN_SIZE, ScreenSettings.MAX_SIZE);
			height = parseNumber(this.heightField.getValue(), ScreenSettings.MIN_SIZE, ScreenSettings.MAX_SIZE);
			renderDistance = parseNumber(
				this.renderDistanceField.getValue(),
				ScreenSettings.MIN_RENDER_DISTANCE,
				this.maxRenderDistance
			);
			audioDistance = parseNumber(
				this.audioDistanceField.getValue(),
				ScreenSettings.MIN_AUDIO_DISTANCE,
				this.maxAudioDistance
			);
			rotationDegrees = parseNumber(
				this.rotationDegreesField.getValue(),
				ScreenSettings.MIN_ROTATION,
				ScreenSettings.MAX_ROTATION
			);
		} catch (IllegalArgumentException exception) {
			this.status = Component.translatable("onescreen.editor.error.invalid_value");
			return;
		}

		String url = this.urlField.getValue();
		if (!url.isBlank()) {
			try {
				url = ScreenUrlValidator.validate(url);
			} catch (IllegalArgumentException exception) {
				this.status = Component.translatable("onescreen.command.error.invalid_url." + exception.getMessage());
				return;
			}
		}

		if (!ClientPlayNetworking.canSend(ScreenSettingsUpdatePayload.TYPE)) {
			this.status = Component.translatable("onescreen.editor.error.client_support");
			return;
		}
		ClientPlayNetworking.send(new ScreenSettingsUpdatePayload(
			this.screenPos,
			url,
			width,
			height,
			renderDistance,
			audioDistance,
			this.alignment,
			this.fitMode,
			rotationDegrees,
			this.hideFrame,
			this.loop,
			this.refreshOnResume
		));
		this.onClose();
	}

	private static float parseNumber(final String value, final float min, final float max) {
		final float parsed;
		try {
			parsed = Float.parseFloat(value.trim());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("number", exception);
		}
		if (!Float.isFinite(parsed) || parsed < min || parsed > max) {
			throw new IllegalArgumentException("range");
		}
		return parsed;
	}

	private static String formatNumber(final float value) {
		if (value == Math.round(value)) {
			return Integer.toString(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String formatTime(final long milliseconds) {
		long totalSeconds = Math.max(0L, milliseconds) / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = totalSeconds % 3600L / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0L) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
	}

	@Override
	public void extractRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		MediaPlaybackInfo playback = ScreenMediaManager.get().playbackInfo(this.screenPos);
		this.playbackSlider.setPlaybackInfo(playback);
		boolean controlsActive = playback.ready();
		this.playButton.active = controlsActive;
		this.pauseButton.active = controlsActive;
		this.stopButton.active = controlsActive;
		this.refreshButton.active = !this.urlValue.isBlank();

		graphics.fill(this.panelLeft, this.panelTop, this.panelLeft + PANEL_WIDTH, this.panelTop + PANEL_HEIGHT, 0xE0101010);
		graphics.outline(this.panelLeft, this.panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFF808080);
		graphics.centeredText(
			this.font,
			this.status == null ? this.title : this.status,
			this.width / 2,
			this.panelTop + 3,
			this.status == null ? 0xFFFFFFFF : STATUS_COLOR
		);

		int contentLeft = this.panelLeft + 20;
		graphics.text(this.font, Component.translatable("onescreen.editor.url"), contentLeft, this.panelTop + 10, LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.width"), contentLeft, this.fieldLabelY(this.widthField), LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.height"), contentLeft + 180, this.fieldLabelY(this.heightField), LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.render_distance"), contentLeft, this.fieldLabelY(this.renderDistanceField), LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.audio_distance"), contentLeft + 180, this.fieldLabelY(this.audioDistanceField), LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.rotation"), contentLeft, this.fieldLabelY(this.rotationDegreesField), LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.position"), contentLeft, this.panelTop + 91, LABEL_COLOR);

		graphics.text(this.font, Component.translatable("onescreen.editor.fit_mode"), contentLeft, this.panelTop + 143, LABEL_COLOR);
		graphics.text(this.font, Component.translatable("onescreen.editor.playback"), contentLeft, this.panelTop + 167, LABEL_COLOR);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void requestReload() {
		if (this.urlValue.isBlank()) {
			this.status = Component.translatable("onescreen.command.error.no_url");
			return;
		}
		if (!ClientPlayNetworking.canSend(ScreenReloadPayload.TYPE)) {
			this.status = Component.translatable("onescreen.editor.error.client_support");
			return;
		}
		ClientPlayNetworking.send(new ScreenReloadPayload(this.screenPos));
		this.status = Component.translatable("onescreen.editor.refresh.requested");
	}

	private static final class ScaledGlyphButton extends Button {
		private final Font font;
		private final float glyphScale;

		private ScaledGlyphButton(
			final int x,
			final int y,
			final int width,
			final int height,
			final Component message,
			final Font font,
			final float glyphScale,
			final OnPress onPress
		) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.font = font;
			this.glyphScale = glyphScale;
		}

		@Override
		protected void extractContents(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float partialTick
		) {
			this.extractDefaultSprite(graphics);
			float glyphWidth = this.font.width(this.getMessage()) * this.glyphScale;
			float glyphHeight = this.font.lineHeight * this.glyphScale;
			float x = this.getX() + (this.width - glyphWidth) * 0.5F;
			float y = this.getY() + (this.height - glyphHeight) * 0.5F;
			int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(this.glyphScale, this.glyphScale);
			graphics.text(this.font, this.getMessage(), 0, 0, color, true);
			graphics.pose().popMatrix();
		}
	}

	private final class PlaybackSlider extends AbstractSliderButton {
		private MediaPlaybackInfo playback = MediaPlaybackInfo.EMPTY;
		private boolean seeking;

		private PlaybackSlider(final int x, final int y, final int width, final int height) {
			super(x, y, width, height, Component.empty(), 0.0D);
			this.active = false;
			this.updateMessage();
		}

		private void setPlaybackInfo(final MediaPlaybackInfo playback) {
			if (this.playback.equals(playback)) {
				return;
			}
			this.playback = playback;
			this.active = playback.ready() && playback.canSeek() && playback.durationMs() > 0L;
			if (!this.seeking) {
				this.value = playback.progressFraction();
			}
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (!this.playback.ready()) {
				this.setMessage(Component.translatable("onescreen.editor.playback.loading"));
				return;
			}
			if (this.playback.live()) {
				if (this.playback.segmentDurationMs() <= 0L) {
					this.setMessage(Component.translatable("onescreen.editor.playback.live_loading"));
				} else {
					this.setMessage(Component.translatable(
						"onescreen.editor.playback.live_segment",
						formatTime(this.playback.progressTimeMs()),
						formatTime(this.playback.progressDurationMs())
					));
				}
				return;
			}
			if (this.playback.canSeek() && this.playback.durationMs() > 0L) {
				long displayedTime = this.seeking
					? this.playback.seekTimeMs(this.value)
					: this.playback.progressTimeMs();
				this.setMessage(Component.translatable(
					"onescreen.editor.playback.position",
					formatTime(displayedTime),
					formatTime(this.playback.durationMs())
				));
				return;
			}
			this.setMessage(Component.translatable("onescreen.editor.playback.unavailable"));
		}

		@Override
		protected void applyValue() {
			if (this.seeking && this.active) {
				ScreenMediaManager.get().seek(
					ScreenEditorScreen.this.screenPos,
					this.playback.seekTimeMs(this.value),
					false
				);
			}
		}

		@Override
		public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
			if (!this.active) {
				return;
			}
			this.seeking = true;
			super.onClick(event, doubleClick);
		}

		@Override
		public void onRelease(final MouseButtonEvent event) {
			if (this.seeking && this.active) {
				ScreenMediaManager.get().seek(
					ScreenEditorScreen.this.screenPos,
					this.playback.seekTimeMs(this.value),
					true
				);
			}
			this.seeking = false;
			super.onRelease(event);
			this.updateMessage();
		}
	}
}
