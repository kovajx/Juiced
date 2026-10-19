package dev.juiced.modules.combat;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sparks;

public final class TotemPops extends Module {
	private final BoolSetting effect = add(new BoolSetting("effect", "Effekt", true));
	private final BoolSetting chat = add(new BoolSetting("chat", "Chat-Nachricht", true));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", Colors.GOLD, true));

	private final List<Pop> pops = new ArrayList<>();

	public TotemPops() {
		super("totem_pops", "Totem-Pops", Category.COMBAT, true,
				"Zählt, wie viele Totems jeder Spieler gepoppt hat (auch im Target-HUD), mit Effekt und optionaler Chat-Info.");
	}

	@Override
	public void onTotemPop(LivingEntity entity, int count) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;

		if (effect.on()) {
			double y = entity.getY() + entity.getBbHeight() * 0.55;
			Sparks.burst(entity.getX(), y, entity.getZ(), 26, 4f, 0.08f, 1.1f, t -> color.rgb(t), Sparks.STAR, 4f, 0.3f);

			if (pops.size() < 8) {
				pops.add(new Pop(entity, Clock.t()));
			}
		}

		if (chat.on() && self != null && entity instanceof Player) {
			String who = entity == self ? "Du hast" : entity.getName().getString() + " hat";
			Component message = Component.literal("⚡ ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(who + " ein Totem gepoppt ").withStyle(ChatFormatting.WHITE))
					.append(Component.literal("(" + count + ")").withStyle(ChatFormatting.GOLD));
			self.sendSystemMessage(message);
		}
	}

	/** Without the effect a pop only goes to the chat, and only for players, so the dummy would show nothing. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return effect.on() ? null : "Effekt ist ausgeschaltet";
	}

	@Override
	public void renderWorld(Geo geo) {
		if (pops.isEmpty()) {
			return;
		}

		double now = Clock.t();
		int segments = Theme.detail(40);

		for (int i = pops.size() - 1; i >= 0; i--) {
			Pop pop = pops.get(i);
			float t = (float) ((now - pop.start) / 0.8);

			if (t >= 1f || pop.entity.isRemoved()) {
				pops.remove(i);
				continue;
			}

			double x = geo.x(pop.entity);
			double y = geo.y(pop.entity) + pop.entity.getBbHeight() * 0.55;
			double z = geo.z(pop.entity);
			float radius = 0.25f + Clock.easeOut(t) * 1.3f;
			float alpha = 1f - t;
			geo.groundRing(x, y, z, radius, 0.05f * alpha + 0.01f, 0.16f, segments, Clock.tf(), (float) (Math.PI * 2), s -> color.rgb(s), alpha);
			geo.billboardRing(x, y, z, radius * 0.7f, 0.04f * alpha + 0.01f, 0.12f, segments, s -> color.rgb(s), alpha * 0.8f);
		}
	}

	@Override
	protected void onDisable() {
		pops.clear();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cycle = Previews.cycle(time, 1.4f);
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float cy = feetY - 18;
		int rgb = color.rgb();
		float t = Clock.easeOut(cycle);
		Gui2D.ellipse(g, cx, cy, 6 + t * 28, 2 + t * 8, 28, 2, rgb, 1f - cycle, time, true);

		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + 0.4;
			Gui2D.glowDot(g, cx + (float) Math.cos(a) * t * 26, cy + (float) Math.sin(a) * t * 18 + cycle * cycle * 8, 2, rgb, 1f - cycle);
		}

		int count = 1 + (int) (time / 1.4f) % 3;
		Gui2D.text(g, "x" + count, x + w - 26, y + 8, Colors.argb(1f, rgb), true, 1.4f);
	}

	private record Pop(LivingEntity entity, double start) {
	}
}
