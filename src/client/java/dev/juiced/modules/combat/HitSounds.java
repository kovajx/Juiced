package dev.juiced.modules.combat;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

public final class HitSounds extends Module {
	private static final String[] SOUNDS = {
			"block.note_block.hat", "block.note_block.bell", "block.note_block.pling", "block.amethyst_block.hit",
			"block.bubble_column.bubble_pop", "entity.experience_orb.pickup", "block.note_block.bass", "block.note_block.xylophone"
	};

	private final ChoiceSetting sound = add(new ChoiceSetting("sound", "Sound", 0, "Klick", "Glocke", "Pling", "Amethyst", "Blase", "XP", "Bass", "Xylophon"));
	private final SliderSetting volume = add(SliderSetting.percent("volume", "Lautstärke", 0.0, 1.0, 0.7));
	private final SliderSetting pitch = add(new SliderSetting("pitch", "Tonhöhe", 0.5, 2.0, 0.05, 1.0, v -> String.format("%.2fx", v)));
	private final BoolSetting critPitch = add(new BoolSetting("crit_pitch", "Crits höher", true));
	private final SliderSetting killVolume = add(SliderSetting.percent("kill_volume", "Kill-Sound", 0.0, 1.0, 0.6));

	private long lastPreviewSound;

	public HitSounds() {
		super("hit_sounds", "Hit-Sounds", Category.COMBAT, false,
				"Ein klarer Sound bei jedem Treffer, damit du Treffer hörst statt sie zu raten. Nur du hörst ihn.");
	}

	@Override
	public void onHit(LivingEntity target, boolean crit) {
		float p = pitch.f() * (crit && critPitch.on() ? 1.25f : 1f);
		p += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.06f;
		Fx.sound(SOUNDS[sound.index()], volume.f(), p);
	}

	@Override
	public void onKill(LivingEntity target) {
		Fx.sound("entity.experience_orb.pickup", killVolume.f(), 0.7f);
		Fx.sound(SOUNDS[sound.index()], killVolume.f(), pitch.f() * 1.5f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int bars = 11;
		int barW = 5;
		int total = bars * barW + (bars - 1) * 3;
		int startX = x + (w - total) / 2;
		float beat = (time * 1.6f) % 1f;
		float hitPulse = beat < 0.2f ? 1f - beat / 0.2f : 0f;

		for (int i = 0; i < bars; i++) {
			float center = 1f - Math.abs(i - bars / 2f) / (bars / 2f);
			float wobble = 0.3f + 0.25f * (float) Math.sin(time * 9 + i * 1.3f);
			float height = (wobble * 0.5f + hitPulse * center) * (h * 0.6f) + 3;
			int bx = startX + i * (barW + 3);
			int cy = y + h / 2;
			Gui2D.rounded(g, bx, Math.round(cy - height / 2), bx + barW, Math.round(cy + height / 2), Colors.argb(0.9f, Theme.accent(i / (float) bars)));
		}

		Gui2D.centered(g, sound.name(), x + w / 2f, y + h - 12, 0x99FFFFFF, false, 0.75f);
	}
}
