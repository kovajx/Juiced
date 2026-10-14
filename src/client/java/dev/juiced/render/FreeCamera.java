package dev.juiced.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import dev.juiced.core.Clock;

/**
 * Lets the third-person camera look around independently of where the player faces.
 * Used by Freelook (look around while running) and the wardrobe (orbit around yourself).
 *
 * <p>Only the camera turns. The player's real rotation, and everything the server sees, stay unchanged.
 */
public final class FreeCamera {
	public enum Owner {
		FREELOOK,
		WARDROBE,
		ZEN
	}

	private static final float RETURN_SECONDS = 0.28f;

	private static @Nullable Owner owner;
	private static float yaw;
	private static float pitch;
	private static float distance = 4f;
	private static float invertY = 1f;
	private static @Nullable CameraType previous;

	// After letting go, the camera swings back to the player's view instead of snapping
	private static boolean returning;
	private static float returnProgress;

	private FreeCamera() {
	}

	/** True while mouse movement turns the camera instead of the player. */
	public static boolean controlsMouse() {
		return owner != null;
	}

	/** True while the camera rotation is overridden (including the swing back). */
	public static boolean active() {
		return owner != null || returning;
	}

	public static boolean ownedBy(Owner who) {
		return owner == who;
	}

	public static void start(Owner who, float startYaw, float startPitch, float startDistance) {
		Minecraft minecraft = Minecraft.getInstance();

		if (owner == null && !returning) {
			previous = minecraft.options.getCameraType();
		}

		owner = who;
		returning = false;
		yaw = startYaw;
		pitch = clampPitch(startPitch);
		distance = startDistance;
		invertY = 1f;

		if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	/** Gives the camera back. With {@code smooth} it swings back to the player's view first. */
	public static void stop(Owner who, boolean smooth) {
		if (owner != who) {
			return;
		}

		owner = null;

		if (smooth) {
			returning = true;
			returnProgress = 0f;
		} else {
			finish();
		}
	}

	/** Drops everything immediately (leaving the world, mod reload). */
	public static void reset() {
		if (owner != null || returning) {
			owner = null;
			finish();
		}
	}

	private static void finish() {
		returning = false;
		Minecraft minecraft = Minecraft.getInstance();

		if (previous != null && minecraft.options.getCameraType() != previous) {
			minecraft.options.setCameraType(previous);
		}

		previous = null;
	}

	public static void setRotation(float newYaw, float newPitch) {
		yaw = newYaw;
		pitch = clampPitch(newPitch);
	}

	public static void setDistance(float newDistance) {
		distance = Math.max(1f, Math.min(12f, newDistance));
	}

	public static void setInvert(boolean invert) {
		invertY = invert ? -1f : 1f;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static float distance() {
		return distance;
	}

	/** Called from EntityMixin with the mouse movement that would have turned the player. */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = clampPitch(pitch + (float) dy * 0.15f * invertY);
	}

	// ---- camera hooks (CameraMixin) -------------------------------------------------------

	/** Once per frame, before the camera is placed. */
	public static void frame() {
		if (owner != null) {
			Minecraft minecraft = Minecraft.getInstance();

			// F5 while looking around would otherwise put the camera in front of the player
			if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (returning) {
			if (returnProgress >= 1f) {
				finish();
			} else {
				returnProgress = Math.min(1f, returnProgress + Math.max(0.001f, Clock.dt()) / RETURN_SECONDS);
			}
		}
	}

	public static float cameraYaw(float original) {
		if (owner != null) {
			return yaw;
		}

		if (returning) {
			float delta = ((original - yaw) % 360f + 540f) % 360f - 180f;
			return yaw + delta * ease(returnProgress);
		}

		return original;
	}

	public static float cameraPitch(float original) {
		if (owner != null) {
			return pitch;
		}

		if (returning) {
			return pitch + (original - pitch) * ease(returnProgress);
		}

		return original;
	}

	public static float cameraDistance(float original) {
		if (owner != null) {
			return distance;
		}

		if (returning) {
			// Coming back into first person: pull the camera in so the switch isn't a jump
			float target = previous != null && previous.isFirstPerson() ? 0.05f : original;
			return distance + (target - distance) * ease(returnProgress);
		}

		return original;
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	private static float clampPitch(float value) {
		return Math.max(-90f, Math.min(90f, value));
	}
}
