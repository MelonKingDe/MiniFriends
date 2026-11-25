package de.melonkingde.minifriends;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

/** Invisible client-side armor stand that only exists to draw a name tag above a friend. */
public class NameStand extends ArmorStand {
	public NameStand(Level level, String name) {
		super(Compat.armorStandType(), level);
		this.setCustomName(Component.literal(name));
		this.setCustomNameVisible(true);
		this.setInvisible(true);
		this.setNoGravity(true);
		this.noPhysics = true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
