package de.melonkingde.minifriends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MinifriendsConfig {
	public static final int MAX_FRIENDS = 5;
	public static final float MIN_SCALE = 0.15F;
	public static final float MAX_SCALE = 1.0F;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static MinifriendsConfig instance = new MinifriendsConfig();
	private static int version = 0;

	public boolean enabled = true;
	/** Size of the friends relative to a normal player (1.0 = same size). */
	public float scale = 0.35F;
	public List<Friend> friends = new ArrayList<>();

	public static final class Friend {
		public String name = "";
		public boolean showNametag = true;
	}

	public static MinifriendsConfig get() {
		return instance;
	}

	/** Increases every time the config is loaded or saved, so the manager knows when to rebuild. */
	public static int version() {
		return version;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("minifriends.json");
	}

	public static void load() {
		Path file = path();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				MinifriendsConfig loaded = GSON.fromJson(reader, MinifriendsConfig.class);
				if (loaded != null) {
					if (loaded.friends == null) {
						loaded.friends = new ArrayList<>();
					}
					loaded.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, loaded.scale));
					instance = loaded;
				}
			} catch (IOException | RuntimeException e) {
				MinifriendsClient.LOGGER.error("Could not read minifriends.json, using defaults", e);
			}
		}
		version++;
	}

	public static void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			MinifriendsClient.LOGGER.error("Could not save minifriends.json", e);
		}
		version++;
	}
}
