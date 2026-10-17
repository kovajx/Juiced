package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.NearbyBlocks;
import dev.juiced.render.Sky;

/**
 * Soft beams of sunlight fall through the treetops around you, with specks of dust drifting
 * slowly inside them. Each beam fades in, stays a while and fades out somewhere else.
 */
public final class SunRays extends Module {
	private static final int MAX_RAYS = 10;
	private static final int MOTES_PER_RAY = 6;
	/** Beams lean a little, as if the sun stood in the south-east. */
	private static final float LEAN_X = -0.3f;
	private static final float LEAN_Z = -0.15f;
	/** Points along a beam (0 = treetop, 1 = ground) and how bright the beam is there. */
	private static final float[] ALONG = {0f, 0.2f, 0.75f, 1f};
	private static final float[] STRENGTH = {0f, 1f, 0.8f, 0f};

	private final SliderSetting amount = add(SliderSetting.percent("amount", "Menge", 0.3, 2.0, 1.0));
	private final BoolSetting dust = add(new BoolSetting("dust", "Staubkörnchen im Licht", true));

	private final double[] topX = new double[MAX_RAYS];
	private final double[] topY = new double[MAX_RAYS];
	private final double[] topZ = new double[MAX_RAYS];
	private final float[] length = new float[MAX_RAYS];
	private final float[] width = new float[MAX_RAYS];
	private final float[] age = new float[MAX_RAYS];
	private final float[] life = new float[MAX_RAYS];
	private final float[] phase = new float[MAX_RAYS];
	private final boolean[] alive = new boolean[MAX_RAYS];
	/** Dust specks: position along the beam (0..1) and a small sideways offset. */
	private final float[] moteAlong = new float[MAX_RAYS * MOTES_PER_RAY];
	private final float[] moteSide = new float[MAX_RAYS * MOTES_PER_RAY];

	// Reused for the beam ribbons
	private final float[] px = new float[4];
	private final float[] py = new float[4];
	private final float[] pz = new float[4];
	private final float[] hw = new float[4];
	private final int[] colors = new int[4];

	private float daylight;

	public SunRays() {
		super("sun_rays", "Sonnenstrahlen", Category.VISUALS, true,
				"Unter Bäumen fallen tagsüber weiche Lichtstrahlen durch die Blätter, in denen kleine Staubkörnchen schweben.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(alive, false);
	}

	@Override
	public void tick(Minecraft minecraft) {
		NearbyBlocks.update(minecraft);
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return null;
		}

		boolean day = Sky.overworld(level) && !level.isRaining() && Sky.day(level, player.blockPosition()) >= 0.2f;
		return day && NearbyBlocks.count(NearbyBlocks.LEAVES) >= 12 ? null : "Tagsüber unter Bäumen";
	}

	@Override
	public void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return;
		}

		float dt = Math.min(0.1f, Clock.dt());
		float time = Clock.tf();
		float target = Sky.overworld(level) && !level.isRaining() ? Sky.day(level, player.blockPosition()) : 0f;
		daylight = Clock.approach(daylight, target, 0.4f);
		int leaves = NearbyBlocks.count(NearbyBlocks.LEAVES);
		// Only under real trees, not next to a single leaf block
		int wanted = leaves < 12 || daylight < 0.2f ? 0 : Math.min(MAX_RAYS, Math.round(Theme.detail(6) * amount.f()));
		int running = 0;

		for (int i = 0; i < MAX_RAYS; i++) {
			if (!alive[i]) {
				continue;
			}

			age[i] += dt;

			if (age[i] >= life[i]) {
				alive[i] = false;
				continue;
			}

			running++;
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < MAX_RAYS && running < wanted; i++) {
			if (!alive[i] && spawn(i, level, leaves, random)) {
				running++;
			}
		}

		for (int i = 0; i < MAX_RAYS; i++) {
			if (alive[i]) {
				drawRay(geo, i, time, dt);
			}
		}
	}

	/** Starts a beam under a random leaf block that has open ground below it. */
	private boolean spawn(int i, ClientLevel level, int leaves, ThreadLocalRandom random) {
		int l = random.nextInt(leaves);
		int bx = NearbyBlocks.x(NearbyBlocks.LEAVES, l);
		int by = NearbyBlocks.y(NearbyBlocks.LEAVES, l);
		int bz = NearbyBlocks.z(NearbyBlocks.LEAVES, l);
		int floor = NearbyBlocks.surface(level, bx, bz, true);

		if (floor >= by || by - floor > 18) {
			return false;
		}

		topX[i] = bx + 0.5;
		topY[i] = by;
		topZ[i] = bz + 0.5;
		length[i] = by - floor;
		width[i] = 0.35f + random.nextFloat() * 0.45f;
		age[i] = 0f;
		life[i] = 9f + random.nextFloat() * 8f;
		phase[i] = random.nextFloat() * 6.28f;

		for (int m = 0; m < MOTES_PER_RAY; m++) {
			moteAlong[i * MOTES_PER_RAY + m] = random.nextFloat();
			moteSide[i * MOTES_PER_RAY + m] = (random.nextFloat() - 0.5f) * 0.8f;
		}

		alive[i] = true;
		return true;
	}

	private void drawRay(Geo geo, int i, float time, float dt) {
		float fadeIn = Math.min(1f, age[i] / 3f);
		float fadeOut = Math.min(1f, (life[i] - age[i]) / 3f);
		float shimmer = 0.8f + 0.2f * (float) Math.sin(time * 0.7f + phase[i]);
		float alpha = 0.16f * fadeIn * fadeOut * shimmer * daylight;

		if (alpha < 0.004f || geo.distanceSq(topX[i], topY[i], topZ[i]) > 40 * 40) {
			return;
		}

		float len = length[i];
		int light = 0xFFF1C4;

		// A soft wide glow around a brighter core
		for (int pass = 0; pass < 2; pass++) {
			float widthScale = pass == 0 ? 2.2f : 1f;
			float alphaScale = pass == 0 ? 0.35f : 1f;

			for (int k = 0; k < 4; k++) {
				float d = ALONG[k] * len;
				px[k] = geo.rx(topX[i] + LEAN_X * d);
				py[k] = geo.ry(topY[i] - d);
				pz[k] = geo.rz(topZ[i] + LEAN_Z * d);
				// Beams widen a little towards the ground
				hw[k] = width[i] * widthScale * (0.8f + 0.4f * ALONG[k]);
				colors[k] = Colors.argb(alpha * alphaScale * STRENGTH[k], light);
			}

			geo.ribbon(px, py, pz, hw, colors, 4);
		}

		if (!dust.on()) {
			return;
		}

		for (int m = 0; m < MOTES_PER_RAY; m++) {
			int index = i * MOTES_PER_RAY + m;
			// Specks sink very slowly and wrap around to the top
			moteAlong[index] += dt * 0.025f;

			if (moteAlong[index] > 0.95f) {
				moteAlong[index] = 0.1f;
			}

			float t = moteAlong[index];
			float d = t * len;
			float drift = (float) Math.sin(time * 0.6f + phase[i] + m * 1.3f) * 0.12f;
			double x = topX[i] + LEAN_X * d + moteSide[index] * width[i] + drift;
			double y = topY[i] - d;
			double z = topZ[i] + LEAN_Z * d + (float) Math.cos(time * 0.5f + m) * 0.1f;
			float twinkle = 0.5f + 0.5f * (float) Math.sin(time * 2.3f + m * 2.1f + phase[i]);
			geo.glowDot(x, y, z, 0.035f, 0xFFF6DA, alpha * 4f * twinkle * (float) Math.sin(t * Math.PI));
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		Gui2D.fill(g, x + 6, y + 2, x + w - 6, y + 14, 0xFF2F5A26);
		Gui2D.fill(g, x + 6, y + h - 8, x + w - 6, y + h - 2, 0xFF3D5A2A);

		for (int r = 0; r < 3; r++) {
			float shimmer = 0.6f + 0.4f * (float) Math.sin(time * 0.8f + r * 2f);
			int top = y + 14;
			int bottom = y + h - 8;
			int rx = x + 18 + r * (w - 36) / 2;

			for (int row = top; row < bottom; row += 2) {
				float t = (row - top) / (float) (bottom - top);
				int lean = Math.round(t * -6);
				int half = Math.round(3 + t * 3);
				float a = 0.28f * shimmer * (float) Math.sin(Math.max(0.05f, t) * Math.PI);
				Gui2D.fill(g, rx + lean - half, row, rx + lean + half, row + 2, Colors.argb(a, 0xFFF1C4));
			}

			if (dust.on()) {
				float mt = (time * 0.08f + r * 0.3f) % 1f;
				Gui2D.glowDot(g, rx + mt * -6 + (float) Math.sin(time + r) * 2, top + mt * (bottom - top), 1.2f, 0xFFF6DA, 0.7f);
			}
		}
	}
}
