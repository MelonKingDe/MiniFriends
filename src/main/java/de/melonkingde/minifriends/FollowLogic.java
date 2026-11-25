package de.melonkingde.minifriends;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Behaviour of one friend: stay near the player, chase when the player walks away, play emotes when idle. */
public final class FollowLogic {
	/** The player has to stand still this long before the friends start their emotes (5 seconds). */
	private static final int IDLE_TICKS_BEFORE_EMOTES = 100;

	private enum Emote { NONE, CROUCH, WAVE, HOP, SPIN, LOOK }

	private final int slot;
	private final int total;
	private final double scale;
	private final Random random = new Random();

	private boolean moving = false;

	// idle detection
	private boolean hasLastPos = false;
	private double lastPx;
	private double lastPy;
	private double lastPz;
	private int idleTicks = 0;

	// emotes
	private Emote emote = Emote.NONE;
	private int emoteTicks = 0;
	private int emoteLength = 0;
	private int cooldown;
	private float spinYaw;

	public FollowLogic(int slot, int total, double scale) {
		this.slot = slot;
		this.total = total;
		this.scale = scale;
		this.cooldown = 10 + this.random.nextInt(60) + slot * 15;
	}

	public void teleport(LivingEntity e, LocalPlayer player) {
		double angle = Math.PI * 2.0D * this.slot / Math.max(1, this.total);
		e.setPos(player.getX() + Math.cos(angle) * (1.2D + this.scale), player.getY(), player.getZ() + Math.sin(angle) * (1.2D + this.scale));
		Compat.setOldPosAndRot(e);
		this.moving = false;
		this.idleTicks = 0;
		this.endEmote(e);
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

		this.updateIdle(player);
		if (this.moving || this.idleTicks < IDLE_TICKS_BEFORE_EMOTES) {
			if (this.emote != Emote.NONE) {
				this.endEmote(e);
			}
		} else {
			this.runEmotes(e, player, yaw);
		}
	}

	private void updateIdle(LocalPlayer player) {
		if (!this.hasLastPos) {
			this.hasLastPos = true;
			this.lastPx = player.getX();
			this.lastPy = player.getY();
			this.lastPz = player.getZ();
		}
		double mx = player.getX() - this.lastPx;
		double my = player.getY() - this.lastPy;
		double mz = player.getZ() - this.lastPz;
		this.lastPx = player.getX();
		this.lastPy = player.getY();
		this.lastPz = player.getZ();

		if (mx * mx + my * my + mz * mz > 1.0E-4D) {
			this.idleTicks = 0;
		} else if (this.idleTicks < Integer.MAX_VALUE - 1) {
			this.idleTicks++;
		}
	}

	// ---- emotes ----------------------------------------------------------------------

	private void runEmotes(LivingEntity e, LocalPlayer player, float bodyYaw) {
		if (this.emote == Emote.NONE) {
			if (--this.cooldown <= 0) {
				this.startEmote(e);
			}
			return;
		}

		this.emoteTicks++;
		switch (this.emote) {
			case CROUCH -> e.setPose(Pose.CROUCHING);
			case WAVE -> {
				if (this.emoteTicks % 12 == 1) {
					e.swing(InteractionHand.MAIN_HAND);
				}
			}
			case HOP -> {
				double t = Math.min(1.0D, (double) this.emoteTicks / this.emoteLength);
				double height = Math.sin(Math.PI * t) * 0.35D * Math.max(0.4D, this.scale + 0.3D);
				e.setPos(e.getX(), player.getY() + height, e.getZ());
			}
			case SPIN -> {
				this.spinYaw += 360.0F / this.emoteLength;
				e.setYRot(this.spinYaw);
				e.setYHeadRot(this.spinYaw);
				e.setYBodyRot(this.spinYaw);
			}
			case LOOK -> e.setYHeadRot(bodyYaw + 45.0F * (float) Math.sin(this.emoteTicks * 0.2D));
			default -> {
			}
		}

		if (this.emoteTicks >= this.emoteLength) {
			this.endEmote(e);
		}
	}

	private void startEmote(LivingEntity e) {
		Emote[] all = Emote.values();
		this.emote = all[1 + this.random.nextInt(all.length - 1)];
		this.emoteTicks = 0;
		this.spinYaw = e.getYRot();
		this.emoteLength = switch (this.emote) {
			case CROUCH -> 60 + this.random.nextInt(60);
			case WAVE -> 36;
			case HOP -> 14;
			case SPIN -> 30;
			case LOOK -> 50;
			default -> 20;
		};
	}

	private void endEmote(LivingEntity e) {
		if (this.emote == Emote.CROUCH) {
			e.setPose(Pose.STANDING);
		}
		this.emote = Emote.NONE;
		this.emoteTicks = 0;
		this.cooldown = 40 + this.random.nextInt(100);
	}
}
