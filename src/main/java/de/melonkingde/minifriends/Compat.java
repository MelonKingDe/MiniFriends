package de.melonkingde.minifriends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
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

	// ---- shared setup for friend entities ----------------------------------------------

	private static final double SCALE = 0.35D;

	static void configure(Mannequin e, String name, boolean showNametag) {
		setProfile(e, ResolvableProfile.createUnresolved(name));
		e.setCustomName(Component.literal(name));
		e.setCustomNameVisible(showNametag);
		AttributeInstance scale = e.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(SCALE);
		}
		e.noPhysics = true;
		e.setNoGravity(true);
	}

	/** A plain vanilla Mannequin (no subclass), created through its own factory. */
	static Mannequin createPlain(Level level) {
		try {
			EntityType<Mannequin> type = mannequinType();
			for (Method m : Mannequin.class.getDeclaredMethods()) {
				if (m.getName().equals("create") && m.getParameterCount() == 2
					&& m.getParameterTypes()[0].isAssignableFrom(EntityType.class)
					&& m.getParameterTypes()[1].isAssignableFrom(Level.class)) {
					m.setAccessible(true);
					Object result = m.invoke(null, type, level);
					if (result instanceof Mannequin mannequin) {
						return mannequin;
					}
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			MinifriendsClient.LOGGER.error("Could not create a plain mannequin", e);
		}
		return null;
	}

	private static Method animationMethod;
	private static Method oldPosMethod;

	static void animate(Entity e) {
		try {
			if (animationMethod == null) {
				animationMethod = findMethod(e.getClass(), "calculateEntityAnimation", boolean.class);
			}
			if (animationMethod != null) {
				animationMethod.invoke(e, false);
			}
		} catch (ReflectiveOperationException | RuntimeException ex) {
			MinifriendsClient.LOGGER.error("Could not update walk animation", ex);
			animationMethod = null;
		}
	}

	static void setOldPosAndRot(Entity e) {
		try {
			if (oldPosMethod == null) {
				oldPosMethod = findMethod(e.getClass(), "setOldPosAndRot");
			}
			if (oldPosMethod != null) {
				oldPosMethod.invoke(e);
			}
		} catch (ReflectiveOperationException | RuntimeException ex) {
			oldPosMethod = null;
		}
	}

	private static Method findMethod(Class<?> start, String name, Class<?>... params) {
		for (Class<?> c = start; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name, params);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
				// try the superclass
			}
		}
		return null;
	}

	// ---- renderer check + diagnostics ------------------------------------------------

	static boolean hasRenderer(Entity entity) {
		try {
			return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}

	static void dump(Entity entity) {
		var log = MinifriendsClient.LOGGER;
		log.error("[diag] entity class={} type={} typeClass={}", entity.getClass().getName(),
			BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getType().getClass().getName());
		Object dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		for (String mapName : new String[] {"renderers", "playerRenderers", "mannequinRenderers"}) {
			try {
				Field f = dispatcher.getClass().getDeclaredField(mapName);
				f.setAccessible(true);
				java.util.Map<?, ?> map = (java.util.Map<?, ?>) f.get(dispatcher);
				if (mapName.equals("renderers")) {
					log.error("[diag] renderers size={} containsMannequin={}", map.size(), map.containsKey(entity.getType()));
				} else {
					log.error("[diag] {} keys={}", mapName, map.keySet());
				}
			} catch (ReflectiveOperationException | RuntimeException ex) {
				log.error("[diag] could not read {}", mapName);
			}
		}
		for (Method m : dispatcher.getClass().getDeclaredMethods()) {
			if (m.getName().equals("getRenderer") || m.getName().equals("getAvatarRenderer")) {
				log.error("[diag] {} params={}", m.getName(), java.util.Arrays.toString(m.getParameterTypes()));
			}
		}
		Class<?> c = entity.getClass();
		while (c != null && c != Entity.class) {
			dumpClass(log, c);
			c = c.getSuperclass();
		}
	}

	private static void dumpClass(org.slf4j.Logger log, Class<?> c) {
		log.error("[diag] class {} extends {}", c.getName(), c.getSuperclass() == null ? "-" : c.getSuperclass().getName());
		for (Field f : c.getDeclaredFields()) {
			log.error("[diag]   field {} {}", f.getType().getSimpleName(), f.getName());
		}
		for (Method m : c.getDeclaredMethods()) {
			if (!m.isSynthetic()) {
				log.error("[diag]   method {} {}({})", m.getReturnType().getSimpleName(), m.getName(), m.getParameterCount());
			}
		}
	}
}
