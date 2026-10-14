package dev.juiced.modules.tweaks;

import java.lang.reflect.Field;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import org.joml.Vector4f;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.ServerBlockList;
import dev.juiced.render.Gui2D;

/** Everything fully lit, so caves and night fights are readable. Only changes how you see the world. */
public final class Fullbright extends Module {
	public static Fullbright INSTANCE;

	private static Field textureField;
	private static boolean broken;

	private final ServerBlockList servers = ServerBlockList.add(this, List.of(), null);

	public Fullbright() {
		super("fullbright", "Fullbright", Category.TWEAKS, false,
				"Alles ist voll beleuchtet: Höhlen und Nachtkämpfe sind hell und klar. Ändert nur, was du siehst. "
						+ "Auf Servern, die du sperrst, bleibt es aus.");
		INSTANCE = this;
	}

	public static boolean active() {
		return INSTANCE != null && INSTANCE.isEnabled() && !broken && !INSTANCE.servers.blockedHere();
	}

	/**
	 * Called from LightmapMixin instead of the normal light calculation: fills the light texture
	 * with white. Returns false if that didn't work, so the game keeps its normal lighting.
	 */
	public static boolean whiteOut(Object lightmap) {
		try {
			if (textureField == null) {
				for (Field field : lightmap.getClass().getDeclaredFields()) {
					if (GpuTexture.class.isAssignableFrom(field.getType())) {
						field.setAccessible(true);
						textureField = field;
						break;
					}
				}

				if (textureField == null) {
					throw new IllegalStateException("no light texture field");
				}
			}

			GpuTexture texture = (GpuTexture) textureField.get(lightmap);

			if (texture == null) {
				return false;
			}

			RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1f, 1f, 1f, 1f));
			return true;
		} catch (Throwable error) {
			broken = true;
			JuicedClient.LOGGER.warn("Fullbright is not available in this Minecraft version", error);
			return false;
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float t = 0.5f + 0.5f * (float) Math.sin(time * 1.5f);
		int dark = 0x15151C;
		int bright = 0x8FA3B8;

		for (int i = 0; i < 6; i++) {
			int bx = x + 8 + i * (w - 16) / 6;
			int bh = 10 + (i * 7) % 18;
			Gui2D.fill(g, bx, y + h - 8 - bh, bx + (w - 16) / 6 - 1, y + h - 8, Colors.argb(1f, Colors.mixRgb(dark, bright, t)));
		}

		Gui2D.centered(g, "☀", x + w / 2f, y + 6, Colors.argb(0.4f + 0.6f * t, 0xFFE070), true, 2f);
	}
}
