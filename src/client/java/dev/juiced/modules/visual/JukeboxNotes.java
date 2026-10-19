package dev.juiced.modules.visual;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.modules.cosmetic.HeadWear;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/** While a jukebox plays, colorful notes float out of it and a soft ring pulses on top in a steady beat. */
public final class JukeboxNotes extends Module {
	private final SliderSetting tempo = add(new SliderSetting("tempo", "Takt", 60, 160, 5, 100, v -> Math.round(v) + " BPM"));

	public JukeboxNotes() {
		super("jukebox_notes", "Jukebox-Noten", Category.VISUALS, true,
				"Läuft eine Jukebox, steigen bunte Musiknoten aus ihr auf, und ein Ring auf ihr pulsiert im Takt.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		dev.juiced.render.NearbyBlocks.update(minecraft);
	}

	/** Only a jukebox with a record in it counts. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return dev.juiced.render.NearbyBlocks.count(dev.juiced.render.NearbyBlocks.JUKEBOX) > 0 ? null : "Braucht laufende Jukebox";
	}

	@Override
	public void renderWorld(Geo geo) {
		int count = dev.juiced.render.NearbyBlocks.count(dev.juiced.render.NearbyBlocks.JUKEBOX);

		if (count == 0) {
			return;
		}

		float time = Clock.tf();
		float beatLength = 60f / tempo.f();
		float beat = (time % beatLength) / beatLength;
		float pulse = (float) Math.pow(1f - beat, 3);

		for (int j = 0; j < count; j++) {
			double x = dev.juiced.render.NearbyBlocks.x(dev.juiced.render.NearbyBlocks.JUKEBOX, j) + 0.5;
			double y = dev.juiced.render.NearbyBlocks.y(dev.juiced.render.NearbyBlocks.JUKEBOX, j) + 1.0;
			double z = dev.juiced.render.NearbyBlocks.z(dev.juiced.render.NearbyBlocks.JUKEBOX, j) + 0.5;

			if (geo.distanceSq(x, y, z) > 48 * 48) {
				continue;
			}

			int ringColor = Theme.rainbow(time * 0.1f + j * 0.3f);
			geo.groundRing(x, y + 0.02, z, 0.42f + pulse * 0.18f, 0.025f, 0.06f, Theme.detail(32), 0f, (float) (Math.PI * 2),
					t -> Theme.rainbow(t + time * 0.1f), 0.35f + 0.5f * pulse);
			geo.glowDot(x, y + 0.1, z, 0.6f + pulse * 0.3f, ringColor, 0.15f + 0.15f * pulse);

			for (int k = 0; k < 5; k++) {
				float life = (time * 0.3f + k / 5f + j * 0.37f) % 1f;
				double a = k * 1.9 + j;
				double nx = x + Math.cos(a) * (0.15 + life * 0.5) + Math.sin(time * 1.5 + k) * 0.08;
				double nz = z + Math.sin(a) * (0.15 + life * 0.5);
				double ny = y + 0.15 + life * 1.6;
				float alpha = (float) Math.sin(life * Math.PI);
				HeadWear.drawNote(geo, nx, ny, nz, 0.07f, Colors.mixRgb(Theme.rainbow(k / 5f + j * 0.2f), 0xFFFFFF, 0.2f), alpha, k % 2 == 0);
			}
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		float cx = px + w / 2f;
		float base = py + h - 12;
		Gui2D.rounded(g, Math.round(cx - 10), Math.round(base - 14), Math.round(cx + 10), Math.round(base + 4), 0xFF6B4A2E);
		Gui2D.fill(g, Math.round(cx - 6), Math.round(base - 14), Math.round(cx + 6), Math.round(base - 12), 0xFF2A1A10);
		float beat = (time % 0.6f) / 0.6f;
		Gui2D.ellipse(g, cx, base - 14, 12 + (1 - beat) * 4, 3, 24, 1f, 0xFFD36B, 0.4f + 0.5f * (1 - beat), 0, false);

		for (int k = 0; k < 3; k++) {
			float life = (time * 0.4f + k / 3f) % 1f;
			Gui2D.text(g, k % 2 == 0 ? "♪" : "♫", cx - 8 + k * 7, base - 18 - life * (h - 30), Colors.argb((float) Math.sin(life * Math.PI), Theme.rainbow(k / 3f)), false, 1f);
		}
	}
}
