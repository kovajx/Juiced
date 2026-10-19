package dev.juiced.modules.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

public final class SessionStats extends HudModule {
	private final BoolSetting playersOnly = add(new BoolSetting("players_only", "Nur Spieler-Kills", true));
	private final BoolSetting showTime = add(new BoolSetting("time", "Spielzeit", true));
	private final BoolSetting showStreak = add(new BoolSetting("streak", "Killstreak", true));
	private final BoolSetting showPops = add(new BoolSetting("pops", "Eigene Totem-Pops", true));
	private final BoolSetting resetOnWorld = add(new BoolSetting("reset_on_world", "Bei Weltwechsel zurücksetzen", false));

	private final List<String[]> lines = new ArrayList<>();
	private int kills;
	private int deaths;
	private int streak;
	private int bestStreak;
	private int pops;
	private long playTicks;
	private Object lastLevel;

	public SessionStats() {
		super("session_stats", "Session-Stats", false, 0f, 0.15f,
				"Kills, Tode, K/D, Killstreak, eigene Totem-Pops und Spielzeit seit dem Start.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		if (minecraft.level != lastLevel) {
			if (lastLevel != null && resetOnWorld.on()) {
				reset();
			}

			lastLevel = minecraft.level;
		}

		// A try-out in the menu is no session: count only while it is switched on
		if (minecraft.player != null && !minecraft.isPaused() && isSwitchedOn()) {
			playTicks++;
		}
	}

	private void reset() {
		kills = 0;
		deaths = 0;
		streak = 0;
		bestStreak = 0;
		pops = 0;
		playTicks = 0;
	}

	@Override
	public void onKill(LivingEntity target) {
		if (!isSwitchedOn() || (playersOnly.on() && !(target instanceof Player))) {
			return;
		}

		kills++;
		streak++;
		bestStreak = Math.max(bestStreak, streak);
	}

	@Override
	public void onDeath() {
		if (!isSwitchedOn()) {
			return;
		}

		deaths++;
		streak = 0;
	}

	@Override
	public void onTotemPop(LivingEntity entity, int count) {
		if (isSwitchedOn() && entity == Minecraft.getInstance().player) {
			pops++;
		}
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		lines.clear();
		// Never counted anything yet (a try-out in the menu, or the HUD editor): show what a session looks like
		boolean sample = editing && kills == 0 && deaths == 0 && pops == 0 && playTicks == 0;
		int k = sample ? 7 : kills;
		int d = sample ? 3 : deaths;
		String kd = d == 0 ? String.valueOf(k) : String.format("%.2f", k / (float) d);
		lines.add(new String[] {"Kills", String.valueOf(k)});
		lines.add(new String[] {"Tode", String.valueOf(d)});
		lines.add(new String[] {"K/D", kd});

		if (showStreak.on()) {
			lines.add(new String[] {"Streak", sample ? "2 (Best 4)" : streak + " (Best " + bestStreak + ")"});
		}

		if (showPops.on()) {
			lines.add(new String[] {"Pops", String.valueOf(sample ? 1 : pops)});
		}

		if (showTime.on()) {
			long seconds = sample ? 47 * 60 + 12 : playTicks / 20;
			lines.add(new String[] {"Zeit", String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)});
		}

		int labelWidth = 0;
		int valueWidth = 0;

		for (String[] line : lines) {
			labelWidth = Math.max(labelWidth, Gui2D.width(line[0], 0.85f));
			valueWidth = Math.max(valueWidth, Gui2D.width(line[1], 0.85f));
		}

		width = Math.max(70, labelWidth + valueWidth + 20);
		height = 14 + lines.size() * 10 + 3;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		Gui2D.panel(g, 0, 0, width, height);
		int accent = Colors.argb(1f, Theme.accent());
		Gui2D.text(g, "SESSION", 6, 4, accent, true, 0.75f);
		Gui2D.fill(g, 6, 12, width - 5, 13, Colors.argb(0.3f, Theme.accent()));

		for (int i = 0; i < lines.size(); i++) {
			String[] line = lines.get(i);
			int y = 16 + i * 10;
			Gui2D.text(g, line[0], 6, y, 0xFFB8B8C6, true, 0.85f);
			Gui2D.text(g, line[1], width - 5 - Gui2D.width(line[1], 0.85f), y, 0xFFFFFFFF, true, 0.85f);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int kills = 3 + (int) (time / 2f) % 5;
		String[][] rows = {{"Kills", String.valueOf(kills)}, {"Tode", "2"}, {"K/D", String.format("%.2f", kills / 2f)}};
		int top = y + (h - rows.length * 12) / 2;
		int accent = Colors.argb(1f, Theme.accent());

		for (int i = 0; i < rows.length; i++) {
			Gui2D.text(g, rows[i][0], x + 20, top + i * 12, 0xFFB8B8C6, true, 0.9f);
			Gui2D.text(g, rows[i][1], x + w - 20 - Gui2D.width(rows[i][1], 0.9f), top + i * 12, i == 2 ? accent : 0xFFFFFFFF, true, 0.9f);
		}
	}
}
