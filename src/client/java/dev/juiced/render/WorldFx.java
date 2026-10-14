package dev.juiced.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.juiced.core.Clock;
import dev.juiced.core.ModuleManager;
import dev.juiced.modules.visual.BlockOutline;

/**
 * Hooks the 3D effects into world rendering.
 *
 * <p>During extraction (main thread, game state readable) all modules write their geometry into a
 * {@link Geo}. During drawing, that finished buffer is submitted as one custom geometry call.
 * Two buffers alternate so a frame being drawn is never overwritten.
 */
public final class WorldFx {
	private static final Geo FIRST = new Geo();
	private static final Geo SECOND = new Geo();
	private static Geo building = FIRST;
	private static volatile Geo ready;

	private WorldFx() {
	}

	public static void init() {
		LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register(BlockOutline::capture);
		LevelExtractionEvents.END_EXTRACTION.register(WorldFx::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(WorldFx::submit);
		// Hide the vanilla outline while our own is on
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !BlockOutline.replacesVanilla());
	}

	private static void extract(LevelExtractionContext context) {
		Clock.frame();
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			ready = null;
			return;
		}

		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true);
		Geo geo = building;
		geo.begin(camera.x, camera.y, camera.z, partialTick);

		ModuleManager.renderWorld(geo);
		Sparks.update(Clock.dt());
		Sparks.render(geo);

		ready = geo.isEmpty() ? null : geo;
		building = geo == FIRST ? SECOND : FIRST;
	}

	private static void submit(LevelRenderContext context) {
		Geo frame = ready;

		if (frame == null) {
			return;
		}

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(), (pose, buffer) -> frame.emit(pose, buffer));
	}
}
