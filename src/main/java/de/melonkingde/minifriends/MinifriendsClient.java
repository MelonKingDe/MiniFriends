package de.melonkingde.minifriends;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinifriendsClient implements ClientModInitializer {
	public static final String MOD_ID = "minifriends";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		MinifriendsConfig.load();

		ClientTickEvents.END_CLIENT_TICK.register(FriendManager::tick);

		// /minifriends opens the settings screen
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(
				LiteralArgumentBuilder.<FabricClientCommandSource>literal("minifriends")
					.executes(context -> {
						FriendManager.requestOpenScreen();
						return 1;
					})
			)
		);
	}
}
