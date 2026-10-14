package dev.juiced.modules.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/** Warns when your offhand has no totem although you still have some, or when you ran out. */
public final class TotemWarning extends HudModule {
	private final BoolSetting outOfTotems = add(new BoolSetting("out", "Warnen wenn keine mehr", true));
	private final BoolSetting sound = add(new BoolSetting("sound", "Warnton", true));
	private final BoolSetting onlyWithTotems = add(new BoolSetting("only_with", "Nur wenn du Totems benutzt", true));

	private int state;
	private int lastState;
	private boolean usedTotems;

	public TotemWarning() {
		super("totem_warning", "Totem-Warnung", false, 0.5f, 0.4f,
				"Großer Hinweis, wenn kein Totem in der Offhand ist (du aber noch welche hast) oder wenn alle weg sind.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || player.isDeadOrDying()) {
			state = 0;
			return;
		}

		int total = ItemCounter.countTotems(player);
		boolean inOffhand = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);

		if (total > 0 || inOffhand) {
			usedTotems = true;
		}

		if (onlyWithTotems.on() && !usedTotems) {
			state = 0;
		} else if (inOffhand) {
			state = 0;
		} else if (total > 0) {
			state = 1;
		} else {
			state = outOfTotems.on() ? 2 : 0;
		}

		if (state != 0 && state != lastState && sound.on()) {
			Fx.sound("block.note_block.bass", 0.8f, state == 2 ? 0.6f : 0.9f);
		}

		lastState = state;
	}

	@Override
	public void onDeath() {
		usedTotems = false;
	}

	private String text(boolean editing) {
		int shown = state == 0 && editing ? 1 : state;
		return shown == 2 ? "KEINE TOTEMS MEHR" : "KEIN TOTEM IN DER OFFHAND";
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		if (state == 0 && !editing) {
			return false;
		}

		width = Gui2D.width(text(editing), 1.1f) + 20;
		height = 18;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		float pulse = 0.55f + 0.45f * (float) Math.sin(Clock.tf() * 8);
		int red = Colors.mixRgb(0xFF3030, 0xFFFFFF, pulse * 0.25f);
		Gui2D.rounded2(g, 0, 0, width, height, Colors.argb(0.75f, 0x1A0505));
		Gui2D.outline(g, 0, 0, width, height, Colors.argb(0.6f + 0.4f * pulse, red));
		Gui2D.centered(g, text(editing), width / 2f, 5, Colors.argb(1f, red), true, 1.1f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float pulse = 0.55f + 0.45f * (float) Math.sin(time * 8);
		int red = Colors.mixRgb(0xFF3030, 0xFFFFFF, pulse * 0.25f);
		int bw = w - 20;
		int bx = x + 10;
		int by = y + h / 2 - 10;
		Gui2D.rounded2(g, bx, by, bx + bw, by + 20, Colors.argb(0.75f, 0x1A0505));
		Gui2D.outline(g, bx, by, bx + bw, by + 20, Colors.argb(0.6f + 0.4f * pulse, red));
		Gui2D.centered(g, "KEIN TOTEM!", x + w / 2f, by + 6, Colors.argb(1f, red), true, 1f);
	}
}
