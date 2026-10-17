package dev.juiced.modules.cosmetic;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.NearbyBlocks;
import dev.juiced.render.Sky;

/**
 * Your shadow on the ground becomes an animal: a fox, a cat, a little dragon or a bunny. It walks
 * when you walk, sits down when you stand still, and is cast away from the sun (or the moon), long
 * at sunset and short at noon.
 *
 * <p>The animal is one side-view outline that is laid flat on the ground the way a real shadow
 * falls: its height points away from the sun and is stretched by how low the sun is. Because it is
 * a single outline (legs included), it is filled without overlapping triangles, so the shadow has
 * the same darkness everywhere.
 */
public final class ShadowPet extends CosmeticModule {
	/** Height above the ground, against z-fighting. */
	private static final float LIFT = 0.02f;
	private static final int MAX_POINTS = 64;
	/** Numbers per outline point in a kind's table: stand x, y, sit x, y, tail sway (positions in hundredths of a block). */
	private static final int STRIDE = 5;

	private static final Kind FOX = new Kind(new float[] {
			// stand x, y | sit x, y | tail sway
			-80, 30, -66, 8, 1f, // tail tip
			-68, 20, -54, 0, 0.75f,
			-52, 22, -40, 0, 0.45f,
			-38, 31, -28, 0, 0.1f, // rump
			-31, 30, -14, 2, 0f, // hind legs hang between this point and the next
			-23, 29, -5, 3, 0f,
			-4, 28, 4, 12, 0f, // belly
			14, 30, 7, 30, 0f, // front legs
			22, 30, 15, 32, 0f,
			31, 37, 22, 44, 0f, // chest
			40, 48, 26, 60, 0f,
			52, 52, 36, 70, 0f, // chin
			65, 53, 48, 72, 0f,
			71, 57, 54, 76, 0f, // nose
			61, 61, 45, 80, 0f,
			52, 66, 36, 85, 0f,
			49, 84, 33, 103, 0f, // ear
			43, 72, 27, 90, 0f,
			38, 82, 22, 100, 0f, // second ear
			33, 68, 17, 86, 0f,
			28, 60, 12, 76, 0f, // nape
			20, 51, 6, 64, 0f,
			-10, 50, -12, 48, 0f, // back
			-33, 48, -26, 32, 0f, // rump
			-42, 45, -36, 18, 0.3f, // bushy tail
			-56, 44, -50, 16, 0.6f,
			-70, 40, -60, 13, 0.85f,
	}, 4, 7, 0.08f, 0.06f, 0.012f, 1.5f, false, 0.03f, 0f, 1.15f, 56, 60, 40, 79);

	private static final Kind CAT = new Kind(new float[] {
			-30, 32, -26, 0, 0f, // back of the thigh
			-27, 28, -12, 2, 0f, // hind legs
			-19, 28, -4, 3, 0f,
			0, 27, 3, 12, 0f, // belly
			13, 28, 6, 30, 0f, // front legs
			20, 28, 13, 31, 0f,
			28, 34, 18, 44, 0f, // chest
			35, 44, 22, 58, 0f,
			46, 47, 30, 64, 0f, // chin
			57, 50, 40, 67, 0f,
			59, 54, 42, 71, 0f, // nose
			53, 59, 36, 76, 0f,
			51, 61, 34, 78, 0f,
			50, 73, 33, 91, 0f, // ear
			45, 64, 28, 81, 0f,
			40, 72, 22, 89, 0f, // second ear
			38, 61, 20, 78, 0f,
			33, 55, 14, 72, 0f, // nape
			24, 46, 4, 60, 0f,
			-6, 44, -16, 40, 0f, // back
			-28, 45, -26, 24, 0.05f, // the tail starts here, raised in a curve
			-32, 54, -31, 18, 0.3f,
			-35, 68, -38, 10, 0.55f,
			-31, 80, -52, 7, 0.8f,
			-26, 82, -62, 12, 1f, // tail tip
			-29, 87, -64, 6, 1f,
			-38, 82, -54, 1, 0.8f,
			-42, 68, -40, 1, 0.55f,
			-39, 54, -32, 2, 0.3f,
			-34, 44, -28, 3, 0.05f,
	}, 1, 4, 0.07f, 0.05f, 0.008f, 1.3f, false, 0.03f, 0f, 1.1f, 51, 55, 35, 72);

	private static final Kind DRAGON = new Kind(new float[] {
			-98, 31, -90, 10, 1f, // spade at the end of the tail
			-88, 22, -80, 0, 1f,
			-86, 27, -78, 4, 0.9f,
			-70, 27, -62, 0, 0.7f,
			-52, 31, -44, 0, 0.45f,
			-38, 33, -28, 0, 0.15f, // rump
			-31, 31, -12, 2, 0f, // hind legs
			-21, 30, -2, 4, 0f,
			-2, 28, 5, 14, 0f, // belly
			14, 30, 8, 32, 0f, // front legs
			23, 31, 17, 34, 0f,
			32, 40, 26, 46, 0f, // chest
			42, 54, 32, 62, 0f, // neck
			52, 68, 40, 78, 0f,
			62, 71, 50, 84, 0f, // jaw
			78, 73, 66, 86, 0f,
			80, 79, 68, 92, 0f, // snout
			68, 83, 56, 96, 0f,
			57, 86, 45, 99, 0f,
			40, 98, 28, 112, 0f, // horn
			46, 82, 34, 91, 0f,
			38, 64, 26, 76, 0f, // neck
			30, 54, 18, 64, 0f, // shoulder: the folded wing starts here
			16, 90, 6, 98, 0f, // wrist
			20, 96, 10, 104, 0f, // claw
			10, 90, 0, 98, 0f,
			-16, 76, -20, 80, 0f, // finger tips with the skin between them
			-12, 68, -16, 72, 0f,
			-32, 64, -32, 64, 0f,
			-28, 56, -26, 56, 0f,
			-48, 52, -40, 46, 0f,
			-38, 47, -32, 30, 0.1f, // rump
			-54, 42, -46, 10, 0.45f, // tail
			-72, 35, -62, 8, 0.7f,
			-86, 34, -78, 9, 0.9f,
			-88, 42, -82, 18, 1f,
	}, 6, 9, 0.07f, 0.05f, 0.015f, 1.6f, false, 0.03f, 0f, 1f, 62, 78, 50, 91);

	private static final Kind BUNNY = new Kind(new float[] {
			-37, 24, -26, 14, 1f, // fluffy tail
			-34, 16, -24, 6, 0.6f,
			-28, 10, -20, 2, 0f, // rump
			-24, 6, -16, 2, 0f, // long hind feet
			-8, 6, 2, 2, 0f,
			6, 9, 6, 16, 0f, // belly
			10, 12, 8, 36, 0f, // front legs
			16, 13, 14, 37, 0f,
			22, 20, 18, 46, 0f, // chest
			30, 27, 24, 56, 0f,
			40, 30, 32, 60, 0f,
			43, 35, 34, 65, 0f, // nose
			38, 42, 29, 71, 0f,
			33, 46, 25, 74, 0f,
			36, 66, 28, 94, 0f, // ear
			31, 84, 24, 112, 0f,
			27, 66, 20, 94, 0f,
			24, 50, 18, 78, 0f,
			21, 64, 15, 92, 0f, // second ear
			14, 80, 9, 108, 0f,
			14, 62, 8, 90, 0f,
			17, 46, 10, 74, 0f,
			12, 41, 6, 68, 0f, // nape
			-2, 43, -6, 58, 0f, // back
			-18, 40, -16, 40, 0f,
			-28, 34, -24, 26, 0f,
			-33, 30, -27, 20, 0.5f,
	}, 3, 6, 0.06f, 0.07f, 0.09f, 1.2f, true, 0f, 0.24f, 1.25f, 34, 38, 27, 66);

	private static final Kind[] KINDS = {FOX, CAT, DRAGON, BUNNY};

	private final ChoiceSetting kind = add(new ChoiceSetting("kind", "Art", 0, "Fuchs", "Katze", "Drache", "Hase"));
	private final SliderSetting opacity = add(SliderSetting.percent("opacity", "Deckkraft", 0.2, 1.0, 0.55));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", Colors.BLACK, true));
	private final BoolSetting nightRim = add(new BoolSetting("night_rim", "Leuchtender Rand nachts", true));

	// Animation, all smoothed per frame
	private float walk;
	private float phase;
	private float still;
	private float sit;
	private float crouch;
	private float facing = 1f;
	private float facingTarget = 1f;
	private double lastX;
	private double lastZ;
	private boolean placed;

	// Light, aimed every tick and eased per frame. Direction (radians on the ground, 0 = +X) and length
	// (blocks of shadow per block of height) are eased apart, so a flip turns the shadow around instead
	// of shrinking it to a point on the way.
	private float castAngle = (float) (-Math.PI / 2);
	private float castLength = 1f;
	private float soft = 1f;
	private float light;
	private float night;
	private float aimAngle = castAngle;
	private float aimLength = 1f;
	private float aimSoft = 1f;
	private float aimLight = 0.5f;
	private float aimNight;

	// Ground under you: where you land while in the air, and how far thin blocks (snow layers, soul
	// sand) show above your feet while you stand
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	private double groundY;
	private boolean hasGround;
	/** When the menu last showed the shadow; it fades in quicker there (it starts dark every time it is tuned in). */
	private double probeSeen = -1;
	private float groundLift;

	// Reused buffers: the outline in side view, then on the ground (camera relative)
	private final float[] ox = new float[MAX_POINTS];
	private final float[] oy = new float[MAX_POINTS];
	private final float[] gx = new float[MAX_POINTS];
	private final float[] gz = new float[MAX_POINTS];
	private final int[] fill = new int[MAX_POINTS];
	private final float[] edgeX = new float[MAX_POINTS];
	private final float[] edgeZ = new float[MAX_POINTS];
	private final float[] blur = new float[MAX_POINTS];
	private final int[] order = new int[MAX_POINTS];
	private final int[] tris = new int[MAX_POINTS * 3];
	private final float[] spans = new float[MAX_POINTS];

	public ShadowPet() {
		super("shadow_pet", "Schattentier", "Dein Schatten am Boden wird zu einem Fuchs, einer Katze, einem kleinen Drachen oder einem Hasen. "
				+ "Er läuft mit dir, setzt sich hin, wenn du stehst, und wird bei Sonnenuntergang lang. Auch in der Ego-Sicht zu sehen.");
		keepOnGround();
	}

	@Override
	protected boolean showInFirstPerson() {
		return true;
	}

	@Override
	protected void onDisable() {
		placed = false;
		hasGround = false;
		groundLift = 0f;
		// Fade in again next time, in the light of the new world
		light = 0f;
		still = 0f;
		sit = 0f;
	}

	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		probeSeen = Clock.t();
	}

	/** The shadow lies on the ground under you; high in the air or swimming there is none. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;

		if (self != null && !self.onGround() && (!hasGround || self.isInWater())) {
			return "Braucht Boden unter dir";
		}

		return super.probeLiveReason(minecraft);
	}

	// ---- light and ground, once per tick ------------------------------------------------------

	@Override
	protected void onTick(Minecraft minecraft, LocalPlayer self) {
		ClientLevel level = minecraft.level;

		if (level == null) {
			return;
		}

		findGround(level, self);
		BlockPos pos = self.blockPosition();
		float sun = Sky.sunAngle(level, pos);
		float moon = Sky.moonAngle(level, pos);
		// Something above you (a roof, a tree): no sun shadow, only a short soft one below you
		boolean roofed = NearbyBlocks.surface(level, pos.getX(), pos.getZ(), false) > Math.ceil(self.getY()) + 1;
		aimNight = Sky.night(level, pos);

		if (!Sky.overworld(level) || roofed || Float.isNaN(sun)) {
			// No moonlit rim in caves or under a roof
			aimNight = 0f;
			aimSoftly(0.55f);
			return;
		}

		// The moon always stands opposite the sun. If its angle reads close to the sun's, it is counted
		// from the other side of the sky, so turn it around (and guess it if the game doesn't tell us).
		if (Float.isNaN(moon) || Math.cos(Math.toRadians(moon - sun)) > 0.0) {
			moon = Float.isNaN(moon) ? sun + 180f : moon + 180f;
		}

		float sunUp = above(sun);
		float moonUp = above(moon) * (1f - sunUp);

		if (sunUp < 0.02f && moonUp < 0.02f) {
			// Twilight: neither stands high enough to cast a shadow
			aimSoftly(0.3f);
			return;
		}

		aim(sunUp >= moonUp ? sun : moon);
		aimSoft = 0f;
		// The moon casts a much fainter shadow; clouds blur both
		aimLight = sunUp + moonUp * 0.4f;

		if (level.isRaining()) {
			aimLight *= 0.45f;
			aimSoft = 0.7f;
		}
	}

	/** 0 below the horizon, 1 once the sun or moon stands a little above it. */
	private static float above(float angleDegrees) {
		float height = (float) Math.cos(Math.toRadians(angleDegrees));
		return Colors.clamp01(height / 0.14f);
	}

	/**
	 * Points the shadow away from a sun or moon at this angle. The sun moves from east (+X) over the
	 * top to west (-X); around noon the shadow leans north instead of flipping sides at once.
	 */
	private void aim(float angleDegrees) {
		double a = Math.toRadians(angleDegrees);
		float east = (float) Math.sin(a);
		float up = (float) Math.cos(a);
		float north = -0.3f * up;
		// Length per block of height: tan of the angle from straight up, kept between 1 and 3.5
		aimLength = Math.max(1f, Math.min(3.5f, Math.abs(east) / Math.max(0.01f, up)));
		aimAngle = (float) Math.atan2(north, east);
	}

	/** A short, soft shadow under you that keeps its current direction. */
	private void aimSoftly(float strength) {
		aimAngle = castAngle;
		aimLength = 0.55f;
		aimSoft = 1f;
		aimLight = strength;
	}

	/** The ground under you: your feet while you stand, otherwise the first solid block a few blocks below. */
	private void findGround(ClientLevel level, LocalPlayer self) {
		double feet = self.getY();
		int x = (int) Math.floor(self.getX());
		int z = (int) Math.floor(self.getZ());

		if (self.onGround()) {
			groundY = feet;
			groundLift = (float) (visibleTop(level, x, z, feet) - feet);
			hasGround = true;
			return;
		}

		int top = (int) Math.floor(feet);
		hasGround = false;

		for (int y = top; y >= top - 5; y--) {
			probe.set(x, y, z);
			VoxelShape shape = level.getBlockState(probe).getCollisionShape(level, probe);

			if (shape.isEmpty()) {
				continue;
			}

			double surface = y + shape.max(Direction.Axis.Y);

			if (surface <= feet + 0.01) {
				groundY = visibleTop(level, x, z, surface);
				hasGround = true;
				return;
			}
		}
	}

	/**
	 * Some blocks look a little higher than you stand on them: a thin snow layer (no collision at all),
	 * soul sand, mud, a pressure plate. A shadow at your feet would be hidden inside them, so it goes on
	 * top. Taller things at your feet (grass, flowers, torches) are left alone.
	 */
	private double visibleTop(ClientLevel level, int x, int z, double surface) {
		int y = (int) Math.floor(surface + 1.0e-3);
		probe.set(x, y, z);
		VoxelShape shape = level.getBlockState(probe).getShape(level, probe);

		if (shape.isEmpty()) {
			return surface;
		}

		double top = y + shape.max(Direction.Axis.Y);
		return top > surface && top - surface < 0.2 ? top : surface;
	}

	// ---- drawing --------------------------------------------------------------------------------

	@Override
	protected void draw(Geo geo) {
		float dt = Math.min(0.1f, Clock.dt());
		float time = Clock.tf();
		Kind k = KINDS[Math.floorMod(kind.index(), KINDS.length)];
		float s = size.f() * k.scale;

		// Walking: how fast you move, from the drawn position so it is smooth at any frame rate
		double moved = placed ? Math.sqrt((px - lastX) * (px - lastX) + (pz - lastZ) * (pz - lastZ)) : 0;

		if (moved > 3) {
			moved = 0;
		}

		lastX = px;
		lastZ = pz;
		placed = true;
		float speed = dt > 0f ? (float) moved / dt : 0f;
		boolean grounded = player.onGround();
		walk = Clock.approach(walk, grounded ? Math.min(1f, speed / 2.2f) : walk * 0.9f, 8f);
		phase = (phase + (float) (moved / (k.cycle * s) * Math.PI * 2)) % (float) (Math.PI * 200);
		still = speed < 0.3f && grounded ? still + dt : 0f;
		// Sits down after a second of standing still, gets up quickly
		float sitTarget = still > 1f && !crouching ? 1f : 0f;
		sit = Clock.approach(sit, sitTarget, sitTarget > sit ? 4f : 10f);
		crouch = Clock.approach(crouch, crouching ? 1f : 0f, 10f);

		// Turn the short way round
		float turn = (float) Math.IEEEremainder(aimAngle - castAngle, Math.PI * 2);
		castAngle = (float) Math.IEEEremainder(castAngle + Clock.approach(0f, turn, 1.5f), Math.PI * 2);
		castLength = Clock.approach(castLength, aimLength, 1.5f);
		soft = Clock.approach(soft, aimSoft, 1.5f);
		light = Clock.approach(light, aimLight, Clock.t() - probeSeen < 0.3 ? 4f : 1.5f);
		night = Clock.approach(night, aimNight, 1f);

		// Nothing to land on, or swimming (the water surface would need its own shadow)
		if ((!hasGround && !grounded) || (player.isInWater() && !grounded)) {
			return;
		}

		double ground = grounded ? py + groundLift : groundY;
		float height = (float) Math.max(0.0, py - ground);
		// Higher up, the shadow gets smaller and fainter and is gone at about four blocks
		float fade = Colors.clamp01(1f - height / 4f);
		float alpha = opacity.f() * light * fade;

		if (alpha < 0.01f) {
			return;
		}

		s /= 1f + 0.15f * height;
		float stretch = Math.max(0.3f, castLength);
		float dirX = (float) Math.cos(castAngle);
		float dirZ = (float) Math.sin(castAngle);
		float sideX = -dirZ;
		float sideZ = dirX;

		// The animal faces the side you face; it turns around like a paper cut-out
		double yaw = Math.toRadians(bodyYaw);
		float dot = (float) (-Math.sin(yaw) * sideX + Math.cos(yaw) * sideZ);

		if (dot > 0.3f) {
			facingTarget = 1f;
		} else if (dot < -0.3f) {
			facingTarget = -1f;
		}

		facing = Clock.approach(facing, facingTarget, 9f);

		int n = outline(k, sit, walk, phase, time, crouch, ox, oy);
		int triangles = triangulate(ox, oy, n, order, tris);
		int rgb = color.rgb();
		float baseX = geo.rx(px);
		float baseY = geo.ry(ground + LIFT);
		float baseZ = geo.rz(pz);
		// The far end of a long evening shadow is a bit lighter and softer
		float longness = Colors.clamp01((stretch - 1.2f) / 2.3f);

		for (int i = 0; i < n; i++) {
			float across = ox[i] * facing * s;
			float along = oy[i] * stretch * s;
			gx[i] = baseX + sideX * across + dirX * along;
			gz[i] = baseZ + sideZ * across + dirZ * along;
			fill[i] = Colors.argb(alpha * (1f - 0.35f * longness * Colors.clamp01(oy[i])), rgb);
			blur[i] = s * (0.03f + 0.03f * soft) + along * 0.025f + height * 0.03f;
		}

		for (int t = 0; t < triangles; t++) {
			int a = tris[t * 3];
			int b = tris[t * 3 + 1];
			int c = tris[t * 3 + 2];
			geo.tri(gx[a], baseY, gz[a], fill[a], gx[b], baseY, gz[b], fill[b], gx[c], baseY, gz[c], fill[c]);
		}

		// Soft edge: a strip around the outline that fades out (left out on low quality)
		edges(n);

		if (Theme.quality() > 0) {
			for (int i = 0; i < n; i++) {
				int j = i + 1 == n ? 0 : i + 1;
				geo.quad(gx[i], baseY, gz[i], fill[i], gx[j], baseY, gz[j], fill[j],
						gx[j] + edgeX[j] * blur[j], baseY, gz[j] + edgeZ[j] * blur[j], fill[j] & 0xFFFFFF,
						gx[i] + edgeX[i] * blur[i], baseY, gz[i] + edgeZ[i] * blur[i], fill[i] & 0xFFFFFF);
			}
		}

		// At night a faint moonlit rim, and the eye catches the light
		float rim = nightRim.on() ? night * fade * Math.min(1f, opacity.f() + 0.2f) : 0f;

		if (rim > 0.02f) {
			int rimRgb = Colors.mixRgb(0xBFD4FF, rgb, 0.2f);
			int inner = Colors.argb(rim * 0.5f, rimRgb);
			int outer = rimRgb;
			float width = 0.035f * s;

			for (int i = 0; i < n; i++) {
				int j = i + 1 == n ? 0 : i + 1;
				geo.quad(gx[i], baseY, gz[i], inner, gx[j], baseY, gz[j], inner,
						gx[j] + edgeX[j] * width, baseY, gz[j] + edgeZ[j] * width, outer,
						gx[i] + edgeX[i] * width, baseY, gz[i] + edgeZ[i] * width, outer);
			}

			// Placed like the outline points, so the eye stays in the head while it bobs or ducks
			float eyeX = (k.eyeX + (k.eyeSitX - k.eyeX) * sit) * 0.01f * (1f + 0.06f * crouch) * facing * s;
			float eyeY = ((k.eyeY + (k.eyeSitY - k.eyeY) * sit) * 0.01f + bob(k, sit, walk, phase)) * (1f - 0.28f * crouch) * stretch * s;
			float blink = (time % 4.7f) < 0.12f ? 0.1f : 1f;
			geo.glowDot(px + sideX * eyeX + dirX * eyeY, ground + LIFT + 0.01, pz + sideZ * eyeX + dirZ * eyeY, 0.035f * s, rimRgb, rim * 0.8f * blink);
		}
	}

	/** Outward directions at every outline point on the ground (mitered, so corners stay closed); shortens the blur at inner corners. */
	private void edges(int n) {
		float area = 0f;

		for (int i = 0; i < n; i++) {
			int j = i + 1 == n ? 0 : i + 1;
			area += gx[i] * gz[j] - gx[j] * gz[i];
		}

		float sign = area >= 0f ? 1f : -1f;

		for (int i = 0; i < n; i++) {
			int p = i == 0 ? n - 1 : i - 1;
			int q = i + 1 == n ? 0 : i + 1;
			float ax = gx[i] - gx[p];
			float az = gz[i] - gz[p];
			float bx = gx[q] - gx[i];
			float bz = gz[q] - gz[i];
			float la = Math.max(1.0e-6f, (float) Math.sqrt(ax * ax + az * az));
			float lb = Math.max(1.0e-6f, (float) Math.sqrt(bx * bx + bz * bz));
			float n1x = sign * az / la;
			float n1z = -sign * ax / la;
			float n2x = sign * bz / lb;
			float n2z = -sign * bx / lb;
			float mx = n1x + n2x;
			float mz = n1z + n2z;
			float ml = (float) Math.sqrt(mx * mx + mz * mz);

			if (ml < 1.0e-3f) {
				edgeX[i] = n1x;
				edgeZ[i] = n1z;
				continue;
			}

			mx /= ml;
			mz /= ml;

			if ((ax * bz - az * bx) * sign < 0f) {
				// Inner corner (between the legs, at the neck): keep the strip short so it can't fold over
				blur[i] = Math.min(blur[i], 0.4f * Math.min(la, lb));
				edgeX[i] = mx;
				edgeZ[i] = mz;
				continue;
			}

			// Longer at outer corners so the strip keeps its width, but not endlessly at sharp tips
			float miter = Math.min(2f, 1f / Math.max(0.5f, mx * n1x + mz * n1z));
			edgeX[i] = mx * miter;
			edgeZ[i] = mz * miter;
		}
	}

	// ---- the outline ----------------------------------------------------------------------------

	/** One animal: its outline standing and sitting, and how it walks. */
	private static final class Kind {
		final float[] points;
		final int rearHip;
		final int frontHip;
		final float stride;
		final float lift;
		final float bob;
		/** Blocks travelled per full step cycle (at size 1). */
		final float cycle;
		final boolean hops;
		/** How far the hind paws come forward when sitting. */
		final float sitPaws;
		/** How high the front paws are held when sitting (the bunny sits up). */
		final float sitFrontLift;
		final float scale;
		final float eyeX;
		final float eyeY;
		final float eyeSitX;
		final float eyeSitY;

		Kind(float[] points, int rearHip, int frontHip, float stride, float lift, float bob, float cycle, boolean hops,
				float sitPaws, float sitFrontLift, float scale, float eyeX, float eyeY, float eyeSitX, float eyeSitY) {
			this.points = points;
			this.rearHip = rearHip;
			this.frontHip = frontHip;
			this.stride = stride;
			this.lift = lift;
			this.bob = bob;
			this.cycle = cycle;
			this.hops = hops;
			this.sitPaws = sitPaws;
			this.sitFrontLift = sitFrontLift;
			this.scale = scale;
			this.eyeX = eyeX;
			this.eyeY = eyeY;
			this.eyeSitX = eyeSitX;
			this.eyeSitY = eyeSitY;
		}
	}

	/**
	 * Builds the animal's side view into (xs, ys): counter-clockwise, in blocks, facing +x, paws on
	 * y = 0. Returns the number of points.
	 */
	private static int outline(Kind k, float sit, float walk, float phase, float time, float crouch, float[] xs, float[] ys) {
		float[] p = k.points;
		int count = p.length / STRIDE;
		float stand = 1f - sit;
		// No stepping while sitting down or getting up
		float gait = walk * stand;
		float air = Math.max(0f, (float) Math.sin(phase));
		float bob = bob(k, sit, walk, phase);
		// Tail: a slow wave while idle, a quick bounce while walking; sitting it sweeps along the ground
		float idle = 1f - walk;
		float squash = 1f - 0.28f * crouch;
		float widen = 1f + 0.06f * crouch;
		int n = 0;

		for (int i = 0; i < count; i++) {
			int o = i * STRIDE;
			float x = (p[o] * stand + p[o + 2] * sit) * 0.01f;
			float y = (p[o + 1] * stand + p[o + 3] * sit) * 0.01f + bob;
			float tail = p[o + 4];

			if (tail > 0f) {
				float wave = (float) Math.sin(time * 2.2f - tail * 2.2f) * tail;
				float trot = (float) Math.sin(phase * 2f - tail * 1.5f) * tail * gait;
				y += stand * (wave * 0.035f * idle + trot * 0.03f);
				x += sit * wave * 0.06f;
			}

			n = put(xs, ys, n, x * widen, y * squash);

			if (i != k.rearHip && i != k.frontHip) {
				continue;
			}

			boolean front = i == k.frontHip;
			int o2 = (i + 1) * STRIDE;
			float nextX = (p[o2] * stand + p[o2 + 2] * sit) * 0.01f;
			float nextY = (p[o2 + 1] * stand + p[o2 + 3] * sit) * 0.01f + bob;
			float reachA;
			float reachB;
			float liftA;
			float liftB;
			float rest;

			if (k.hops) {
				// In the air the front paws reach forward and the hind legs stretch back
				reachA = reachB = k.stride * gait * air * (front ? 1f : -1f);
				liftA = liftB = k.lift * gait * air;
				rest = 0.12f;
			} else {
				// Front legs a quarter step behind the hind legs; the two legs of a pair take turns
				float swing = phase + (front ? (float) Math.PI * 0.5f : 0f);
				float up = (float) Math.cos(swing);
				reachA = k.stride * gait * (float) Math.sin(swing);
				reachB = -reachA;
				liftA = k.lift * gait * Math.max(0f, up);
				liftB = k.lift * gait * Math.max(0f, -up);
				rest = (0.45f - 0.3f * sit) * (1f - 0.7f * walk);
			}

			float pawX = front ? 0f : k.sitPaws * sit;
			float pawY = front ? k.sitFrontLift * sit : 0f;
			n = legs(xs, ys, n, x, y, nextX, nextY, rest, reachA, liftA, reachB, liftB, pawX, pawY, widen, squash);
		}

		// Close the loop without a doubled point
		while (n > 3 && Math.abs(xs[n - 1] - xs[0]) < 1.0e-4f && Math.abs(ys[n - 1] - ys[0]) < 1.0e-4f) {
			n--;
		}

		return n;
	}

	/** How far the body rises while walking: a hop for the bunny, a small bounce on every step for the others. */
	private static float bob(Kind k, float sit, float walk, float phase) {
		float gait = walk * (1f - sit);

		if (k.hops) {
			return k.bob * gait * Math.max(0f, (float) Math.sin(phase));
		}

		return k.bob * gait * (0.5f - 0.5f * (float) Math.cos(phase * 2f));
	}

	private static int put(float[] xs, float[] ys, int n, float x, float y) {
		if (n > 0 && Math.abs(xs[n - 1] - x) < 1.0e-4f && Math.abs(ys[n - 1] - y) < 1.0e-4f) {
			return n;
		}

		if (n >= xs.length) {
			return n;
		}

		xs[n] = x;
		ys[n] = y;
		return n + 1;
	}

	/**
	 * A pair of legs hanging from the hip between (x0, y0) and (x1, y1). Both legs are drawn as one
	 * shape, so where they cross the shadow does not get darker: the feet are sorted left to right
	 * and the gap between them closes into one leg when they meet.
	 */
	private static int legs(float[] xs, float[] ys, int n, float x0, float y0, float x1, float y1, float rest,
			float reachA, float liftA, float reachB, float liftB, float pawX, float pawY, float widen, float squash) {
		float width = Math.max(0.02f, x1 - x0);
		float center = (x0 + x1) / 2f + pawX;
		float a = center + width * rest + reachA;
		float b = center - width * rest + reachB;
		// Paws never come up past the hips (short legs, high steps)
		float top = Math.min(y0, y1) - 0.025f;
		float ay = pawY + Math.min(liftA, Math.max(0f, top - pawY));
		float by = pawY + Math.min(liftB, Math.max(0f, top - pawY));
		float left = Math.min(a, b);
		float right = Math.max(a, b);
		float leftY = a <= b ? ay : by;
		float rightY = a <= b ? by : ay;
		float half = width / 2f;
		n = put(xs, ys, n, (left - half) * widen, leftY * squash);

		if (left + half < right - half) {
			// Where the inner edges of the two legs meet
			float dx = left + half - x1;
			float dy = leftY - y1;
			float ex = right - half - x0;
			float ey = rightY - y0;
			float denom = dx * ey - dy * ex;
			float t = Math.abs(denom) < 1.0e-6f ? 1f : ((x0 - x1) * ey - (y0 - y1) * ex) / denom;
			t = Math.max(0f, Math.min(1f, t));
			n = put(xs, ys, n, (left + half) * widen, leftY * squash);
			n = put(xs, ys, n, (x1 + dx * t) * widen, (y1 + dy * t) * squash);
			n = put(xs, ys, n, (right - half) * widen, rightY * squash);
		} else {
			n = put(xs, ys, n, (left + right) / 2f * widen, Math.min(leftY, rightY) * squash);
		}

		return put(xs, ys, n, (right + half) * widen, rightY * squash);
	}

	/**
	 * Splits a counter-clockwise outline into triangles by cutting off ears, without allocating.
	 * Writes index triples into {@code tris} and returns the triangle count.
	 */
	private static int triangulate(float[] xs, float[] ys, int n, int[] work, int[] tris) {
		if (n < 3) {
			return 0;
		}

		for (int i = 0; i < n; i++) {
			work[i] = i;
		}

		int count = n;
		int made = 0;
		int misses = 0;
		int i = 0;

		while (count > 3) {
			if (i >= count) {
				i = 0;
			}

			int a = work[i == 0 ? count - 1 : i - 1];
			int b = work[i];
			int c = work[i + 1 == count ? 0 : i + 1];
			float cross = (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a]);
			// Cut flat corners right away; if nothing fits (a twisted outline), cut anyway rather than loop
			boolean flat = Math.abs(cross) < 1.0e-7f;

			if (flat || misses > count || (cross > 0f && isEar(xs, ys, work, count, a, b, c))) {
				if (!flat) {
					tris[made * 3] = a;
					tris[made * 3 + 1] = b;
					tris[made * 3 + 2] = c;
					made++;
				}

				System.arraycopy(work, i + 1, work, i, count - i - 1);
				count--;
				misses = 0;

				if (i > 0) {
					i--;
				}
			} else {
				i++;
				misses++;
			}
		}

		tris[made * 3] = work[0];
		tris[made * 3 + 1] = work[1];
		tris[made * 3 + 2] = work[2];
		return made + 1;
	}

	private static boolean isEar(float[] xs, float[] ys, int[] work, int count, int a, int b, int c) {
		for (int j = 0; j < count; j++) {
			int v = work[j];

			if (v == a || v == b || v == c) {
				continue;
			}

			float px = xs[v];
			float py = ys[v];
			float d1 = (xs[b] - xs[a]) * (py - ys[a]) - (ys[b] - ys[a]) * (px - xs[a]);
			float d2 = (xs[c] - xs[b]) * (py - ys[b]) - (ys[c] - ys[b]) * (px - xs[b]);
			float d3 = (xs[a] - xs[c]) * (py - ys[c]) - (ys[a] - ys[c]) * (px - xs[c]);

			if (d1 >= 0f && d2 >= 0f && d3 >= 0f) {
				return false;
			}
		}

		return true;
	}

	// ---- menu card --------------------------------------------------------------------------------

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		Kind k = KINDS[Math.floorMod(kind.index(), KINDS.length)];
		// Evening sky and sunlit ground; the low sun is behind you on the left, so the shadow falls away to the right
		int horizon = Math.round(y + h * 0.38f);

		for (int i = 0; i < 6; i++) {
			int band = Math.round(h * 0.05f);
			Gui2D.fill(g, x, horizon - (i + 1) * band, x + w, horizon - i * band, Colors.argb(0.2f - i * 0.033f, 0xF2A65A));
		}

		Gui2D.fill(g, x, horizon, x + w, y + h, Colors.argb(0.5f, 0xC9A46A));
		Gui2D.fillGradientSafe(g, x, horizon, x + w, horizon + Math.round(h * 0.3f), Colors.argb(0.35f, 0xFFE2A8));
		float feetX = x + w * 0.3f;
		float feetY = y + h * 0.9f;

		// Walk a few seconds, then stand, sit down and wag
		float t = time % 7f;
		float walking = smooth(0f, 0.4f, t) * (1f - smooth(3f, 3.5f, t));
		float sitting = smooth(4.4f, 5f, t) * (1f - smooth(6.5f, 7f, t));
		int n = outline(k, sitting, walking, time * 7f, time, 0f, ox, oy);
		float scale = h * 0.38f * k.scale;
		float stretch = 1.5f + 0.3f * (float) Math.sin(time * 0.4f);

		for (int i = 0; i < n; i++) {
			gx[i] = feetX + ox[i] * scale + oy[i] * stretch * scale * 0.35f;
			gz[i] = feetY - oy[i] * stretch * scale * 0.55f;
		}

		fillPolygon(g, n, Colors.argb(Math.min(1f, opacity.f() + 0.25f), color.rgb()));
		Gui2D.figure(g, feetX, feetY, 0.7f, 0xFFD8D8E0);
	}

	private static float smooth(float from, float to, float t) {
		float v = Colors.clamp01((t - from) / (to - from));
		return v * v * (3f - 2f * v);
	}

	/** Fills the outline in (gx, gz) row by row with 1px spans. */
	private void fillPolygon(GuiGraphicsExtractor g, int n, int color) {
		float top = Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;

		for (int i = 0; i < n; i++) {
			top = Math.min(top, gz[i]);
			bottom = Math.max(bottom, gz[i]);
		}

		for (int row = (int) Math.floor(top); row <= (int) Math.ceil(bottom); row++) {
			float sy = row + 0.5f;
			int found = 0;

			for (int i = 0; i < n; i++) {
				int j = i + 1 == n ? 0 : i + 1;
				float y0 = gz[i];
				float y1 = gz[j];

				if ((y0 <= sy) != (y1 <= sy) && found < spans.length) {
					spans[found++] = gx[i] + (sy - y0) / (y1 - y0) * (gx[j] - gx[i]);
				}
			}

			// Few crossings per row: insertion sort
			for (int i = 1; i < found; i++) {
				float v = spans[i];
				int j = i - 1;

				while (j >= 0 && spans[j] > v) {
					spans[j + 1] = spans[j];
					j--;
				}

				spans[j + 1] = v;
			}

			for (int i = 0; i + 1 < found; i += 2) {
				Gui2D.fill(g, Math.round(spans[i]), row, Math.round(spans[i + 1]), row + 1, color);
			}
		}
	}
}
