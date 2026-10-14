package dev.juiced.modules.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/** Floating numbers in the world that show how much damage each hit did. */
public final class DamageNumbers extends Module {
	private static final int MAX = 40;
	private static final float LIFE = 1.0f;

	private final ChoiceSetting format = add(new ChoiceSetting("format", "Format", 0, "Herzen", "Lebenspunkte"));
	private final BoolSetting onlyMine = add(new BoolSetting("only_mine", "Nur meine Treffer", true));
	private final BoolSetting heals = add(new BoolSetting("heals", "Heilung zeigen", false));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", 9, true));
	private final ColorSetting critColor = add(new ColorSetting("crit_color", "Crit-Farbe", 6, true));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.5, 2.0, 1.0));

	private final List<Number> numbers = new ArrayList<>();

	public DamageNumbers() {
		super("damage_numbers", "Schadenszahlen", Category.VISUALS, true,
				"Zahlen fliegen aus dem Gegner und zeigen, wie viel jeder Treffer gemacht hat. Crits leuchten golden.");
	}

	@Override
	public void onHealthChange(LivingEntity entity, float amount, boolean byMe, boolean crit) {
		if (entity == Minecraft.getInstance().player) {
			return;
		}

		boolean heal = amount < 0;

		if ((heal && !heals.on()) || (!heal && onlyMine.on() && !byMe)) {
			return;
		}

		if (numbers.size() >= MAX) {
			numbers.remove(0);
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();
		double spread = entity.getBbWidth() * 0.6;
		numbers.add(new Number(
				entity.getX() + (random.nextDouble() - 0.5) * spread,
				entity.getY() + entity.getBbHeight() + 0.25,
				entity.getZ() + (random.nextDouble() - 0.5) * spread,
				text(Math.abs(amount), heal, crit), heal, crit, Clock.t()));
	}

	private String text(float amount, boolean heal, boolean crit) {
		float value = format.index() == 0 ? amount / 2f : amount;
		String number = Math.abs(value - Math.round(value)) < 0.05f ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
		String text = (heal ? "+" : "") + number;

		if (format.index() == 0) {
			text += " h";
		}

		return crit ? text + "!" : text;
	}

	@Override
	public void renderWorld(Geo geo) {
		if (numbers.isEmpty()) {
			return;
		}

		double now = Clock.t();
		float pixel = 0.045f * size.f();

		for (int i = numbers.size() - 1; i >= 0; i--) {
			Number number = numbers.get(i);
			float t = (float) ((now - number.start) / LIFE);

			if (t >= 1f) {
				numbers.remove(i);
				continue;
			}

			// Pop in big, settle, float up, fade out at the end
			float pop = t < 0.12f ? 1.5f - (t / 0.12f) * 0.5f : 1f;
			float rise = Clock.easeOut(t) * 0.7f;
			float alpha = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
			int rgb = number.heal ? 0x5CFF7A : number.crit ? critColor.rgb() : color.rgb();
			float scale = number.crit ? 1.25f : 1f;
			geo.pixelText(number.x, number.y + rise, number.z, number.text, pixel * pop * scale, rgb, alpha, true);
		}
	}

	@Override
	protected void onDisable() {
		numbers.clear();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, x + w / 2f, 0);
		String[] texts = {"2.5", "3!", "1.5"};

		for (int i = 0; i < 3; i++) {
			float t = Previews.cycle(time + i * 0.4f, 1.2f);
			float alpha = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
			boolean crit = i == 1;
			int rgb = crit ? critColor.rgb() : color.rgb();
			float px = x + w / 2f + (i - 1) * 16;
			float py = feetY - 44 - Clock.easeOut(t) * 18;
			Gui2D.centered(g, texts[i] + "❤", px, py, Colors.argb(alpha, rgb), true, crit ? 1.2f : 1f);
		}
	}

	private record Number(double x, double y, double z, String text, boolean heal, boolean crit, double start) {
	}
}
