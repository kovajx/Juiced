package dev.juiced.gui.weltprobe;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.juiced.core.Clock;
import dev.juiced.render.FreeCamera;

/**
 * The Weltprobe camera: owns {@link dev.juiced.render.FreeCamera} as {@code Owner.MENU} while the menu is open and
 * glides between the channel shots ({@link Shot}). All angles are relative to how you stood when the menu opened
 * (the anchor: your body yaw for the third-person shots, your view for the first-person ones).
 *
 * <p>Moves (spec S1, S4, S11, S14): opening glides from your current view into the first shot in 350 ms. A shot
 * change from zapping (wheel, arrows) waits until the selection has rested for 250 ms; a deliberate jump
 * (click, search hit, 1-6, Backspace) moves at once. Third person to first person takes 450 ms: the distance
 * tweens down to 0.05 together with yaw and pitch, then the view switches to first person
 * ({@code FreeCamera.setEgo(true)}); the reverse on the way out. First person to first person (HIMMEL and EGO)
 * is a 400 ms pitch tween, moves between third-person shots are 500 ms orbits; a new target mid-move retargets
 * smoothly from where the camera is. NAH_FRONT sways ±10° yaw with a 7 s sine. A left-drag on the stage orbits by
 * hand (0.15° per pixel, pitch -30..60) until the next shot change; a double-click glides back to the shot.
 */
public final class MenuCamera {
	/** Extra movement on top of a third-person shot, for channels whose effect is a camera move. */
	enum Motion {
		NONE,
		/** Zen-Modus: a slow orbit around you. */
		ORBIT,
		/** Freelook: the view swings left and right behind you while you keep facing forward. */
		SWEEP
	}

	private static final float EYE = 0.05f;
	/** A third-person shot needs this much room between your eyes and the first block behind the camera. */
	private static final double MIN_ROOM = 1.5;
	private static final float ZAP_REST = 0.25f;
	private static final float OPEN_SECONDS = 0.35f;
	private static final float SWITCH_SECONDS = 0.45f;
	private static final float EGO_SECONDS = 0.4f;
	private static final float ORBIT_SECONDS = 0.5f;
	private static final float RESET_SECONDS = 0.3f;
	private static final float DRAG_DEGREES_PER_PIXEL = 0.15f;

	// How you stood when the menu opened
	private boolean anchored;
	private float anchorBody;
	private float anchorYaw;
	private float anchorPitch;
	/** Whether your own camera was first person, for the glide back when the menu closes. */
	private boolean startedFirstPerson = true;
	/** The wardrobe took the camera from us; coming back starts from its last view. */
	private boolean released;

	/** The shot the camera is at or moving to; null for the view the menu opened from (not a shot). */
	private @Nullable Shot shot;
	private float shotAimYaw = Float.NaN;
	private float shotAimPitch = Float.NaN;

	// The camera without the sway and motion offsets
	private float yaw;
	private float pitch;
	private float distance = 4f;

	private boolean moving;
	private double moveStart;
	private float moveSeconds;
	private float fromYaw;
	private float fromPitch;
	private float fromDistance;
	private float toYaw;
	private float toPitch;
	private float toDistance = 4f;
	/** Switch to first person when the current move arrives. */
	private boolean egoOnArrival;
	/** The next move is the opening glide. */
	private boolean opening;

	// A zapped shot waits here until the selection rests
	private @Nullable Shot pending;
	private float pendingAimYaw;
	private float pendingAimPitch;
	private double pendingSince;

	/** Turned by hand (world drag) until the next shot change. */
	private boolean manual;

	private float sway;
	private Motion motion = Motion.NONE;
	private float motionRate = 1f;
	private float motionWeight;
	private float orbitYaw;
	private double motionStart;

	/** Takes the camera, starting from the current view. Called again when the menu comes back from a child screen. */
	public void open() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return;
		}

		anchor(player);

		if (FreeCamera.ownedBy(FreeCamera.Owner.MENU)) {
			// Back from the colour picker: the camera never left
			return;
		}

		CameraType type = minecraft.options.getCameraType();
		boolean firstPerson;
		float startYaw;
		float startPitch;
		float startDistance;

		if (released) {
			// Back from the wardrobe: continue from its last view, so there's no cut
			firstPerson = false;
			startYaw = FreeCamera.yaw();
			startPitch = FreeCamera.pitch();
			startDistance = FreeCamera.distance();
		} else if (FreeCamera.active()) {
			// Freelook still holds the camera, or our own glide back hasn't finished: carry on from there
			firstPerson = type.isFirstPerson();
			startYaw = FreeCamera.yaw();
			startPitch = FreeCamera.pitch();
			startDistance = firstPerson ? EYE : FreeCamera.distance();
		} else {
			firstPerson = type.isFirstPerson();
			startedFirstPerson = firstPerson;
			startYaw = player.getYRot() + (type.isMirrored() ? 180f : 0f);
			startPitch = type.isMirrored() ? -player.getXRot() : player.getXRot();
			startDistance = firstPerson ? EYE : 4f;
		}

		released = false;
		FreeCamera.start(FreeCamera.Owner.MENU, startYaw, startPitch, startDistance);

		if (firstPerson) {
			FreeCamera.setEgo(true);
		}

		yaw = startYaw;
		pitch = startPitch;
		distance = startDistance;
		toYaw = yaw;
		toPitch = pitch;
		toDistance = distance;
		// From your own first-person view the camera already is at the EGO shot
		shot = firstPerson ? Shot.EGO : null;
		shotAimYaw = Float.NaN;
		shotAimPitch = Float.NaN;
		moving = false;
		egoOnArrival = false;
		pending = null;
		manual = false;
		opening = true;
		sway = 0f;
		motionWeight = 0f;
		orbitYaw = 0f;
		push();
	}

	/**
	 * Aims at a shot. {@code aimYaw} and {@code aimPitch} are the channel's HIMMEL direction (NaN = default).
	 * Without {@code deliberate} the move waits until nothing new was aimed for 250 ms. KEEP leaves the camera where it is.
	 */
	public void aim(Shot shot, float aimYaw, float aimPitch, boolean deliberate) {
		if (shot == Shot.KEEP) {
			// Also drops a zapped shot that hasn't started yet: the camera stays where it is
			pending = null;
			opening = false;
			return;
		}

		if (!deliberate) {
			pending = shot;
			pendingAimYaw = aimYaw;
			pendingAimPitch = aimPitch;
			pendingSince = Clock.t();
			return;
		}

		pending = null;
		moveTo(shot, aimYaw, aimPitch);
	}

	/** Every frame: advances the glide and hands the values to FreeCamera. */
	public void frame() {
		if (!FreeCamera.ownedBy(FreeCamera.Owner.MENU)) {
			return;
		}

		double now = Clock.t();
		float dt = Clock.dt();

		if (pending != null && now - pendingSince >= ZAP_REST) {
			Shot next = pending;
			pending = null;
			moveTo(next, pendingAimYaw, pendingAimPitch);
		}

		if (moving) {
			float t = (float) Math.min(1.0, (now - moveStart) / moveSeconds);
			float e = t * t * (3f - 2f * t);
			yaw = fromYaw + (toYaw - fromYaw) * e;
			pitch = fromPitch + (toPitch - fromPitch) * e;
			distance = fromDistance + (toDistance - fromDistance) * e;

			if (t >= 1f) {
				moving = false;

				if (egoOnArrival) {
					egoOnArrival = false;
					FreeCamera.setEgo(true);
				}
			}
		}

		boolean outside = !FreeCamera.ego();
		sway = Clock.approach(sway, shot == Shot.NAH_FRONT && outside && !manual ? 1f : 0f, 3f);
		boolean moves = motion != Motion.NONE && outside && !manual;
		motionWeight = Clock.approach(motionWeight, moves ? 1f : 0f, 3f);

		if (moves && motion == Motion.ORBIT) {
			// Like Zen-Modus, four times faster so a short look shows it moving
			orbitYaw = wrap(orbitYaw + dt * 18f * motionRate * motionWeight);
		} else {
			// Back to the shot the short way (a whole turn more or less looks the same)
			orbitYaw = Clock.approach(wrap(orbitYaw), 0f, 2.5f);
		}

		push();
	}

	/** Left-drag on the empty stage: orbit by hand (third-person shots only). */
	public void drag(double dx, double dy) {
		if (!FreeCamera.ownedBy(FreeCamera.Owner.MENU) || FreeCamera.ego() || egoOnArrival) {
			return;
		}

		settle();
		manual = true;
		yaw += (float) dx * DRAG_DEGREES_PER_PIXEL;
		pitch = Math.max(-30f, Math.min(60f, pitch + (float) dy * DRAG_DEGREES_PER_PIXEL));
		push();
	}

	/** Double-click on the stage: glide back to the current shot. */
	public void resetDrag() {
		if (!manual) {
			return;
		}

		manual = false;
		startMove(toYaw, toPitch, toDistance, RESET_SECONDS);
	}

	/** True if a third-person shot has room here (no block within 1.5 blocks along the way to the camera). First-person shots always fit. */
	public boolean fits(Shot shot) {
		if (shot.firstPerson || shot == Shot.KEEP) {
			return true;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null) {
			return true;
		}

		anchor(player);
		float[] target = target(shot, Float.NaN, Float.NaN);
		return room(player, target[0], target[1], target[2]) >= MIN_ROOM;
	}

	/** The shot the camera is at or moving to. */
	public Shot shot() {
		return shot != null ? shot : Shot.KEEP;
	}

	/** True while the camera shows you from outside (third person), so cosmetics are visible. */
	public boolean thirdPerson() {
		return FreeCamera.ownedBy(FreeCamera.Owner.MENU) && !FreeCamera.ego() && !egoOnArrival;
	}

	/** Closing the menu: gives the camera back with the 280 ms glide into your normal view. */
	public void close() {
		pending = null;
		moving = false;
		egoOnArrival = false;

		if (!FreeCamera.ownedBy(FreeCamera.Owner.MENU)) {
			return;
		}

		if (FreeCamera.ego() && !startedFirstPerson) {
			// Your own camera is third person: glide out of the eyes instead of cutting at the end
			FreeCamera.setEgo(false);
			FreeCamera.setDistanceFree(EYE);
		}

		FreeCamera.stop(FreeCamera.Owner.MENU, true);
	}

	/** Lets go without moving, because another screen takes the camera next (the wardrobe). */
	public void release() {
		pending = null;
		moving = false;
		egoOnArrival = false;
		released = FreeCamera.ownedBy(FreeCamera.Owner.MENU);
	}

	// ---- for the probe controller ----------------------------------------------------------------

	/** Extra movement for channels whose effect is a camera move; {@code rate} scales its speed. */
	void motion(Motion motion, float rate) {
		if (this.motion != motion) {
			motionStart = Clock.t();
		}

		this.motion = motion;
		this.motionRate = rate;
	}

	/** The camera yaw of the BÜHNE shot, which the straw dummy is placed for. */
	float stageYaw() {
		ensureAnchor();
		return anchorBody + 215f;
	}

	/** Your view yaw when the menu opened (the EGO shot). */
	float viewYaw() {
		ensureAnchor();
		return anchorYaw;
	}

	// ---- moves -------------------------------------------------------------------------------

	private void moveTo(Shot next, float aimYaw, float aimPitch) {
		if (!FreeCamera.ownedBy(FreeCamera.Owner.MENU)) {
			return;
		}

		boolean openingMove = opening;
		opening = false;
		boolean sameAim = same(aimYaw, shotAimYaw) && same(aimPitch, shotAimPitch);

		if (next == shot && sameAim) {
			// Zapping between channels of the same shot: nothing moves, a hand-turned view stays
			return;
		}

		float[] target = target(next, aimYaw, aimPitch);
		boolean wasFirstPerson = FreeCamera.ego() || egoOnArrival;
		float seconds;

		if (openingMove) {
			seconds = OPEN_SECONDS;
		} else if (next.firstPerson && wasFirstPerson) {
			seconds = EGO_SECONDS;
		} else if (next.firstPerson || wasFirstPerson) {
			seconds = SWITCH_SECONDS;
		} else {
			seconds = ORBIT_SECONDS;
		}

		if (!next.firstPerson && FreeCamera.ego()) {
			// Out of first person: switch to the camera right at your eyes, then pull back
			FreeCamera.setEgo(false);
			distance = EYE;
		}

		shot = next;
		shotAimYaw = aimYaw;
		shotAimPitch = aimPitch;
		manual = false;
		startMove(target[0], target[1], target[2], seconds);
		egoOnArrival = next.firstPerson && !FreeCamera.ego();
	}

	/** Starts a glide from where the camera is right now (sway and motion included) to the given values. */
	private void startMove(float targetYaw, float targetPitch, float targetDistance, float seconds) {
		settle();
		fromYaw = yaw;
		fromPitch = pitch;
		fromDistance = distance;
		// The short way round
		toYaw = yaw + wrap(targetYaw - yaw);
		toPitch = targetPitch;
		toDistance = targetDistance;
		moveStart = Clock.t();
		moveSeconds = seconds;
		moving = true;
		egoOnArrival = false;
	}

	/** Folds the sway and motion offsets into the camera, so a new move or a drag starts exactly where it looks. */
	private void settle() {
		float[] offset = offsets();
		yaw += offset[0];
		pitch += offset[1];
		sway = 0f;
		motionWeight = 0f;
		orbitYaw = 0f;
		moving = false;
	}

	/** Yaw, pitch and distance of a shot. */
	private float[] target(Shot target, float aimYaw, float aimPitch) {
		return switch (target) {
			case BUEHNE -> new float[] { anchorBody + 215f, 14f, 4.2f };
			case NAH_FRONT -> new float[] { anchorBody + 215f, 10f, 3.2f };
			case NAH_REAR -> new float[] { anchorBody - 35f, 12f, 3.2f };
			case NAH_TOP -> new float[] { anchorBody + 215f, 40f, 3.2f };
			case HIMMEL -> new float[] { Float.isNaN(aimYaw) ? anchorYaw : aimYaw, Float.isNaN(aimPitch) ? -50f : aimPitch, EYE };
			case EGO -> new float[] { anchorYaw, anchorPitch, EYE };
			case KEEP -> new float[] { yaw, pitch, distance };
		};
	}

	private float[] offsets() {
		double t = Clock.t();
		float offsetYaw = sway * 10f * (float) Math.sin(t * Math.PI * 2 / 7.0);
		float offsetPitch = 0f;

		if (motionWeight > 0f) {
			double since = t - motionStart;

			if (motion == Motion.SWEEP) {
				offsetYaw += motionWeight * 60f * (float) Math.sin(since * Math.PI * 2 / 6.0);
			} else if (motion == Motion.ORBIT) {
				offsetPitch += motionWeight * 5f * (float) Math.sin(since * 0.5);
			}
		}

		offsetYaw += orbitYaw;

		return new float[] { offsetYaw, offsetPitch };
	}

	/** Hands this frame's values to FreeCamera. */
	private void push() {
		if (!FreeCamera.ownedBy(FreeCamera.Owner.MENU)) {
			return;
		}

		float[] offset = offsets();
		FreeCamera.setRotation(yaw + offset[0], pitch + offset[1]);
		FreeCamera.setDistanceFree(distance);
	}

	// ---- anchor and room -----------------------------------------------------------------------

	private void anchor(LocalPlayer player) {
		if (!anchored) {
			anchored = true;
			anchorBody = player.yBodyRot;
			anchorYaw = player.getYRot();
			anchorPitch = player.getXRot();
		}
	}

	private void ensureAnchor() {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null) {
			anchor(player);
		}
	}

	/**
	 * Free distance from your eyes towards a third-person camera at this rotation, up to {@code wanted}.
	 * Casts from the eye and its eight corners 0.1 around it, like the game's own camera does.
	 */
	private static double room(LocalPlayer player, float yaw, float pitch, float wanted) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null) {
			return wanted;
		}

		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		// The camera sits behind its look direction
		double backX = Math.sin(yawRad) * Math.cos(pitchRad);
		double backY = Math.sin(pitchRad);
		double backZ = -Math.cos(yawRad) * Math.cos(pitchRad);
		Vec3 eye = player.getEyePosition();
		double best = wanted;

		for (int corner = 0; corner < 8; corner++) {
			double ox = ((corner & 1) * 2 - 1) * 0.1;
			double oy = ((corner >> 1 & 1) * 2 - 1) * 0.1;
			double oz = ((corner >> 2 & 1) * 2 - 1) * 0.1;
			Vec3 from = new Vec3(eye.x + ox, eye.y + oy, eye.z + oz);
			Vec3 to = new Vec3(from.x + backX * wanted, from.y + backY * wanted, from.z + backZ * wanted);
			BlockHitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

			if (hit != null && hit.getType() != HitResult.Type.MISS) {
				best = Math.min(best, hit.getLocation().distanceTo(from));
			}
		}

		return best;
	}

	private static boolean same(float a, float b) {
		return Float.isNaN(a) ? Float.isNaN(b) : a == b;
	}

	private static float wrap(float degrees) {
		float d = degrees % 360f;

		if (d >= 180f) {
			d -= 360f;
		} else if (d < -180f) {
			d += 360f;
		}

		return d;
	}
}
