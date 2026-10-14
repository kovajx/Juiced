package dev.juiced.modules.tweaks;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** Size and position of the items in your hands (first person). */
public final class HandView extends Module {
	public static HandView INSTANCE;

	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.4, 1.5, 1.0));
	private final SliderSetting x = add(new SliderSetting("x", "Seitlich", -0.5, 0.5, 0.02, 0, v -> String.format("%+.2f", v)));
	private final SliderSetting y = add(new SliderSetting("y", "Höhe", -0.5, 0.5, 0.02, 0, v -> String.format("%+.2f", v)));
	private final SliderSetting z = add(new SliderSetting("z", "Abstand", -0.5, 0.5, 0.02, 0, v -> String.format("%+.2f", v)));

	public HandView() {
		super("hand_view", "Hand-Ansicht", Category.TWEAKS, false,
				"Mach Schwert, Kristalle und Co. in der Hand kleiner oder schieb sie zur Seite, für mehr Sicht.");
		INSTANCE = this;
	}

	/**
	 * Called from FirstPersonHandsAndItemsRendererMixin right before an item in your hand is drawn.
	 * Also applies Low Shield, so both work together.
	 */
	public static void apply(InteractionHand hand, ItemStack stack, PoseStack poseStack) {
		float low = LowShield.offsetFor(stack);
		HandView module = INSTANCE;
		boolean view = module != null && module.isEnabled();

		if (!view && low == 0f) {
			return;
		}

		// The off hand is mirrored, so sideways movement is flipped there
		float side = hand == InteractionHand.OFF_HAND ? -1f : 1f;
		float dx = view ? module.x.f() * side : 0f;
		float dy = (view ? module.y.f() : 0f) - low;
		float dz = view ? -module.z.f() : 0f;

		if (dx != 0f || dy != 0f || dz != 0f) {
			poseStack.translate(dx, dy, dz);
		}

		if (view && module.size.f() != 1f) {
			poseStack.scale(module.size.f(), module.size.f(), module.size.f());
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		float s = size.f();
		int bladeLength = Math.round(44 * s);
		int baseX = Math.round(px + w * 0.7f + x.f() * 40);
		int baseY = Math.round(py + h - 6 - y.f() * 30);
		int accent = Theme.accent();

		for (int i = 0; i < bladeLength; i++) {
			int bx = baseX - i / 2;
			int by = baseY - i;
			Gui2D.fill(g, bx, by, bx + Math.max(2, Math.round(4 * s)), by + 1, i < 8 * s ? 0xFF6B4A2E : Colors.argb(1f, Colors.mixRgb(0xCFE8FF, accent, 0.3f)));
		}

		int cx = px + w / 2 - 10;
		int cy = py + h / 2;
		Gui2D.fill(g, cx - 4, cy, cx + 5, cy + 1, 0xE0FFFFFF);
		Gui2D.fill(g, cx, cy - 4, cx + 1, cy + 5, 0xE0FFFFFF);
	}
}
