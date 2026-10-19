package dev.juiced.modules.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/**
 * Item cooldowns (pearl, wind charge, chorus, shield, horn) with time left.
 * The game only tells us "on cooldown or not", so the time left comes from the known vanilla durations.
 */
public final class Cooldowns extends HudModule {
	private static final int CELL = 22;

	private final BoolSetting readySound = add(new BoolSetting("ready_sound", "Sound wenn bereit", false));

	private final Map<Item, Integer> durations = new HashMap<>();
	private final Map<Item, Long> started = new HashMap<>();
	private final Map<Item, ItemStack> icons = new HashMap<>();
	private final List<Item> active = new ArrayList<>();
	private long tick;
	private float partial;

	public Cooldowns() {
		super("cooldowns", "Cooldowns", false, 0.5f, 0.78f,
				"Restzeit von Enderperle, Windkugel, Chorusfrucht, Schild und Ziegenhorn, damit du weißt, wann du wieder darfst.");
	}

	private Map<Item, Integer> durations() {
		if (durations.isEmpty()) {
			durations.put(Items.ENDER_PEARL, 20);
			durations.put(Items.WIND_CHARGE, 10);
			durations.put(Items.CHORUS_FRUIT, 20);
			durations.put(Items.SHIELD, 100);
			durations.put(Items.GOAT_HORN, 140);
		}

		return durations;
	}

	@Override
	public void tick(Minecraft minecraft) {
		tick++;
		LocalPlayer player = minecraft.player;
		active.clear();

		if (player == null) {
			started.clear();
			return;
		}

		Inventory inventory = player.getInventory();

		for (Item item : durations().keySet()) {
			ItemStack stack = find(player, inventory, item);
			boolean cooling = stack != null && player.getCooldowns().isOnCooldown(stack);

			if (cooling) {
				started.putIfAbsent(item, tick);
				icons.putIfAbsent(item, new ItemStack(item));
				active.add(item);
			} else if (started.remove(item) != null && readySound.on()) {
				Fx.sound("block.note_block.pling", 0.4f, 1.8f);
			}
		}
	}

	private static ItemStack find(LocalPlayer player, Inventory inventory, Item item) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty() && stack.is(item)) {
				return stack;
			}
		}

		ItemStack offhand = player.getOffhandItem();
		return !offhand.isEmpty() && offhand.is(item) ? offhand : null;
	}

	/** Forget what was cooling down, so a later try-out in the menu doesn't show a frozen cell from back then. */
	@Override
	protected void onDisable() {
		active.clear();
		started.clear();
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		partial = hud.partialTick;
		int count = active.isEmpty() && editing ? 2 : active.size();

		if (count == 0) {
			return false;
		}

		width = count * CELL + 4;
		height = 30;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		Gui2D.panel(g, 0, 0, width, height);
		int accent = Theme.accent();

		if (active.isEmpty()) {
			drawCell(g, 0, icon(Items.ENDER_PEARL), 0.65f, "0.7s", accent);
			drawCell(g, 1, icon(Items.WIND_CHARGE), 0.3f, "0.2s", accent);
			return;
		}

		for (int i = 0; i < active.size(); i++) {
			Item item = active.get(i);
			int total = durations().get(item);
			float elapsed = tick - started.getOrDefault(item, tick) + partial;
			float left = Math.max(0f, total - elapsed);
			drawCell(g, i, icons.get(item), left / total, String.format("%.1fs", left / 20f), accent);
		}
	}

	private ItemStack icon(Item item) {
		return icons.computeIfAbsent(item, ItemStack::new);
	}

	private static void drawCell(GuiGraphicsExtractor g, int index, ItemStack stack, float fraction, String text, int accent) {
		int x = 4 + index * CELL;
		g.fakeItem(stack, x + 1, 3);
		// Dark cover that shrinks as the cooldown runs out
		int cover = Math.round(16 * Colors.clamp01(fraction));
		Gui2D.fill(g, x + 1, 3 + 16 - cover, x + 17, 19, 0x88000000);
		Gui2D.centered(g, text, x + 9, 21, Colors.argb(1f, fraction < 0.25f ? 0x8CFF9A : 0xFFFFFF), true, 0.7f);
		Gui2D.fill(g, x + 1, 28, x + 1 + Math.round(16 * Colors.clamp01(fraction)), 29, Colors.argb(1f, accent));
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float t = (time % 1.5f) / 1.5f;
		int accent = Theme.accent();
		int cx = x + w / 2;
		int cy = y + h / 2;

		for (int i = 0; i < 24; i++) {
			double angle = -Math.PI / 2 + i * Math.PI * 2 / 24;
			boolean lit = i / 24f >= t;
			Gui2D.dot(g, cx + (float) Math.cos(angle) * 18, cy + (float) Math.sin(angle) * 18, 2, lit ? Colors.argb(1f, accent) : 0x33FFFFFF);
		}

		Gui2D.centered(g, String.format("%.1fs", (1 - t) * 1.0f), cx, cy - 4, 0xFFFFFFFF, true, 1f);
	}
}
