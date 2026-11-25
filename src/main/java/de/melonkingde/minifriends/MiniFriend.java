package de.melonkingde.minifriends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A small, purely client-side player model that follows the local player.
 * Built on the vanilla Mannequin so the skin is resolved from the player name by vanilla itself.
 */
public class MiniFriend extends Mannequin {
	private static final double SCALE = 0.35D;

	private final int slot;
	private final int total;
	private boolean moving = false;

	public MiniFriend(Level level, String name, boolean showNametag, int slot, int total) {
		super(Compat.mannequinType(), level);
		this.slot = slot;
		this.total = total;

		Compat.setProfile(this, ResolvableProfile.createUnresolved(name));
		this.setCustomName(Component.literal(name));
		this.setCustomNameVisible(showNametag);

		AttributeInstance scale = this.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(SCALE);
		}

		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public void teleportToPlayer(LocalPlayer player) {
		double angle = Math.PI * 2.0D * this.slot / Math.max(1, this.total);
		this.setPos(player.getX() + Math.cos(angle) * 1.5D, player.getY(), player.getZ() + Math.sin(angle) * 1.5D);
		this.setOldPosAndRot();
		this.moving = false;
	}

	@Override
	public void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			this.follow(player);
		}
		super.tick();
		this.calculateEntityAnimation(false);
	}

	private void follow(LocalPlayer player) {
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double distSq = dx * dx + dz * dz;

		if (distSq > 24.0D * 24.0D || Math.abs(player.getY() - this.getY()) > 10.0D) {
			this.teleportToPlayer(player);
			return;
		}

		double dist = Math.sqrt(distSq);
		double keep = 1.5D + 0.45D * this.slot;

		if (!this.moving && dist > keep + 1.5D) {
			this.moving = true;
		} else if (this.moving && dist < keep) {
			this.moving = false;
		}

		double nx = this.getX();
		double nz = this.getZ();
		float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

		if (this.moving && dist > 1.0E-4D) {
			// each friend approaches at a slightly different angle so they spread out
			double offset = (this.slot - (this.total - 1) / 2.0D) * 0.35D;
			double heading = Math.atan2(dz, dx) + offset;
			double step = Mth.clamp((dist - keep) * 0.25D, 0.08D, 0.32D);
			nx += Math.cos(heading) * step;
			nz += Math.sin(heading) * step;
			targetYaw = (float) (heading * 180.0D / Math.PI) - 90.0F;
		}

		double dy = player.getY() - this.getY();
		double ny = Math.abs(dy) < 0.02D ? player.getY() : this.getY() + dy * 0.35D;

		this.setPos(nx, ny, nz);
		this.setDeltaMovement(Vec3.ZERO);

		float yaw = Mth.rotLerp(0.35F, this.getYRot(), targetYaw);
		this.setYRot(yaw);
		this.setXRot(0.0F);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
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
