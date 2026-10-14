package dev.juiced.modules.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.render.FrameCounter;
import dev.juiced.render.Gui2D;

public final class InfoHud extends HudModule {
	private static final String[] DIRECTIONS = {"Süd", "Südwest", "West", "Nordwest", "Nord", "Nordost", "Ost", "Südost"};
	private static final String[] AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Anordnung", 0, "Liste", "Eine Zeile"));
	private final BoolSetting fps = add(new BoolSetting("fps", "FPS", true));
	private final BoolSetting ping = add(new BoolSetting("ping", "Ping", true));
	private final BoolSetting coords = add(new BoolSetting("coords", "Koordinaten", true));
	private final BoolSetting direction = add(new BoolSetting("direction", "Richtung", true));
	private final BoolSetting speed = add(new BoolSetting("speed", "Tempo", false));
	private final BoolSetting clock = add(new BoolSetting("clock", "Uhrzeit", false));
	private final BoolSetting server = add(new BoolSetting("server", "Server", false));

	private final List<String[]> lines = new ArrayList<>();
	private double lastX;
	private double lastZ;
	private boolean hasLast;
	private float blocksPerSecond;

	public InfoHud() {
		super("info_hud", "Info", false, 0f, 0f,
				"FPS, Ping, Koordinaten, Blickrichtung, Tempo, Uhrzeit und Server, jeweils einzeln an- und ausschaltbar.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null) {
			hasLast = false;
			return;
		}

		if (hasLast) {
			double dx = player.getX() - lastX;
			double dz = player.getZ() - lastZ;
			float now = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
			blocksPerSecond = blocksPerSecond * 0.6f + now * 0.4f;
		}

		lastX = player.getX();
		lastZ = player.getZ();
		hasLast = true;
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		lines.clear();
		LocalPlayer player = hud.minecraft.player;

		if (player == null) {
			return false;
		}

		if (fps.on()) {
			lines.add(new String[] {"FPS", String.valueOf(FrameCounter.fps())});
		}

		if (ping.on()) {
			lines.add(new String[] {"Ping", ping(hud.minecraft, player)});
		}

		if (coords.on()) {
			lines.add(new String[] {"XYZ", String.format("%d %d %d", (int) Math.floor(player.getX()), (int) Math.floor(player.getY()), (int) Math.floor(player.getZ()))});
		}

		if (direction.on()) {
			float yaw = ((player.getYRot() % 360f) + 360f) % 360f;
			int index = Math.round(yaw / 45f) % 8;
			lines.add(new String[] {"Richtung", DIRECTIONS[index] + " (" + AXES[index] + ")"});
		}

		if (speed.on()) {
			lines.add(new String[] {"Tempo", String.format("%.1f b/s", blocksPerSecond)});
		}

		if (clock.on()) {
			lines.add(new String[] {"Uhr", LocalTime.now().format(TIME)});
		}

		if (server.on()) {
			ServerData data = hud.minecraft.getCurrentServer();
			lines.add(new String[] {"Server", data == null ? "Einzelspieler" : data.ip});
		}

		if (lines.isEmpty()) {
			return false;
		}

		if (layout.index() == 1) {
			int w = 8;

			for (String[] line : lines) {
				w += Gui2D.width(line[0] + " ", 0.85f) + Gui2D.width(line[1], 0.85f) + 10;
			}

			width = w - 6;
			height = 14;
		} else {
			int labelWidth = 0;
			int valueWidth = 0;

			for (String[] line : lines) {
				labelWidth = Math.max(labelWidth, Gui2D.width(line[0], 0.85f));
				valueWidth = Math.max(valueWidth, Gui2D.width(line[1], 0.85f));
			}

			width = labelWidth + valueWidth + 18;
			height = lines.size() * 10 + 5;
		}

		return true;
	}

	private static String ping(Minecraft minecraft, LocalPlayer player) {
		ClientPacketListener connection = minecraft.getConnection();

		if (connection == null) {
			return "-";
		}

		PlayerInfo info = connection.getPlayerInfo(player.getUUID());
		return info == null ? "-" : info.getLatency() + " ms";
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		Gui2D.panel(g, 0, 0, width, height);
		int accent = Colors.argb(1f, Theme.accent());

		if (layout.index() == 1) {
			float x = 6;

			for (String[] line : lines) {
				Gui2D.text(g, line[0] + " ", x, 3.5f, accent, true, 0.85f);
				x += Gui2D.width(line[0] + " ", 0.85f);
				Gui2D.text(g, line[1], x, 3.5f, 0xFFFFFFFF, true, 0.85f);
				x += Gui2D.width(line[1], 0.85f) + 10;
			}

			return;
		}

		int labelWidth = 0;

		for (String[] line : lines) {
			labelWidth = Math.max(labelWidth, Gui2D.width(line[0], 0.85f));
		}

		for (int i = 0; i < lines.size(); i++) {
			String[] line = lines.get(i);
			Gui2D.text(g, line[0], 6, 3 + i * 10, accent, true, 0.85f);
			Gui2D.text(g, line[1], 12 + labelWidth, 3 + i * 10, 0xFFFFFFFF, true, 0.85f);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		String[][] rows = {{"FPS", String.valueOf(240 + (int) (Math.sin(time) * 12))}, {"Ping", "23 ms"}, {"XYZ", "128 64 -903"}, {"Richtung", "Nord"}};
		int top = y + (h - rows.length * 11) / 2;
		int accent = Colors.argb(1f, Theme.accent());

		for (int i = 0; i < rows.length; i++) {
			Gui2D.text(g, rows[i][0], x + 14, top + i * 11, accent, true, 0.8f);
			Gui2D.text(g, rows[i][1], x + 56, top + i * 11, 0xFFFFFFFF, true, 0.8f);
		}
	}
}
