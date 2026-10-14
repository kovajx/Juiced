package dev.juiced.render;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

import dev.juiced.JuicedClient;

/**
 * Puts one of our full-screen shader effects (assets/juiced/post_effect) on the screen.
 *
 * <p>Uses the game's own slot for such effects: the one that tints the screen when you spectate a
 * creeper or a spider. The game requests it every frame (in any game mode) and draws it after the
 * world and before the HUD. Reading and clearing the slot are public; only setting it is private,
 * so that one method is looked up by name. If that ever fails, the effect stays off and a warning
 * goes to the log.
 */
public final class PostEffect {
	private static boolean looked;
	private static @Nullable Method set;
	private static @Nullable Identifier applied;

	private PostEffect() {
	}

	/** Shows {@code effect}, or removes ours with null. Cheap to call every tick. */
	public static void show(GameRenderer renderer, @Nullable Identifier effect) {
		Identifier now = renderer.spectatedEntityPostEffect();

		if (effect == null) {
			// Only clear the slot while it still holds our effect, never the game's own
			if (applied != null && applied.equals(now)) {
				renderer.clearSpectatedEntityPostEffect();
			}

			applied = null;
			return;
		}

		// The game clears the slot whenever the camera switches entities (respawn, new world), so set it again
		if (!effect.equals(now) && lookUp()) {
			try {
				set.invoke(renderer, effect);
			} catch (ReflectiveOperationException | RuntimeException error) {
				set = null;
				JuicedClient.LOGGER.warn("Juiced: screen effects are not available in this Minecraft version", error);
			}
		}

		applied = effect;
	}

	private static boolean lookUp() {
		if (!looked) {
			looked = true;

			try {
				set = GameRenderer.class.getDeclaredMethod("setSpectatedEntityPostEffect", Identifier.class);
				set.setAccessible(true);
			} catch (ReflectiveOperationException | RuntimeException error) {
				set = null;
				JuicedClient.LOGGER.warn("Juiced: screen effects are not available in this Minecraft version", error);
			}
		}

		return set != null;
	}
}
