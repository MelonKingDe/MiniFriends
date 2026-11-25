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

	/** Every entity we added to the level (friends and name stands). */
	private static final List<Entity> ACTIVE = new ArrayList<>();
	/** Friends that are driven from the client tick (plain mannequin fallback). */
	private static final Map<Mannequin, FollowLogic> EXTERNAL = new IdentityHashMap<>();
	/** Name tag carrier for each friend. */
	private static final Map<Mannequin, NameStand> STANDS = new IdentityHashMap<>();

	private static ClientLevel level;
	private static int appliedVersion = -1;
	private static int openCountdown = 0;
	private static int nextId = ID_BASE;
	private static double activeScale = 0.35D;

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
			clearAll();
			level = null;
			appliedVersion = -1;
			return;
		}

		if (current != level) {
			// new world / dimension: old entities belong to the old level
			clearAll();
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

		for (Map.Entry<Mannequin, NameStand> entry : STANDS.entrySet()) {
			Mannequin friend = entry.getKey();
			NameStand stand = entry.getValue();
			if (!friend.isRemoved() && !stand.isRemoved()) {
				Compat.setOldPosAndRot(stand);
				// the name tag floats 0.5 above the top of the stand's hitbox
				double y = friend.getY() + 1.8D * activeScale + 0.42D - (stand.getBbHeight() + 0.5D);
				stand.setPos(friend.getX(), y, friend.getZ());
			}
		}

		boolean broken = false;
		for (Entity entity : ACTIVE) {
			if (entity.isRemoved()) {
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
				removeAll(current);
				appliedVersion = MinifriendsConfig.version();
			}
		}
	}

	private static void clearAll() {
		ACTIVE.clear();
		EXTERNAL.clear();
		STANDS.clear();
	}

	private static void removeAll(ClientLevel lvl) {
		for (Entity entity : ACTIVE) {
			if (!entity.isRemoved()) {
				lvl.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		clearAll();
	}

	private static void rebuild(ClientLevel lvl, LocalPlayer player) {
		removeAll(lvl);
		appliedVersion = MinifriendsConfig.version();

		MinifriendsConfig cfg = MinifriendsConfig.get();
		if (!cfg.enabled) {
			return;
		}
		activeScale = cfg.scale;

		List<MinifriendsConfig.Friend> valid = new ArrayList<>();
		for (MinifriendsConfig.Friend f : cfg.friends) {
			if (f != null && f.name != null && !f.name.isBlank() && valid.size() < MinifriendsConfig.MAX_FRIENDS) {
				valid.add(f);
			}
		}

		for (int i = 0; i < valid.size(); i++) {
			MinifriendsConfig.Friend f = valid.get(i);
			String name = f.name.trim();
			FollowLogic logic = new FollowLogic(i, valid.size(), cfg.scale);
			Mannequin entity = new MiniFriend(lvl, name, cfg.scale, logic);
			logic.teleport(entity, player);
			boolean external = false;

			if (!Compat.hasRenderer(entity)) {
				// the subclass has no renderer in this version: use a plain vanilla mannequin instead
				Mannequin plain = Compat.createPlain(lvl);
				if (plain != null) {
					Compat.configure(plain, name, cfg.scale);
					logic.teleport(plain, player);
					if (Compat.hasRenderer(plain)) {
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
			ACTIVE.add(entity);
			if (external) {
				EXTERNAL.put(entity, logic);
			}

			if (f.showNametag) {
				NameStand stand = new NameStand(lvl, name);
				if (Compat.hasRenderer(stand)) {
					stand.setPos(entity.getX(), entity.getY(), entity.getZ());
					stand.setId(nextId++);
					lvl.addEntity(stand);
					ACTIVE.add(stand);
					STANDS.put(entity, stand);
				} else {
					MinifriendsClient.LOGGER.error("No renderer for the name stand, name tag skipped");
				}
			}
		}
	}
}
