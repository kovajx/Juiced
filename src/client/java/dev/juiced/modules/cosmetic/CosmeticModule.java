package dev.juiced.modules.cosmetic;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Module;
import dev.juiced.core.setting.ButtonSetting;
import dev.juiced.core.setting.Setting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Pose3;

/**
 * Base for cosmetics drawn on your own player. Only visible in third person (F5),
 * and only on your screen.
 *
 * <p>Every cosmetic gets three position sliders (height, forward, sideways) at the end of its
 * settings, so it can be nudged to fit skins and other cosmetics.
 */
public abstract class CosmeticModule extends Module {
	/** Top of the head above the neck pivot: 8 skin pixels at the player's 15/16 render scale. */
	protected static final float HEAD_TOP = 0.47f;
	/** Half size of the head's outer skin layer (hair, hats), half a skin pixel bigger than the head on every side. */
	protected static final float HEAD_OUTER = 0.264f;

	protected LocalPlayer player;
	/** Interpolated feet position. */
	protected double px;
	protected double py;
	protected double pz;
	/** Body and head rotation in degrees (Minecraft yaw: 0 = facing south / +Z). */
	protected float bodyYaw;
	protected float headYaw;
	protected float height;
	/** Head pitch in degrees (positive = looking down). */
	protected float headPitch;
	protected float eyeHeight;
	protected boolean crouching;
	protected float partialTick;

	// Rotation at the last two ticks, for smooth turning between ticks
	private float bodyPrevious;
	private float bodyCurrent;
	private float headPrevious;
	private float headCurrent;

	private final SliderSetting offsetUp;
	private final SliderSetting offsetForward;
	private final SliderSetting offsetSide;

	protected CosmeticModule(String id, String name, String description) {
		super(id, name, Category.COSMETICS, false, description);
		// Created before the subclass's own settings, but add() below keeps these four at the end
		offsetUp = super.add(new SliderSetting("offset_up", "Höhe", -0.5, 0.5, 0.02, 0, CosmeticModule::signed));
		offsetForward = super.add(new SliderSetting("offset_forward", "Vorne/Hinten", -0.4, 0.4, 0.02, 0, CosmeticModule::signed));
		offsetSide = super.add(new SliderSetting("offset_side", "Links/Rechts", -0.4, 0.4, 0.02, 0, CosmeticModule::signed));
		super.add(new ButtonSetting("offset_reset", () -> "Position", () -> "Zurücksetzen", this::resetPosition));
	}

	/** The cosmetic's own settings go in front of the position settings. */
	@Override
	protected <S extends Setting<?>> S add(S setting) {
		settings.add(settings.indexOf(offsetUp), setting);
		return setting;
	}

	private void resetPosition() {
		offsetUp.reset();
		offsetForward.reset();
		offsetSide.reset();
	}

	private static String signed(double value) {
		return Math.abs(value) < 0.005 ? "0" : String.format(Locale.ROOT, "%+.2f", value);
	}

	@Override
	public final void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;

		if (self == null) {
			return;
		}

		bodyPrevious = bodyCurrent;
		bodyCurrent = self.yBodyRot;
		headPrevious = headCurrent;
		headCurrent = self.yHeadRot;
		onTick(minecraft, self);
	}

	/** Per-tick logic for cosmetics that need it (physics, pets). */
	protected void onTick(Minecraft minecraft, LocalPlayer self) {
	}

	@Override
	public final void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		player = minecraft.player;

		if (player == null || player.isInvisible() || (minecraft.options.getCameraType().isFirstPerson() && !showInFirstPerson())) {
			return;
		}

		float pt = geo.partialTick();
		partialTick = pt;
		px = geo.x(player);
		py = geo.y(player);
		pz = geo.z(player);
		bodyYaw = lerpAngle(pt, bodyPrevious, bodyCurrent);
		headYaw = lerpAngle(pt, headPrevious, headCurrent);
		height = player.getBbHeight();
		headPitch = player.getXRot(pt);
		eyeHeight = player.getEyeHeight();
		crouching = player.isShiftKeyDown();
		applyOffset();
		draw(geo);
	}

	/**
	 * Moves the anchor by the position settings: up, and forward/right of where the body faces.
	 * Everything is drawn from px/py/pz (directly or through the poses below), so all parts move together.
	 */
	private void applyOffset() {
		float forward = offsetForward.f();
		float side = offsetSide.f();

		if (forward != 0f || side != 0f) {
			double yaw = Math.toRadians(bodyYaw);
			double sin = Math.sin(yaw);
			double cos = Math.cos(yaw);
			px += -sin * forward - cos * side;
			pz += cos * forward - sin * side;
		}

		py += offsetUp.f();
	}

	/** Cosmetics that should also be visible in first person (like a pet) return true. */
	protected boolean showInFirstPerson() {
		return false;
	}

	/** Pose at the neck, turned and tilted with the head. The top of the head is {@link #HEAD_TOP} above it. */
	protected Pose3 headPose() {
		return Pose3.at(px, py + eyeHeight - 0.22, pz, headYaw).pitch(headPitch);
	}

	/**
	 * Level pose above the middle of the head, at the height of its highest corner (outer skin layer
	 * included), turned with the head. For things that float over the head: they stay upright but
	 * follow the head when it turns, tilts or ducks, and never sink into it.
	 */
	protected Pose3 aboveHead() {
		Pose3 head = headPose().move(0, HEAD_TOP / 2, 0);
		double top = head.y + HEAD_OUTER * (Math.abs(head.ry) + Math.abs(head.uy) + Math.abs(head.fy));
		return Pose3.at(head.x, top, head.z, headYaw);
	}

	/** Pose at the feet, turned with the body. */
	protected Pose3 bodyPose() {
		return Pose3.at(px, py, pz, bodyYaw);
	}

	protected abstract void draw(Geo geo);

	/** Unit vector the body faces, as {x, z}. */
	protected static float[] forward(float yawDegrees) {
		double r = Math.toRadians(yawDegrees);
		return new float[] {(float) -Math.sin(r), (float) Math.cos(r)};
	}

	/** Unit vector to the player's right, as {x, z}. */
	protected static float[] right(float yawDegrees) {
		double r = Math.toRadians(yawDegrees);
		return new float[] {(float) -Math.cos(r), (float) -Math.sin(r)};
	}

	protected static float lerpAngle(float t, float from, float to) {
		float delta = ((to - from) % 360f + 540f) % 360f - 180f;
		return from + delta * t;
	}
}
