package dev.juiced.modules.cosmetic;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Pose3;

/** Cat ears, bunny ears, devil horns or ram horns on your head. */
public final class Ears extends CosmeticModule {
	private static final float TOP = 0.47f;

	private final ChoiceSetting kind = add(new ChoiceSetting("kind", "Art", 0, "Katzenohren", "Hasenohren", "Teufelshörner", "Widderhörner", "Fuchsohren", "Elfenohren", "Bärenohren", "Fühler"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", Colors.BLACK, true));
	private final ColorSetting inner = add(new ColorSetting("inner", "Innenfarbe", Colors.PINK, true));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.5, 1.0));
	private final BoolSetting twitch = add(new BoolSetting("twitch", "Zucken", true));

	private float twitchLeft;
	private float twitchRight;
	private float flop;
	private double nextTwitch;

	public Ears() {
		super("ears", "Ohren & Hörner", "Katzen-, Hasen-, Fuchs-, Elfen- oder Bärenohren, Teufels- oder Widderhörner und leuchtende Fühler. Sie bewegen sich mit deinem Kopf und zucken ab und zu.");
	}

	@Override
	protected void onTick(Minecraft minecraft, LocalPlayer self) {
		double now = Clock.t();

		if (twitch.on() && now > nextTwitch) {
			ThreadLocalRandom random = ThreadLocalRandom.current();

			if (random.nextBoolean()) {
				twitchLeft = 1f;
			} else {
				twitchRight = 1f;
			}

			nextTwitch = now + 2.5 + random.nextDouble() * 4;
		}
	}

	@Override
	protected void draw(Geo geo) {
		float dt = Clock.dt();
		twitchLeft = Math.max(0f, twitchLeft - dt * 5f);
		twitchRight = Math.max(0f, twitchRight - dt * 5f);
		double speed = player.getDeltaMovement().horizontalDistance();
		flop = Clock.approach(flop, (float) Math.min(1.0, speed * 4), 6f);
		float s = size.f();
		int main = color.rgb();
		int in = inner.rgb();

		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			float twitchAmount = sideSign < 0 ? twitchLeft : twitchRight;
			float wiggle = (float) Math.sin(twitchAmount * Math.PI * 3) * 14f * twitchAmount;

			switch (kind.index()) {
				case 1 -> bunny(geo, sideSign, s, main, in, wiggle);
				case 2 -> devil(geo, sideSign, s, main);
				case 3 -> ram(geo, sideSign, s, main);
				case 4 -> fox(geo, sideSign, s, main, in, wiggle);
				case 5 -> elf(geo, sideSign, s, main, wiggle);
				case 6 -> bear(geo, sideSign, s, main, in, wiggle);
				case 7 -> antenna(geo, sideSign, s, wiggle);
				default -> cat(geo, sideSign, s, main, in, wiggle);
			}
		}
	}

	private void cat(Geo geo, int sideSign, float s, int main, int in, float wiggle) {
		Pose3 ear = headPose().move(sideSign * 0.15, TOP - 0.02, 0.02).roll(sideSign * 14 + wiggle * sideSign).pitch(-6);
		float w = 0.085f * s;
		float h = 0.15f * s;
		// Counter-clockwise in the ear's right/up plane
		geo.prism(ear, new float[] {-w, w, sideSign * 0.015f * s}, new float[] {0, 0, h}, 0.012f * s, main, 1f);
		Pose3 inside = ear.copy().move(0, 0.012 * s, 0.014 * s);
		geo.prism(inside, new float[] {-w * 0.55f, w * 0.55f, sideSign * 0.012f * s}, new float[] {0, 0, h * 0.68f}, 0.003f * s, in, 1f);
	}

	private void fox(Geo geo, int sideSign, float s, int main, int in, float wiggle) {
		int fur = color.get() == Colors.BLACK ? 0xE8752B : main;
		int inside = inner.get() == Colors.PINK ? 0xF4F1EA : in;
		Pose3 ear = headPose().move(sideSign * 0.14, TOP - 0.02, 0.0).roll(sideSign * 10 + wiggle * sideSign).pitch(-4);
		float w = 0.075f * s;
		float h = 0.2f * s;
		geo.prism(ear, new float[] {-w, w, sideSign * 0.01f * s}, new float[] {0, 0, h}, 0.012f * s, fur, 1f);
		geo.prism(ear.copy().move(0, 0.01 * s, 0.014 * s), new float[] {-w * 0.55f, w * 0.55f, sideSign * 0.008f * s}, new float[] {0, 0, h * 0.62f}, 0.003f * s, inside, 1f);
		// Dark tip
		geo.prism(ear.copy().move(0, 0, 0.013 * s), new float[] {-w * 0.34f + sideSign * 0.004f * s, w * 0.34f + sideSign * 0.004f * s, sideSign * 0.01f * s},
				new float[] {h * 0.66f, h * 0.66f, h}, 0.004f * s, 0x2A1A14, 1f);
	}

	private void elf(Geo geo, int sideSign, float s, int main, float wiggle) {
		int skin = color.get() == Colors.BLACK ? 0xE8B894 : main;
		// Flat pointed ear on the side of the head, tip pointing back and up
		Pose3 ear = headPose().move(sideSign * 0.245, 0.24, -0.02).yaw(-90).roll(wiggle * 0.3f);
		geo.prism(ear, new float[] {-0.05f * s, 0.03f * s, 0.03f * s, -0.17f * s}, new float[] {-0.03f * s, -0.03f * s, 0.06f * s, 0.15f * s}, 0.012f * s, skin, 1f);
	}

	private void bear(Geo geo, int sideSign, float s, int main, int in, float wiggle) {
		int fur = color.get() == Colors.BLACK ? 0x8B5A2B : main;
		int inside = inner.get() == Colors.PINK ? 0xD9A066 : in;
		Pose3 ear = headPose().move(sideSign * 0.17, TOP - 0.035, -0.01).roll(sideSign * 12 + wiggle * 0.4f * sideSign);
		geo.prism(ear, halfCircleX(0.08f * s), halfCircleY(0.08f * s), 0.02f * s, fur, 1f);
		geo.prism(ear.copy().move(0, 0.008 * s, 0.021 * s), halfCircleX(0.048f * s), halfCircleY(0.048f * s), 0.003f * s, inside, 1f);
	}

	/** x coordinates of a half circle (flat side down), counter-clockwise. */
	private static float[] halfCircleX(float radius) {
		float[] xs = new float[8];

		for (int i = 0; i < 8; i++) {
			xs[i] = (float) Math.cos(i * Math.PI / 7) * radius;
		}

		return xs;
	}

	private static float[] halfCircleY(float radius) {
		float[] ys = new float[8];

		for (int i = 0; i < 8; i++) {
			ys[i] = (float) Math.sin(i * Math.PI / 7) * radius;
		}

		return ys;
	}

	/** Two thin feelers with glowing tips that sway as you move. */
	private void antenna(Geo geo, int sideSign, float s, float wiggle) {
		int stalk = color.get() == Colors.BLACK ? 0x2A2A30 : color.rgb();
		float time = Clock.tf();
		Pose3 part = headPose().move(sideSign * 0.1, TOP, 0.06).roll(sideSign * 18).pitch(-15);
		float sway = (float) Math.sin(time * 2.2f + sideSign) * 6f + wiggle * 0.6f + flop * 25f;
		geo.beginModel();

		for (int i = 0; i < 6; i++) {
			geo.box(part.copy().move(0, 0.025 * s, 0), 0.008f * s, 0.026f * s, 0.008f * s, stalk, 1f);
			part.move(0, 0.05 * s, 0).pitch(-10 - sway * 0.4f).roll(sideSign * 4);
		}

		geo.endModel();
		int glow = inner.rgb(time * 0.1f);
		geo.glowDot(part.x, part.y, part.z, 0.11f * s, glow, 0.35f);
		geo.glowDot(part.x, part.y, part.z, 0.035f * s, Colors.mixRgb(glow, 0xFFFFFF, 0.5f), 1f);
	}

	private void bunny(Geo geo, int sideSign, float s, int main, int in, float wiggle) {
		float back = -10f - flop * 30f;
		Pose3 ear = headPose().move(sideSign * 0.1, TOP - 0.02, -0.03).roll(sideSign * 9 + wiggle * 0.5f * sideSign).pitch(back);
		Pose3 center = ear.copy().move(0, 0.17 * s, 0);
		geo.beginModel();
		geo.box(center, 0.045f * s, 0.17f * s, 0.018f * s, main, 1f);
		geo.box(center.copy().move(0, 0.01 * s, 0.017 * s), 0.026f * s, 0.13f * s, 0.003f * s, in, 1f);
		geo.endModel();
	}

	private void devil(Geo geo, int sideSign, float s, int main) {
		int rgb = color.get() == Colors.BLACK ? 0xC0201A : main;
		Pose3 horn = headPose().move(sideSign * 0.15, TOP - 0.05, 0.12).roll(sideSign * 18);
		geo.beginModel();

		for (int i = 0; i < 4; i++) {
			float half = (0.042f - i * 0.009f) * s;
			geo.box(horn.copy().move(0, 0.035 * s, 0), half, 0.035f * s, half, Colors.shade(rgb, -0.12f * i), 1f);
			horn.move(0, 0.06 * s, 0).pitch(-22).roll(sideSign * 8);
		}

		geo.endModel();
	}

	private void ram(Geo geo, int sideSign, float s, int main) {
		int rgb = color.get() == Colors.BLACK ? 0xC9B48E : main;
		Pose3 horn = headPose().move(sideSign * 0.22, TOP - 0.1, 0.03).yaw(sideSign * 90).pitch(-55);
		geo.beginModel();

		for (int i = 0; i < 9; i++) {
			float half = (0.045f - i * 0.0035f) * s;
			geo.box(horn.copy().move(0, 0, 0.025 * s), half, half, 0.028f * s, Colors.shade(rgb, (i % 2 == 0 ? 0f : -0.12f)), 1f);
			horn.move(0, 0, 0.045 * s).pitch(38);
		}

		geo.endModel();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float top = feetY - 36;
		int main = color.rgb();
		int in = inner.rgb();
		float wiggle = (time % 3f) < 0.3f ? (float) Math.sin(time * 40) * 1.5f : 0f;

		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			float ex = cx + sideSign * 3;

			switch (kind.index()) {
				case 1 -> {
					Gui2D.fill(g, Math.round(ex - 1.5f), Math.round(top - 11), Math.round(ex + 1.5f), Math.round(top), Colors.argb(1f, main == 0x24242C ? 0xEDEDF2 : main));
					Gui2D.fill(g, Math.round(ex - 0.5f), Math.round(top - 9), Math.round(ex + 0.5f), Math.round(top - 1), Colors.argb(1f, in));
				}
				case 4 -> {
					for (int row = 0; row < 7; row++) {
						int half = Math.max(0, 2 - row / 3);
						Gui2D.fill(g, Math.round(ex - half), Math.round(top - row - 1), Math.round(ex + half + 1), Math.round(top - row), Colors.argb(1f, row > 4 ? 0x2A1A14 : 0xE8752B));
					}
				}
				case 5 -> Gui2D.fill(g, Math.round(cx + sideSign * 5), Math.round(top + 4), Math.round(cx + sideSign * 8), Math.round(top + 6), Colors.argb(1f, 0xE8B894));
				case 6 -> Gui2D.rounded(g, Math.round(ex - 2), Math.round(top - 4), Math.round(ex + 3), Math.round(top), Colors.argb(1f, 0x8B5A2B));
				case 7 -> {
					Gui2D.fill(g, Math.round(ex), Math.round(top - 8), Math.round(ex + 1), Math.round(top), 0xFF2A2A30);
					Gui2D.glowDot(g, ex + 0.5f + sideSign, top - 9, 1.6f, inner.rgb(), 1f);
				}
				case 2, 3 -> {
					int rgb = kind.index() == 2 ? 0xC0201A : 0xC9B48E;

					for (int i = 0; i < 4; i++) {
						Gui2D.fill(g, Math.round(ex + sideSign * i * 0.8f), Math.round(top - 2 - i * 1.5f), Math.round(ex + sideSign * i * 0.8f) + 2, Math.round(top - i * 1.5f), Colors.argb(1f, rgb));
					}
				}
				default -> {
					for (int row = 0; row < 5; row++) {
						int half = Math.max(0, 2 - row / 2);
						Gui2D.fill(g, Math.round(ex - half + wiggle * sideSign), Math.round(top - row - 1), Math.round(ex + half + 1 + wiggle * sideSign), Math.round(top - row), Colors.argb(1f, row < 2 ? in : main));
					}
				}
			}
		}
	}
}
