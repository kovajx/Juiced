package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.mixin.FishingHookAccessor;
import dev.juiced.render.Fx;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/**
 * Makes fishing feel good: when a fish bites, the water splashes in rings and a "!" pops up over
 * the bobber. When you reel something in, a small card shows what you caught; treasure shines gold.
 */
public final class FishingMoment extends Module {
	private static final int MAX_DROPS = 40;

	private final BoolSetting sound = add(new BoolSetting("sound", "Leiser Ton beim Biss", true));
	private final BoolSetting popup = add(new BoolSetting("popup", "Fang anzeigen", true));

	private boolean wasBiting;
	private double biteTime = -10;
	private double hookX;
	private double hookY;
	private double hookZ;
	private boolean hookAlive;
	private double reeledAt = -10;
	private int caughtToday;

	private final double[] dx = new double[MAX_DROPS];
	private final double[] dy = new double[MAX_DROPS];
	private final double[] dz = new double[MAX_DROPS];
	private final float[] dvx = new float[MAX_DROPS];
	private final float[] dvy = new float[MAX_DROPS];
	private final float[] dvz = new float[MAX_DROPS];
	private final float[] dAge = new float[MAX_DROPS];
	private final boolean[] dAlive = new boolean[MAX_DROPS];

	private ItemStack shown = ItemStack.EMPTY;
	private double shownAt = -10;
	private int shownKind;
	private double splashX;
	private double splashY;
	private double splashZ;
	private double splashAt = -10;
	private boolean treasureBurst;

	public FishingMoment() {
		super("fishing", "Angel-Moment", Category.VISUALS, true,
				"Beißt ein Fisch an, spritzt das Wasser in Ringen und ein \"!\" springt über den Schwimmer. Beim Einholen zeigt eine kleine Karte deinen Fang, Schätze glänzen golden.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(dAlive, false);
		hookAlive = false;
	}

	/**
	 * A bite can't be faked without touching the catch counter and the real bite timing, so the menu shows
	 * this live only while your line is actually out (then a bite and the catch card work as usual).
	 */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.fishing == null ? "Nur beim Angeln" : null;
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null) {
			return;
		}

		FishingHook hook = player.fishing;
		double now = Clock.t();

		if (hook != null) {
			hookX = hook.getX();
			hookY = hook.getY();
			hookZ = hook.getZ();
			hookAlive = true;
			boolean biting = ((FishingHookAccessor) hook).juiced$isBiting();

			if (biting && !wasBiting) {
				bite(now);
			}

			wasBiting = biting;
		} else {
			if (hookAlive) {
				// Just reeled in: if a fish was on the line, watch for the catch flying to you
				hookAlive = false;

				if (now - biteTime < 2.0) {
					reeledAt = now;
				}
			}

			wasBiting = false;
		}

		if (now - reeledAt < 1.0) {
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity instanceof ItemEntity item && entity.tickCount < 12 && entity.distanceToSqr(hookX, hookY, hookZ) < 9) {
					catchShown(item.getItem().copy(), now);
					reeledAt = -10;
					break;
				}
			}
		}
	}

	private void bite(double now) {
		biteTime = now;
		splashX = hookX;
		splashY = hookY;
		splashZ = hookZ;
		splashAt = now;
		treasureBurst = false;
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int n = 0; n < 14; n++) {
			for (int i = 0; i < MAX_DROPS; i++) {
				if (!dAlive[i]) {
					double angle = random.nextDouble() * Math.PI * 2;
					float out = 0.6f + random.nextFloat() * 0.9f;
					dx[i] = hookX;
					dy[i] = hookY + 0.1;
					dz[i] = hookZ;
					dvx[i] = (float) Math.cos(angle) * out;
					dvy[i] = 1.6f + random.nextFloat() * 1.4f;
					dvz[i] = (float) Math.sin(angle) * out;
					dAge[i] = 0f;
					dAlive[i] = true;
					break;
				}
			}
		}

		if (sound.on()) {
			Fx.sound("block.note_block.bell", 0.35f, 1.7f);
		}
	}

	/** 0 = fish, 1 = treasure, 2 = junk. */
	private static int kindOf(ItemStack stack) {
		if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PUFFERFISH)) {
			return 0;
		}

		if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.NAME_TAG) || stack.is(Items.SADDLE) || stack.is(Items.NAUTILUS_SHELL)
				|| stack.is(Items.BOW) || stack.is(Items.FISHING_ROD)) {
			return 1;
		}

		return 2;
	}

	private void catchShown(ItemStack stack, double now) {
		shown = stack;
		shownAt = now;
		shownKind = kindOf(stack);
		caughtToday++;

		if (shownKind == 1) {
			treasureBurst = true;
			splashAt = now;

			if (sound.on()) {
				Fx.sound("entity.player.levelup", 0.25f, 1.6f);
			}
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		double now = Clock.t();
		float dt = Math.min(0.1f, Clock.dt());
		float since = (float) (now - splashAt);

		if (since < 1.6f) {
			// Rings spreading out where the fish bit (or a golden burst for treasure)
			for (int ring = 0; ring < 3; ring++) {
				float t = (since - ring * 0.18f) / 1.2f;

				if (t <= 0f || t >= 1f) {
					continue;
				}

				float eased = 1f - (1f - t) * (1f - t);
				int rgb = treasureBurst ? 0xFFD54F : 0xDCEBFA;
				geo.groundRing(splashX, splashY + 0.06, splashZ, 0.1f + eased * 0.9f, 0.025f, 0.04f, 32, 0f, (float) (Math.PI * 2), c -> rgb, 0.7f * (1f - t));
			}

			if (!treasureBurst && since < 1.0f) {
				// "!" jumps up over the bobber
				float jump = (float) Math.sin(Math.min(1f, since * 4f) * Math.PI / 2) * 0.35f;
				geo.pixelText(splashX, splashY + 0.6 + jump, splashZ, "!", 0.05f, 0xFFD54F, 1f - Math.max(0f, since - 0.6f) / 0.4f, true);
			}

			if (treasureBurst) {
				for (int k = 0; k < 6; k++) {
					double a = k * Math.PI / 3 + since * 2;
					geo.star(splashX + Math.cos(a) * since * 0.8, splashY + 0.5 + since * 0.6, splashZ + Math.sin(a) * since * 0.8, 0.12f, since * 4 + k, 0xFFE070, 1f - since / 1.6f);
				}
			}
		}

		for (int i = 0; i < MAX_DROPS; i++) {
			if (!dAlive[i]) {
				continue;
			}

			dAge[i] += dt;

			if (dAge[i] > 1.1f) {
				dAlive[i] = false;
				continue;
			}

			dvy[i] -= 9f * dt;
			dx[i] += dvx[i] * dt;
			dy[i] += dvy[i] * dt;
			dz[i] += dvz[i] * dt;
			geo.glowDot(dx[i], dy[i], dz[i], 0.045f, 0xCFE8FF, 0.9f * (1f - dAge[i] / 1.1f));
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor g, Hud hud) {
		if (!popup.on() || shown.isEmpty()) {
			return;
		}

		float age = (float) (Clock.t() - shownAt);

		if (age > 3.2f) {
			return;
		}

		float in = Clock.easeOut(Math.min(1f, age / 0.25f));
		float out = age > 2.6f ? 1f - (age - 2.6f) / 0.6f : 1f;
		float alpha = in * out;
		String name = shown.getHoverName().getString();
		String label = switch (shownKind) {
			case 1 -> "Schatz! " + name;
			case 2 -> name;
			default -> "Gefangen: " + name;
		};
		String count = "#" + caughtToday;
		int textW = Gui2D.width(label, 0.85f);
		int w = textW + 34 + Gui2D.width(count, 0.65f);
		int x = hud.centerX() - w / 2;
		int y = hud.height - 78 + Math.round((1f - in) * 10);
		int accent = shownKind == 1 ? 0xFFD54F : shownKind == 0 ? 0x6FD3FF : 0x9A9AA8;

		if (shownKind == 1) {
			Gui2D.glow(g, x, y, x + w, y + 22, accent, 0.6f * alpha * (0.7f + 0.3f * (float) Math.sin(age * 6)), 4);
		}

		Gui2D.rounded2(g, x, y, x + w, y + 22, Colors.argb(0.85f * alpha, 0x0D0D13));
		Gui2D.fill(g, x + 2, y + 4, x + 4, y + 18, Colors.argb(alpha, accent));

		if (alpha > 0.5f) {
			g.fakeItem(shown, x + 8, y + 3);
		}

		Gui2D.text(g, label, x + 28, y + 7, Colors.argb(alpha, shownKind == 2 ? 0xC8C8D2 : 0xEDEDF2), false, 0.85f);
		Gui2D.text(g, count, x + 30 + textW, y + 8, Colors.argb(alpha * 0.8f, 0x8A8A99), false, 0.65f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		float cx = px + w / 2f;
		float cy = py + h * 0.6f;
		Gui2D.fill(g, px + 4, Math.round(cy), px + w - 4, py + h - 4, 0x662F6D9A);
		float t = (time * 0.6f) % 1f;
		Gui2D.ellipse(g, cx, cy, 4 + t * 20, 1.5f + t * 5, 28, 1f, 0xDCEBFA, 1f - t, 0, false);
		Gui2D.fill(g, Math.round(cx - 1), Math.round(cy - 5), Math.round(cx + 2), Math.round(cy), 0xFFE8452C);
		Gui2D.fill(g, Math.round(cx), Math.round(py + 4), Math.round(cx) + 1, Math.round(cy - 5), 0x88FFFFFF);

		if (t < 0.4f) {
			Gui2D.centered(g, "!", cx, cy - 18 - t * 8, 0xFFFFD54F, true, 1.4f);
		}
	}
}
