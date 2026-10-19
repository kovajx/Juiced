package dev.juiced.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.JuicedClient;
import dev.juiced.modules.combat.HitEffects;
import dev.juiced.modules.combat.HitSounds;
import dev.juiced.modules.combat.KillEffects;
import dev.juiced.modules.combat.KnockbackComet;
import dev.juiced.modules.combat.TargetMarker;
import dev.juiced.modules.combat.TotemPops;
import dev.juiced.modules.cosmetic.Aura;
import dev.juiced.modules.cosmetic.Cape;
import dev.juiced.modules.cosmetic.Ears;
import dev.juiced.modules.cosmetic.FloatingBlades;
import dev.juiced.modules.cosmetic.Pet;
import dev.juiced.modules.cosmetic.ShadowPet;
import dev.juiced.modules.cosmetic.Tail;
import dev.juiced.modules.cosmetic.ConeHat;
import dev.juiced.modules.cosmetic.Crown;
import dev.juiced.modules.cosmetic.Halo;
import dev.juiced.modules.cosmetic.HeadWear;
import dev.juiced.modules.cosmetic.Wings;
import dev.juiced.modules.hud.ArmorHud;
import dev.juiced.modules.hud.AttackIndicator;
import dev.juiced.modules.hud.Compass;
import dev.juiced.modules.hud.Cooldowns;
import dev.juiced.modules.hud.EffectsHud;
import dev.juiced.modules.hud.InfoHud;
import dev.juiced.modules.hud.ItemCounter;
import dev.juiced.modules.hud.Keystrokes;
import dev.juiced.modules.hud.LowHealthWarning;
import dev.juiced.modules.hud.ReachDisplay;
import dev.juiced.modules.hud.SessionStats;
import dev.juiced.modules.hud.TargetHud;
import dev.juiced.modules.hud.TotemWarning;
import dev.juiced.modules.system.PerformanceModule;
import dev.juiced.modules.system.ThemeModule;
import dev.juiced.modules.tweaks.ClearView;
import dev.juiced.modules.tweaks.ExplosionTweaks;
import dev.juiced.modules.tweaks.Freelook;
import dev.juiced.modules.tweaks.Fullbright;
import dev.juiced.modules.tweaks.HandView;
import dev.juiced.modules.tweaks.HitDirection;
import dev.juiced.modules.tweaks.LowFire;
import dev.juiced.modules.tweaks.LowShield;
import dev.juiced.modules.tweaks.ToggleSprint;
import dev.juiced.modules.tweaks.Trajectories;
import dev.juiced.modules.tweaks.InventoryAnimations;
import dev.juiced.modules.tweaks.SmoothHotbar;
import dev.juiced.modules.tweaks.ZenMode;
import dev.juiced.modules.tweaks.Zoom;
import dev.juiced.modules.visual.AmbientParticles;
import dev.juiced.modules.visual.Aurora;
import dev.juiced.modules.visual.BirdFlocks;
import dev.juiced.modules.visual.BlockOutline;
import dev.juiced.modules.visual.CustomCrosshair;
import dev.juiced.modules.visual.DamageNumbers;
import dev.juiced.modules.visual.Footprints;
import dev.juiced.modules.visual.JumpCircles;
import dev.juiced.modules.visual.BreathClouds;
import dev.juiced.modules.visual.Constellations;
import dev.juiced.modules.visual.CozyLights;
import dev.juiced.modules.visual.FallingLeaves;
import dev.juiced.modules.visual.FishingMoment;
import dev.juiced.modules.visual.JukeboxNotes;
import dev.juiced.modules.visual.KoiPond;
import dev.juiced.modules.visual.RainRipples;
import dev.juiced.modules.visual.ScreenDrops;
import dev.juiced.modules.visual.ZenGarden;
import dev.juiced.modules.visual.Petals;
import dev.juiced.modules.visual.ProjectileTrails;
import dev.juiced.modules.visual.ScreenEffects;
import dev.juiced.modules.visual.SunRays;
import dev.juiced.modules.visual.Trails;
import dev.juiced.render.Geo;

/** Holds every module and forwards game events to the ones that are switched on. */
public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static void init() {
		ThemeModule theme = new ThemeModule();
		PerformanceModule performance = new PerformanceModule();
		Theme.theme = theme;
		Theme.performance = performance;

		// Combat
		register(ModuleGroup.bundle("hit_effects", "Treffer", Category.COMBAT, true,
				"Was bei jedem Treffer passiert: Effekte am Gegner (Funken, Sterne, Ringe) und ein klarer Hit-Sound, den nur du hörst.")
				.bundled("", HitEffects::new, "Treffer-Effekte", "effects")
				.bundled("sound.", HitSounds::new, "Hit-Sounds", "sounds")
				.build());
		register(new KillEffects());
		register(new TargetMarker());
		register(new TotemPops());
		register(new KnockbackComet());
		// Visuals
		register(new DamageNumbers());
		register(new JumpCircles());
		register(ModuleGroup.bundle("trails", "Trails", Category.VISUALS, false,
				"Leuchtende Spuren hinter dir und hinter Pfeilen, Perlen, Tränken und Dreizacken.")
				.bundled("", Trails::new, "Dein Trail", "player")
				.bundled("projectiles.", ProjectileTrails::new, "Projektil-Trails", "projectiles")
				.build());
		register(new BlockOutline());
		register(new CustomCrosshair());
		register(new ScreenEffects());
		register(new Footprints());
		register(ModuleGroup.choice("ambient", "Atmosphäre", Category.VISUALS,
				"Ruhige Stimmung um dich herum: Glühwürmchen, Laternen, Schmetterlinge, Kirschblüten, Herbstlaub, Lavendel, Rosen, Schnee, Sternschnuppen, Pusteblumen und mehr.")
				.styles("", AmbientParticles::new, "style", "Glühwürmchen", 0, "Laternen", 5, "Schmetterlinge", 6, "Sternschnuppen", 7, "Pusteblumen", 8,
						"Asche", 2, "Seelen", 3, "Sporen", 4)
				.styles("petals.", Petals::new, "style", "Kirschblüten", 0, "Herbstlaub", 1, "Lavendel", 2, "Rosen", 3, "Schnee", 4)
				.build());
		register(new KoiPond());
		register(ModuleGroup.bundle("sky", "Himmel", Category.VISUALS, false,
				"Ruhe am Himmel: nachts Polarlichter und Sternbilder, die langsam nachgezeichnet werden, tagsüber ziehen Vogelschwärme vorbei.")
				.bundled("aurora.", Aurora::new, "Polarlicht", "aurora")
				.bundled("stars.", Constellations::new, "Sternbilder", "constellations")
				.bundled("birds.", BirdFlocks::new, "Vogelschwärme", "birds")
				.build());
		register(ModuleGroup.bundle("environment", "Umgebung", Category.VISUALS, true,
				"Ruhige Natur um dich herum: geharkte Linien im Sand, Regenringe und Pfützen, Atemwolken in der Kälte, Funken über Lagerfeuern, "
						+ "leuchtende Kerzen und Laternen, Noten aus Jukeboxen, fallende Blätter, Sonnenstrahlen durch die Bäume und Wassertropfen auf dem Bildschirm.")
				.bundled("zen.", ZenGarden::new, "Zen-Garten", "zen_garden")
				.bundled("rain.", RainRipples::new, "Regen & Pfützen", "rain")
				.bundled("breath.", BreathClouds::new, "Atemwolken", "breath")
				.bundled("lights.", CozyLights::new, "Lichter & Lagerfeuer", "lights")
				.bundled("jukebox.", JukeboxNotes::new, "Jukebox-Noten", "jukebox")
				.bundled("leaves.", FallingLeaves::new, "Fallende Blätter", "leaves")
				.bundled("sun_rays.", SunRays::new, "Sonnenstrahlen", "sun_rays")
				.bundled("drops.", ScreenDrops::new, "Wassertropfen", "drops")
				.build());
		register(new FishingMoment());
		// Tweaks
		register(new ExplosionTweaks());
		register(ModuleGroup.bundle("clear_view", "Klare Sicht", Category.TWEAKS, true,
				"Alles, was im Kampf die Sicht stört, an einem Ort: Overlays (Hurt-Cam, Totem, Kürbis, Portal, Wasser, Block), Low Fire, Low Shield und die Hand-Ansicht. "
						+ "Dazu zeigt ein Pinselbogen am Fadenkreuz, woher ein Treffer kam.")
				.bundled("", ClearView::new, "Overlays ausblenden", "overlays")
				.bundled("fire.", LowFire::new, "Low Fire", "low_fire")
				.bundled("shield.", LowShield::new, "Low Shield", "low_shield")
				.bundled("hand.", HandView::new, "Hand-Ansicht", "hand_view")
				.bundled("hit_dir.", HitDirection::new, "Treffer-Richtung", "hit_direction")
				.build());
		register(new Trajectories());
		register(new Zoom());
		register(ModuleGroup.bundle("smooth_ui", "Sanfte Oberfläche", Category.TWEAKS, true,
				"Kleine Animationen, die sich gut anfühlen: die Hotbar-Auswahl gleitet und das Item hüpft, Items im Inventar wackeln unter der Maus und fliegen bei Shift-Klick sichtbar an ihren Platz.")
				.bundled("hotbar.", SmoothHotbar::new, "Weiche Hotbar", "hotbar")
				.bundled("inventory.", InventoryAnimations::new, "Inventar-Animationen", "inventory")
				.build());
		register(new ZenMode());
		register(new Freelook());
		register(new ToggleSprint());
		register(new Fullbright());
		// Cosmetics
		register(ModuleGroup.choice("head", "Kopf", Category.COSMETICS,
				"Alles für deinen Kopf: Halo, Krone, China-Hut, Blumenkranz, Zylinder, Zauberhut, Strohhut, Kopfhörer, Kristall, Mondsichel oder eine kleine Regenwolke.")
				.part("halo.", Halo::new, null)
				.part("crown.", Crown::new, null)
				.part("cone_hat.", ConeHat::new, null)
				.part("headwear.", HeadWear::new, "kind", HeadWear.KINDS)
				.build());
		register(ModuleGroup.choice("back", "Rücken", Category.COSMETICS,
				"Ein Cape mit Physik oder Flügel: Engel, Drache, Energie, Schmetterling oder Papier.")
				.part("cape.", Cape::new, null)
				.part("wings.", Wings::new, "style", "Engelsflügel", "Drachenflügel", "Energieflügel", "Schmetterlingsflügel", "Papierflügel")
				.build());
		register(new Aura());
		register(new Ears());
		register(new Tail());
		register(new Pet());
		register(new ShadowPet());
		register(new FloatingBlades());
		// HUD
		register(new TargetHud());
		register(new Keystrokes());
		register(new InfoHud());
		register(new ArmorHud());
		register(new EffectsHud());
		register(new ItemCounter());
		register(new Cooldowns());
		register(new TotemWarning());
		register(new ReachDisplay());
		register(new SessionStats());
		register(new Compass());
		register(new AttackIndicator());
		register(new LowHealthWarning());
		// System
		register(theme);
		register(performance);

		JuicedClient.LOGGER.info("Juiced: {} modules registered", MODULES.size());
	}

	private static void register(Module module) {
		MODULES.add(module);
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static List<Module> in(Category category) {
		List<Module> result = new ArrayList<>();

		for (Module module : MODULES) {
			if (module.category == category) {
				result.add(module);
			}
		}

		return result;
	}

	public static Module byId(String id) {
		for (Module module : MODULES) {
			if (module.id.equals(id)) {
				return module;
			}
		}

		return null;
	}

	// ---- event dispatch (only enabled modules) --------------------------------------------

	public static void tick(Minecraft minecraft) {
		for (int i = 0; i < MODULES.size(); i++) {
			Module module = MODULES.get(i);

			if (module.isEnabled()) {
				module.tick(minecraft);
			}
		}
	}

	public static void renderWorld(Geo geo) {
		for (int i = 0; i < MODULES.size(); i++) {
			Module module = MODULES.get(i);

			if (module.isEnabled()) {
				module.renderWorld(geo);
			}
		}
	}

	public static void renderHud(GuiGraphicsExtractor graphics, Hud hud) {
		for (int i = 0; i < MODULES.size(); i++) {
			Module module = MODULES.get(i);

			if (module.isEnabled()) {
				module.renderHud(graphics, hud);
			}
		}
	}

	public static void hit(LivingEntity target, boolean crit) {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onHit(target, crit);
			}
		}
	}

	public static void kill(LivingEntity target) {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onKill(target);
			}
		}
	}

	public static void healthChange(LivingEntity entity, float amount, boolean byMe, boolean crit) {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onHealthChange(entity, amount, byMe, crit);
			}
		}
	}

	public static void death() {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onDeath();
			}
		}
	}

	public static void totemPop(LivingEntity entity, int pops) {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onTotemPop(entity, pops);
			}
		}
	}

	public static void jump(Entity entity) {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onJump(entity);
			}
		}
	}

	public static void land(Entity entity, float fallHeight) {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onLand(entity, fallHeight);
			}
		}
	}

	/** Clears running effects, for example after leaving a world. */
	public static void clearEffects() {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.clearEffects();
			}
		}
	}
}
