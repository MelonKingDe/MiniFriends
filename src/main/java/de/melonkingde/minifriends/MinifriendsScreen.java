package de.melonkingde.minifriends;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MinifriendsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;
	private final MinifriendsConfig config = MinifriendsConfig.get();

	public MinifriendsScreen(Screen parent) {
		super(Component.literal("Minifriends"));
		this.parent = parent;
		if (this.config.friends.isEmpty()) {
			this.config.friends.add(new MinifriendsConfig.Friend());
		}
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int rows = this.config.friends.size();
		int totalHeight = 14 + 26 + 26 + rows * ROW_HEIGHT + 28 + 24;
		int y = Math.max(10, this.height / 2 - totalHeight / 2);

		this.addRenderableWidget(
			new StringWidget(cx - 100, y, 200, 12, Component.literal("Minifriends"), this.font)
		);
		y += 18;

		this.addRenderableWidget(
			CycleButton.onOffBuilder(this.config.enabled).create(
				cx - 100, y, 200, 20, Component.literal("Minifriends"),
				(button, value) -> this.config.enabled = value
			)
		);
		y += 24;

		this.addRenderableWidget(new ScaleSlider(cx - 100, y, 200, 20, this.config));
		y += 28;

		int start = cx - 139;
		for (int i = 0; i < this.config.friends.size(); i++) {
			final MinifriendsConfig.Friend friend = this.config.friends.get(i);

			EditBox nameBox = new EditBox(this.font, start, y, 120, 20, Component.literal("Player name"));
			nameBox.setMaxLength(16);
			nameBox.setHint(Component.literal("Player name"));
			nameBox.setValue(friend.name == null ? "" : friend.name);
			nameBox.setResponder(text -> friend.name = text);
			this.addRenderableWidget(nameBox);

			this.addRenderableWidget(
				CycleButton.onOffBuilder(friend.showNametag).create(
					start + 124, y, 130, 20, Component.literal("Nametag"),
					(button, value) -> friend.showNametag = value
				)
			);

			this.addRenderableWidget(
				Button.builder(Component.literal("X"), button -> {
					this.config.friends.remove(friend);
					this.rebuildWidgets();
				}).bounds(start + 258, y, 20, 20).build()
			);

			y += ROW_HEIGHT;
		}

		Button add = Button.builder(Component.literal("+ Add friend"), button -> {
			this.config.friends.add(new MinifriendsConfig.Friend());
			this.rebuildWidgets();
		}).bounds(cx - 100, y, 200, 20).build();
		add.active = this.config.friends.size() < MinifriendsConfig.MAX_FRIENDS;
		this.addRenderableWidget(add);
		y += 28;

		this.addRenderableWidget(
			Button.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(cx - 100, y, 200, 20).build()
		);
	}

	@Override
	public void onClose() {
		this.config.friends.removeIf(f -> f == null || f.name == null || f.name.isBlank());
		for (MinifriendsConfig.Friend f : this.config.friends) {
			f.name = f.name.trim();
		}
		MinifriendsConfig.save();
		Compat.openScreen(this.minecraft, this.parent);
	}

	/** Slider for the friend size, 15% to 100% of a normal player in 5% steps. */
	private static final class ScaleSlider extends AbstractSliderButton {
		private final MinifriendsConfig config;

		ScaleSlider(int x, int y, int width, int height, MinifriendsConfig config) {
			super(x, y, width, height, Component.empty(),
				(config.scale - MinifriendsConfig.MIN_SCALE) / (MinifriendsConfig.MAX_SCALE - MinifriendsConfig.MIN_SCALE));
			this.config = config;
			this.updateMessage();
		}

		private static float toScale(double sliderValue) {
			double raw = MinifriendsConfig.MIN_SCALE + sliderValue * (MinifriendsConfig.MAX_SCALE - MinifriendsConfig.MIN_SCALE);
			return (float) (Math.round(raw * 20.0D) / 20.0D);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal("Size: " + Math.round(toScale(this.value) * 100.0F) + "%"));
		}

		@Override
		protected void applyValue() {
			this.config.scale = Math.max(MinifriendsConfig.MIN_SCALE, Math.min(MinifriendsConfig.MAX_SCALE, toScale(this.value)));
		}
	}
}
