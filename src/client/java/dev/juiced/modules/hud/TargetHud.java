package dev.juiced.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

public final class TargetHud extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
	private static final int WIDTH = 154;

	private final BoolSetting armor = add(new BoolSetting("armor", "Rüstung zeigen", true));
	private final BoolSetting advantage = add(new BoolSetting("advantage", "Vorteil-Anzeige", true));

	private LivingEntity shown;
	private float visibility;
	private float health;
	private float ghost;
	private double ghostHoldUntil;
	private ItemStack[] sampleArmor;

	// What prepare() decided to show
	private String name;
	private float hp;
	private float maxHp;
	private float absorption;
	private String distance;
	private int pops;
	private float lead;
	private boolean hasLead;
	private boolean sample;
	private float alpha;

	public TargetHud() {
		super("target_hud", "Target-HUD", true, 0.62f, 0.62f,
				"Karte mit Leben, Rüstung, Abstand und Totem-Pops deines Gegners. Zeigt, ob du gerade vorne liegst.");
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		LivingEntity target = CombatEvents.target();

		if (target != null && target != shown) {
			shown = target;
			health = target.getHealth();
			ghost = health;
		}

		visibility = Clock.approach(visibility, target != null ? 1f : 0f, 12f);

		if (shown != null && visibility < 0.02f && target == null) {
			shown = null;
		}

		LocalPlayer self = hud.minecraft.player;
		sample = shown == null;

		if (sample && !editing) {
			return false;
		}

		if (sample) {
			name = "Gegner";
			hp = 13f;
			maxHp = 20f;
			absorption = 4f;
			distance = "3.1m";
			pops = 2;
			lead = 2.5f;
			alpha = 1f;
			health = hp;
			ghost = 16f;
		} else {
			name = shown.getName().getString();
			hp = Math.max(0f, shown.getHealth());
			maxHp = Math.max(1f, shown.getMaxHealth());
			absorption = shown.getAbsorptionAmount();
			distance = self == null ? "" : String.format("%.1fm", Math.sqrt(shown.distanceToSqr(self)));
			pops = CombatEvents.totemPops(shown);
			lead = self == null ? 0f : (self.getHealth() + self.getAbsorptionAmount()) - (hp + absorption);
			alpha = editing ? 1f : visibility;
			animateHealth();
		}

		hasLead = advantage.on() && self != null;
		width = WIDTH;
		height = armor.on() ? 52 : 38;
		return true;
	}

	private void animateHealth() {
		health = Clock.approach(health, hp, 14f);
		double now = Clock.t();

		if (hp >= ghost) {
			ghost = hp;
		} else if (now > ghostHoldUntil) {
			ghost = Clock.approach(ghost, hp, 4f);
		}

		if (Math.abs(hp - health) > 0.3f && hp < health) {
			ghostHoldUntil = now + 0.35;
		}
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		int accent = Theme.accent();
		float a = alpha;
		float bg = Theme.hudBackground();

		if (bg > 0.01f) {
			Gui2D.rounded2(g, 0, 0, WIDTH, height, Colors.argb(Math.max(0.35f, bg) * 0.95f * a, 0x0D0D13));
		}

		if (Theme.hudAccent()) {
			Gui2D.fill(g, 0, 3, 2, height - 3, Colors.argb(a, accent));
		}

		Gui2D.fillGradientSafe(g, 2, 0, WIDTH - 2, 10, Colors.argb(0.10f * a, accent));

		// Badge with the first letter of the name
		Gui2D.rounded(g, 7, 6, 35, 34, Colors.argb(0.22f * a, accent));
		Gui2D.outline(g, 7, 6, 35, 34, Colors.argb(0.6f * a, accent));
		Gui2D.centered(g, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 21, 13, Colors.argb(a, accent), true, 2f);

		int distanceWidth = Gui2D.width(distance, 0.8f);
		Gui2D.text(g, trim(name, WIDTH - 46 - distanceWidth - 6), 41, 7, Colors.argb(a, 0xFFFFFF), true, 1f);
		Gui2D.text(g, distance, WIDTH - 6 - distanceWidth, 8, Colors.argb(0.7f * a, 0xCFCFDA), false, 0.8f);

		// Health bar: dark track, white "ghost" that trails behind, colored health, gold absorption
		int barX = 41;
		int barY = 19;
		int barW = WIDTH - 47;
		Gui2D.fill(g, barX, barY, barX + barW, barY + 5, Colors.argb(0.35f * a, 0x000000));
		Gui2D.fill(g, barX, barY, barX + Math.round(barW * Colors.clamp01(ghost / maxHp)), barY + 5, Colors.argb(0.55f * a, 0xFFFFFF));
		float fraction = Colors.clamp01(health / maxHp);
		int healthColor = fraction > 0.5f ? Colors.mixRgb(0xFFD84D, 0x5CFF7A, (fraction - 0.5f) * 2f) : Colors.mixRgb(0xFF4D4D, 0xFFD84D, fraction * 2f);
		Gui2D.fill(g, barX, barY, barX + Math.round(barW * fraction), barY + 5, Colors.argb(a, healthColor));

		if (absorption > 0f) {
			int start = barX + Math.round(barW * fraction);
			int end = Math.min(barX + barW, start + Math.round(barW * absorption / maxHp));
			Gui2D.fill(g, start, barY, end, barY + 5, Colors.argb(a, 0xFFC23D));
		}

		Gui2D.text(g, String.format("%.1f", hp + absorption) + " ❤", barX, 28, Colors.argb(a, healthColor), true, 0.85f);
		int rightX = WIDTH - 6;

		if (hasLead) {
			String text;
			int rgb;

			if (Math.abs(lead) < 0.5f) {
				text = "≈ Gleich";
				rgb = 0xB8B8C6;
			} else if (lead > 0) {
				text = String.format("+%.1f", lead);
				rgb = 0x5CFF7A;
			} else {
				text = String.format("%.1f", lead);
				rgb = 0xFF5A5A;
			}

			int w = Gui2D.width(text, 0.85f);
			Gui2D.text(g, text, rightX - w, 28, Colors.argb(a, rgb), true, 0.85f);
			rightX -= w + 6;
		}

		if (pops > 0) {
			String text = "⚡" + pops;
			int w = Gui2D.width(text, 0.85f);
			Gui2D.text(g, text, rightX - w, 28, Colors.argb(a, 0xFFC23D), true, 0.85f);
		}

		if (armor.on() && a > 0.6f) {
			int itemX = barX;

			for (int i = 0; i < SLOTS.length; i++) {
				ItemStack stack = sample ? sampleArmor()[i] : shown.getItemBySlot(SLOTS[i]);

				if (!stack.isEmpty()) {
					g.pose().pushMatrix();
					g.pose().translate(itemX, 38);
					g.pose().scale(0.7f);
					g.fakeItem(stack, 0, 0);
					g.pose().popMatrix();
				}

				itemX += 14;
			}
		}
	}

	private ItemStack[] sampleArmor() {
		if (sampleArmor == null) {
			sampleArmor = new ItemStack[] {
					new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
					new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.NETHERITE_SWORD)
			};
		}

		return sampleArmor;
	}

	private static String trim(String text, int maxWidth) {
		if (Gui2D.width(text, 1f) <= maxWidth) {
			return text;
		}

		String result = text;

		while (!result.isEmpty() && Gui2D.width(result + "…", 1f) > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}

		return result + "…";
	}

	@Override
	protected void onDisable() {
		shown = null;
		visibility = 0f;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int cw = Math.min(w - 16, 110);
		int ch = 34;
		int cx = x + (w - cw) / 2;
		int cy = y + (h - ch) / 2;
		int accent = Theme.accent();
		float cycle = (time % 3f) / 3f;
		float life = 1f - cycle * 0.8f;
		float ghostHp = Math.min(1f, life + 0.15f * (1f - (time % 1f)));

		Gui2D.rounded2(g, cx, cy, cx + cw, cy + ch, 0xE00F0F15);
		Gui2D.fill(g, cx, cy + 3, cx + 2, cy + ch - 3, Colors.argb(1f, accent));
		Gui2D.rounded(g, cx + 6, cy + 6, cx + 26, cy + 26, Colors.argb(0.25f, accent));
		Gui2D.centered(g, "S", cx + 16, cy + 11, Colors.argb(1f, accent), true, 1.4f);
		Gui2D.text(g, "Steve", cx + 31, cy + 6, 0xFFFFFFFF, true, 0.8f);
		int bw = cw - 37;
		Gui2D.fill(g, cx + 31, cy + 17, cx + 31 + bw, cy + 21, 0x55000000);
		Gui2D.fill(g, cx + 31, cy + 17, cx + 31 + Math.round(bw * ghostHp), cy + 21, 0x88FFFFFF);
		int rgb = life > 0.5f ? Colors.mixRgb(0xFFD84D, 0x5CFF7A, (life - 0.5f) * 2) : Colors.mixRgb(0xFF4D4D, 0xFFD84D, life * 2);
		Gui2D.fill(g, cx + 31, cy + 17, cx + 31 + Math.round(bw * life), cy + 21, Colors.argb(1f, rgb));
		Gui2D.text(g, "+4.0", cx + cw - 22, cy + 24, 0xFF5CFF7A, true, 0.7f);
	}
}
