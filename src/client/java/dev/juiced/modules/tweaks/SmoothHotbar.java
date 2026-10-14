package dev.juiced.modules.tweaks;

import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** The hotbar selection glides to the new slot instead of jumping, and the chosen item hops a little. */
public final class SmoothHotbar extends Module {
	public static SmoothHotbar INSTANCE;

	private final SliderSetting speed = add(SliderSetting.percent("speed", "Tempo", 0.5, 2.0, 1.0));
	private final SliderSetting bounce = add(SliderSetting.percent("bounce", "Hüpfen", 0.0, 2.0, 1.0));

	private float selectionX = Float.NaN;
	private int targetX;
	private int lastSelected = -1;
	private double changedAt = -10;

	public SmoothHotbar() {
		super("smooth_hotbar", "Weiche Hotbar", Category.TWEAKS, true,
				"Der Auswahlrahmen der Hotbar gleitet zum neuen Slot, statt zu springen, und das gewählte Item hüpft kurz.");
		INSTANCE = this;
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return;
		}

		int selected = player.getInventory().getSelectedSlot();

		if (selected != lastSelected) {
			if (lastSelected >= 0) {
				changedAt = Clock.t();
			}

			lastSelected = selected;
		}
	}

	private static boolean active() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}

	/** Called for every sprite the hotbar draws; moves the selection frame. */
	public static void adjustSprite(Args args) {
		if (!active()) {
			return;
		}

		Object sprite = args.get(1);

		if (!(sprite instanceof Identifier id) || !id.getPath().equals("hud/hotbar_selection")) {
			return;
		}

		int target = args.get(2);
		SmoothHotbar module = INSTANCE;
		module.targetX = target;

		if (Float.isNaN(module.selectionX) || Math.abs(module.selectionX - target) > 400) {
			module.selectionX = target;
		} else {
			float ease = 1f - (float) Math.exp(-20f * module.speed.f() * Math.min(0.1f, Clock.dt()));
			module.selectionX += (target - module.selectionX) * ease;

			if (Math.abs(module.selectionX - target) < 1.5f) {
				module.selectionX = target;
			}
		}

		args.set(2, Math.round(module.selectionX));
	}

	/** Called for every item slot the hotbar draws; lifts the selected item for a short hop. */
	public static void adjustSlot(Args args) {
		if (!active() || Float.isNaN(INSTANCE.selectionX)) {
			return;
		}

		SmoothHotbar module = INSTANCE;
		float age = (float) (Clock.t() - module.changedAt);

		if (age > 0.35f || module.bounce.f() <= 0f) {
			return;
		}

		int x = args.get(1);
		// The selected item sits 4 pixels right of the (real, unmoved) selection frame
		int slotX = module.targetX + 4;

		if (Math.abs(x - slotX) > 1) {
			return;
		}

		int y = args.get(2);
		float hop = (float) Math.sin(age / 0.35f * Math.PI) * 3f * module.bounce.f();
		args.set(2, y - Math.round(hop));
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int bar = 9 * 12;
		int bx = x + (w - bar) / 2;
		int by = y + h / 2 - 6;
		Gui2D.rounded(g, bx - 2, by - 2, bx + bar + 2, by + 14, 0xC0101016);
		float t = (time * 0.6f) % 3f;
		float slot = t < 1f ? 1 + Clock.easeInOut(t) * 5 : t < 1.5f ? 6 : t < 2.5f ? 6 - Clock.easeInOut(t - 1.5f) * 4 : 2;
		float sx = bx + slot * 12;
		Gui2D.outline(g, Math.round(sx - 1), by - 2, Math.round(sx + 13), by + 14, Colors.argb(1f, Theme.accent()));

		for (int i = 0; i < 9; i++) {
			Gui2D.fill(g, bx + i * 12 + 3, by + 3, bx + i * 12 + 9, by + 9, 0xFF5A5A66);
		}
	}
}
