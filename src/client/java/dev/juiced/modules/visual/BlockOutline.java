package dev.juiced.modules.visual;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/** Replaces the thin black block outline with a glowing, colored one. */
public final class BlockOutline extends Module {
	public static BlockOutline INSTANCE;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 2, "Kanten", "Gefüllt", "Beides"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting thickness = add(SliderSetting.percent("thickness", "Dicke", 0.5, 3.0, 1.0));
	private final SliderSetting fill = add(SliderSetting.percent("fill", "Füllung", 0.05, 0.6, 0.18));
	private final BoolSetting pulse = add(new BoolSetting("pulse", "Pulsieren", true));

	private @Nullable AABB box;

	public BlockOutline() {
		super("block_outline", "Block-Umrandung", Category.VISUALS, false,
				"Leuchtende, farbige Umrandung für den Block, den du anschaust, statt der dünnen schwarzen Linie.");
		INSTANCE = this;
	}

	/** True while the vanilla outline should be hidden. */
	public static boolean replacesVanilla() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}

	/** Called during world extraction with what the crosshair is pointing at. */
	public static void capture(LevelExtractionContext context, @Nullable HitResult hit) {
		BlockOutline module = INSTANCE;

		if (module == null) {
			return;
		}

		module.box = null;

		if (!module.isEnabled() || !(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = context.level().getBlockState(pos);
		VoxelShape shape = state.getShape(context.level(), pos);

		if (shape.isEmpty()) {
			return;
		}

		module.box = shape.bounds().move(pos.getX(), pos.getY(), pos.getZ()).inflate(0.004);
	}

	@Override
	public void renderWorld(Geo geo) {
		AABB b = box;

		if (b == null) {
			return;
		}

		float time = Clock.tf();
		float glow = pulse.on() ? 0.75f + 0.25f * (float) Math.sin(time * 4) : 1f;
		int rgb = color.rgb(time * 0.1f);

		if (style.index() != 0) {
			face(geo, b, Colors.argb(fill.f() * glow, rgb));
		}

		if (style.index() != 1) {
			float w = 0.008f * thickness.f();
			edges(geo, b, w * 3.5f, Colors.argb(0.22f * glow, rgb));
			edges(geo, b, w, Colors.argb(0.95f, Colors.shade(rgb, 0.25f)));
		}
	}

	private static void face(Geo geo, AABB b, int c) {
		float x0 = geo.rx(b.minX);
		float y0 = geo.ry(b.minY);
		float z0 = geo.rz(b.minZ);
		float x1 = geo.rx(b.maxX);
		float y1 = geo.ry(b.maxY);
		float z1 = geo.rz(b.maxZ);
		geo.quad(x0, y0, z0, c, x1, y0, z0, c, x1, y1, z0, c, x0, y1, z0, c);
		geo.quad(x0, y0, z1, c, x1, y0, z1, c, x1, y1, z1, c, x0, y1, z1, c);
		geo.quad(x0, y0, z0, c, x0, y0, z1, c, x0, y1, z1, c, x0, y1, z0, c);
		geo.quad(x1, y0, z0, c, x1, y0, z1, c, x1, y1, z1, c, x1, y1, z0, c);
		geo.quad(x0, y0, z0, c, x1, y0, z0, c, x1, y0, z1, c, x0, y0, z1, c);
		geo.quad(x0, y1, z0, c, x1, y1, z0, c, x1, y1, z1, c, x0, y1, z1, c);
	}

	private static void edges(Geo geo, AABB b, float w, int c) {
		double[] xs = {b.minX, b.maxX};
		double[] ys = {b.minY, b.maxY};
		double[] zs = {b.minZ, b.maxZ};

		for (double y : ys) {
			for (double z : zs) {
				geo.line(b.minX, y, z, b.maxX, y, z, w, c, c);
			}
		}

		for (double x : xs) {
			for (double z : zs) {
				geo.line(x, b.minY, z, x, b.maxY, z, w, c, c);
			}
		}

		for (double x : xs) {
			for (double y : ys) {
				geo.line(x, y, b.minZ, x, y, b.maxZ, w, c, c);
			}
		}
	}

	@Override
	protected void onDisable() {
		box = null;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int size = Math.min(w, h) / 2;
		int bx = x + (w - size) / 2;
		int by = y + (h - size) / 2 + 4;
		int d = size / 3;
		int rgb = color.rgb(time * 0.1f);
		float glow = 0.75f + 0.25f * (float) Math.sin(time * 4);
		// Cube faces
		Gui2D.fill(g, bx, by, bx + size, by + size, 0xFF5A4632);
		Gui2D.fill(g, bx + d, by - d, bx + size + d, by, 0xFF6E5A3E);
		Gui2D.fill(g, bx, by, bx + size, by + size, Colors.argb(fill.f() * glow, rgb));
		Gui2D.outline(g, bx - 1, by - 1, bx + size + 1, by + size + 1, Colors.argb(0.95f, rgb));
		Gui2D.glow(g, bx - 1, by - 1, bx + size + 1, by + size + 1, rgb, glow, 3);
	}
}
