package de.melonkingde.minifriends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import net.minecraft.world.entity.decoration.Mannequin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Keeps the client-side friend entities in sync with the config. */
public final class FriendManager {
	private static final int ID_BASE = 1_500_000_000;

	private static final List<Mannequin> ACTIVE = new ArrayList<>();
	/** Friends that are driven from the client tick (plain mannequin fallback). */
	private static final Map<Mannequin, FollowLogic> EXTERNAL = new IdentityHashMap<>();
	private static ClientLevel level;
	private static int appliedVersion = -1;
	private static int openCountdown = 0;
	private static int nextId = ID_BASE;

	private FriendManager() {
	}

	/** Opens the settings screen a couple of ticks later, so the chat screen can close first. */
	public static void requestOpenScreen() {
		openCountdown = 2;
	}

	public static void tick(Minecraft mc) {
		if (openCountdown > 0 && --openCountdown == 0) {
			Compat.openScreen(mc, new MinifriendsScreen(null));
		}

		ClientLevel current = mc.level;
		LocalPlayer player = mc.player;
		if (current == null || player == null) {
			ACTIVE.clear();
			level = null;
			appliedVersion = -1;
			return;
		}

		if (current != level) {
			// new world / dimension: old entities belong to the old level
			ACTIVE.clear();
			level = current;
			appliedVersion = -1;
		}

		for (Map.Entry<Mannequin, FollowLogic> entry : EXTERNAL.entrySet()) {
			Mannequin e = entry.getKey();
			if (!e.isRemoved()) {
				Compat.setOldPosAndRot(e);
				entry.getValue().step(e, player);
				Compat.animate(e);
			}
		}

		boolean broken = false;
		for (Mannequin friend : ACTIVE) {
			if (friend.isRemoved()) {
				broken = true;
				break;
			}
		}

		if (appliedVersion != MinifriendsConfig.version() || broken) {
			try {
				rebuild(current, player);
			} catch (RuntimeException | LinkageError e) {
				// never crash the game because of a cosmetic: log once and wait for the next config change
				MinifriendsClient.LOGGER.error("Could not create Minifriends", e);
				for (Mannequin friend : ACTIVE) {
					if (!friend.isRemoved()) {
						current.removeEntity(friend.getId(), Entity.RemovalReason.DISCARDED);
					}
				}
				ACTIVE.clear();
				EXTERNAL.clear();
				appliedVersion = MinifriendsConfig.version();
			}
		}
	}

	private static void rebuild(ClientLevel lvl, LocalPlayer player) {
		for (Mannequin friend : ACTIVE) {
			if (!friend.isRemoved()) {
				lvl.removeEntity(friend.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		ACTIVE.clear();
		EXTERNAL.clear();
		appliedVersion = MinifriendsConfig.version();

		MinifriendsConfig cfg = MinifriendsConfig.get();
		if (!cfg.enabled) {
			return;
		}

		List<MinifriendsConfig.Friend> valid = new ArrayList<>();
		for (MinifriendsConfig.Friend f : cfg.friends) {
			if (f != null && f.name != null && !f.name.isBlank() && valid.size() < MinifriendsConfig.MAX_FRIENDS) {
				valid.add(f);
			}
		}

		for (int i = 0; i < valid.size(); i++) {
			MinifriendsConfig.Friend f = valid.get(i);
			FollowLogic logic = new FollowLogic(i, valid.size());
			Mannequin entity = new MiniFriend(lvl, f.name.trim(), f.showNametag, logic);
			logic.teleport(entity, player);
			boolean external = false;

			if (!Compat.hasRenderer(entity)) {
				// the subclass has no renderer in this version: try a plain vanilla mannequin instead
				Mannequin plain = Compat.createPlain(lvl);
				if (plain != null) {
					Compat.configure(plain, f.name.trim(), f.showNametag);
					logic.teleport(plain, player);
					if (Compat.hasRenderer(plain)) {
						MinifriendsClient.LOGGER.info("Using plain mannequin fallback for {}", f.name);
						entity = plain;
						external = true;
					} else {
						Compat.dump(plain);
					}
				}
				if (!external) {
					Compat.dump(entity);
					throw new IllegalStateException("No renderer for Minifriend entity");
				}
			}

			entity.setId(nextId++);
			lvl.addEntity(entity);
			if (external) {
				EXTERNAL.put(entity, logic);
			}
			ACTIVE.add(entity);
		}
	}
}
