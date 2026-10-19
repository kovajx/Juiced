package dev.juiced.modules.hud;

import org.jspecify.annotations.Nullable;

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
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.render.Gui2D;

public final class ItemCounter extends HudModule {
	private static final int CELL = 30;

	private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Anordnung", 0, "Horizontal", "Vertikal"));
	private final BoolSetting totems = add(new BoolSetting("totems", "Totems", true));
	private final BoolSetting apples = add(new BoolSetting("apples", "Goldäpfel", true));
	private final BoolSetting pearls = add(new BoolSetting("pearls", "Enderperlen", true));
	private final BoolSetting windCharges = add(new BoolSetting("wind_charges", "Windkugeln", true));
	private final BoolSetting crystals = add(new BoolSetting("crystals", "Endkristalle", false));
	private final BoolSetting anchors = add(new BoolSetting("anchors", "Seelenanker + Glowstone", false));
	private final BoolSetting xp = add(new BoolSetting("xp", "XP-Flaschen", false));
	private final BoolSetting arrows = add(new BoolSetting("arrows", "Pfeile", false));

	private final Entry[] entries = {
			new Entry(Items.TOTEM_OF_UNDYING, true),
			new Entry(Items.GOLDEN_APPLE, false),
			new Entry(Items.ENCHANTED_GOLDEN_APPLE, false),
			new Entry(Items.ENDER_PEARL, false),
			new Entry(Items.WIND_CHARGE, false),
			new Entry(Items.END_CRYSTAL, false),
			new Entry(Items.RESPAWN_ANCHOR, false),
			new Entry(Items.GLOWSTONE, false),
			new Entry(Items.EXPERIENCE_BOTTLE, false),
			new Entry(Items.ARROW, false)
	};
	private int visible;
	private boolean counted;

	public ItemCounter() {
		super("item_counter", "Item-Zähler", false, 1f, 0.82f,
				"Wie viele Totems, Goldäpfel, Perlen, Kristalle und mehr du noch hast. Kein Totem mehr? Wird rot.");
	}

	private boolean shown(int index) {
		return switch (index) {
			case 0 -> totems.on();
			case 1, 2 -> apples.on();
			case 3 -> pearls.on();
			case 4 -> windCharges.on();
			case 5 -> crystals.on();
			case 6, 7 -> anchors.on();
			case 8 -> xp.on();
			default -> arrows.on();
		};
	}

	/** Totems in the inventory, for other modules (totem warning). */
	public static int countTotems(LocalPlayer player) {
		return count(player, Items.TOTEM_OF_UNDYING);
	}

	public static int count(LocalPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty() && stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player != null) {
			countAll(player);
		}
	}

	@Override
	protected void onDisable() {
		counted = false;
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		for (int i = 0; i < entries.length; i++) {
			if (shown(i)) {
				return null;
			}
		}

		return "Keine Items ausgewählt";
	}

	private void countAll(LocalPlayer player) {
		counted = true;

		for (Entry entry : entries) {
			entry.count = 0;
		}

		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			for (Entry entry : entries) {
				if (stack.is(entry.item)) {
					entry.count += stack.getCount();
				}
			}
		}
	}

	private boolean visible(int index, boolean editing) {
		return shown(index) && (editing || entries[index].count > 0 || entries[index].alwaysShow);
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		// Counts come from tick. A fresh try-out in the menu, or the editor showing it while it's off, counts here
		// instead of showing old counts or zeros
		if (editing && (!counted || !isEnabled()) && hud.minecraft.player != null) {
			countAll(hud.minecraft.player);
		}

		visible = 0;

		for (int i = 0; i < entries.length; i++) {
			if (visible(i, editing)) {
				visible++;
			}
		}

		if (visible == 0) {
			return false;
		}

		boolean horizontal = layout.index() == 0;
		width = horizontal ? visible * CELL + 4 : CELL + 14;
		height = horizontal ? 22 : visible * 19 + 3;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		boolean horizontal = layout.index() == 0;
		Gui2D.panel(g, 0, 0, width, height);
		int n = 0;

		for (int i = 0; i < entries.length; i++) {
			if (!visible(i, editing)) {
				continue;
			}

			Entry entry = entries[i];
			int x = horizontal ? 4 + n * CELL : 4;
			int y = horizontal ? 3 : 2 + n * 19;
			g.fakeItem(entry.stack(), x, y);
			String text = String.valueOf(entry.count);
			int rgb = entry.count == 0 ? 0xFF4D4D : 0xFFFFFF;

			if (horizontal) {
				Gui2D.text(g, text, x + 17 - Gui2D.width(text, 0.85f) / 2f + 4, y + 9, Colors.argb(1f, rgb), true, 0.85f);
			} else {
				Gui2D.text(g, text, x + 19, y + 5, Colors.argb(1f, rgb), true, 0.9f);
			}

			n++;
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		String[] labels = {"✦", "●", "◆"};
		int[] counts = {2, 7, 16};
		int cell = 28;
		int startX = x + (w - cell * 3) / 2;
		int cy = y + h / 2 - 10;

		for (int i = 0; i < 3; i++) {
			int bx = startX + i * cell;
			Gui2D.rounded(g, bx + 2, cy, bx + cell - 2, cy + 20, 0x40FFFFFF);
			Gui2D.centered(g, labels[i], bx + cell / 2f, cy + 4, Colors.argb(1f, Theme.accent(i / 3f)), true, 1f);
			int count = counts[i] - (i == 0 ? (int) (time / 2f) % 3 : 0);
			Gui2D.centered(g, String.valueOf(count), bx + cell / 2f, cy + 22, count == 0 ? 0xFFFF4D4D : 0xFFFFFFFF, true, 0.8f);
		}
	}

	private static final class Entry {
		final Item item;
		final boolean alwaysShow;
		int count;
		/** Created on first use: item stacks can't exist yet while mods are loading. */
		private ItemStack stack;

		Entry(Item item, boolean alwaysShow) {
			this.item = item;
			this.alwaysShow = alwaysShow;
		}

		ItemStack stack() {
			if (stack == null) {
				stack = new ItemStack(item);
			}

			return stack;
		}
	}
}
