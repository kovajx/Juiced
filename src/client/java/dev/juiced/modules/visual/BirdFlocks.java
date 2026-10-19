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
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sky;

/**
 * Now and then a small flock of birds crosses the sky in a loose V, flapping and gliding,
 * high above you. Only by day and when it doesn't rain.
 */
public final class BirdFlocks extends Module {
	private static final int FLOCKS = 2;
	private static final int MAX_BIRDS = 11;
	private static final float SPEED = 7f;
	private static final double HALF_PATH = 75;
	/** Ticks after tuning in before the menu sends a flock, so its camera has arrived. */
	private static final int PROBE_DELAY = 24;

	private final ChoiceSetting kind = add(new ChoiceSetting("kind", "Art", 0, "Silhouetten", "Möwen"));
	private final SliderSetting frequency = add(SliderSetting.percent("frequency", "Häufigkeit", 0.3, 3.0, 1.0));

	// Per flock: start point, heading, distance flown, length of the flight path, number of birds
	private final double[] startX = new double[FLOCKS];
	private final double[] startY = new double[FLOCKS];
	private final double[] startZ = new double[FLOCKS];
	private final float[] headingX = new float[FLOCKS];
	private final float[] headingZ = new float[FLOCKS];
	private final float[] flown = new float[FLOCKS];
	private final float[] length = new float[FLOCKS];
	private final int[] birds = new int[FLOCKS];
	private final boolean[] flying = new boolean[FLOCKS];
	// Per bird: small offsets that keep the V from looking too perfect, and its own wing rhythm
	private final float[][] jitter = new float[FLOCKS][MAX_BIRDS * 2];
	private final float[][] phase = new float[FLOCKS][MAX_BIRDS];

	private float nextFlock = 6f;
	/** The flock the menu sent last, or -1. */
	private int probeFlock = -1;

	public BirdFlocks() {
		super("birds", "Vogelschwärme", Category.VISUALS, true,
				"Ab und zu zieht ein kleiner Vogelschwarm in V-Formation hoch über dich hinweg, mal flatternd, mal gleitend.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(flying, false);
		nextFlock = 6f;
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
		double px = geo.x(player);
		double py = geo.y(player);
		double pz = geo.z(player);
		boolean weather = flyingWeather(level, player);

		nextFlock -= dt * frequency.f();

		if (weather && nextFlock <= 0f) {
			start(px, py, pz);
			nextFlock = 25f + ThreadLocalRandom.current().nextFloat() * 35f;
		}

		for (int f = 0; f < FLOCKS; f++) {
			if (!flying[f]) {
				continue;
			}

			flown[f] += SPEED * dt;

			if (flown[f] > length[f]) {
				flying[f] = false;
				continue;
			}

			// Fade in and out at the ends of the path
			float t = flown[f] / length[f];
			float fade = Math.min(1f, Math.min(t, 1f - t) * 6f);
			drawFlock(geo, f, time, fade);
		}
	}

	private static boolean flyingWeather(ClientLevel level, LocalPlayer player) {
		return Sky.overworld(level) && !level.isRaining() && Sky.day(level, player.blockPosition()) > 0.3f;
	}

	/**
	 * The menu doesn't wait for the next flock: soon after tuning in, and again whenever the sky is empty,
	 * one crosses right in front of its camera at the height the camera looks up to.
	 */
	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null || ticks < PROBE_DELAY || (probeFlock >= 0 && flying[probeFlock]) || !flyingWeather(level, player)) {
			return;
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();
		double yaw = Math.toRadians(FreeCamera.cameraYaw(player.getYRot()));
		float up = Math.max(15f, Math.min(60f, -FreeCamera.cameraPitch(player.getXRot())));
		double distance = 34 + random.nextDouble() * 12;
		int way = random.nextBoolean() ? 1 : -1;
		// Flying across the view (along the camera's left or right), with the path the given distance in front
		double heading = yaw + (way < 0 ? Math.PI : 0);
		double height = player.getEyeHeight() + distance * Math.tan(Math.toRadians(up));
		// Already a stretch along the path, so it enters the picture right away instead of in half a minute
		probeFlock = launch(player.getX(), player.getY(), player.getZ(), heading, way * distance, height, 22f);

		if (probeFlock >= 0) {
			nextFlock = 25f + random.nextFloat() * 35f;
		}
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		if (anyFlying()) {
			return null;
		}

		String sky = SkyCheck.reason(minecraft);

		if (sky != null || minecraft.player == null || minecraft.level == null) {
			return sky;
		}

		return Sky.day(minecraft.level, minecraft.player.blockPosition()) > 0.3f ? null : "Nur tagsüber";
	}

	private boolean anyFlying() {
		for (boolean f : flying) {
			if (f) {
				return true;
			}
		}

		return false;
	}

	/** Starts a flock on one side of you that will pass by at some distance. */
	private void start(double px, double py, double pz) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double heading = random.nextDouble() * Math.PI * 2;
		// Pass beside you: shift the path sideways by 15 to 45 blocks
		double side = (random.nextBoolean() ? 1 : -1) * (15 + random.nextDouble() * 30);
		launch(px, py, pz, heading, side, 24 + random.nextDouble() * 14, 0f);
	}

	/**
	 * A flock on a straight path past you: {@code side} blocks to the left of its heading at the closest point,
	 * {@code height} above your feet, starting {@code flownAlready} blocks into its path. Returns its slot, or
	 * -1 if all flocks are in the air.
	 */
	private int launch(double px, double py, double pz, double heading, double side, double height, float flownAlready) {
		int f = -1;

		for (int i = 0; i < FLOCKS; i++) {
			if (!flying[i]) {
				f = i;
				break;
			}
		}

		if (f < 0) {
			return -1;
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();
		float hx = (float) Math.cos(heading);
		float hz = (float) Math.sin(heading);
		startX[f] = px - hx * HALF_PATH - hz * side;
		startZ[f] = pz - hz * HALF_PATH + hx * side;
		startY[f] = py + height;
		headingX[f] = hx;
		headingZ[f] = hz;
		flown[f] = flownAlready;
		length[f] = (float) (HALF_PATH * 2);
		birds[f] = 5 + random.nextInt(MAX_BIRDS - 4);

		for (int b = 0; b < MAX_BIRDS; b++) {
			jitter[f][b * 2] = (random.nextFloat() - 0.5f) * 0.8f;
			jitter[f][b * 2 + 1] = (random.nextFloat() - 0.5f) * 0.6f;
			phase[f][b] = random.nextFloat() * 6.28f;
		}

		flying[f] = true;
		return f;
	}

	private void drawFlock(Geo geo, int f, float time, float fade) {
		float hx = headingX[f];
		float hz = headingZ[f];
		// Sideways direction of the V
		float sx = -hz;
		float sz = hx;
		double leadX = startX[f] + hx * flown[f];
		double leadY = startY[f] + Math.sin(flown[f] * 0.05) * 1.5;
		double leadZ = startZ[f] + hz * flown[f];
		boolean gulls = kind.index() == 1;
		int body = gulls ? 0xF2F2F5 : 0x24242C;
		int tip = gulls ? 0x6A6A75 : 0x24242C;
		float alpha = (gulls ? 0.95f : 0.85f) * fade;

		for (int b = 0; b < birds[f]; b++) {
			// Leader at the front, the others alternate left and right behind it
			int row = (b + 1) / 2;
			int sideSign = b % 2 == 0 ? 1 : -1;
			float back = row * 2.2f + jitter[f][b * 2];
			float across = b == 0 ? 0f : sideSign * (row * 1.9f + jitter[f][b * 2 + 1]);
			double bx = leadX - hx * back + sx * across;
			double by = leadY - row * 0.35 + Math.sin(time * 0.9f + phase[f][b]) * 0.25;
			double bz = leadZ - hz * back + sz * across;
			bird(geo, bx, by, bz, hx, hz, time, phase[f][b], body, tip, alpha);
		}
	}

	/** Two wings that flap, with now and then a short glide. */
	private static void bird(Geo geo, double x, double y, double z, float hx, float hz, float time, float phase, int body, int tip, float alpha) {
		float sx = -hz;
		float sz = hx;
		// Flap for a while, then glide with the wings held slightly up
		boolean gliding = Math.sin(time * 0.35f + phase) > 0.55;
		float flap = gliding ? 0.25f : (float) Math.sin(time * 9f + phase);
		float span = 0.75f;
		float lift = flap * 0.45f;
		int inner = Colors.argb(alpha, body);
		int outer = Colors.argb(alpha, tip);

		for (int side = -1; side <= 1; side += 2) {
			double tipX = x + sx * side * span - hx * 0.15;
			double tipY = y + lift;
			double tipZ = z + sz * side * span - hz * 0.15;
			// Wing: from the body (front and back) out to the tip, bent a little at the middle
			double elbowX = x + sx * side * span * 0.5;
			double elbowY = y + lift * 0.6 + 0.05;
			double elbowZ = z + sz * side * span * 0.5;
			geo.triWorld(x + hx * 0.12, y, z + hz * 0.12, inner, x - hx * 0.12, y, z - hz * 0.12, inner, elbowX, elbowY, elbowZ, inner);
			geo.triWorld(elbowX + hx * 0.08, elbowY, elbowZ + hz * 0.08, inner, elbowX - hx * 0.08, elbowY, elbowZ - hz * 0.08, inner, tipX, tipY, tipZ, outer);
		}

		// Small body
		geo.line(x + hx * 0.22, y, z + hz * 0.22, x - hx * 0.22, y, z - hz * 0.22, 0.05f, inner, inner);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		Gui2D.fill(g, x + 6, y + 2, x + w - 6, y + h - 2, 0xFF7FB2E5);
		Gui2D.fill(g, x + 6, y + h - 14, x + w - 6, y + h - 2, 0xFFF2C98A);
		boolean gulls = kind.index() == 1;
		int color = gulls ? 0xFFFFFFFF : 0xFF24242C;
		float t = (time * 0.12f) % 1.3f - 0.15f;
		float leadX = x + 6 + t * (w - 12);
		float leadY = y + h * 0.35f;

		for (int b = 0; b < 7; b++) {
			int row = (b + 1) / 2;
			int sideSign = b % 2 == 0 ? 1 : -1;
			float bx = leadX - row * 6;
			float by = leadY + (b == 0 ? 0 : sideSign * row * 4);

			if (bx < x + 8 || bx > x + w - 10) {
				continue;
			}

			float flap = (float) Math.sin(time * 9 + b) * 1.5f;
			Gui2D.fill(g, Math.round(bx - 3), Math.round(by - flap), Math.round(bx), Math.round(by - flap + 1), color);
			Gui2D.fill(g, Math.round(bx), Math.round(by), Math.round(bx + 1), Math.round(by + 1), color);
			Gui2D.fill(g, Math.round(bx + 1), Math.round(by - flap), Math.round(bx + 4), Math.round(by - flap + 1), color);
		}
	}
}
