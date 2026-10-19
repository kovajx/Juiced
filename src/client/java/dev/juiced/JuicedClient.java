package dev.juiced;

import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Config;
import dev.juiced.core.ModuleManager;
import dev.juiced.core.Theme;
import dev.juiced.gui.DeckScreen;
import dev.juiced.gui.weltprobe.WeltprobeScreen;
import dev.juiced.modules.visual.CustomCrosshair;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.JuicedHud;
import dev.juiced.render.NearbyBlocks;
import dev.juiced.render.Sparks;
import dev.juiced.render.WorldFx;

public class JuicedClient implements ClientModInitializer {
	public static final String MOD_ID = "juiced";
	public static final Logger LOGGER = LoggerFactory.getLogger("Juiced");

	/** Opens the menu. Right Shift by default, rebindable in Options > Controls. */
	public static KeyMapping MENU_KEY;
	/** Hold to zoom. C by default. */
	public static KeyMapping ZOOM_KEY;
	/** Hold (or toggle) to look around in third person. Left Alt by default. */
	public static KeyMapping FREELOOK_KEY;

	private static boolean inWorld;

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.juiced.menu", SDLScancode.SDL_SCANCODE_RSHIFT, category));
		ZOOM_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.juiced.zoom", SDLScancode.SDL_SCANCODE_C, category));
		FREELOOK_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.juiced.freelook", SDLScancode.SDL_SCANCODE_LALT, category));

		AttackEntityCallback.EVENT.register(CombatEvents::onAttack);

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (MENU_KEY.consumeClick()) {
				if (minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(Theme.classicMenu() ? new DeckScreen() : new WeltprobeScreen());
				}
			}

			boolean nowInWorld = minecraft.player != null && minecraft.level != null;

			if (inWorld && !nowInWorld) {
				// Left the world: drop running effects so nothing lingers into the next one
				ModuleManager.clearEffects();
				Sparks.clear();
				FreeCamera.reset();
				NearbyBlocks.clear();
			}

			inWorld = nowInWorld;
			CombatEvents.tick(minecraft);

			if (nowInWorld) {
				ModuleManager.tick(minecraft);
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), JuicedHud::render);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, deltaTracker) -> {
			if (CustomCrosshair.replacesVanilla()) {
				CustomCrosshair.INSTANCE.render(graphics, deltaTracker);
			} else {
				vanilla.extractRenderState(graphics, deltaTracker);
			}
		});
		WorldFx.init();

		LOGGER.info("Juiced loaded. Press Right Shift in game to open the menu.");
	}
}
