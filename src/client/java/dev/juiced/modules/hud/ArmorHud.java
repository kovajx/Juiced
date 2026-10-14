package dev.juiced.modules.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

public final class ArmorHud extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Anordnung", 0, "Vertikal", "Horizontal"));
	private final ChoiceSetting display = add(new ChoiceSetting("display", "Anzeige", 0, "Prozent", "Haltbarkeit", "Balken"));
	private final SliderSetting warn = add(new SliderSetting("warn", "Warnen unter", 5, 40, 1, 15, v -> Math.round(v) + "%"));
	private final BoolSetting hands = add(new BoolSetting("hands", "Hände zeigen", true));

	private final List<ItemStack> items = new ArrayList<>();
	private List<ItemStack> samples;
	private boolean sample;

	public ArmorHud() {
		super("armor_hud", "Rüstungs-HUD", false, 1f, 0.03f,
				"Deine Rüstung mit Haltbarkeit. Blinkt rot, bevor ein Teil kaputtgeht.");
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		LocalPlayer player = hud.minecraft.player;
		items.clear();

		if (player != null) {
			for (EquipmentSlot slot : SLOTS) {
				ItemStack stack = player.getItemBySlot(slot);

				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}

			if (hands.on()) {
				ItemStack main = player.getItemBySlot(EquipmentSlot.MAINHAND);
				ItemStack off = player.getItemBySlot(EquipmentSlot.OFFHAND);

				if (!main.isEmpty() && main.isDamageableItem()) {
					items.add(main);
				}

				if (!off.isEmpty()) {
					items.add(off);
				}
			}
		}

		sample = items.isEmpty();

		if (sample) {
			if (!editing) {
				return false;
			}

			items.addAll(samples());
		}

		boolean vertical = layout.index() == 0;
		int cell = vertical ? 18 : 42;
		width = vertical ? 54 : items.size() * cell + 4;
		height = vertical ? items.size() * cell + 4 : 22;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		boolean vertical = layout.index() == 0;
		int cell = vertical ? 18 : 42;
		Gui2D.panel(g, 0, 0, width, height);
		float blink = 0.5f + 0.5f * (float) Math.sin(Clock.tf() * 10);

		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			int ix = vertical ? 4 : 4 + i * cell;
			int iy = vertical ? 3 + i * cell : 3;
			g.fakeItem(stack, ix, iy);
			float left;
			boolean damageable = stack.isDamageableItem() && stack.getMaxDamage() > 0;

			if (sample) {
				left = new float[] {0.92f, 0.71f, 0.46f, 0.12f}[i % 4];
			} else {
				left = damageable ? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : 1f;
			}

			if (!damageable && !sample) {
				String count = stack.getCount() > 1 ? "x" + stack.getCount() : "";
				Gui2D.text(g, count, ix + 19, iy + 4, Colors.argb(1f, Theme.accent()), true, 0.9f);
				continue;
			}

			int percent = Math.round(left * 100);
			int rgb = percent < warn.get() ? Colors.mixRgb(0xFF3B3B, 0xFFFFFF, blink * 0.4f) : left < 0.5f ? 0xFFD84D : 0x8CFF9A;

			switch (display.index()) {
				case 1 -> {
					int remaining = sample ? Math.round(left * 407) : stack.getMaxDamage() - stack.getDamageValue();
					Gui2D.text(g, String.valueOf(remaining), ix + 19, iy + 4, Colors.argb(1f, rgb), true, 0.85f);
				}
				case 2 -> Gui2D.bar(g, ix + 19, iy + 7, vertical ? 28 : 18, 2, left, Colors.argb(1f, rgb), 0x33FFFFFF);
				default -> Gui2D.text(g, percent + "%", ix + 19, iy + 4, Colors.argb(1f, rgb), true, 0.9f);
			}
		}
	}

	private List<ItemStack> samples() {
		if (samples == null) {
			samples = List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
					new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
		}

		return samples;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float[] levels = {0.92f, 0.71f, 0.46f, 0.12f};
		int rowH = Math.max(10, (h - 8) / 4);
		float blink = 0.5f + 0.5f * (float) Math.sin(time * 10);

		for (int i = 0; i < 4; i++) {
			int ry = y + 4 + i * rowH;
			int bx = x + w / 2 - 30;
			Gui2D.rounded(g, bx, ry, bx + 10, ry + rowH - 3, 0x55FFFFFF);
			float level = levels[i];
			int rgb = level < 0.15f ? Colors.mixRgb(0xFF3B3B, 0xFFFFFF, blink * 0.4f) : level < 0.5f ? 0xFFD84D : 0x8CFF9A;
			Gui2D.bar(g, bx + 14, ry + rowH / 2 - 2, 46, 3, level, Colors.argb(1f, rgb), 0x33FFFFFF);
		}
	}
}
