package dev.juiced.modules.tweaks;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

/** Removes screen effects that block your view in fights. */
public final class ClearView extends Module {
	public static ClearView INSTANCE;

	private final BoolSetting hurtCam = add(new BoolSetting("hurt_cam", "Keine Hurt-Cam (Wackeln)", true));
	private final BoolSetting totem = add(new BoolSetting("totem", "Totem-Animation aus", false));
	private final BoolSetting pumpkin = add(new BoolSetting("pumpkin", "Kürbis-Overlay aus", true));
	private final BoolSetting portal = add(new BoolSetting("portal", "Portal-Overlay aus", true));
	private final BoolSetting water = add(new BoolSetting("water", "Wasser-Overlay aus", false));
	private final BoolSetting inBlock = add(new BoolSetting("in_block", "Block-Overlay aus", true));

	public ClearView() {
		super("clear_view", "Klare Sicht", Category.TWEAKS, true,
				"Weg mit allem, was im Kampf die Sicht stört: Bildschirmwackeln bei Treffern, Totem-Animation, Kürbis-, Portal- und Block-Overlay.");
		INSTANCE = this;
	}

	private static boolean on(BoolSetting setting) {
		return INSTANCE != null && INSTANCE.isEnabled() && setting.on();
	}

	public static boolean noHurtCam() {
		return INSTANCE != null && on(INSTANCE.hurtCam);
	}

	public static boolean hideTotemAnimation(ItemStack stack) {
		return INSTANCE != null && on(INSTANCE.totem) && stack != null && stack.is(Items.TOTEM_OF_UNDYING);
	}

	public static boolean noPumpkin() {
		return INSTANCE != null && on(INSTANCE.pumpkin);
	}

	public static boolean noPortal() {
		return INSTANCE != null && on(INSTANCE.portal);
	}

	public static boolean noWater() {
		return INSTANCE != null && on(INSTANCE.water);
	}

	public static boolean noInBlock() {
		return INSTANCE != null && on(INSTANCE.inBlock);
	}

	/**
	 * Hurt cam, totem, block and portal need a fight or a mishap, and the pumpkin overlay is part of the HUD the
	 * menu hides. Only the water overlay can disappear in front of you, with your eyes under water.
	 */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return water.on() && player != null && player.isUnderWater() ? null : "Wirkt im Kampf";
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float wipe = (time * 0.6f) % 1f;
		int split = x + Math.round(w * wipe);
		// Left of the wipe: blurred, tinted view. Right: clear.
		Gui2D.fill(g, x + 4, y + 4, split, y + h - 4, 0x55D06A1A);

		for (int i = 0; i < 6; i++) {
			int bx = x + 10 + i * (w - 20) / 6;

			if (bx < split) {
				Gui2D.fill(g, bx, y + 10, bx + 6, y + h - 10, 0x30000000);
			}
		}

		Gui2D.fill(g, split, y + 4, split + 1, y + h - 4, Colors.argb(1f, Theme.accent()));
		Gui2D.centered(g, "◉", x + w * 0.75f, y + h / 2f - 6, 0xFFFFFFFF, true, 1.5f);
	}
}
