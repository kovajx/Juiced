package dev.juiced.modules.tweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/**
 * Shows where a pearl, arrow, trident, wind charge or potion will land before you throw it.
 * Uses the same physics numbers as the game.
 */
public final class Trajectories extends Module {
	private static final int MAX_STEPS = 120;

	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final BoolSetting pearls = add(new BoolSetting("pearls", "Enderperlen", true));
	private final BoolSetting bows = add(new BoolSetting("bows", "Bogen & Armbrust", true));
	private final BoolSetting others = add(new BoolSetting("others", "Dreizack, Windkugel, Tränke", true));

	private final float[] px = new float[MAX_STEPS + 1];
	private final float[] py = new float[MAX_STEPS + 1];
	private final float[] pz = new float[MAX_STEPS + 1];
	private final float[] hw = new float[MAX_STEPS + 1];
	private final int[] colors = new int[MAX_STEPS + 1];

	public Trajectories() {
		super("trajectories", "Flugbahn", Category.TWEAKS, true,
				"Zeigt beim Zielen, wo deine Perle, dein Pfeil oder deine Windkugel landet. Perfekt für Pearl-Clutches.");
	}

	/** Physics for one kind of projectile. {@code arrowOrder}: move first, then drag and gravity. */
	private record Motion(float power, float pitchOffset, double gravity, double drag, boolean arrowOrder) {
	}

	private Motion motionFor(LocalPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		if (pearls.on() && stack.is(Items.ENDER_PEARL)) {
			return new Motion(1.5f, 0, 0.03, 0.99, false);
		}

		if (bows.on()) {
			if (stack.is(Items.BOW)) {
				if (!player.isUsingItem()) {
					return null;
				}

				float charge = BowItem.getPowerForTime(player.getTicksUsingItem());
				return charge <= 0.1f ? null : new Motion(charge * 3f, 0, 0.05, 0.99, true);
			}

			if (stack.is(Items.CROSSBOW)) {
				return CrossbowItem.isCharged(stack) ? new Motion(3.15f, 0, 0.05, 0.99, true) : null;
			}
		}

		if (others.on()) {
			if (stack.is(Items.TRIDENT)) {
				return player.isUsingItem() ? new Motion(2.5f, 0, 0.05, 0.99, true) : null;
			}

			if (stack.is(Items.WIND_CHARGE)) {
				return new Motion(1.5f, 0, 0, 1, false);
			}

			if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
				return new Motion(1.5f, 0, 0.03, 0.99, false);
			}

			if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
				return new Motion(0.5f, -20f, 0.05, 0.99, false);
			}

			if (stack.is(Items.EXPERIENCE_BOTTLE)) {
				return new Motion(0.7f, -20f, 0.07, 0.99, false);
			}
		}

		return null;
	}

	@Override
	public void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return;
		}

		Motion motion = motionFor(player, player.getMainHandItem());

		if (motion == null) {
			motion = motionFor(player, player.getOffhandItem());
		}

		if (motion == null) {
			return;
		}

		float partial = geo.partialTick();
		float yaw = player.getYRot(partial);
		float pitch = player.getXRot(partial);
		double x = geo.x(player);
		double y = geo.y(player) + player.getEyeHeight() - 0.1;
		double z = geo.z(player);
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
		double vy = -Math.sin(Math.toRadians(pitch + motion.pitchOffset));
		double vz = Math.cos(yawRad) * Math.cos(pitchRad);
		double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
		vx = vx / length * motion.power;
		vy = vy / length * motion.power;
		vz = vz / length * motion.power;

		// The projectile keeps the thrower's movement
		Vec3 movement = player.getDeltaMovement();
		vx += movement.x;
		vz += movement.z;

		if (!player.onGround()) {
			vy += movement.y;
		}

		int n = 0;
		px[n] = geo.rx(x);
		py[n] = geo.ry(y);
		pz[n] = geo.rz(z);
		n++;
		BlockHitResult hit = null;

		for (int step = 0; step < MAX_STEPS; step++) {
			double nx;
			double ny;
			double nz;

			if (motion.arrowOrder) {
				nx = x + vx;
				ny = y + vy;
				nz = z + vz;
				vx *= motion.drag;
				vy = vy * motion.drag - motion.gravity;
				vz *= motion.drag;
			} else {
				vy -= motion.gravity;
				vx *= motion.drag;
				vy *= motion.drag;
				vz *= motion.drag;
				nx = x + vx;
				ny = y + vy;
				nz = z + vz;
			}

			BlockHitResult result = level.clip(new ClipContext(new Vec3(x, y, z), new Vec3(nx, ny, nz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

			if (result != null && result.getType() != HitResult.Type.MISS) {
				hit = result;
				Vec3 at = result.getLocation();
				x = at.x;
				y = at.y;
				z = at.z;
			} else {
				x = nx;
				y = ny;
				z = nz;
			}

			px[n] = geo.rx(x);
			py[n] = geo.ry(y);
			pz[n] = geo.rz(z);
			n++;

			if (hit != null || y < level.getMinY()) {
				break;
			}
		}

		float spin = Clock.tf() * 0.2f;

		for (int i = 0; i < n; i++) {
			float f = i / (float) Math.max(1, n - 1);
			// Fade in near the hand so the line doesn't cover your crosshair
			float nearFade = Colors.clamp01(i / 4f);
			hw[i] = 0.025f;
			colors[i] = Colors.argb(0.9f * nearFade, color.rgb(f * 0.5f + spin));
		}

		geo.ribbon(px, py, pz, hw, colors, n);

		if (hit != null) {
			Vec3 at = hit.getLocation();
			int rgb = color.rgb(0.5f + spin);
			float pulse = 0.85f + 0.15f * (float) Math.sin(Clock.tf() * 6);

			if (hit.getDirection() == Direction.UP) {
				geo.groundRing(at.x, at.y + 0.02, at.z, 0.35f * pulse, 0.04f, 0.12f, Theme.detail(32), 0f, (float) (Math.PI * 2), t -> color.rgb(t + spin), 0.95f);
				geo.disc(at.x, at.y + 0.015, at.z, 0.3f * pulse, Theme.detail(24), t -> color.rgb(t + spin), 0.25f, 0f);
			} else {
				geo.billboardRing(at.x, at.y, at.z, 0.3f * pulse, 0.04f, 0.12f, Theme.detail(32), t -> color.rgb(t + spin), 0.95f);
			}

			geo.glowDot(at.x, at.y + 0.02, at.z, 0.12f, rgb, 1f);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float startX = x + 12;
		float startY = y + h - 16;
		int rgb = color.rgb();
		int points = 30;
		float landX = 0;
		float landY = 0;

		for (int i = 0; i < points; i++) {
			float t = i / (float) (points - 1);
			float px = startX + t * (w - 30);
			float py = startY - (float) Math.sin(t * Math.PI * 0.9) * (h * 0.55f);

			if ((i + (int) (time * 12)) % 3 != 0) {
				Gui2D.dot(g, px, py, 2, Colors.argb(0.95f, rgb));
			}

			landX = px;
			landY = py;
		}

		float pulse = 0.8f + 0.2f * (float) Math.sin(time * 6);
		Gui2D.ellipse(g, landX, landY + 2, 8 * pulse, 3 * pulse, 20, 1.5f, rgb, 1f, 0, true);
		Gui2D.glowDot(g, startX, startY, 3, 0xA77BFF, 1f);
	}
}
