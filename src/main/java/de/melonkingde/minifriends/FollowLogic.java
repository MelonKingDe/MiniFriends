package de.melonkingde.minifriends;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Movement of one friend: stay near the player, chase when the player walks away. */
public final class FollowLogic {
	private final int slot;
	private final int total;
	private final double scale;
	private boolean moving = false;

	public FollowLogic(int slot, int total, double scale) {
		this.slot = slot;
		this.total = total;
		this.scale = scale;
	}

	public void teleport(LivingEntity e, LocalPlayer player) {
		double angle = Math.PI * 2.0D * this.slot / Math.max(1, this.total);
		e.setPos(player.getX() + Math.cos(angle) * (1.2D + this.scale), player.getY(), player.getZ() + Math.sin(angle) * (1.2D + this.scale));
		Compat.setOldPosAndRot(e);
		this.moving = false;
	}

	public void step(LivingEntity e, LocalPlayer player) {
		double dx = player.getX() - e.getX();
		double dz = player.getZ() - e.getZ();
		double distSq = dx * dx + dz * dz;

		if (distSq > 24.0D * 24.0D || Math.abs(player.getY() - e.getY()) > 10.0D) {
			this.teleport(e, player);
			return;
		}

		double dist = Math.sqrt(distSq);
		double keep = 1.2D + this.scale + this.slot * (0.3D + 0.6D * this.scale);

		if (!this.moving && dist > keep + 1.5D) {
			this.moving = true;
		} else if (this.moving && dist < keep) {
			this.moving = false;
		}

		double nx = e.getX();
		double nz = e.getZ();
		float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

		if (this.moving && dist > 1.0E-4D) {
			// each friend approaches at a slightly different angle so they spread out
			double offset = (this.slot - (this.total - 1) / 2.0D) * 0.35D;
			double heading = Math.atan2(dz, dx) + offset;
			double stepLen = Mth.clamp((dist - keep) * 0.25D, 0.08D, 0.32D);
			nx += Math.cos(heading) * stepLen;
			nz += Math.sin(heading) * stepLen;
			targetYaw = (float) (heading * 180.0D / Math.PI) - 90.0F;
		}

		double dy = player.getY() - e.getY();
		double ny = Math.abs(dy) < 0.02D ? player.getY() : e.getY() + dy * 0.35D;

		e.setPos(nx, ny, nz);
		e.setDeltaMovement(Vec3.ZERO);

		float yaw = Mth.rotLerp(0.35F, e.getYRot(), targetYaw);
		e.setYRot(yaw);
		e.setXRot(0.0F);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
	}
}
