package dev.juiced.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Module;
import dev.juiced.render.Geo;
import dev.juiced.render.Pose3;

/**
 * Base for cosmetics drawn on your own player. Only visible in third person (F5),
 * and only on your screen.
 */
public abstract class CosmeticModule extends Module {
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

	protected CosmeticModule(String id, String name, String description) {
		super(id, name, Category.COSMETICS, false, description);
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
		draw(geo);
	}

	/** Cosmetics that should also be visible in first person (like a pet) return true. */
	protected boolean showInFirstPerson() {
		return false;
	}

	/** Pose at the neck, turned and tilted with the head. Head is about 0.47 blocks big. */
	protected Pose3 headPose() {
		return Pose3.at(px, py + eyeHeight - 0.22, pz, headYaw).pitch(headPitch);
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
