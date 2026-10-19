package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

/**
 * When it rains, drops make little rings on water around you, and puddles slowly
 * form on the ground (and dry up again after the rain).
 */
public final class RainRipples extends Module {
	private static final int MAX_RIPPLES = 160;
	private static final int MAX_PUDDLES = 14;
	private static final float RIPPLE_LIFE = 0.75f;
	private static final double RADIUS = 9;

	private final SliderSetting amount = add(SliderSetting.percent("amount", "Menge", 0.3, 2.0, 1.0));
	private final BoolSetting puddles = add(new BoolSetting("puddles", "Pfützen", true));

	private final double[] rx = new double[MAX_RIPPLES];
	private final double[] ry = new double[MAX_RIPPLES];
	private final double[] rz = new double[MAX_RIPPLES];
	private final double[] rBorn = new double[MAX_RIPPLES];
	private final float[] rSize = new float[MAX_RIPPLES];
	private int rHead;

	private final double[] px = new double[MAX_PUDDLES];
	private final double[] py = new double[MAX_PUDDLES];
	private final double[] pz = new double[MAX_PUDDLES];
	private final float[] pSize = new float[MAX_PUDDLES];
	private final float[] pTarget = new float[MAX_PUDDLES];
	private final float[] pGrow = new float[MAX_PUDDLES];
	private final boolean[] pAlive = new boolean[MAX_PUDDLES];
	private float spawnBudget;
	private double nextPuddle;
	private boolean raining;

	public RainRipples() {
		super("rain_ripples", "Regen & Pfützen", Category.VISUALS, true,
				"Bei Regen entstehen kleine Wellenringe auf dem Wasser um dich, und auf dem Boden bilden sich langsam Pfützen, die danach wieder trocknen.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(rBorn, 0);
		java.util.Arrays.fill(pAlive, false);
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return raining ? null : "Nur bei Regen";
	}

	@Override
	public void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;

		if (level == null || player == null) {
			return;
		}

		raining = level.isRaining() && level.getBiome(player.blockPosition()).value().hasPrecipitation();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double now = Clock.t();

		// Puddles grow while it rains and dry up afterwards
		for (int i = 0; i < MAX_PUDDLES; i++) {
			if (!pAlive[i]) {
				continue;
			}

			double dx = px[i] - player.getX();
			double dz = pz[i] - player.getZ();

			if (dx * dx + dz * dz > 30 * 30) {
				pAlive[i] = false;
				continue;
			}

			pSize[i] = raining ? Math.min(pTarget[i], pSize[i] + pGrow[i]) : pSize[i] - 0.004f;

			if (pSize[i] <= 0f) {
				pAlive[i] = false;
			}
		}

		if (raining && puddles.on() && now > nextPuddle) {
			nextPuddle = now + 1.2 + random.nextDouble() * 1.5;
			int x = (int) Math.floor(player.getX() + (random.nextDouble() - 0.5) * 2 * RADIUS);
			int z = (int) Math.floor(player.getZ() + (random.nextDouble() - 0.5) * 2 * RADIUS);
			int top = NearbyBlocks.surface(level, x, z, false);
			BlockState ground = level.getBlockState(new BlockPos(x, top - 1, z));

			if (Math.abs(top - player.getY()) < 6 && isPuddleGround(ground)) {
				for (int i = 0; i < MAX_PUDDLES; i++) {
					if (!pAlive[i]) {
						px[i] = x + 0.2 + random.nextDouble() * 0.6;
						py[i] = top + 0.008;
						pz[i] = z + 0.2 + random.nextDouble() * 0.6;
						pSize[i] = 0f;
						pTarget[i] = 0.5f + random.nextFloat() * 0.8f;
						pGrow[i] = 0.0015f + random.nextFloat() * 0.002f;
						pAlive[i] = true;
						break;
					}
				}
			}
		}
	}

	/** Puddles only on solid, plain ground: not on water, snow, leaves or plants. */
	private static boolean isPuddleGround(BlockState state) {
		return !state.isAir() && !state.is(Blocks.WATER) && !state.is(BlockTags.LEAVES) && !state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)
				&& !state.is(Blocks.POWDER_SNOW) && !state.is(BlockTags.SAND);
	}

	@Override
	public void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;

		if (level == null || player == null) {
			return;
		}

		double now = Clock.t();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		if (raining) {
			spawnBudget += Clock.dt() * 26f * amount.f() * (Theme.quality() + 1) / 3f;

			while (spawnBudget >= 1f) {
				spawnBudget -= 1f;
				spawnDrop(level, player, random, now);
			}
		}

		// Puddles: a soft dark pool with a faint sky reflection
		int pool = 0x2F4D68;
		int sky = 0xA9C4DA;

		for (int i = 0; i < MAX_PUDDLES; i++) {
			if (!pAlive[i] || pSize[i] <= 0.01f) {
				continue;
			}

			float r = pSize[i];
			float px0 = geo.rx(px[i]);
			float py0 = geo.ry(py[i]);
			float pz0 = geo.rz(pz[i]);
			geo.band(px0, py0, pz0, 1, 0, 0, 0, 0, 1, 0f, r * 0.75f, 20, 0f, (float) (Math.PI * 2), t -> pool, 0.38f, 0.38f, 0f);
			geo.band(px0, py0, pz0, 1, 0, 0, 0, 0, 1, r * 0.75f, r, 20, 0f, (float) (Math.PI * 2), t -> pool, 0.38f, 0f, 0f);
			geo.band(px0, py0 + 0.001f, pz0, 1, 0, 0, 0, 0, 1, r * 0.35f, r * 0.55f, 12, 3.6f, 1.4f, t -> sky, 0.12f, 0f, 0f);

			// Drops falling into the puddle
			if (raining && random.nextFloat() < r * 0.25f) {
				double a = random.nextDouble() * Math.PI * 2;
				double d = Math.sqrt(random.nextDouble()) * r * 0.7;
				ripple(px[i] + Math.cos(a) * d, py[i] + 0.002, pz[i] + Math.sin(a) * d, now, 0.8f);
			}
		}

		int light = 0xDCEBFA;
		int segments = Theme.detail(14);

		for (int i = 0; i < MAX_RIPPLES; i++) {
			if (rBorn[i] == 0) {
				continue;
			}

			float t = (float) ((now - rBorn[i]) / RIPPLE_LIFE);

			if (t >= 1f || t < 0f) {
				continue;
			}

			float eased = 1f - (1f - t) * (1f - t);
			geo.groundRing(rx[i], ry[i], rz[i], (0.02f + 0.2f * eased) * rSize[i], 0.012f, 0f, segments, 0f, (float) (Math.PI * 2), c -> light, 0.55f * (1f - t));
		}
	}

	/** One rain drop: a ring where it hits water. */
	private void spawnDrop(ClientLevel level, LocalPlayer player, ThreadLocalRandom random, double now) {
		int x = (int) Math.floor(player.getX() + (random.nextDouble() - 0.5) * 2 * RADIUS);
		int z = (int) Math.floor(player.getZ() + (random.nextDouble() - 0.5) * 2 * RADIUS);
		int top = NearbyBlocks.surface(level, x, z, false);

		if (Math.abs(top - player.getY()) > 8) {
			return;
		}

		BlockState below = level.getBlockState(new BlockPos(x, top - 1, z));

		if (below.is(Blocks.WATER)) {
			ripple(x + random.nextDouble(), top - 1 + 0.9, z + random.nextDouble(), now, 0.8f + random.nextFloat() * 0.5f);
		}
	}

	private void ripple(double x, double y, double z, double now, float size) {
		rx[rHead] = x;
		ry[rHead] = y;
		rz[rHead] = z;
		rBorn[rHead] = now;
		rSize[rHead] = size;
		rHead = (rHead + 1) % MAX_RIPPLES;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		Gui2D.rounded2(g, x + 8, y + h / 2 - 6, x + w - 8, y + h / 2 + 14, Colors.argb(0.6f, 0x2F4D68));
		java.util.Random random = new java.util.Random(5);

		for (int i = 0; i < 6; i++) {
			float phase = (time * 1.3f + random.nextFloat()) % 1f;
			float cx = x + 14 + random.nextFloat() * (w - 28);
			float cy = y + h / 2f + 2 + random.nextFloat() * 8;
			Gui2D.ellipse(g, cx, cy, 1 + phase * 7, 0.5f + phase * 2.5f, 14, 1f, 0xDCEBFA, 1f - phase, 0, false);
		}

		for (int i = 0; i < 8; i++) {
			float fall = (time * 2.2f + i * 0.13f) % 1f;
			float cx = x + 10 + (i * 37 % (w - 20));
			Gui2D.fill(g, Math.round(cx), Math.round(y + 4 + fall * (h / 2f - 8)), Math.round(cx) + 1, Math.round(y + 8 + fall * (h / 2f - 8)), 0x99A9C4DA);
		}
	}
}
