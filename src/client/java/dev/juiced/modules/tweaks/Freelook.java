package dev.juiced.modules.tweaks;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.Module;
import dev.juiced.core.ServerBlockList;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.Gui2D;

/**
 * Hold (or toggle) a key to look around your player in third person while you keep running
 * in the same direction. Switches itself off on servers that don't allow it (Hypixel by default).
 */
public final class Freelook extends Module {
	public static Freelook INSTANCE;

	private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Taste", 0, "Halten", "Umschalten"));
	private final SliderSetting distance = add(new SliderSetting("distance", "Abstand", 2, 8, 0.5, 4, v -> String.format(Locale.ROOT, "%.1f Blöcke", v)));
	private final BoolSetting scroll = add(new BoolSetting("scroll", "Mausrad ändert Abstand", true));
	private final BoolSetting invert = add(new BoolSetting("invert", "Maus invertieren", false));
	private final BoolSetting smoothReturn = add(new BoolSetting("smooth_return", "Weich zurückschwenken", true));
	private final ServerBlockList servers = ServerBlockList.add(this, List.of("hypixel.net"), () -> stopLooking(false));

	private boolean toggled;
	private float extraDistance;
	private double noticeUntil;

	public Freelook() {
		super("freelook", "Freelook", Category.TWEAKS, true,
				"Halte Linke Alt-Taste (änderbar unter Steuerung), um dich in der dritten Person umzuschauen, während du weiter geradeaus läufst. "
						+ "Auf Servern, die das verbieten, bleibt es aus (Hypixel ist schon eingetragen).");
		INSTANCE = this;
	}

	// ---- looking around ---------------------------------------------------------------------

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean pressed = false;

		while (JuicedClient.FREELOOK_KEY != null && JuicedClient.FREELOOK_KEY.consumeClick()) {
			pressed = true;

			if (mode.index() == 1) {
				toggled = !toggled;
			}
		}

		boolean keyDown = JuicedClient.FREELOOK_KEY != null && JuicedClient.FREELOOK_KEY.isDown();
		boolean want = player != null && minecraft.gui.screen() == null && (mode.index() == 1 ? toggled : keyDown);

		if (want && servers.blockedHere()) {
			if (pressed) {
				noticeUntil = Clock.t() + 2.0;
			}

			toggled = false;
			want = false;
		}

		if (want && !FreeCamera.controlsMouse()) {
			extraDistance = 0f;
			FreeCamera.start(FreeCamera.Owner.FREELOOK, player.getYRot(), player.getXRot(), distance.f());
		} else if (!want && FreeCamera.ownedBy(FreeCamera.Owner.FREELOOK)) {
			stopLooking(smoothReturn.on());
		}

		if (FreeCamera.ownedBy(FreeCamera.Owner.FREELOOK)) {
			FreeCamera.setDistance(distance.f() + extraDistance);
			FreeCamera.setInvert(invert.on());
		}
	}

	private void stopLooking(boolean smooth) {
		toggled = false;
		FreeCamera.stop(FreeCamera.Owner.FREELOOK, smooth);
	}

	@Override
	protected void onDisable() {
		toggled = false;
		noticeUntil = 0;
		FreeCamera.stop(FreeCamera.Owner.FREELOOK, false);
	}

	/**
	 * The menu shows Freelook with its own camera behind you (its close rear shot: body yaw - 35, pitch 12,
	 * 3.2 blocks); Freelook itself never starts while a screen is open or another camera is active.
	 */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || !cameraFits(minecraft, player.yBodyRot - 35f, 12f, 3.2f)) {
			return "Hier kein Platz hinter dir";
		}

		return null;
	}

	/**
	 * True if a third-person camera turned to (yaw, pitch) has room at {@code distance} from your eyes: nothing
	 * closer than 1.5 blocks on the way back, the same rule the menu camera uses for its shots.
	 */
	static boolean cameraFits(Minecraft minecraft, float yaw, float pitch, float distance) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return false;
		}

		Vec3 eye = player.getEyePosition();
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		// The camera sits behind the eyes, against the way it looks
		double dx = Math.sin(yawRad) * Math.cos(pitchRad) * distance;
		double dy = Math.sin(pitchRad) * distance;
		double dz = -Math.cos(yawRad) * Math.cos(pitchRad) * distance;
		Vec3 end = new Vec3(eye.x + dx, eye.y + dy, eye.z + dz);
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return true;
		}

		Vec3 at = hit.getLocation();
		double hx = at.x - eye.x;
		double hy = at.y - eye.y;
		double hz = at.z - eye.z;
		return hx * hx + hy * hy + hz * hz >= 1.5 * 1.5;
	}

	/** Called from MouseHandlerMixin. Returns true if the scroll changed the freelook distance. */
	public static boolean handleScroll(double amount) {
		Freelook module = INSTANCE;

		if (module == null || !module.isEnabled() || !module.scroll.on() || amount == 0 || !FreeCamera.ownedBy(FreeCamera.Owner.FREELOOK)) {
			return false;
		}

		float base = module.distance.f();
		module.extraDistance = Math.max(1.5f - base, Math.min(10f - base, module.extraDistance - (float) Math.signum(amount) * 0.5f));
		FreeCamera.setDistance(base + module.extraDistance);
		return true;
	}

	@Override
	public void renderHud(GuiGraphicsExtractor g, Hud hud) {
		double left = noticeUntil - Clock.t();

		if (left <= 0) {
			return;
		}

		float alpha = (float) Math.min(1.0, left / 0.4);
		String text = "Freelook ist auf diesem Server gesperrt";
		int w = Gui2D.width(text, 0.85f) + 16;
		int x = hud.centerX() - w / 2;
		int y = hud.centerY() + 22;
		Gui2D.rounded(g, x, y, x + w, y + 15, Colors.argb(0.75f * alpha, 0x0B0B10));
		Gui2D.fill(g, x + 3, y + 3, x + 5, y + 12, Colors.argb(alpha, 0xFF5A5A));
		Gui2D.centered(g, text, hud.centerX() + 2, y + 4, Colors.argb(alpha, 0xEDEDF2), false, 0.85f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cx = x + w / 2f;
		float cy = y + h / 2f + 4;
		int accent = Theme.accent();
		// Player keeps running to the right while the camera (eye) swings around
		Gui2D.figure(g, cx, cy + 10, 0.9f, 0xFFEDEDF2);
		Gui2D.fill(g, Math.round(cx + 5), Math.round(cy - 2), Math.round(cx + 13), Math.round(cy - 1), Colors.argb(0.8f, accent));
		double a = time * 1.3;
		float ex = cx + (float) Math.cos(a) * 18;
		float ey = cy - 4 + (float) Math.sin(a) * 6;
		Gui2D.ellipse(g, cx, cy - 4, 18, 6, 32, 1f, accent, 0.35f, 0, true);
		Gui2D.glowDot(g, ex, ey, 3.5f, accent, 0.9f);
		Gui2D.dot(g, ex, ey, 1.6f, 0xFF0B0B10);
	}
}
