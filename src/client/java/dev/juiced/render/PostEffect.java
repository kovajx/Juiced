package dev.juiced.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

import dev.juiced.JuicedClient;

/**
 * Puts one of our full-screen shader effects (assets/juiced/post_effect) on the screen.
 *
 * <p>Uses the game's own slot for such effects: the one that tints the screen when you spectate a
 * creeper or a spider. That way the game draws it at the right moment, after the world and before
 * the HUD. The slot's methods are private, so they're looked up once by name; if that ever fails,
 * the effect simply stays off and a warning goes to the log.
 */
public final class PostEffect {
	private static boolean looked;
	private static boolean broken;
	private static @Nullable Method set;
	private static @Nullable Method clear;
	/** The slot's current effect, to notice when the game replaced or cleared ours. Optional. */
	private static @Nullable Field current;
	private static @Nullable Identifier applied;

	private PostEffect() {
	}

	/** Shows {@code effect}, or removes ours with null. Cheap to call every tick. */
	public static void show(GameRenderer renderer, @Nullable Identifier effect) {
		if (broken || !lookUp()) {
			return;
		}

		try {
			Object now = current == null ? null : current.get(renderer);

			if (effect == null) {
				// Only clear the slot while it still holds our effect, never the game's own
				if (applied != null && (current == null || applied.equals(now))) {
					clear.invoke(renderer);
				}

				applied = null;
				return;
			}

			// Without the field we can't see whether the game cleared it, so set it again each time
			if (current == null || !effect.equals(now)) {
				set.invoke(renderer, effect);
			}

			applied = effect;
		} catch (ReflectiveOperationException | RuntimeException error) {
			broken = true;
			JuicedClient.LOGGER.warn("Juiced: screen effects are not available in this Minecraft version", error);
		}
	}

	private static boolean lookUp() {
		if (looked) {
			return set != null && clear != null;
		}

		looked = true;

		try {
			set = GameRenderer.class.getDeclaredMethod("setSpectatedEntityPostEffect", Identifier.class);
			clear = GameRenderer.class.getDeclaredMethod("clearSpectatedEntityPostEffect");
			set.setAccessible(true);
			clear.setAccessible(true);
		} catch (ReflectiveOperationException | RuntimeException error) {
			broken = true;
			JuicedClient.LOGGER.warn("Juiced: screen effects are not available in this Minecraft version", error);
			return false;
		}

		for (Field field : GameRenderer.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && field.getType() == Identifier.class) {
				if (current != null) {
					// More than one candidate: don't guess
					current = null;
					break;
				}

				current = field;
			}
		}

		if (current != null) {
			try {
				current.setAccessible(true);
			} catch (RuntimeException error) {
				current = null;
			}
		}

		return true;
	}
}
