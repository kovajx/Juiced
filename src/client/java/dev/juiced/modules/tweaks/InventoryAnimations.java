package dev.juiced.modules.tweaks;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

/**
 * Small animations in inventories: the item under your mouse grows a little and wiggles,
 * and with Shift-click you see the item fly to where it went.
 */
public final class InventoryAnimations extends Module {
	public static InventoryAnimations INSTANCE;

	private static final float FLIGHT = 0.28f;
	private static final int MAX_FLIGHTS = 8;

	private final BoolSetting hover = add(new BoolSetting("hover", "Item unter der Maus wackelt", true));
	private final BoolSetting fly = add(new BoolSetting("fly", "Shift-Klick fliegt sichtbar", true));

	private Slot hovered;
	private double hoverSince;
	private boolean pushed;

	// A shift-click waiting to see where the item ends up
	private Slot pendingFrom;
	private ItemStack pendingStack = ItemStack.EMPTY;
	private int[] pendingCounts = new int[0];
	private int pendingFrames;

	private final ItemStack[] flightStack = new ItemStack[MAX_FLIGHTS];
	private final int[] fromX = new int[MAX_FLIGHTS];
	private final int[] fromY = new int[MAX_FLIGHTS];
	private final int[] toX = new int[MAX_FLIGHTS];
	private final int[] toY = new int[MAX_FLIGHTS];
	private final double[] flightStart = new double[MAX_FLIGHTS];

	public InventoryAnimations() {
		super("inventory_animations", "Inventar-Animationen", Category.TWEAKS, true,
				"Das Item unter deiner Maus wird etwas größer und wackelt leicht, und bei Shift-Klick fliegt das Item sichtbar an seinen neuen Platz.");
		INSTANCE = this;
	}

	private static boolean active() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}

	/** Before a slot is drawn: grow and wiggle the hovered one. Coordinates are relative to the container. */
	public static void beforeSlot(GuiGraphicsExtractor g, Slot slot, Slot hoveredSlot) {
		InventoryAnimations module = INSTANCE;

		if (!active() || !module.hover.on()) {
			return;
		}

		if (hoveredSlot != module.hovered) {
			module.hovered = hoveredSlot;
			module.hoverSince = Clock.t();
		}

		if (slot != hoveredSlot || slot == null || !slot.hasItem()) {
			return;
		}

		float age = (float) (Clock.t() - module.hoverSince);
		float grow = 1f + 0.12f * Clock.easeOut(Math.min(1f, age / 0.12f));
		// A short wiggle that settles down
		float wiggle = (float) Math.sin(age * 22f) * 0.12f * (float) Math.exp(-age * 5f);
		float cx = slot.x + 8f;
		float cy = slot.y + 8f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(wiggle);
		g.pose().scale(grow, grow);
		g.pose().translate(-cx, -cy);
		module.pushed = true;
	}

	public static void afterSlot(GuiGraphicsExtractor g) {
		InventoryAnimations module = INSTANCE;

		if (module != null && module.pushed) {
			g.pose().popMatrix();
			module.pushed = false;
		}
	}

	/** A shift-click is about to move this slot's items: remember everything to find where they land. */
	public static void quickMove(AbstractContainerMenu menu, Slot slot) {
		InventoryAnimations module = INSTANCE;

		if (!active() || !module.fly.on() || slot == null || !slot.hasItem()) {
			return;
		}

		module.pendingFrom = slot;
		module.pendingStack = slot.getItem().copy();
		module.pendingCounts = new int[menu.slots.size()];

		for (int i = 0; i < module.pendingCounts.length; i++) {
			module.pendingCounts[i] = countIfSame(menu.slots.get(i).getItem(), module.pendingStack);
		}

		module.pendingFrames = 0;
	}

	private static int countIfSame(ItemStack stack, ItemStack like) {
		return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, like) ? stack.getCount() : 0;
	}

	/** After the container is drawn: find where a shift-click went and draw items in flight. */
	public static void draw(GuiGraphicsExtractor g, AbstractContainerMenu menu, int left, int top) {
		InventoryAnimations module = INSTANCE;

		if (!active()) {
			return;
		}

		if (module.pendingFrom != null) {
			module.pendingFrames++;
			int size = Math.min(module.pendingCounts.length, menu.slots.size());

			for (int i = 0; i < size; i++) {
				Slot target = menu.slots.get(i);

				if (target == module.pendingFrom) {
					continue;
				}

				int now = countIfSame(target.getItem(), module.pendingStack);

				if (now > module.pendingCounts[i]) {
					ItemStack moving = module.pendingStack.copy();
					moving.setCount(now - module.pendingCounts[i]);
					module.launch(moving, left + module.pendingFrom.x, top + module.pendingFrom.y, left + target.x, top + target.y);
					module.pendingCounts[i] = now;
				}
			}

			if (module.pendingFrames > 6) {
				module.pendingFrom = null;
			}
		}

		double clock = Clock.t();

		for (int f = 0; f < MAX_FLIGHTS; f++) {
			if (module.flightStack[f] == null) {
				continue;
			}

			float t = (float) ((clock - module.flightStart[f]) / FLIGHT);

			if (t >= 1f) {
				module.flightStack[f] = null;
				continue;
			}

			float eased = Clock.easeInOut(t);
			float x = module.fromX[f] + (module.toX[f] - module.fromX[f]) * eased;
			// A gentle arc upwards on the way
			float y = module.fromY[f] + (module.toY[f] - module.fromY[f]) * eased - (float) Math.sin(t * Math.PI) * 14f;
			float scale = 1f + 0.25f * (float) Math.sin(t * Math.PI);
			Gui2D.glowDot(g, x + 8, y + 8, 6, Theme.accent(), 0.35f * (1f - t));
			g.pose().pushMatrix();
			g.pose().translate(x + 8, y + 8);
			g.pose().scale(scale, scale);
			g.pose().translate(-8, -8);
			g.fakeItem(module.flightStack[f], 0, 0);
			g.pose().popMatrix();
		}
	}

	private void launch(ItemStack stack, int x0, int y0, int x1, int y1) {
		for (int f = 0; f < MAX_FLIGHTS; f++) {
			if (flightStack[f] == null) {
				flightStack[f] = stack;
				fromX[f] = x0;
				fromY[f] = y0;
				toX[f] = x1;
				toY[f] = y1;
				flightStart[f] = Clock.t();
				return;
			}
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int grid = 18;
		int gx = x + (w - grid * 4) / 2;
		int gy = y + 10;

		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				Gui2D.rounded(g, gx + c * grid, gy + r * grid + r * 14, gx + c * grid + 16, gy + r * grid + r * 14 + 16, 0xFF2A2A36);
			}
		}

		float t = (time * 0.7f) % 1.5f;

		if (t < 1f) {
			float e = Clock.easeInOut(t);
			float ix = gx + 1 * grid + (3 - 1) * grid * e;
			float iy = gy + (grid + 14) * e - (float) Math.sin(t * Math.PI) * 10;
			Gui2D.glowDot(g, ix + 8, iy + 8, 6, Theme.accent(), 0.4f * (1f - t));
			Gui2D.rounded(g, Math.round(ix + 3), Math.round(iy + 3), Math.round(ix + 13), Math.round(iy + 13), Colors.argb(1f, 0x6FD3FF));
		}
	}
}
