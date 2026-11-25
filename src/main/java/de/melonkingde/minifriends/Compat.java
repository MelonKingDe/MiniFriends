package de.melonkingde.minifriends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Mannequin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection fallbacks for Minecraft APIs that changed between versions.
 * Minecraft 26.x is unobfuscated, so looking things up by name is stable.
 */
final class Compat {
	private Compat() {
	}

	// ---- open a screen ---------------------------------------------------------------

	static void openScreen(Minecraft mc, Screen screen) {
		try {
			if (invokeSetScreen(mc, screen)) {
				return;
			}
			// maybe setScreen moved to another object (e.g. mc.gui)
			for (Field field : mc.getClass().getFields()) {
				if (hasSetScreen(field.getType())) {
					Object target = field.get(mc);
					if (target != null && invokeSetScreen(target, screen)) {
						return;
					}
				}
			}
			for (Method accessor : mc.getClass().getMethods()) {
				if (accessor.getParameterCount() == 0 && hasSetScreen(accessor.getReturnType())) {
					Object target = accessor.invoke(mc);
					if (target != null && invokeSetScreen(target, screen)) {
						return;
					}
				}
			}
			MinifriendsClient.LOGGER.error("Could not find a setScreen method");
		} catch (ReflectiveOperationException e) {
			MinifriendsClient.LOGGER.error("Could not open screen", e);
		}
	}

	private static boolean hasSetScreen(Class<?> type) {
		for (Method m : type.getMethods()) {
			if (isSetScreen(m)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSetScreen(Method m) {
		return m.getName().equals("setScreen")
			&& m.getParameterCount() == 1
			&& m.getParameterTypes()[0].isAssignableFrom(Screen.class);
	}

	private static boolean invokeSetScreen(Object target, Screen screen) throws ReflectiveOperationException {
		for (Method m : target.getClass().getMethods()) {
			if (isSetScreen(m)) {
				m.invoke(target, screen);
				return true;
			}
		}
		return false;
	}

	// ---- mannequin entity type -------------------------------------------------------

	@SuppressWarnings("unchecked")
	static EntityType<Mannequin> mannequinType() {
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().equals("mannequin")) {
				return (EntityType<Mannequin>) type;
			}
		}
		StringBuilder names = new StringBuilder();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			names.append(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()).append(' ');
		}
		MinifriendsClient.LOGGER.error("Mannequin entity type not found. Registered types: {}", names);
		throw new IllegalStateException("Mannequin entity type not found");
	}

	// ---- mannequin profile -----------------------------------------------------------

	@SuppressWarnings("unchecked")
	static void setProfile(Entity entity, Object profile) {
		try {
			for (Method m : entity.getClass().getMethods()) {
				if (m.getName().equals("setProfile") && m.getParameterCount() == 1
					&& m.getParameterTypes()[0].isInstance(profile)) {
					m.invoke(entity, profile);
					return;
				}
			}
			for (Class<?> c = entity.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())
						&& EntityDataAccessor.class.isAssignableFrom(field.getType())
						&& field.getName().toUpperCase().contains("PROFILE")) {
						field.setAccessible(true);
						EntityDataAccessor<Object> accessor = (EntityDataAccessor<Object>) field.get(null);
						entity.getEntityData().set(accessor, profile);
						return;
					}
				}
			}
			MinifriendsClient.LOGGER.error("Could not find a way to set the mannequin profile");
		} catch (ReflectiveOperationException | RuntimeException e) {
			MinifriendsClient.LOGGER.error("Could not set the mannequin profile", e);
		}
	}
}
