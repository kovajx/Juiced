package dev.juiced.modules.cosmetic;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Pose3;

/** A cape that swings with your movement. Patterns are animated and drawn by the mod itself. */
public final class Cape extends CosmeticModule {
	public static Cape INSTANCE;

	private static final int COLS = 4;
	private static final int ROWS = 12;
	private static final float WIDTH = 0.6f;
	private static final float[][] STARS = new float[16][];

	static {
		java.util.Random random = new java.util.Random(42);

		for (int i = 0; i < STARS.length; i++) {
			STARS[i] = new float[] {0.08f + random.nextFloat() * 0.84f, 0.06f + random.nextFloat() * 0.88f, random.nextFloat() * 6.28f};
		}
	}

	private final ChoiceSetting pattern = add(new ChoiceSetting("pattern", "Muster", 0, "Verlauf", "Einfarbig", "Sternenhimmel", "Flammen", "Regenbogen", "Streifen", "Sakura", "Nordlicht"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final ColorSetting color2 = add(new ColorSetting("color2", "Zweitfarbe", Colors.BLACK, true));
	private final SliderSetting length = add(SliderSetting.percent("length", "Länge", 0.7, 1.3, 1.0));
	private final SliderSetting flutter = add(SliderSetting.percent("flutter", "Flattern", 0.0, 2.0, 1.0));
	private final BoolSetting border = add(new BoolSetting("border", "Rand", true));
	private final BoolSetting hideVanilla = add(new BoolSetting("hide_vanilla", "Echtes Cape ausblenden", true));

	private float angle = 6f;
	private float roll;
	private float targetAngle = 6f;
	private float targetRoll;
	private float speed;
	private double lastX;
	private double lastY;
	private double lastZ;
	private boolean hasLast;

	private final double[] gx = new double[(COLS + 1) * (ROWS + 1)];
	private final double[] gy = new double[(COLS + 1) * (ROWS + 1)];
	private final double[] gz = new double[(COLS + 1) * (ROWS + 1)];

	public Cape() {
		super("cape", "Cape", "Ein Cape, das beim Laufen weht und beim Fallen hochflattert. Muster: Verlauf, Sternenhimmel, Flammen, Sakura, Nordlicht und mehr.");
		INSTANCE = this;
	}

	/** True while the player's real cape should be hidden so it doesn't overlap ours. */
	public static boolean hidesVanilla() {
		return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.hideVanilla.on();
	}

	/** Hangs at rest when it shows again; without this, the distance walked meanwhile reads as one huge swing. */
	@Override
	protected void onDisable() {
		hasLast = false;
		angle = 6f;
		targetAngle = 6f;
		roll = 0f;
		targetRoll = 0f;
		speed = 0f;
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;

		if (self != null && self.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
			return "Nicht mit Elytra";
		}

		return super.probeLiveReason(minecraft);
	}

	@Override
	protected void onTick(Minecraft minecraft, LocalPlayer self) {
		if (hasLast) {
			double vx = self.getX() - lastX;
			double vy = self.getY() - lastY;
			double vz = self.getZ() - lastZ;
			double yaw = Math.toRadians(self.yBodyRot);
			double forward = vx * -Math.sin(yaw) + vz * Math.cos(yaw);
			double side = vx * -Math.cos(yaw) + vz * -Math.sin(yaw);
			speed = (float) Math.sqrt(vx * vx + vz * vz);
			float lift = (float) Math.max(0, Math.min(70, forward * 180));
			float fall = (float) Math.max(0, Math.min(35, -vy * 90));
			targetAngle = 6f + lift + fall + (self.isShiftKeyDown() ? 22f : 0f);
			targetRoll = (float) Math.max(-14, Math.min(14, side * -70));
		}

		lastX = self.getX();
		lastY = self.getY();
		lastZ = self.getZ();
		hasLast = true;
	}

	@Override
	protected void draw(Geo geo) {
		if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
			return;
		}

		angle = Clock.approach(angle, targetAngle, 6f);
		roll = Clock.approach(roll, targetRoll, 5f);
		float time = Clock.tf();
		float len = 0.95f * length.f();
		float dl = len / ROWS;
		float flutterAmount = flutter.f() * (2.5f + Math.min(1f, speed * 4f) * 6f);
		Pose3 back = bodyPose().move(0, eyeHeight - 0.24, crouching ? -0.28 : -0.15);
		float rollRad = (float) Math.toRadians(roll);

		for (int c = 0; c <= COLS; c++) {
			float u = c / (float) COLS;
			float x = (u - 0.5f) * WIDTH;
			float down = 0f;
			float out = 0f;

			for (int r = 0; r <= ROWS; r++) {
				float t = r / (float) ROWS;

				if (r > 0) {
					float wave = (float) Math.sin(time * (5f + speed * 20f) - t * 5f + u * 1.5f) * flutterAmount * t;
					double theta = Math.toRadians(angle * (0.35f + 0.65f * t) + wave);
					down += (float) Math.cos(theta) * dl;
					out += (float) Math.sin(theta) * dl;
				}

				// Edges curl slightly towards the body at the top; sideways motion swings the cape
				float curl = Math.abs(x) / (WIDTH / 2f) * 0.035f * (1f - t);
				float swing = (float) Math.sin(rollRad) * t * x * 0.9f;
				int index = r * (COLS + 1) + c;
				gx[index] = back.wx(x, -down, -out + curl + swing);
				gy[index] = back.wy(x, -down, -out + curl + swing);
				gz[index] = back.wz(x, -down, -out + curl + swing);
			}
		}

		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int a = r * (COLS + 1) + c;
				int b = a + 1;
				int d = a + COLS + 1;
				int e = d + 1;
				geo.quadWorld(gx[a], gy[a], gz[a], shade(c, r, time),
						gx[b], gy[b], gz[b], shade(c + 1, r, time),
						gx[e], gy[e], gz[e], shade(c + 1, r + 1, time),
						gx[d], gy[d], gz[d], shade(c, r + 1, time));
			}
		}

		if (pattern.index() == 2) {
			drawStars(geo, time);
		} else if (pattern.index() == 6) {
			drawPetals(geo, time);
		}

		if (border.on()) {
			int edge = Colors.argb(0.95f, Colors.shade(color2.rgb(), 0.15f));

			for (int r = 0; r < ROWS; r++) {
				int left = r * (COLS + 1);
				int right = left + COLS;
				geo.line(gx[left], gy[left], gz[left], gx[left + COLS + 1], gy[left + COLS + 1], gz[left + COLS + 1], 0.012f, edge, edge);
				geo.line(gx[right], gy[right], gz[right], gx[right + COLS + 1], gy[right + COLS + 1], gz[right + COLS + 1], 0.012f, edge, edge);
			}

			int bottom = ROWS * (COLS + 1);

			for (int c = 0; c < COLS; c++) {
				geo.line(gx[bottom + c], gy[bottom + c], gz[bottom + c], gx[bottom + c + 1], gy[bottom + c + 1], gz[bottom + c + 1], 0.012f, edge, edge);
			}
		}
	}

	private int shade(int c, int r, float time) {
		float u = c / (float) COLS;
		float v = r / (float) ROWS;
		int rgb = switch (pattern.index()) {
			case 1 -> Colors.shade(color.rgb(), -0.25f * v);
			case 2 -> Colors.mixRgb(0x0B0B26, 0x2A1A5A, v * 0.8f + 0.1f * (float) Math.sin(u * 3 + time * 0.5f));
			case 3 -> fire(u, v, time);
			case 4 -> Theme.rainbow(v * 0.7f + u * 0.1f);
			case 5 -> (((int) Math.floor(v * 6 + time * 0.6f)) % 2 == 0) ? color.rgb() : color2.rgb();
			case 6 -> Colors.mixRgb(0xF4A3BF, 0xFFF1F6, v * 0.85f + 0.08f * (float) Math.sin(u * 4 + time * 0.4f));
			case 7 -> aurora(u, v, time);
			default -> Colors.mixRgb(color.rgb(v * 0.5f), color2.rgb(), v);
		};
		// Slightly darker towards the edges for depth
		float edge = Math.abs(u - 0.5f) * 2f;
		return Colors.argb(1f, Colors.shade(rgb, -0.18f * edge * edge));
	}

	private static int fire(float u, float v, float time) {
		float heat = v * 1.35f - 0.25f + 0.22f * (float) Math.sin(u * 10f + time * 5f + v * 7f) + 0.12f * (float) Math.sin(u * 23f - time * 7f);
		heat = Colors.clamp01(heat);

		if (heat < 0.33f) {
			return Colors.mixRgb(0x1A0A05, 0xB3200A, heat / 0.33f);
		}

		if (heat < 0.66f) {
			return Colors.mixRgb(0xB3200A, 0xFF7A1A, (heat - 0.33f) / 0.33f);
		}

		return Colors.mixRgb(0xFF7A1A, 0xFFE070, (heat - 0.66f) / 0.34f);
	}

	/** Dark night sky with slowly moving green and violet light bands. */
	private static int aurora(float u, float v, float time) {
		int sky = Colors.mixRgb(0x0A1A33, 0x050A18, v);
		float center = 0.38f + 0.12f * (float) Math.sin(u * 4f + time * 0.45f);
		float band = (float) Math.exp(-(v - center) * (v - center) * 28f);
		float second = (float) Math.exp(-(v - center - 0.28f) * (v - center - 0.28f) * 40f) * 0.6f;
		int light = Colors.mixRgb(0x3DFFB0, 0xA77BFF, 0.5f + 0.5f * (float) Math.sin(u * 2.5f + time * 0.3f + v * 3f));
		return Colors.mixRgb(sky, light, Math.min(1f, band * 0.9f + second));
	}

	/** Pink petals drifting slowly down the cape. */
	private void drawPetals(Geo geo, float time) {
		for (float[] star : STARS) {
			float fu = star[0] + 0.04f * (float) Math.sin(time * 0.8f + star[2]);
			float fv = (star[1] + time * 0.06f + star[2] * 0.1f) % 1f;
			float cu = Math.max(0f, Math.min(0.999f, fu)) * COLS;
			float cv = fv * ROWS;
			int c = Math.min(COLS - 1, (int) cu);
			int r = Math.min(ROWS - 1, (int) cv);
			float lu = cu - c;
			float lv = cv - r;
			int a = r * (COLS + 1) + c;
			int b = a + 1;
			int d = a + COLS + 1;
			int e = d + 1;
			double x = lerp2(gx[a], gx[b], gx[d], gx[e], lu, lv);
			double y = lerp2(gy[a], gy[b], gy[d], gy[e], lu, lv);
			double z = lerp2(gz[a], gz[b], gz[d], gz[e], lu, lv);
			float fade = Math.min(1f, Math.min(fv, 1f - fv) * 6f);
			geo.glowDot(x, y, z, 0.022f, 0xFF8FB8, 0.85f * fade);
		}
	}

	private void drawStars(Geo geo, float time) {
		for (float[] star : STARS) {
			float cu = star[0] * COLS;
			float cv = star[1] * ROWS;
			int c = Math.min(COLS - 1, (int) cu);
			int r = Math.min(ROWS - 1, (int) cv);
			float fu = cu - c;
			float fv = cv - r;
			int a = r * (COLS + 1) + c;
			int b = a + 1;
			int d = a + COLS + 1;
			int e = d + 1;
			double x = lerp2(gx[a], gx[b], gx[d], gx[e], fu, fv);
			double y = lerp2(gy[a], gy[b], gy[d], gy[e], fu, fv);
			double z = lerp2(gz[a], gz[b], gz[d], gz[e], fu, fv);
			float twinkle = 0.35f + 0.65f * Math.max(0f, (float) Math.sin(time * 2.5f + star[2]));
			geo.glowDot(x, y, z, 0.03f, 0xFFFFFF, twinkle);
		}
	}

	private static double lerp2(double a, double b, double d, double e, float fu, float fv) {
		double top = a + (b - a) * fu;
		double bottom = d + (e - d) * fu;
		return top + (bottom - top) * fv;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float top = feetY - 28;
		int rows = 10;

		for (int r = 0; r < rows; r++) {
			float t = r / (float) rows;
			float sway = (float) Math.sin(time * 4 - t * 4) * 3 * t;
			float half = 5 + t * 2;
			int rgb = switch (pattern.index()) {
				case 3 -> fire(0.5f, t, time);
				case 4 -> Theme.rainbow(t * 0.7f);
				case 2 -> Colors.mixRgb(0x0B0B26, 0x2A1A5A, t);
				case 6 -> Colors.mixRgb(0xF4A3BF, 0xFFF1F6, t);
				case 7 -> aurora(0.5f, t, time);
				default -> Colors.mixRgb(color.rgb(), color2.rgb(), t);
			};
			Gui2D.fill(g, Math.round(cx - half + sway + t * 6), Math.round(top + r * 2.4f), Math.round(cx + half + sway + t * 6), Math.round(top + r * 2.4f + 3), Colors.argb(1f, rgb));
		}
	}
}
