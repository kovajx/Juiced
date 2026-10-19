package dev.juiced.modules.cosmetic;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/**
 * Wings with a real skeleton: per side a bone chain from the shoulder blade over elbow and wrist
 * to the hand tip. Feathers, membranes and paper folds hang off the bones, so folding, breathing
 * and flapping move them the way a real wing moves.
 */
public final class Wings extends CosmeticModule {
	/** One model pixel in blocks (players render at 15/16 scale). */
	private static final float PX = 0.9375f / 16f;

	// Bone angles for {upper arm, forearm, hand} per pose: folded rest, open rest, sneaking, flying.
	// Sweep: 0 = straight out to the side, 90 = straight back. Lift: up from horizontal. Degrees.
	// The upper arm stays flat enough that the elbow passes below the head, which is deeper than
	// the torso. Sneaking leans the torso forward, which already tips backward bones upwards.
	private static final float[][] SWEEP = {{42, 54, 66}, {20, 22, 28}, {55, 76, 82}, {12, 14, 20}};
	private static final float[][] LIFT = {{32, 44, -52}, {28, 34, -12}, {5, 30, -66}, {22, 14, -4}};
	// How far the feather plane is turned backwards around each bone
	private static final float[][] TWIST = {{28, 32, 32}, {22, 26, 26}, {36, 42, 42}, {30, 34, 34}};
	private static final float[] BONE = {0.22f, 0.32f, 0.28f};
	private static final float[] DRAGON_BONE = {0.2f, 0.32f, 0.2f};
	// Flapping: outer bones follow a little later, which makes the stroke whip outwards
	private static final float[] FLAP_LIFT = {20, 26, 28};
	private static final float[] FLAP_SWEEP = {-8, -10, -12};
	private static final float[] FLAP_LAG = {0f, 0.9f, 1.8f};

	// Dragon fingers: how far each one turns from the trailing direction to the hand bone, per pose
	private static final float[][] FINGER = {{0.75f, 0.55f, 0.38f, 0.22f}, {0.95f, 0.68f, 0.42f, 0.18f}, {0.7f, 0.52f, 0.36f, 0.22f}, {0.97f, 0.7f, 0.44f, 0.18f}};
	private static final float[] FINGER_LENGTH = {0.66f, 0.74f, 0.64f, 0.5f};

	// Stations along a feather (0 = root, 1 = tip) with the half width there
	private static final float[] FLIGHT_T = {0f, 0.4f, 0.8f, 0.94f, 1f};
	private static final float[] FLIGHT_W = {0.3f, 0.95f, 0.88f, 0.6f, 0.14f};
	private static final float[] COVERT_T = {0f, 0.45f, 0.82f, 1f};
	private static final float[] COVERT_W = {0.6f, 1f, 0.85f, 0.3f};
	private static final float[] OUTLINE_T = {0f, 0.4f, 0.82f, 1f, 0.82f, 0.4f, 0f};
	private static final float[] OUTLINE_W = {-0.25f, -0.95f, -0.75f, 0f, 0.75f, 0.95f, 0.25f};

	// Butterfly outlines in the wing plane (x out, y up), as closed splines through these keys
	private static final float[] FORE_KEYS = {
			0.02f, 0.03f, 0.08f, 0.2f, 0.17f, 0.42f, 0.3f, 0.6f, 0.5f, 0.7f, 0.65f, 0.72f,
			0.7f, 0.64f, 0.67f, 0.46f, 0.57f, 0.28f, 0.4f, 0.14f, 0.2f, 0.04f};
	private static final float[] HIND_KEYS = {
			0.02f, -0.02f, 0.22f, -0.02f, 0.4f, -0.1f, 0.5f, -0.24f, 0.48f, -0.39f,
			0.39f, -0.5f, 0.3f, -0.58f, 0.25f, -0.71f, 0.19f, -0.58f, 0.11f, -0.5f, 0.04f, -0.35f, 0.02f, -0.18f};
	private static final float[] FORE = spline(FORE_KEYS, 2);
	private static final float[] HIND = spline(HIND_KEYS, 2);

	private static final int WHITE = 0xFBFAF5;
	private static final int PAPER_BACK = 0xF4F0E6;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Engel", "Drache", "Energie", "Schmetterling", "Papier"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final SliderSetting speed = add(SliderSetting.percent("speed", "Flügelschlag", 0.2, 2.0, 1.0));
	private final SliderSetting spread = add(SliderSetting.percent("spread", "Öffnung im Stand", 0.0, 1.0, 0.4));

	// Smoothed state: 0..1 in the air, 0..1 sneaking
	private float air;
	private float tuck;
	private boolean settled;

	// Where the geometry goes: the world (geo), or the menu card (preview, through a pixel buffer)
	private Geo geo;
	private GuiGraphicsExtractor gui;
	private float guiX;
	private float guiY;
	private float guiScale;
	private boolean preview;
	// Menu card pixel buffer, grown when a bigger card asks for it
	private int[] pixels = new int[0];
	private int bufX;
	private int bufY;
	private int bufW;
	private int bufH;

	// Pose blend weights for folded, open, sneaking and flying
	private float wFold;
	private float wOpen;
	private float wTuck;
	private float wFly;

	// Body frame at the neck pivot (camera-relative), leaning forward like the vanilla crouch pose
	private final float[] origin = new float[3];
	private final float[] right = new float[3];
	private final float[] up = new float[3];
	private final float[] fwd = new float[3];
	private float lean;

	// Bone chain of the side being drawn
	private int side;
	private float scale;
	private final float[] out = new float[3];
	private final float[] back = new float[3];
	private final float[][] joint = new float[4][3];
	private final float[][] boneD = new float[3][3];
	private final float[][] boneT = new float[3][3];
	private final float[][] boneN = new float[3][3];
	private final float[][] jointD = new float[4][3];

	// Result of sample(): point on the chain, bone direction, trailing direction, top-side normal
	private final float[] sp = new float[3];
	private final float[] sd = new float[3];
	private final float[] st = new float[3];
	private final float[] sn = new float[3];

	// Result of featherFrame(): base point, feather direction, width direction
	private final float[] fb = new float[3];
	private final float[] fc = new float[3];
	private final float[] fw = new float[3];

	// Reused buffers
	private final float[] ax = new float[8];
	private final float[] ay = new float[8];
	private final float[] az = new float[8];
	private final float[] bx = new float[8];
	private final float[] by = new float[8];
	private final float[] bz = new float[8];
	private final int[] ac = new int[8];
	private final int[] bc = new int[8];
	private final float[] gx = new float[64];
	private final float[] gy = new float[64];
	private final float[] gz = new float[64];
	private final float[][] finger = new float[12][3];
	private final float[] rbX = new float[16];
	private final float[] rbY = new float[16];
	private final float[] rbZ = new float[16];
	private final float[] rbW = new float[16];
	private final int[] rbC = new int[16];
	private final float[] sideX = new float[16];
	private final float[] sideY = new float[16];
	private final float[] sideZ = new float[16];
	private final float[] tmp = new float[3];
	private final float[] tmp2 = new float[3];

	public Wings() {
		super("wings", "Flügel", "Engels-, Drachen-, Energie-, Schmetterlings- oder Papierflügel mit echtem Flügelskelett: im Stand halb angelegt, in der Luft weit offen und schlagend, beim Schleichen eng am Rücken.");
	}

	@Override
	protected void onDisable() {
		settled = false;
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;

		if (self != null && self.isFallFlying()) {
			return "Nicht beim Gleiten";
		}

		if (self != null && self.isVisuallySwimming()) {
			return "Nicht beim Schwimmen";
		}

		return super.probeLiveReason(minecraft);
	}

	@Override
	protected void draw(Geo target) {
		// Lying poses (elytra, swimming, riptide spin, sleeping, falling over on death) would leave
		// upright wings floating in the air
		if (player.isFallFlying() || player.isVisuallySwimming() || player.isAutoSpinAttack() || player.isSleeping() || player.isDeadOrDying()) {
			return;
		}

		boolean crouch = player.isCrouching();
		// Sitting in a boat or on a horse, climbing a ladder or floating in water is not flying
		boolean flying = !player.onGround() && !player.isPassenger() && !player.onClimbable() && !player.isInWater();
		float airTarget = flying ? 1f : 0f;
		float tuckTarget = crouch ? 1f : 0f;

		if (!settled) {
			air = airTarget;
			tuck = tuckTarget;
			settled = true;
		}

		air = Clock.approach(air, airTarget, 5f);
		tuck = Clock.approach(tuck, tuckTarget, 14f);
		// Vanilla crouch: the torso pivot drops and the torso leans 0.5 rad forward
		float pivot = crouch ? 20.8f * PX - 0.125f : 24f * PX;
		geo = target;
		preview = false;
		frame(target.rx(px), target.ry(py + pivot), target.rz(pz), bodyYaw, crouch ? 0.5f : 0f);
		render(Clock.tf(), air, tuck);
		geo = null;
	}

	private void frame(float x, float y, float z, float yawDegrees, float leanRadians) {
		double yaw = Math.toRadians(yawDegrees);
		float sin = (float) Math.sin(yaw);
		float cos = (float) Math.cos(yaw);
		float c = (float) Math.cos(leanRadians);
		float s = (float) Math.sin(leanRadians);
		lean = leanRadians;
		origin[0] = x;
		origin[1] = y;
		origin[2] = z;
		right[0] = -cos;
		right[1] = 0f;
		right[2] = -sin;
		up[0] = -sin * s;
		up[1] = c;
		up[2] = cos * s;
		fwd[0] = -sin * c;
		fwd[1] = -s;
		fwd[2] = cos * c;
	}

	private void render(float time, float airAmount, float tuckAmount) {
		float open = spread.f();
		// Sneaking wins over flying: in the crouch pose the head sits right above the shoulders
		wTuck = tuckAmount;
		wFly = airAmount * (1f - tuckAmount);
		wOpen = open * (1f - tuckAmount) * (1f - airAmount);
		wFold = (1f - open) * (1f - tuckAmount) * (1f - airAmount);
		// The menu card shows the wings a bit larger than life, so they read next to the small figure
		scale = size.f() * (preview ? 1.45f : 1f);
		int kind = style.index();

		for (int s = -1; s <= 1; s += 2) {
			if (kind == 3) {
				butterfly(s, time);
				continue;
			}

			pose(s, time, kind == 1 ? DRAGON_BONE : BONE);

			switch (kind) {
				case 1 -> dragon();
				case 2 -> energy(time);
				case 4 -> paper();
				default -> angel();
			}
		}
	}

	private float blend(float[][] table, int i) {
		return wFold * table[0][i] + wOpen * table[1][i] + wTuck * table[2][i] + wFly * table[3][i];
	}

	// ---- skeleton -------------------------------------------------------------------------

	/** Builds the bone chain of one side for the current pose weights. */
	private void pose(int s, float time, float[] lengths) {
		side = s;
		// Root on the shoulder blade (1.25 blocks up when standing), just behind the back. While
		// crouching the head hangs over the shoulders, so the root slides a little down the back.
		float down = 0.156f + (lean > 0f ? 0.08f : 0f);

		for (int k = 0; k < 3; k++) {
			out[k] = right[k] * s;
			back[k] = -fwd[k];
			joint[0][k] = origin[k] + right[k] * s * 0.1f - up[k] * down - fwd[k] * 0.15f;
		}

		float beat = time * 6f * speed.f();
		float breath = time * 1.6f * speed.f();
		float calm = 1f - wFly;

		for (int i = 0; i < 3; i++) {
			float flap = (float) Math.sin(beat - FLAP_LAG[i]);
			float sway = (float) Math.sin(breath - i * 0.5f);
			float sweep = blend(SWEEP, i) + wFly * FLAP_SWEEP[i] * (float) Math.cos(beat - FLAP_LAG[i]) + calm * 2.5f * sway;
			float lift = blend(LIFT, i) + wFly * FLAP_LIFT[i] * flap + calm * (2.5f + i * 1.5f) * sway;
			bone(i, sweep, lift, blend(TWIST, i));

			for (int k = 0; k < 3; k++) {
				joint[i + 1][k] = joint[i][k] + boneD[i][k] * lengths[i] * scale;
			}
		}

		// Directions at the joints are the average of both bones, so feathers turn smoothly
		for (int k = 0; k < 3; k++) {
			jointD[0][k] = boneD[0][k];
			jointD[3][k] = boneD[2][k];
			jointD[1][k] = boneD[0][k] + boneD[1][k];
			jointD[2][k] = boneD[1][k] + boneD[2][k];
		}

		normalize(jointD[1]);
		normalize(jointD[2]);
	}

	/** Start of the upper arm tube, moved out a little so its thickness stays out of the back. */
	private float[] shoulder() {
		for (int k = 0; k < 3; k++) {
			tmp[k] = joint[0][k] + boneD[0][k] * 0.04f;
		}

		return tmp;
	}

	/** Direction of bone i from sweep and lift, with its trailing direction and top-side normal. */
	private void bone(int i, float sweepDegrees, float liftDegrees, float twistDegrees) {
		double a = Math.toRadians(sweepDegrees);
		double e = Math.toRadians(liftDegrees);
		double b = Math.toRadians(twistDegrees);
		float ca = (float) Math.cos(a);
		float sa = (float) Math.sin(a);
		float ce = (float) Math.cos(e);
		float se = (float) Math.sin(e);
		float cb = (float) Math.cos(b);
		float sb = (float) Math.sin(b);
		float[] d = boneD[i];
		float[] t = boneT[i];

		for (int k = 0; k < 3; k++) {
			float horizontal = ca * out[k] + sa * back[k];
			float behind = -sa * out[k] + ca * back[k];
			d[k] = ce * horizontal + se * up[k];
			// Straight down the wing, then turned backwards around the bone
			t[k] = cb * (se * horizontal - ce * up[k]) + sb * behind;
		}

		cross(t, d, boneN[i]);

		for (int k = 0; k < 3; k++) {
			boneN[i][k] *= side;
		}
	}

	/**
	 * Point and frame at chain position u (0 = shoulder, 1 = elbow, 2 = wrist, 3 = hand tip). The
	 * trailing direction comes from the upper arm and forearm only and stays with the forearm past
	 * the wrist, so the primaries fall in line with the secondaries when the hand folds down.
	 */
	private void sample(float u) {
		int i = Math.max(0, Math.min(2, (int) u));
		float f = Math.max(0f, Math.min(1f, u - i));
		float[] d0;
		float[] d1;
		float g;

		if (f < 0.5f) {
			d0 = jointD[i];
			d1 = boneD[i];
			g = f * 2f;
		} else {
			d0 = boneD[i];
			d1 = jointD[i + 1];
			g = f * 2f - 1f;
		}

		float h = Math.max(0f, Math.min(1f, u - 0.5f));

		for (int k = 0; k < 3; k++) {
			sp[k] = joint[i][k] + (joint[i + 1][k] - joint[i][k]) * f;
			sd[k] = d0[k] + (d1[k] - d0[k]) * g;
			st[k] = boneT[0][k] + (boneT[1][k] - boneT[0][k]) * h;
		}

		normalize(sd);

		if (u < 2f) {
			float dot = dot(st, sd);

			for (int k = 0; k < 3; k++) {
				st[k] -= dot * sd[k];
			}

			normalize(st);
			cross(st, sd, sn);
		} else {
			// The hand: primaries lie in the plane of the forearm's trailing direction and the hand
			// bone. A bit of the forearm plane keeps it stable when both point the same way.
			cross(boneT[1], boneD[2], sn);

			for (int k = 0; k < 3; k++) {
				sn[k] += boneN[1][k] * side * 0.25f;
			}

			normalize(sn);
		}

		for (int k = 0; k < 3; k++) {
			sn[k] *= side;
		}
	}

	/**
	 * Feather at chain position u: tertials lean slightly inwards, secondaries hang along the
	 * trailing direction, primaries fan out from there towards the hand bone.
	 */
	private void featherFrame(float u, float lift) {
		sample(u);

		if (u <= 2f) {
			double a = Math.toRadians(-10f + 6f * u);
			float ca = (float) Math.cos(a);
			float sa = (float) Math.sin(a);

			for (int k = 0; k < 3; k++) {
				fc[k] = ca * st[k] + sa * sd[k];
			}
		} else {
			float g = 0.9f * (float) Math.pow(u - 2f, 1.2);

			for (int k = 0; k < 3; k++) {
				fc[k] = st[k] * (1f - g) + boneD[2][k] * g;
			}

			normalize(fc);
		}

		// Width direction in the wing surface, pointing towards the wing tip
		cross(sn, fc, fw);

		for (int k = 0; k < 3; k++) {
			fw[k] *= side;
			fb[k] = sp[k] + sn[k] * lift;
		}

		normalize(fw);
	}

	/** Flight feather length along the chain, before size. */
	private static float featherLength(float u) {
		if (u < 1f) {
			return 0.34f + 0.1f * u;
		}

		if (u < 2f) {
			return 0.44f + 0.08f * (u - 1f);
		}

		return 0.52f + 0.13f * (float) Math.sin((u - 2f) * 2.6f);
	}

	private int count(int high) {
		// Theme.detail keeps at least six at medium quality, which would add detail to small counts
		return preview ? Math.max(3, high / 2) : Math.min(high, Theme.detail(high));
	}

	// ---- Engel ----------------------------------------------------------------------------

	private void angel() {
		int rgb = color.rgb();
		float s = scale;
		int tertials = count(4);
		int secondaries = count(10);
		int primaries = count(10);

		// Flight feathers first, coverts on top, so the wing builds up in layers
		for (int k = 0; k < tertials; k++) {
			float u = 0.2f + 0.75f * k / tertials;
			flight(u, featherLength(u) * s, 0.09f * s, 0.004f * s * (tertials - k), 0f, rgb, 0.35f);
		}

		for (int k = 0; k < secondaries; k++) {
			float u = 1f + k / (float) secondaries;
			flight(u, featherLength(u) * s, 0.085f * s, 0.002f * s * (secondaries - k), 0.02f, rgb, 0.45f);
		}

		for (int k = 0; k < primaries; k++) {
			float u = 2f + k / (float) (primaries - 1);
			flight(u, featherLength(u) * s, 0.075f * s, 0.002f * s * k, 0.08f, rgb, 0.6f);
		}

		int greater = count(12);

		for (int k = 0; k < greater; k++) {
			float u = 0.3f + 2.55f * k / (greater - 1);
			featherFrame(u, 0.024f * s);
			feather(featherLength(u) * 0.46f * s, 0.075f * s, 0f, COVERT_T, COVERT_W, WHITE, rgb, 0.18f, 0.86f, 1f, false);
		}

		int lesser = count(12);

		for (int k = 0; k < lesser; k++) {
			float u = 0.12f + 2.4f * k / (lesser - 1);
			featherFrame(u, 0.034f * s);
			feather(0.16f * s, 0.07f * s, 0f, COVERT_T, COVERT_W, WHITE, rgb, 0.06f, 0.9f, 1f, false);
		}

		int edge = Colors.shade(WHITE, -0.03f);
		limb(shoulder(), joint[1], 0.034f * s, 0.028f * s, edge, 1f, boneN[0]);
		limb(joint[1], joint[2], 0.028f * s, 0.02f * s, edge, 1f, boneN[1]);
		limb(joint[2], joint[3], 0.02f * s, 0.008f * s, edge, 1f, boneN[2]);
	}

	private void flight(float u, float length, float width, float lift, float bend, int rgb, float tint) {
		featherFrame(u, lift);
		feather(length, width, bend, FLIGHT_T, FLIGHT_W, WHITE, rgb, tint, 0.7f, 1f, true);
	}

	/**
	 * Draws the feather set up by {@link #featherFrame}: a tapered blade that arches slightly to the top
	 * side, lit from above, with a darker root and one darker edge so overlapping feathers stay apart.
	 */
	private void feather(float length, float width, float bend, float[] stations, float[] widths,
			int base, int tip, float tint, float rootShade, float alpha, boolean shaft) {
		boolean top = dot(sn, fb) < 0f;
		float light = (top ? 1f : 0.8f) * (0.86f + 0.14f * (top ? sn[1] : -sn[1]));
		int n = stations.length;

		for (int i = 0; i < n; i++) {
			float t = stations[i];
			float along = length * t;
			float arch = length * (shaft ? 0.06f : 0.02f) * (float) Math.sin(t * Math.PI);
			float sideways = bend * length * t * t;
			float half = widths[i] * width;
			float cx = fb[0] + fc[0] * along + sn[0] * arch + fw[0] * sideways;
			float cy = fb[1] + fc[1] * along + sn[1] * arch + fw[1] * sideways;
			float cz = fb[2] + fc[2] * along + sn[2] * arch + fw[2] * sideways;
			ax[i] = cx - fw[0] * half;
			ay[i] = cy - fw[1] * half;
			az[i] = cz - fw[2] * half;
			bx[i] = cx + fw[0] * half;
			by[i] = cy + fw[1] * half;
			bz[i] = cz + fw[2] * half;
			int body = Colors.mixRgb(base, tip, tint * t * t);
			float l = light * (rootShade + (1f - rootShade) * Math.min(1f, t * 2.2f));
			ac[i] = Colors.argb(alpha, lit(body, l * 1.03f));
			bc[i] = Colors.argb(alpha, lit(body, l * 0.9f));
		}

		for (int i = 0; i < n - 1; i++) {
			quad(ax[i], ay[i], az[i], ac[i], bx[i], by[i], bz[i], bc[i], bx[i + 1], by[i + 1], bz[i + 1], bc[i + 1], ax[i + 1], ay[i + 1], az[i + 1], ac[i + 1]);
		}

		if (shaft) {
			// The quill, a hair above the vane on the side that faces the camera
			float lift = top ? 0.003f : -0.003f;
			int c = Colors.argb(alpha, lit(Colors.mixRgb(base, tip, tint * 0.4f), light * 1.1f));
			float x0 = (ax[0] + bx[0]) / 2f + sn[0] * lift;
			float y0 = (ay[0] + by[0]) / 2f + sn[1] * lift;
			float z0 = (az[0] + bz[0]) / 2f + sn[2] * lift;
			float x1 = (ax[n - 2] + bx[n - 2]) / 2f + sn[0] * lift;
			float y1 = (ay[n - 2] + by[n - 2]) / 2f + sn[1] * lift;
			float z1 = (az[n - 2] + bz[n - 2]) / 2f + sn[2] * lift;
			float w = width * 0.07f;
			quad(x0 - fw[0] * w, y0 - fw[1] * w, z0 - fw[2] * w, c, x0 + fw[0] * w, y0 + fw[1] * w, z0 + fw[2] * w, c,
					x1 + fw[0] * w * 0.4f, y1 + fw[1] * w * 0.4f, z1 + fw[2] * w * 0.4f, Colors.fade(c, 0.5f),
					x1 - fw[0] * w * 0.4f, y1 - fw[1] * w * 0.4f, z1 - fw[2] * w * 0.4f, Colors.fade(c, 0.5f));
		}
	}

	// ---- Drache ---------------------------------------------------------------------------

	private void dragon() {
		int rgb = color.rgb();
		float s = scale;
		int boneColor = Colors.mixRgb(Colors.shade(rgb, 0.2f), 0xE8DCC4, 0.35f);
		float[] wrist = joint[2];
		float[] hand = boneD[2];
		float[] trail = boneT[1];

		// Fingers: two segments each, the outer one bent a little towards the trailing side. Like the
		// primaries they spread from the forearm's trailing direction towards the hand bone.
		for (int f = 0; f < 4; f++) {
			float g = blendFinger(f);
			float len = FINGER_LENGTH[f] * s * 0.5f;
			float[] knuckle = finger[f * 3 + 1];
			float[] tipPoint = finger[f * 3 + 2];

			for (int k = 0; k < 3; k++) {
				finger[f * 3][k] = wrist[k];
				tmp[k] = trail[k] * (1f - g) + hand[k] * g;
				tmp2[k] = trail[k] * (1.08f - g) + hand[k] * (g - 0.08f);
			}

			normalize(tmp);
			normalize(tmp2);

			for (int k = 0; k < 3; k++) {
				knuckle[k] = wrist[k] + tmp[k] * len;
				tipPoint[k] = knuckle[k] + tmp2[k] * len;
			}
		}

		// Membranes between the fingers, then the big one from the last finger back to the body
		int rows = count(5);
		int cols = 3;

		for (int f = 0; f < 3; f++) {
			fingerPanel(f, rows, cols, rgb);
		}

		armPanel(rows + 1, cols, rgb);

		// Bones on top
		limb(shoulder(), joint[1], 0.03f * s, 0.024f * s, boneColor, 1f, boneN[0]);
		limb(joint[1], joint[2], 0.024f * s, 0.019f * s, boneColor, 1f, boneN[1]);

		for (int f = 0; f < 4; f++) {
			limb(finger[f * 3], finger[f * 3 + 1], 0.016f * s, 0.011f * s, boneColor, 1f, boneN[2]);
			limb(finger[f * 3 + 1], finger[f * 3 + 2], 0.011f * s, 0.003f * s, boneColor, 1f, boneN[2]);
		}

		// Small hooked claw at the wrist, pointing forward along the leading edge
		float[] claw = tmp;
		float[] clawTip = tmp2;

		for (int k = 0; k < 3; k++) {
			claw[k] = wrist[k] + (-trail[k] * 0.7f + boneD[1][k] * 0.5f + up[k] * 0.2f) * 0.07f * s;
			clawTip[k] = claw[k] + (-trail[k] * 0.3f + boneD[1][k] * 0.6f - up[k] * 0.2f) * 0.07f * s;
		}

		limb(wrist, claw, 0.018f * s, 0.011f * s, 0xEFE6D0, 1f, boneN[1]);
		limb(claw, clawTip, 0.011f * s, 0.001f * s, 0x3A332C, 1f, boneN[1]);
	}

	private float blendFinger(int f) {
		return wFold * FINGER[0][f] + wOpen * FINGER[1][f] + wTuck * FINGER[2][f] + wFly * FINGER[3][f];
	}

	/** Membrane between finger f and f + 1, with a scalloped trailing edge pulled towards the wrist. */
	private void fingerPanel(int f, int rows, int cols, int rgb) {
		float[] wrist = joint[2];
		float spanX = finger[f * 3 + 2][0] - finger[f * 3 + 5][0];
		float spanY = finger[f * 3 + 2][1] - finger[f * 3 + 5][1];
		float spanZ = finger[f * 3 + 2][2] - finger[f * 3 + 5][2];
		float billow = 0.08f * (float) Math.sqrt(spanX * spanX + spanY * spanY + spanZ * spanZ);

		for (int i = 0; i <= rows; i++) {
			float s = i / (float) rows;

			for (int j = 0; j <= cols; j++) {
				float v = j / (float) cols;
				float bulge = (float) Math.sin(v * Math.PI);
				int g = i * (cols + 1) + j;

				for (int k = 0; k < 3; k++) {
					float a = along(f, s, k);
					float b = along(f + 1, s, k);
					float p = a + (b - a) * v;
					p += (wrist[k] - p) * 0.3f * bulge * s * s;
					p += boneN[2][k] * billow * bulge * (float) Math.sin(s * Math.PI);
					grid(g, k, p);
				}
			}
		}

		membrane(rows, cols, rgb, boneN[2], false);
	}

	/** Point on finger f at s (0 = wrist, 1 = tip). */
	private float along(int f, float s, int k) {
		if (s < 0.5f) {
			return finger[f * 3][k] + (finger[f * 3 + 1][k] - finger[f * 3][k]) * s * 2f;
		}

		return finger[f * 3 + 1][k] + (finger[f * 3 + 2][k] - finger[f * 3 + 1][k]) * (s * 2f - 1f);
	}

	/** The big membrane: from the last finger over the forearm and upper arm down to the lower back. */
	private void armPanel(int rows, int cols, int rgb) {
		float[] tipPoint = finger[11];
		float[] anchor = tmp;

		for (int k = 0; k < 3; k++) {
			anchor[k] = origin[k] + right[k] * side * 0.05f - up[k] * 0.56f - fwd[k] * 0.13f;
		}

		for (int i = 0; i <= rows; i++) {
			float s = i / (float) rows;
			// Leading edge runs wrist -> elbow -> shoulder
			float chain = s < 0.6f ? 2f - s / 0.6f : 1f - (s - 0.6f) / 0.4f;
			sample(chain);

			for (int j = 0; j <= cols; j++) {
				float v = j / (float) cols;
				int g = i * (cols + 1) + j;

				for (int k = 0; k < 3; k++) {
					float trail = tipPoint[k] + (anchor[k] - tipPoint[k]) * s;
					trail += (sp[k] - trail) * 0.22f * (float) Math.sin(s * Math.PI);
					float p = sp[k] + (trail - sp[k]) * v;
					p += boneN[1][k] * 0.04f * scale * (float) Math.sin(v * Math.PI) * (float) Math.sin(s * Math.PI);
					grid(g, k, p);
				}
			}
		}

		membrane(rows, cols, rgb, boneN[1], true);
	}

	private void grid(int g, int k, float p) {
		if (k == 0) {
			gx[g] = p;
		} else if (k == 1) {
			gy[g] = p;
		} else {
			gz[g] = p;
		}
	}

	/** Shades and draws the membrane grid; {@code trailingCols} says the free edge is the last column. */
	private void membrane(int rows, int cols, int rgb, float[] normal, boolean trailingCols) {
		boolean top = normal[0] * gx[0] + normal[1] * gy[0] + normal[2] * gz[0] < 0f;
		int skin = top ? Colors.shade(rgb, -0.42f) : Colors.shade(rgb, -0.56f);
		int edge = top ? Colors.shade(rgb, -0.18f) : Colors.shade(rgb, -0.4f);

		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				int a = i * (cols + 1) + j;
				int b = a + 1;
				int c = a + cols + 2;
				int d = a + cols + 1;
				// Face normal from the diagonals, turned to the camera, lit from above
				float ux = gx[c] - gx[a];
				float uy = gy[c] - gy[a];
				float uz = gz[c] - gz[a];
				float vx = gx[d] - gx[b];
				float vy = gy[d] - gy[b];
				float vz = gz[d] - gz[b];
				float nx = uy * vz - uz * vy;
				float ny = uz * vx - ux * vz;
				float nz = ux * vy - uy * vx;
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

				if (len < 1.0e-7f) {
					continue;
				}

				if (nx * gx[a] + ny * gy[a] + nz * gz[a] > 0f) {
					ny = -ny;
				}

				float light = 0.8f + 0.25f * ny / len;
				float e0 = trailingCols ? j / (float) cols : i / (float) rows;
				float e1 = trailingCols ? (j + 1) / (float) cols : (i + 1) / (float) rows;
				// Darker at the bones, lighter towards the free edge
				int c0 = Colors.argb(0.93f, lit(Colors.mixRgb(skin, edge, e0 * e0), light));
				int c1 = Colors.argb(0.93f, lit(Colors.mixRgb(skin, edge, e1 * e1), light));
				quad(gx[a], gy[a], gz[a], c0, gx[b], gy[b], gz[b], trailingCols ? c1 : c0, gx[c], gy[c], gz[c], c1, gx[d], gy[d], gz[d], trailingCols ? c0 : c1);
			}
		}

		// Thin rim along the free edge
		int rim = Colors.argb(0.9f, Colors.shade(rgb, top ? -0.62f : -0.7f));
		int steps = trailingCols ? rows : cols;

		for (int i = 0; i < steps; i++) {
			int a = trailingCols ? i * (cols + 1) + cols : rows * (cols + 1) + i;
			int b = trailingCols ? a + cols + 1 : a + 1;
			segment(gx[a], gy[a], gz[a], gx[b], gy[b], gz[b], 0.006f * scale, rim, rim);
		}
	}

	// ---- Energie --------------------------------------------------------------------------

	private void energy(float time) {
		float s = scale;
		// A pulse of light runs from the shoulder out to the feather tips
		float pulse = (time * 0.55f * speed.f()) % 1f;
		int feathers = count(14);
		int n = OUTLINE_T.length;

		for (int k = 0; k < feathers; k++) {
			float u = 0.35f + 2.65f * k / (feathers - 1);
			featherFrame(u, 0f);
			float length = featherLength(u) * s * 1.05f;
			float width = (u > 2f ? 0.08f : 0.09f) * s;

			for (int i = 0; i < n; i++) {
				float t = OUTLINE_T[i];
				float half = OUTLINE_W[i] * width;
				float arch = length * 0.05f * (float) Math.sin(t * Math.PI);
				rbX[i] = fb[0] + fc[0] * length * t + fw[0] * half + sn[0] * arch;
				rbY[i] = fb[1] + fc[1] * length * t + fw[1] * half + sn[1] * arch;
				rbZ[i] = fb[2] + fc[2] * length * t + fw[2] * half + sn[2] * arch;
			}

			float q0 = u / 3f * 0.55f;
			int rgb = color.rgb(k / (float) feathers * 0.3f);

			// Faint body of light inside the outline, brighter where the pulse passes
			for (int i = 0; i < n / 2; i++) {
				int j = n - 1 - i;
				int ca = Colors.argb(0.05f + 0.16f * pulseAt(q0 + 0.45f * OUTLINE_T[i], pulse), rgb);
				int cb = Colors.argb(0.05f + 0.16f * pulseAt(q0 + 0.45f * OUTLINE_T[i + 1], pulse), rgb);
				quad(rbX[i], rbY[i], rbZ[i], ca, rbX[i + 1], rbY[i + 1], rbZ[i + 1], cb,
						rbX[j - 1], rbY[j - 1], rbZ[j - 1], cb, rbX[j], rbY[j], rbZ[j], ca);
			}

			glowLine(n, q0, 0.45f, pulse, rgb, (preview ? 0.012f : 0.006f) * s, true);
		}

		// The bones as one bright line, starting just off the back
		float[] start = shoulder();

		for (int i = 0; i < 4; i++) {
			rbX[i] = i == 0 ? start[0] : joint[i][0];
			rbY[i] = i == 0 ? start[1] : joint[i][1];
			rbZ[i] = i == 0 ? start[2] : joint[i][2];
		}

		glowLine(4, 0f, 0.55f, pulse, color.rgb(), 0.009f * s, false);

		// Faint sparkles drifting off the feather tips
		int sparkles = count(8);

		for (int k = 0; k < sparkles; k++) {
			float phase = (time * 0.45f + k * 0.618f) % 1f;
			float u = 1f + 2f * ((k * 0.381f) % 1f);
			featherFrame(u, 0f);
			float length = featherLength(u) * s * (0.85f + 0.3f * phase);
			float drop = phase * 0.25f * s;
			float x = fb[0] + fc[0] * length - up[0] * drop;
			float y = fb[1] + fc[1] * length - up[1] * drop;
			float z = fb[2] + fc[2] * length - up[2] * drop;
			int rgb = color.rgb(k / (float) sparkles + time * 0.1f);
			sparkle(x, y, z, 0.035f * s, Colors.argb(0.8f * (float) Math.sin(phase * Math.PI), Colors.shade(rgb, 0.55f)));
		}
	}

	/** Brightness (0..1) of the travelling pulse at wing position q (0 = root, 1 = tip). */
	private static float pulseAt(float q, float pulse) {
		float dist = Math.abs(q - pulse);
		dist = Math.min(dist, 1f - dist);
		return (float) Math.exp(-dist * dist * 90f);
	}

	/**
	 * Draws the points in rbX/Y/Z as a glowing line: a soft wide glow and a bright core. {@code q0}
	 * and {@code dq} say where the line sits on the wing; an outline goes out to its tip and back.
	 */
	private void glowLine(int n, float q0, float dq, float pulse, int rgb, float core, boolean outline) {
		// The menu card is too small for the soft glow, the bright core is enough there
		for (int pass = preview ? 1 : 0; pass < 2; pass++) {
			for (int i = 0; i < n; i++) {
				float t = i / (float) (n - 1);
				float glow = 0.3f + 0.7f * pulseAt(q0 + dq * (outline ? 1f - Math.abs(t * 2f - 1f) : t), pulse);

				if (pass == 0) {
					rbW[i] = core * 4f;
					rbC[i] = Colors.argb(0.2f * glow, rgb);
				} else {
					rbW[i] = core * (0.8f + 0.7f * glow);
					rbC[i] = Colors.argb(0.6f + 0.4f * glow, Colors.shade(rgb, 0.4f + 0.45f * glow));
				}
			}

			ribbon(n);
		}
	}

	// ---- Schmetterling --------------------------------------------------------------------

	private void butterfly(int s, float time) {
		side = s;
		int rgb = color.rgb();
		float beat = time * 15f * speed.f();
		// Fold angle: 0 = flat out to the side, 90 = closed behind the back. Fast flutter in the air,
		// a slow opening and closing at rest.
		float rest = 50f - 34f * spread.f() + 10f * (float) Math.sin(time * 1.4f * speed.f());
		float flutter = 10f + 60f * (0.5f + 0.5f * (float) Math.sin(beat));
		float fold = (wFold + wOpen) * rest + wTuck * 74f + wFly * flutter;
		// The hind wings trail the fore wings a little
		float hindFold = fold + 5f + wFly * 10f * (float) Math.sin(beat - 0.9f);
		float down = 0.15f + (lean > 0f ? 0.08f : 0f);

		// A small dark thorax on the spine carries the wings a little off the back, so the fore
		// wings clear the head
		if (s < 0) {
			int body = Colors.mixRgb(Colors.shade(rgb, -0.75f), 0x1A1A22, 0.5f);
			float r = 0.03f * scale;

			for (int k = 0; k < 3; k++) {
				tmp[k] = origin[k] - up[k] * (down - 0.07f) - fwd[k] * (0.122f + r);
				tmp2[k] = origin[k] - up[k] * (down + 0.24f) - fwd[k] * (0.122f + r * 0.55f);
			}

			limb(tmp, tmp2, r, r * 0.55f, body, 1f, fwd);
		}

		for (int k = 0; k < 3; k++) {
			joint[0][k] = origin[k] + right[k] * s * 0.03f - up[k] * down - fwd[k] * 0.18f;
		}

		butterflyWing(HIND, 0.25f, -0.28f, Math.min(80f, hindFold), -18f, rgb, 0.3f, -0.33f, 0.065f);
		butterflyWing(FORE, 0.36f, 0.38f, Math.min(80f, fold), 22f, rgb, 0.54f, 0.55f, 0.045f);
	}

	/**
	 * One butterfly wing: the outline (x out, y up) filled as a fan from (cx, cy), dark at the root and
	 * bright towards the edge, with a dark edge band with white dots, an eye spot and a few veins.
	 */
	private void butterflyWing(float[] outline, float cx, float cy, float foldDegrees, float tiltDegrees, int rgb, float eyeX, float eyeY, float eyeR) {
		double a = Math.toRadians(foldDegrees);
		float ca = (float) Math.cos(a);
		float sa = (float) Math.sin(a);
		float ct = (float) Math.cos(Math.toRadians(tiltDegrees));
		float stilt = (float) Math.sin(Math.toRadians(tiltDegrees));
		float s = scale;
		float[] wx = tmp;
		float[] wy = tmp2;
		float[] wn = sn;

		// Wing plane: out-and-back span axis, and an up axis tilted so the far end leans backwards
		for (int k = 0; k < 3; k++) {
			wx[k] = ca * right[k] * side + sa * -fwd[k];
			wy[k] = up[k] * ct - fwd[k] * stilt;
		}

		float dot = dot(wy, wx);

		for (int k = 0; k < 3; k++) {
			wy[k] -= dot * wx[k];
		}

		normalize(wy);
		cross(wx, wy, wn);

		for (int k = 0; k < 3; k++) {
			wn[k] *= side;
		}

		float[] o = joint[0];
		boolean top = dot(wn, o) < 0f;
		// Details sit a hair above the wing on the side the camera sees
		float facing = top ? 0.004f : -0.004f;
		int n = outline.length / 2;
		int step = preview || Theme.quality() == 0 ? 2 : 1;
		int band = Colors.argb(0.94f, Colors.mixRgb(Colors.shade(rgb, -0.78f), 0x16161E, 0.45f));
		int white = Colors.argb(0.92f, 0xF6F4EE);
		wingPoint(outline, -1, 0f, cx, cy, s, o, wx, wy, wn, 4);
		int center = Colors.argb(0.8f, wingColor(cx - outline[0], cy - outline[1], rgb, top));

		for (int i = 0; i < n; i += step) {
			int j = (i + step) % n;
			// The dark band follows the outer margin and fades out towards the body
			float insetI = 1f - 0.17f * margin(outline, i);
			float insetJ = 1f - 0.17f * margin(outline, j);
			wingPoint(outline, i, 1f, cx, cy, s, o, wx, wy, wn, 0);
			wingPoint(outline, j, 1f, cx, cy, s, o, wx, wy, wn, 1);
			wingPoint(outline, i, insetI, cx, cy, s, o, wx, wy, wn, 2);
			wingPoint(outline, j, insetJ, cx, cy, s, o, wx, wy, wn, 3);
			int fillI = Colors.argb(0.8f, wingColor(cx + (outline[i * 2] - cx) * insetI - outline[0], cy + (outline[i * 2 + 1] - cy) * insetI - outline[1], rgb, top));
			int fillJ = Colors.argb(0.8f, wingColor(cx + (outline[j * 2] - cx) * insetJ - outline[0], cy + (outline[j * 2 + 1] - cy) * insetJ - outline[1], rgb, top));
			tri(ax[4], ay[4], az[4], center, ax[2], ay[2], az[2], fillI, ax[3], ay[3], az[3], fillJ);
			quad(ax[2], ay[2], az[2], band, ax[0], ay[0], az[0], band, ax[1], ay[1], az[1], band, ax[3], ay[3], az[3], band);

			// White dots where the band is wide
			if ((i / step) % 2 == 0 && insetI < 0.88f) {
				float dx = (ax[0] + ax[1] + ax[2] + ax[3]) / 4f + wn[0] * facing;
				float dy = (ay[0] + ay[1] + ay[2] + ay[3]) / 4f + wn[1] * facing;
				float dz = (az[0] + az[1] + az[2] + az[3]) / 4f + wn[2] * facing;
				// Only a few pixels big in game, a diamond reads as round there
				spot(dx, dy, dz, 0.011f * s, wx, wy, white, white, 4);
			}
		}

		// Veins from the root out towards the edge
		int vein = Colors.argb(0.4f, Colors.shade(rgb, -0.7f));

		for (int i = 3; i < n - 2; i += Math.max(3, n / 5)) {
			wingPoint(outline, i, 0.84f, cx, cy, s, o, wx, wy, wn, 0);
			segment(o[0] + wn[0] * facing, o[1] + wn[1] * facing, o[2] + wn[2] * facing,
					ax[0] + wn[0] * facing, ay[0] + wn[1] * facing, az[0] + wn[2] * facing, 0.004f * s, vein, Colors.fade(vein, 0.3f));
		}

		// Eye spot: dark ring, golden ring, dark pupil and a small highlight
		wingPoint(outline, -1, 0f, eyeX, eyeY, s, o, wx, wy, wn, 4);
		float ex = ax[4] + wn[0] * facing * 1.5f;
		float ey = ay[4] + wn[1] * facing * 1.5f;
		float ez = az[4] + wn[2] * facing * 1.5f;
		int ring = Colors.mixRgb(Colors.shade(rgb, 0.3f), 0xFFC23D, 0.6f);
		int pupil = Colors.argb(0.95f, 0x101018);
		float r = eyeR * s;
		spot(ex, ey, ez, r, wx, wy, band, band, 8);
		spot(ex + wn[0] * facing * 0.5f, ey + wn[1] * facing * 0.5f, ez + wn[2] * facing * 0.5f, r * 0.72f, wx, wy, Colors.argb(0.95f, ring), Colors.argb(0.95f, Colors.shade(ring, -0.2f)), 8);
		spot(ex + wn[0] * facing, ey + wn[1] * facing, ez + wn[2] * facing, r * 0.38f, wx, wy, pupil, pupil, 6);
		spot(ex + wn[0] * facing * 1.5f + (wy[0] - wx[0]) * r * 0.18f, ey + wn[1] * facing * 1.5f + (wy[1] - wx[1]) * r * 0.18f, ez + wn[2] * facing * 1.5f + (wy[2] - wx[2]) * r * 0.18f,
				r * 0.13f, wx, wy, white, white, 4);
	}

	/** 0 near the root, 1 along the outer margin of a butterfly wing. */
	private static float margin(float[] outline, int i) {
		float dx = outline[i * 2] - outline[0];
		float dy = outline[i * 2 + 1] - outline[1];
		float d = (float) Math.sqrt(dx * dx + dy * dy);
		return Math.max(0f, Math.min(1f, (d - 0.2f) / 0.3f));
	}

	/** Wing color by distance from the root: dark at the body, bright and a little paler outwards. */
	private static int wingColor(float dx, float dy, int rgb, boolean top) {
		float t = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / 0.5f);
		int c = Colors.mixRgb(Colors.shade(rgb, -0.6f), rgb, t);
		c = Colors.mixRgb(c, 0xFFFFFF, 0.3f * t * t);
		// The underside is duller
		return top ? c : Colors.mixRgb(Colors.shade(c, -0.15f), 0xA8A8B2, 0.35f);
	}

	/** Slight cup towards the top side, stronger away from the root. */
	private static float cup(float x, float y) {
		return 0.05f * (x * x + y * y) / 0.4f;
	}

	/**
	 * Outline point i (or the point (cx, cy) itself when i is -1), moved towards (cx, cy) by
	 * {@code inset}, into slot {@code slot} of ax/ay/az.
	 */
	private void wingPoint(float[] outline, int i, float inset, float cx, float cy, float s, float[] o, float[] wx, float[] wy, float[] wn, int slot) {
		float x = i < 0 ? cx : cx + (outline[i * 2] - cx) * inset;
		float y = i < 0 ? cy : cy + (outline[i * 2 + 1] - cy) * inset;
		float c = cup(x, y);
		ax[slot] = o[0] + (wx[0] * x + wy[0] * y + wn[0] * c) * s;
		ay[slot] = o[1] + (wx[1] * x + wy[1] * y + wn[1] * c) * s;
		az[slot] = o[2] + (wx[2] * x + wy[2] * y + wn[2] * c) * s;
	}

	/** Small round spot in the plane (u, v), as a fan with the given number of sides. */
	private void spot(float x, float y, float z, float r, float[] u, float[] v, int inner, int outer, int sides) {
		for (int i = 0; i < sides; i++) {
			double a0 = i * 2 * Math.PI / sides;
			double a1 = (i + 1) * 2 * Math.PI / sides;
			float c0 = (float) Math.cos(a0) * r;
			float s0 = (float) Math.sin(a0) * r;
			float c1 = (float) Math.cos(a1) * r;
			float s1 = (float) Math.sin(a1) * r;
			tri(x, y, z, inner,
					x + u[0] * c0 + v[0] * s0, y + u[1] * c0 + v[1] * s0, z + u[2] * c0 + v[2] * s0, outer,
					x + u[0] * c1 + v[0] * s1, y + u[1] * c1 + v[1] * s1, z + u[2] * c1 + v[2] * s1, outer);
		}
	}

	// ---- Papier ---------------------------------------------------------------------------

	private void paper() {
		int rgb = color.rgb();
		float s = scale;
		// Origami paper: full color on the top side, paler on the back
		int front = Colors.mixRgb(rgb, PAPER_BACK, 0.45f);
		int back = Colors.mixRgb(rgb, PAPER_BACK, 0.78f);
		// Odd counts, so both outer rays are long ridges. Paper is cheap, and with fewer than seven
		// rays the fan turns into a flat sheet.
		int rays = Math.max(7, count(11)) | 1;

		// Main accordion fan, a shorter fan of folds on top, then the folded leading edge
		pleats(rays, 0.18f, 3f, 1f, 0f, front, back, s);
		pleats(Math.max(5, rays / 2 + 1) | 1, 0.25f, 2.5f, 0.48f, 0.02f * s, front, back, s);
		int strip = count(6);
		int crease = Colors.argb(0.6f, Colors.shade(front, -0.45f));

		for (int i = 0; i < strip; i++) {
			float u0 = 3f * i / strip;
			float u1 = 3f * (i + 1) / strip;
			float w0 = 0.05f * s * (1f - u0 / 3.4f);
			float w1 = 0.05f * s * (1f - u1 / 3.4f);
			sample(u0);
			float x0 = sp[0];
			float y0 = sp[1];
			float z0 = sp[2];
			float hx0 = sp[0] - st[0] * w0 + sn[0] * w0 * 0.4f;
			float hy0 = sp[1] - st[1] * w0 + sn[1] * w0 * 0.4f;
			float hz0 = sp[2] - st[2] * w0 + sn[2] * w0 * 0.4f;
			sample(u1);
			float hx1 = sp[0] - st[0] * w1 + sn[0] * w1 * 0.4f;
			float hy1 = sp[1] - st[1] * w1 + sn[1] * w1 * 0.4f;
			float hz1 = sp[2] - st[2] * w1 + sn[2] * w1 * 0.4f;
			facet(x0, y0, z0, hx0, hy0, hz0, hx1, hy1, hz1, sp[0], sp[1], sp[2], front, back, sn);
			segment(x0, y0, z0, sp[0], sp[1], sp[2], 0.003f * s, crease, crease);
		}
	}

	/**
	 * An accordion fan: rays from the chain, alternately raised (long ridges) and sunken (short
	 * valleys), so neighbouring facets tilt against each other, catch the light differently and
	 * leave a crisp zigzag edge.
	 */
	private void pleats(int rays, float from, float to, float lengthScale, float lift, int front, int back, float s) {
		float pleat = 0.05f * s * lengthScale;

		for (int i = 0; i < rays; i++) {
			float u = from + (to - from) * i / (rays - 1);
			featherFrame(u, lift);
			boolean ridge = i % 2 == 0;
			float length = paperLength(u) * s * lengthScale * (ridge ? 1f : 0.82f);
			float raise = ridge ? pleat : -pleat;
			gx[i * 2] = fb[0] + sn[0] * raise * 0.3f;
			gy[i * 2] = fb[1] + sn[1] * raise * 0.3f;
			gz[i * 2] = fb[2] + sn[2] * raise * 0.3f;
			gx[i * 2 + 1] = fb[0] + fc[0] * length + sn[0] * raise;
			gy[i * 2 + 1] = fb[1] + fc[1] * length + sn[1] * raise;
			gz[i * 2 + 1] = fb[2] + fc[2] * length + sn[2] * raise;
		}

		sample((from + to) / 2f);
		float[] normal = tmp2;
		normal[0] = sn[0];
		normal[1] = sn[1];
		normal[2] = sn[2];

		for (int i = 0; i < rays - 1; i++) {
			int a = i * 2;
			int b = a + 2;
			facet(gx[a], gy[a], gz[a], gx[a + 1], gy[a + 1], gz[a + 1], gx[b + 1], gy[b + 1], gz[b + 1], gx[b], gy[b], gz[b], front, back, normal);
		}

		// Crease lines: ridges catch a light edge, valleys a dark one
		for (int i = 1; i < rays - 1; i++) {
			int a = i * 2;
			int c = i % 2 == 0 ? Colors.argb(0.75f, Colors.shade(front, 0.55f)) : Colors.argb(0.5f, Colors.shade(front, -0.5f));
			segment(gx[a], gy[a], gz[a], gx[a + 1], gy[a + 1], gz[a + 1], 0.0026f * s, c, Colors.fade(c, 0.5f));
		}
	}

	private static float paperLength(float u) {
		if (u < 2f) {
			return 0.34f + 0.08f * u;
		}

		return 0.5f + 0.14f * (float) Math.sin((u - 2f) * 2.4f);
	}

	/**
	 * One flat paper facet (a quad, drawn in one color so it reads as a single folded plane),
	 * colored by the side the camera sees and shaded by its own tilt.
	 */
	private void facet(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int front, int back, float[] topNormal) {
		float ux = x2 - x0;
		float uy = y2 - y0;
		float uz = z2 - z0;
		float vx = x3 - x1;
		float vy = y3 - y1;
		float vz = z3 - z1;
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

		if (len < 1.0e-9f) {
			return;
		}

		nx /= len;
		ny /= len;
		nz /= len;

		// Normal towards the camera decides which side of the paper we see
		if (nx * (x0 + x2) + ny * (y0 + y2) + nz * (z0 + z2) > 0f) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}

		boolean top = nx * topNormal[0] + ny * topNormal[1] + nz * topNormal[2] > 0f;
		// Light from the upper left of the view, so facets folded left and right always differ
		float cx = x0 + x2;
		float cy = y0 + y2;
		float cz = z0 + z2;
		float d = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		float viewX = d < 1.0e-6f ? 0f : cx / d;
		float viewY = d < 1.0e-6f ? 0f : cy / d;
		float viewZ = d < 1.0e-6f ? 1f : cz / d;
		float rl = (float) Math.sqrt(viewX * viewX + viewZ * viewZ);
		float rx = rl < 1.0e-4f ? 1f : -viewZ / rl;
		float rz = rl < 1.0e-4f ? 0f : viewX / rl;
		float light = 0.7f + 0.32f * (-0.6f * (nx * rx + nz * rz) + 0.65f * ny - 0.45f * (nx * viewX + ny * viewY + nz * viewZ));
		int c = Colors.argb(1f, lit(top ? front : back, Math.max(0.5f, Math.min(1.1f, light))));
		quad(x0, y0, z0, c, x1, y1, z1, c, x2, y2, z2, c, x3, y3, z3, c);
	}

	// ---- shared drawing helpers -----------------------------------------------------------

	/** Four-sided tapered tube from a to b; faces turned away from the camera are skipped. */
	private void limb(float[] a, float[] b, float ra, float rb, int rgb, float alpha, float[] reference) {
		float dx = b[0] - a[0];
		float dy = b[1] - a[1];
		float dz = b[2] - a[2];
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (len < 1.0e-5f) {
			return;
		}

		dx /= len;
		dy /= len;
		dz /= len;
		float dot = reference[0] * dx + reference[1] * dy + reference[2] * dz;
		float e1x = reference[0] - dot * dx;
		float e1y = reference[1] - dot * dy;
		float e1z = reference[2] - dot * dz;
		float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);

		if (l1 < 1.0e-5f) {
			return;
		}

		e1x /= l1;
		e1y /= l1;
		e1z /= l1;
		float e2x = dy * e1z - dz * e1y;
		float e2y = dz * e1x - dx * e1z;
		float e2z = dx * e1y - dy * e1x;

		for (int f = 0; f < 4; f++) {
			// Face normal n and the in-face direction t (both perpendicular to the bone)
			float sign = f < 2 ? 1f : -1f;
			boolean first = f % 2 == 0;
			float nx = sign * (first ? e1x : e2x);
			float ny = sign * (first ? e1y : e2y);
			float nz = sign * (first ? e1z : e2z);
			float tx = first ? e2x : e1x;
			float ty = first ? e2y : e1y;
			float tz = first ? e2z : e1z;
			float mx = (a[0] + b[0]) / 2f + nx * (ra + rb) / 2f;
			float my = (a[1] + b[1]) / 2f + ny * (ra + rb) / 2f;
			float mz = (a[2] + b[2]) / 2f + nz * (ra + rb) / 2f;

			if (nx * mx + ny * my + nz * mz >= 0f) {
				continue;
			}

			float shade = ny >= 0 ? 0.76f + 0.3f * ny : 0.76f + 0.2f * ny;
			int c = Colors.argb(alpha, lit(rgb, shade));
			quad(a[0] + (nx + tx) * ra, a[1] + (ny + ty) * ra, a[2] + (nz + tz) * ra, c,
					a[0] + (nx - tx) * ra, a[1] + (ny - ty) * ra, a[2] + (nz - tz) * ra, c,
					b[0] + (nx - tx) * rb, b[1] + (ny - ty) * rb, b[2] + (nz - tz) * rb, c,
					b[0] + (nx + tx) * rb, b[1] + (ny + ty) * rb, b[2] + (nz + tz) * rb, c);
		}
	}

	/** Camera-facing line between two camera-relative points. */
	private void segment(float x0, float y0, float z0, float x1, float y1, float z1, float halfWidth, int c0, int c1) {
		float tx = x1 - x0;
		float ty = y1 - y0;
		float tz = z1 - z0;
		float mx = (x0 + x1) / 2f;
		float my = (y0 + y1) / 2f;
		float mz = (z0 + z1) / 2f;
		float cx = -(ty * mz - tz * my);
		float cy = -(tz * mx - tx * mz);
		float cz = -(tx * my - ty * mx);
		float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);

		if (len < 1.0e-9f) {
			return;
		}

		cx = cx / len * halfWidth;
		cy = cy / len * halfWidth;
		cz = cz / len * halfWidth;
		quad(x0 - cx, y0 - cy, z0 - cz, c0, x0 + cx, y0 + cy, z0 + cz, c0, x1 + cx, y1 + cy, z1 + cz, c1, x1 - cx, y1 - cy, z1 - cz, c1);
	}

	/** Camera-facing ribbon through rbX/Y/Z with widths rbW and colors rbC. */
	private void ribbon(int n) {
		float lastX = 0f;
		float lastY = 1f;
		float lastZ = 0f;

		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			float tx = rbX[b] - rbX[a];
			float ty = rbY[b] - rbY[a];
			float tz = rbZ[b] - rbZ[a];
			float cx = -(ty * rbZ[i] - tz * rbY[i]);
			float cy = -(tz * rbX[i] - tx * rbZ[i]);
			float cz = -(tx * rbY[i] - ty * rbX[i]);
			float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);

			if (len > 1.0e-9f) {
				lastX = cx / len;
				lastY = cy / len;
				lastZ = cz / len;
			}

			sideX[i] = lastX * rbW[i];
			sideY[i] = lastY * rbW[i];
			sideZ[i] = lastZ * rbW[i];
		}

		for (int i = 0; i < n - 1; i++) {
			int j = i + 1;
			quad(rbX[i] - sideX[i], rbY[i] - sideY[i], rbZ[i] - sideZ[i], rbC[i],
					rbX[i] + sideX[i], rbY[i] + sideY[i], rbZ[i] + sideZ[i], rbC[i],
					rbX[j] + sideX[j], rbY[j] + sideY[j], rbZ[j] + sideZ[j], rbC[j],
					rbX[j] - sideX[j], rbY[j] - sideY[j], rbZ[j] - sideZ[j], rbC[j]);
		}
	}

	/** Small four-pointed camera-facing sparkle. */
	private void sparkle(float x, float y, float z, float size, int argb) {
		float len = (float) Math.sqrt(x * x + y * y + z * z);

		if (len < 1.0e-4f || (argb >>> 24) == 0) {
			return;
		}

		float fx = x / len;
		float fy = y / len;
		float fz = z / len;
		float rx = -fz;
		float rz = fx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);

		if (rl < 1.0e-4f) {
			rx = 1f;
			rz = 0f;
		} else {
			rx /= rl;
			rz /= rl;
		}

		float ux = -rz * fy;
		float uy = rz * fx - rx * fz;
		float uz = rx * fy;
		int tip = Colors.fade(argb, 0f);
		float w = size * 0.22f;

		for (int i = 0; i < 4; i++) {
			float dx = i == 0 ? rx : i == 1 ? ux : i == 2 ? -rx : -ux;
			float dy = i == 0 ? 0f : i == 1 ? uy : i == 2 ? 0f : -uy;
			float dz = i == 0 ? rz : i == 1 ? uz : i == 2 ? -rz : -uz;
			float px2 = i % 2 == 0 ? ux : rx;
			float py2 = i % 2 == 0 ? uy : 0f;
			float pz2 = i % 2 == 0 ? uz : rz;
			tri(x + px2 * w, y + py2 * w, z + pz2 * w, argb, x - px2 * w, y - py2 * w, z - pz2 * w, argb, x + dx * size, y + dy * size, z + dz * size, tip);
		}
	}

	/** Light factor around 1: below darkens towards black, above lightens towards white. */
	private static int lit(int rgb, float light) {
		return Colors.shade(rgb, light - 1f);
	}

	private void quad(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
			float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
		if (geo != null) {
			geo.quad(x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3);
		} else if (preview) {
			flat(x0, y0, x1, y1, x2, y2, Colors.mix(Colors.mix(c0, c1, 0.5f), c2, 0.34f));
			flat(x0, y0, x2, y2, x3, y3, Colors.mix(Colors.mix(c0, c3, 0.5f), c2, 0.34f));
		}
	}

	private void tri(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2) {
		if (geo != null) {
			geo.tri(x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2);
		} else if (preview) {
			flat(x0, y0, x1, y1, x2, y2, Colors.mix(Colors.mix(c0, c1, 0.5f), c2, 0.34f));
		}
	}

	/**
	 * Menu card: blends a triangle into the pixel buffer row by row. The preview camera looks along
	 * +z, so screen x is -x.
	 */
	private void flat(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}

		float sx0 = guiX - x0 * guiScale;
		float sy0 = guiY - y0 * guiScale;
		float sx1 = guiX - x1 * guiScale;
		float sy1 = guiY - y1 * guiScale;
		float sx2 = guiX - x2 * guiScale;
		float sy2 = guiY - y2 * guiScale;
		int top = Math.max(0, (int) Math.ceil(Math.min(sy0, Math.min(sy1, sy2)) - 0.5f));
		int bottom = Math.min(bufH - 1, (int) Math.floor(Math.max(sy0, Math.max(sy1, sy2)) - 0.5f));

		for (int row = top; row <= bottom; row++) {
			float y = row + 0.5f;
			float min = Float.MAX_VALUE;
			float max = -Float.MAX_VALUE;

			for (int e = 0; e < 3; e++) {
				float xa = e == 0 ? sx0 : e == 1 ? sx1 : sx2;
				float ya = e == 0 ? sy0 : e == 1 ? sy1 : sy2;
				float xb = e == 0 ? sx1 : e == 1 ? sx2 : sx0;
				float yb = e == 0 ? sy1 : e == 1 ? sy2 : sy0;

				if ((y >= ya && y <= yb) || (y >= yb && y <= ya)) {
					float x = ya == yb ? xa : xa + (xb - xa) * (y - ya) / (yb - ya);
					min = Math.min(min, Math.min(x, ya == yb ? xb : x));
					max = Math.max(max, Math.max(x, ya == yb ? xb : x));
				}
			}

			int left = Math.round(min);
			int right = Math.round(max);

			// Keep thin lines unbroken
			if (right <= left && max - min > 0.15f) {
				right = left + 1;
			}

			left = Math.max(0, left);
			right = Math.min(bufW, right);

			for (int px = left; px < right; px++) {
				blend(row * bufW + px, argb);
			}
		}
	}

	/** Blends a color over a buffer pixel ("over" with straight alpha). */
	private void blend(int index, int argb) {
		int dst = pixels[index];
		float sa = (argb >>> 24) / 255f;
		float da = (dst >>> 24) / 255f * (1f - sa);
		float a = sa + da;

		if (a <= 0f) {
			return;
		}

		int r = Math.round((((argb >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * da) / a);
		int g = Math.round((((argb >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * da) / a);
		int b = Math.round(((argb & 0xFF) * sa + (dst & 0xFF) * da) / a);
		pixels[index] = (Math.round(a * 255f) << 24) | (r << 16) | (g << 8) | b;
	}

	/**
	 * Sends the buffer to the menu as rectangles: one per run of (nearly) equal color, stretched
	 * down over following rows that repeat the same run.
	 */
	private void flush() {
		for (int row = 0; row < bufH; row++) {
			int start = 0;

			while (start < bufW) {
				int c = pixels[row * bufW + start] & 0xF0F0F0F0;
				int end = start + 1;

				while (end < bufW && (pixels[row * bufW + end] & 0xF0F0F0F0) == c) {
					end++;
				}

				if ((c >>> 24) != 0) {
					int rows = 1;

					while (row + rows < bufH && sameRun(row + rows, start, end, c)) {
						java.util.Arrays.fill(pixels, (row + rows) * bufW + start, (row + rows) * bufW + end, 0);
						rows++;
					}

					// Back to the middle of the rounded color steps, full alpha stays full
					Gui2D.fill(gui, bufX + start, bufY + row, bufX + end, bufY + row + rows, c | 0x0F080808);
				}

				start = end;
			}
		}
	}

	private boolean sameRun(int row, int start, int end, int c) {
		int base = row * bufW;

		if ((start > 0 && (pixels[base + start - 1] & 0xF0F0F0F0) == c) || (end < bufW && (pixels[base + end] & 0xF0F0F0F0) == c)) {
			return false;
		}

		for (int i = start; i < end; i++) {
			if ((pixels[base + i] & 0xF0F0F0F0) != c) {
				return false;
			}
		}

		return true;
	}

	// ---- vector helpers -------------------------------------------------------------------

	private static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static void cross(float[] a, float[] b, float[] result) {
		float x = a[1] * b[2] - a[2] * b[1];
		float y = a[2] * b[0] - a[0] * b[2];
		float z = a[0] * b[1] - a[1] * b[0];
		result[0] = x;
		result[1] = y;
		result[2] = z;
	}

	private static void normalize(float[] v) {
		float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);

		if (len > 1.0e-9f) {
			v[0] /= len;
			v[1] /= len;
			v[2] /= len;
		}
	}

	/** Samples a closed Catmull-Rom spline through the key points (x, y pairs). */
	private static float[] spline(float[] keys, int perSegment) {
		int n = keys.length / 2;
		float[] result = new float[n * perSegment * 2];

		for (int i = 0; i < n; i++) {
			int i0 = (i - 1 + n) % n;
			int i2 = (i + 1) % n;
			int i3 = (i + 2) % n;

			for (int j = 0; j < perSegment; j++) {
				float t = j / (float) perSegment;
				float t2 = t * t;
				float t3 = t2 * t;

				for (int k = 0; k < 2; k++) {
					float p0 = keys[i0 * 2 + k];
					float p1 = keys[i * 2 + k];
					float p2 = keys[i2 * 2 + k];
					float p3 = keys[i3 * 2 + k];
					result[(i * perSegment + j) * 2 + k] = 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
				}
			}
		}

		return result;
	}

	// ---- menu card ------------------------------------------------------------------------

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		bufX = x;
		bufY = y;
		bufW = Math.max(1, Math.min(w, 320));
		bufH = Math.max(1, Math.min(h, 240));

		if (pixels.length < bufW * bufH) {
			pixels = new int[bufW * bufH];
		}

		java.util.Arrays.fill(pixels, 0, bufW * bufH, 0);
		// Same scale as the menu figure: 32 model pixels are about 35 GUI pixels
		guiScale = 35.2f / (32f * PX);
		guiX = w / 2f;
		guiY = feetY - y;
		gui = g;
		preview = true;
		// Rest for a while, then a few wing beats
		float cycle = Previews.cycle(time, 7f);
		float flying = Clock.easeInOut(Math.min(1f, Math.max(0f, Math.min(cycle - 0.45f, 0.95f - cycle) * 6f)));
		// The preview camera sits behind the player at the origin, looking along +z. The menu figure
		// has a smaller head than the real model, so its neck sits a bit higher.
		frame(0f, 1.52f, 3f, 8f * (float) Math.sin(time * 0.6f), 0f);
		render(time, flying, 0f);
		flush();
		gui = null;
		preview = false;
	}
}
