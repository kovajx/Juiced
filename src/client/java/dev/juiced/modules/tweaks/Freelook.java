package dev.juiced.modules.tweaks;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Config;
import dev.juiced.core.Hud;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ButtonSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.core.setting.StringListSetting;
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
	private final StringListSetting blocked = add(new StringListSetting("blocked_servers", List.of("hypixel.net")));
	private final ButtonSetting serverButton = add(new ButtonSetting("server", this::serverText, this::serverButtonText, this::toggleCurrentServer));
	private final ButtonSetting listButton = add(new ButtonSetting("server_list", this::listText, () -> "Standard", this::resetList));

	private boolean toggled;
	private float extraDistance;
	private double noticeUntil;

	public Freelook() {
		super("freelook", "Freelook", Category.TWEAKS, true,
				"Halte Linke Alt-Taste (änderbar unter Steuerung), um dich in der dritten Person umzuschauen, während du weiter geradeaus läufst. "
						+ "Auf Servern, die das verbieten, bleibt es aus (Hypixel ist schon eingetragen).");
		INSTANCE = this;
	}

	// ---- server block list ------------------------------------------------------------------

	/** Host of the server you're on, lower case without port; null in singleplayer. */
	private static @Nullable String currentHost() {
		ServerData server = Minecraft.getInstance().getCurrentServer();

		if (server == null || server.ip == null) {
			return null;
		}

		return normalize(server.ip);
	}

	static String normalize(String address) {
		String host = address.trim().toLowerCase(Locale.ROOT);

		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}

		// Strip a port, but leave IPv6 addresses alone
		int colon = host.lastIndexOf(':');

		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}

		return host;
	}

	/** True if {@code host} is a listed server or one of its subdomains (mc.hypixel.net matches hypixel.net). */
	static boolean matches(String host, List<String> entries) {
		for (String entry : entries) {
			String blockedHost = normalize(entry);

			if (!blockedHost.isEmpty() && (host.equals(blockedHost) || host.endsWith("." + blockedHost))) {
				return true;
			}
		}

		return false;
	}

	public boolean blockedHere() {
		String host = currentHost();
		return host != null && matches(host, blocked.get());
	}

	private String serverText() {
		String host = currentHost();

		if (host == null) {
			return "Einzelspieler: immer erlaubt";
		}

		return host + (blockedHere() ? ": gesperrt" : ": erlaubt");
	}

	private String serverButtonText() {
		String host = currentHost();

		if (host == null) {
			return "—";
		}

		return blockedHere() ? "Erlauben" : "Sperren";
	}

	private void toggleCurrentServer() {
		String host = currentHost();

		if (host == null) {
			return;
		}

		List<String> list = blocked.get();

		if (blockedHere()) {
			// Remove every entry that covers this server
			list.removeIf(entry -> {
				String blockedHost = normalize(entry);
				return host.equals(blockedHost) || host.endsWith("." + blockedHost);
			});
		} else {
			list.add(host);
			stopLooking(false);
		}

		Config.save();
	}

	private String listText() {
		List<String> list = blocked.get();
		return list.isEmpty() ? "Gesperrt: keine Server" : "Gesperrt: " + String.join(", ", list);
	}

	private void resetList() {
		blocked.reset();
		Config.save();
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

		if (want && blockedHere()) {
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
