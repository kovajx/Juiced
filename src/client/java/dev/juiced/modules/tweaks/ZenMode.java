package dev.juiced.modules.tweaks;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/**
 * A screensaver inside the game: stand still for a while and the HUD fades away while the camera
 * slowly circles around you. Any key, click or mouse movement brings everything back.
 */
public final class ZenMode extends Module {
	public static ZenMode INSTANCE;

	private final SliderSetting wait = add(new SliderSetting("wait", "Nach", 15, 180, 5, 45, v -> Math.round(v) + "s Stillstand"));
	private final BoolSetting orbit = add(new BoolSetting("orbit", "Kamera kreist langsam", true));
	private final BoolSetting hideHud = add(new BoolSetting("hide_hud", "HUD ausblenden", true));
	private final SliderSetting speed = add(SliderSetting.percent("speed", "Tempo", 0.3, 2.0, 1.0));

	private double lastActivity = Clock.t();
	private boolean active;
	private float lastYaw;
	private float lastPitch;
	private double lastX;
	private double lastY;
	private double lastZ;
	private int lastSlot = -1;
	// Mouse movement over the last moment; tiny sensor jitter decays away instead of waking you
	private double mouseTravel;
	private double mouseTravelTime;

	public ZenMode() {
		super("zen_mode", "Zen-Modus", Category.TWEAKS, false,
				"Wie ein Bildschirmschoner im Spiel: Stehst du eine Weile still, verschwindet das HUD und die Kamera kreist langsam um dich. Jede Taste oder Mausbewegung holt alles zurück.");
		INSTANCE = this;
	}

	/** Called on any input (clicks, scrolling, keys). */
	public static void activity() {
		ZenMode module = INSTANCE;

		if (module == null) {
			return;
		}

		module.lastActivity = Clock.t();

		if (module.active) {
			module.wake();
		}
	}

	/**
	 * Called from EntityMixin with the mouse movement while the Zen camera is active.
	 * Minecraft calls this every frame, also with zero movement, so only real movement counts.
	 */
	public static void mouseMoved(double dx, double dy) {
		ZenMode module = INSTANCE;

		if (module == null) {
			return;
		}

		double now = Clock.t();
		// Halve the remembered movement every 0.25 s
		module.mouseTravel *= Math.pow(0.5, Math.max(0.0, now - module.mouseTravelTime) / 0.25);
		module.mouseTravelTime = now;
		module.mouseTravel += Math.abs(dx) + Math.abs(dy);

		// About a few pixels of deliberate movement
		if (module.mouseTravel > 6.0) {
			module.mouseTravel = 0.0;
			activity();
		}
	}

	/** True while the HUD should be hidden. */
	public static boolean hidesHud() {
		ZenMode module = INSTANCE;
		return module != null && module.isEnabled() && module.active && module.hideHud.on();
	}

	private static boolean anyKeyDown(Options options) {
		KeyMapping[] keys = {options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift,
				options.keySprint, options.keyAttack, options.keyUse};

		for (KeyMapping key : keys) {
			if (key.isDown()) {
				return true;
			}
		}

		return (JuicedClient.FREELOOK_KEY != null && JuicedClient.FREELOOK_KEY.isDown()) || (JuicedClient.ZOOM_KEY != null && JuicedClient.ZOOM_KEY.isDown());
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return;
		}

		// Walking counts; gentle bobbing in water or on a boat doesn't
		double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ) + Math.max(0.0, Math.abs(player.getY() - lastY) - 0.2);
		boolean looked = !active && (Math.abs(player.getYRot() - lastYaw) > 0.01f || Math.abs(player.getXRot() - lastPitch) > 0.01f);
		int slot = player.getInventory().getSelectedSlot();
		boolean otherCamera = FreeCamera.controlsMouse() && !FreeCamera.ownedBy(FreeCamera.Owner.ZEN);
		boolean busy = minecraft.gui.screen() != null || anyKeyDown(minecraft.options) || player.hurtTime > 0 || moved > 0.02 || looked
				|| slot != lastSlot || otherCamera;
		lastX = player.getX();
		lastY = player.getY();
		lastZ = player.getZ();
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();
		lastSlot = slot;

		if (busy) {
			activity();
			return;
		}

		if (!active && Clock.t() - lastActivity > wait.f()) {
			start(player);
		}
	}

	private void start(LocalPlayer player) {
		active = true;
		mouseTravel = 0.0;

		if (orbit.on() && !FreeCamera.controlsMouse()) {
			FreeCamera.start(FreeCamera.Owner.ZEN, player.getYRot(), 16f, 4.5f);
		}
	}

	private void wake() {
		active = false;
		FreeCamera.stop(FreeCamera.Owner.ZEN, true);
	}

	@Override
	public void renderWorld(Geo geo) {
		if (active && FreeCamera.ownedBy(FreeCamera.Owner.ZEN)) {
			float time = Clock.tf();
			FreeCamera.setRotation(FreeCamera.yaw() + Clock.dt() * 4.5f * speed.f(), 16f + (float) Math.sin(time * 0.12f) * 6f);
		}
	}

	@Override
	protected void onDisable() {
		if (active) {
			wake();
		}
	}

	/**
	 * The menu shows Zen with its own camera in front of you (body yaw + 215, pitch 14, 4.2 blocks). Zen itself
	 * stays asleep in the menu: an open screen counts as activity, and it never takes a camera someone else holds.
	 */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || !Freelook.cameraFits(minecraft, player.yBodyRot + 215f, 14f, 4.2f)) {
			return "Hier kein Platz zum Kreisen";
		}

		return null;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cx = x + w / 2f;
		float cy = y + h / 2f + 6;
		int accent = Theme.accent();
		Gui2D.figure(g, cx, cy + 10, 0.8f, 0xFFEDEDF2);
		double a = time * 0.5;
		Gui2D.ellipse(g, cx, cy - 6, 22, 7, 40, 1f, accent, 0.3f, 0, true);
		Gui2D.glowDot(g, cx + (float) Math.cos(a) * 22, cy - 6 + (float) Math.sin(a) * 7, 3, accent, 0.9f);
		float breathe = 0.5f + 0.5f * (float) Math.sin(time * 1.2f);
		Gui2D.centered(g, "z z z", cx + 14, y + 8 - breathe * 2, Colors.argb(0.4f + 0.4f * breathe, 0xEDEDF2), false, 0.7f);
	}
}
