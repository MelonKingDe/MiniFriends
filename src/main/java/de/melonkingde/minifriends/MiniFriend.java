package de.melonkingde.minifriends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.Level;

/**
 * A small, purely client-side player model that follows the local player.
 * Built on the vanilla Mannequin so the skin is resolved from the player name by vanilla itself.
 */
public class MiniFriend extends Mannequin {
	private final FollowLogic logic;

	public MiniFriend(Level level, String name, double scale, FollowLogic logic) {
		super(Compat.mannequinType(), level);
		this.logic = logic;
		Compat.configure(this, name, scale);
	}

	@Override
	public void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			this.logic.step(this, player);
		}
		super.tick();
		Compat.animate(this);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
