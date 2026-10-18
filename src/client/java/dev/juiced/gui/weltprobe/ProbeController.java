package dev.juiced.gui.weltprobe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.JuicedClient;
import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Category;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleGroup;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.Setting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Fx;

/**
 * Runs the tuned channel live in the world (spec S2, S4, S6, S12, S13).
 *
 * <p>Tuning a channel stops the previous one ({@code setProbe(false)}, probe focus -1, dummy away if the new channel
 * has none) and starts the new one: if it is switched off and allowed here, {@code module.setProbe(true)} (with
 * {@code ModuleGroup.setProbeFocus(focus)} for a part or entry hit); then the camera aims at the channel's shot,
 * falling back to EGO when a third-person shot doesn't fit (cosmetics then fall back to the 2D preview with
 * "Zu eng für die Kamera"). While the module runs, every client tick calls {@code module.probeTick} and drives the
 * straw dummy for the channel's {@link DummyMode}; {@code module.probeLiveReason} is checked about once a second
 * and decides between the live view and the 2D preview.
 *
 * <p>The tick count handed to {@code probeTick} starts at 0 on every tune (and when a stopped try-out starts
 * again), so modules can tell a new tune from a running one. Every module hook runs at {@link Fx#volumeScale} 0.6.
 */
public final class ProbeController {
	/** One hit every 1.1 s. */
	private static final int HIT_TICKS = 22;
	/** The knockback comet needs longer: the flight, its distance label and the slide home. */
	private static final int KNOCK_TICKS = 48;
	/** The first hit after tuning, once the dummy came up out of the ground. */
	private static final int FIRST_HIT_TICKS = 10;
	private static final int REASON_TICKS = 20;
	private static final float DAMAGE = 4.5f;
	/** Effects on the dummy play a little quieter than in a real fight. */
	private static final float PROBE_VOLUME = 0.6f;
	private static final String TOO_CRAMPED = "Zu eng für die Kamera";
	/**
	 * Effects you only see from outside, like cosmetics: rings and prints on the ground around you, the koi pond,
	 * the raked sand, your trail's orbit, the shadow pet. From your eyes (no room for the camera) they show 2D.
	 */
	private static final Set<String> OUTSIDE_ONLY = Set.of("jump_circles", "footprints", "koi_pond", "zen_garden", "trails", "shadow_pet");

	private final MenuCamera camera;
	private final StrawDummy dummy = new StrawDummy();
	/** Modules whose probe code failed once; logged once so a broken effect can't flood the log. */
	private final Set<String> failed = new HashSet<>();

	private @Nullable Channel channel;
	private int focus = -1;
	/** The shot the camera was sent to for the channel (EGO when cramped, KEEP for 2D). */
	private Shot shot = Shot.KEEP;
	/** Switched off in the menu: the effect stays stopped until the channel is tuned again. */
	private boolean paused;
	private boolean blocked;
	/** The channel's third-person shot had no room, so the camera shows your own view. */
	private boolean cramped;
	/** The world the channel was tuned in, to notice a dimension change. */
	private @Nullable ClientLevel world;
	private int ticks;
	private int reasonTimer;
	private @Nullable String liveReason;
	private int hitTimer;
	private int hits;
	private int lastHitTick;
	private int totemPops;
	private boolean sprintNext = true;
	/** The saved style (choice cards) or part switches (bundles) when the channel was tuned. */
	private long selectionAtTune;

	public ProbeController(MenuCamera camera) {
		this.camera = camera;
	}

	/**
	 * Tunes a channel. {@code focus} is a part (bundle) or entry (choice card) index from a search hit, else -1.
	 * {@code deliberate} moves the camera at once instead of after the zapping pause.
	 */
	public void tune(Channel channel, int focus, boolean deliberate) {
		if (channel == this.channel && focus == this.focus) {
			// The same channel again (a click on its lamp): only the camera, a stopped try-out stays stopped
			aimCamera(channel, deliberate);
			refreshReason(Minecraft.getInstance(), channel);
			return;
		}

		MenuWatch.watch(this);
		Minecraft minecraft = Minecraft.getInstance();
		Channel previous = this.channel;
		Module module = channel.module;

		if (previous != null && previous.module != module) {
			release(previous.module);
		}

		CombatEvents.endProbe(dummy.entity());
		this.channel = channel;
		this.focus = focus;
		paused = false;
		world = minecraft.level;
		ticks = 0;
		hits = 0;
		lastHitTick = -KNOCK_TICKS;
		totemPops = 0;
		sprintNext = true;
		blocked = module.blockedHere();
		ModuleGroup group = channel.group();

		// The focus first, so the parts switch on once, already showing the right one
		if (group != null) {
			group.setProbeFocus(focus);
		}

		if (!blocked && !module.isSwitchedOn()) {
			module.setProbe(true);
		}

		selectionAtTune = selection(channel);
		// The camera moves of Zen-Modus and Freelook belong to the tune, they start as soon as the camera arrives
		updateMotion(module);
		aimCamera(channel, deliberate);

		if (channel.dummy == DummyMode.NONE || blocked) {
			removeDummy();
		} else {
			if (!dummy.placed() && !dummy.regrowing()) {
				placeDummy();
			}

			hitTimer = FIRST_HIT_TICKS;
		}

		// Right away, so the first frame already shows the live view or the right 2D caption
		reasonTimer = REASON_TICKS;
		refreshReason(minecraft, channel);
	}

	public @Nullable Channel channel() {
		return channel;
	}

	public int focus() {
		return focus;
	}

	/** Stops the probe and takes the dummy away (closing, or handing over to the wardrobe or HUD editor). Leaves the camera alone. */
	public void stop() {
		MenuWatch.unwatch(this);
		Channel current = channel;

		if (current != null) {
			release(current.module);
		}

		removeDummy();
		camera.motion(MenuCamera.Motion.NONE, 1f, Float.NaN);
		channel = null;
		focus = -1;
		shot = Shot.KEEP;
		paused = false;
		blocked = false;
		cramped = false;
		world = null;
		liveReason = null;
	}

	/** Every client tick while the menu is open (Screen.tick). */
	public void tick() {
		Channel current = channel;
		Minecraft minecraft = Minecraft.getInstance();

		if (current == null || minecraft.player == null || minecraft.level == null) {
			return;
		}

		Module module = current.module;
		boolean needsDummy = current.dummy != DummyMode.NONE;

		if (minecraft.level != world) {
			// Another world (a dimension change, a respawn): look for room for the camera and the dummy there
			world = minecraft.level;
			aimCamera(current, true);
			reasonTimer = 0;
		}

		dummy.tick();

		// The drawer can block or allow the current server while the channel is tuned
		boolean nowBlocked = module.blockedHere();

		if (nowBlocked != blocked) {
			blocked = nowBlocked;
			reasonTimer = 0;

			if (blocked) {
				module.setProbe(false);
				removeDummy();
			} else if (!paused && !module.isSwitchedOn()) {
				module.setProbe(true);
				ticks = 0;
			}

			aimCamera(current, true);
		}

		boolean running = running(module);

		if (running) {
			int tick = ticks;
			call(module, () -> module.probeTick(minecraft, tick));
		}

		if (--reasonTimer <= 0) {
			reasonTimer = REASON_TICKS;
			Shot wanted = current.shot;

			// The room can change (a teleport, blocks placed next to you): pick the shot again if it did
			if (!blocked && !current.isHud() && wanted != Shot.KEEP && !wanted.firstPerson && camera.fits(wanted) == cramped) {
				boolean wasCramped = cramped;
				aimCamera(current, true);

				if (needsDummy && wasCramped != cramped) {
					// In front of you for your own view, beside you for the stage
					removeDummy();
				}
			}

			// Teleported or respawned in the same world: the dummy follows you
			if (needsDummy && dummy.placed() && dummy.leftBehind(minecraft.player)) {
				removeDummy();
				aimCamera(current, true);
			}

			// Back from another world, or the ground changed: look for a spot again
			if (needsDummy && !blocked && !dummy.placed() && !dummy.regrowing()) {
				placeDummy();
			}

			refreshReason(minecraft, current);
		}

		if (running && needsDummy) {
			if (hitTimer > 0) {
				hitTimer--;
			}

			if (hitTimer <= 0 && dummy.ready()) {
				hit(current);
				hitTimer = current.dummy == DummyMode.KNOCKBACK ? KNOCK_TICKS : HIT_TICKS;
			}
		}

		updateMotion(module);
		ticks++;
	}

	/** Every frame before the menu draws (dummy wobble, smooth dummy motion). */
	public void frame(float partialTick) {
		dummy.frame();
	}

	public ProbeStatus status() {
		Channel current = channel;

		if (current == null) {
			return ProbeStatus.AUS;
		}

		Module module = current.module;

		if (blocked) {
			return ProbeStatus.GESPERRT;
		}

		if (module.isSwitchedOn()) {
			return ProbeStatus.AN;
		}

		if (paused) {
			return ProbeStatus.AUS;
		}

		if (fallbackReason() != null) {
			return ProbeStatus.VORSCHAU;
		}

		return module.isProbing() ? ProbeStatus.PROBE : ProbeStatus.AUS;
	}

	/**
	 * Caption for the framed 2D preview ("Nur nachts", "Wirkt beim Laufen", "Auf diesem Server gesperrt"), or null
	 * when the channel shows live (HUD channels are live: the menu draws their element itself).
	 */
	public @Nullable String fallbackReason() {
		Channel current = channel;

		if (current == null) {
			return null;
		}

		if (blocked) {
			return "Auf diesem Server gesperrt";
		}

		if (current.dummy != DummyMode.NONE) {
			if (!dummy.hasRoom() && !dummy.regrowing()) {
				return "Kein Platz für die Puppe";
			}

			if (current.dummy == DummyMode.KNOCKBACK && !dummy.canFly()) {
				return "Braucht freie Fläche";
			}
		}

		if (current.shot == Shot.KEEP) {
			String card = keepCaption(current.module);

			// A bundle shown as a whole gets the card's own caption; a part, or a single module, says it itself
			if (card != null && current.group() != null && focus < 0) {
				return card;
			}

			if (liveReason != null) {
				return liveReason;
			}

			if (card != null) {
				return card;
			}

			// A colour filter on the whole picture shows live in any shot
			return current.module.id.equals("screen_effects") ? null : "Wirkt beim Spielen";
		}

		// HUD channels show live unless the module says its element would be empty
		return liveReason;
	}

	/**
	 * The player switched the tuned channel on or off in the menu. Off stops the try-out so the effect visibly stops;
	 * it runs again after tuning away and back. On while a try-out runs changes nothing on screen.
	 */
	public void toggled(boolean on) {
		Channel current = channel;

		if (current == null || on != current.module.isSwitchedOn()) {
			// Not about the tuned channel (or nothing changed)
			return;
		}

		Module module = current.module;
		ModuleGroup group = current.group();

		if (on) {
			// Keep what the try-out showed: the focused entry becomes the chosen style, the focused part gets switched on
			if (group != null) {
				keepShown(group);
			}

			if (paused) {
				// It was stopped, so it starts again from the beginning
				paused = false;
				ticks = 0;
			}

			module.setProbe(false);
		} else {
			paused = true;
			module.setProbe(false);
			CombatEvents.endProbe(dummy.entity());
		}

		if (group != null) {
			group.setProbeFocus(-1);
		}

		selectionAtTune = selection(current);
		reasonTimer = REASON_TICKS;
		refreshReason(Minecraft.getInstance(), current);
	}

	/** A style or setting changed (Up/Down, drawer): re-sync the try-out so it shows the change in the same frame. */
	public void restyled() {
		Channel current = channel;

		if (current == null) {
			return;
		}

		ModuleGroup group = current.group();
		long now = selection(current);

		// A new style or part switch replaces the search hit the try-out was showing
		if (group != null && focus >= 0 && now != selectionAtTune) {
			focus = -1;
			group.setProbeFocus(-1);
		}

		selectionAtTune = now;
		reasonTimer = REASON_TICKS;
		refreshReason(Minecraft.getInstance(), current);
		updateMotion(current.module);

		// Show the change on the dummy soon, but don't hit faster than twice a second while a slider is dragged
		int gap = current.dummy == DummyMode.KNOCKBACK ? KNOCK_TICKS - 4 : HIT_TICKS / 2;

		if (current.dummy != DummyMode.NONE && ticks - lastHitTick >= gap) {
			hitTimer = Math.min(hitTimer, 3);
		}
	}

	/** True while the straw dummy stands in the world. */
	public boolean dummyPlaced() {
		return dummy.placed();
	}

	// ---- internals -----------------------------------------------------------------------------

	/** The tuned module runs for the menu: on or tried out, not stopped in the menu, allowed here. */
	private boolean running(Module module) {
		return module.isEnabled() && !paused && !blocked;
	}

	/** Aims the camera for a channel and remembers the shot it goes to (KEEP: it stays). */
	private void aimCamera(Channel channel, boolean deliberate) {
		Shot wanted = channel.shot;
		cramped = false;

		if (blocked) {
			// Only the 2D preview shows: the camera stays where it is
			wanted = Shot.KEEP;
		} else if (channel.isHud()) {
			// The HUD is drawn over your own view; from a first-person shot (or a 2D one) the camera stays
			wanted = camera.thirdPerson() ? Shot.EGO : Shot.KEEP;
		} else if (wanted != Shot.KEEP && !wanted.firstPerson && !camera.fits(wanted)) {
			cramped = true;
			wanted = Shot.EGO;
		}

		camera.aim(wanted, channel.aimYaw, channel.aimPitch, deliberate);
		shot = wanted;
	}

	/** Ends the try-out of a module: its effects stop unless it is switched on. */
	private void release(Module module) {
		module.setProbe(false);

		if (module instanceof ModuleGroup group) {
			group.setProbeFocus(-1);
		}
	}

	/** Beside you for the stage, in front of you when the camera shows your own view. */
	private void placeDummy() {
		dummy.place(camera.stageYaw(), camera.viewYaw(), shot.firstPerson);
	}

	/** The dummy goes in a straw puff, and stops being the combat target. */
	private void removeDummy() {
		CombatEvents.endProbe(dummy.entity());
		dummy.remove(dummy.placed());
	}

	/** Asks the module whether it can be seen right now; while it is stopped in the menu the last answer stays. */
	private void refreshReason(Minecraft minecraft, Channel current) {
		if (paused) {
			// So the picture doesn't jump when you switch it off
			return;
		}

		Module module = current.module;

		if (!running(module)) {
			liveReason = null;
			return;
		}

		liveReason = cramped ? crampedReason(minecraft, module) : ask(module, () -> module.probeLiveReason(minecraft));
	}

	/**
	 * The camera stays in your eyes because there is no room behind it: effects you only see from outside can't be
	 * shown. A bundle stays live if one of its running parts can be seen from the eyes.
	 */
	private @Nullable String crampedReason(Minecraft minecraft, Module module) {
		if (module.category == Category.COSMETICS) {
			return TOO_CRAMPED;
		}

		if (module instanceof ModuleGroup group) {
			String reason = null;
			boolean any = false;
			boolean outside = false;

			for (Module part : group.parts()) {
				if (!part.isEnabled()) {
					continue;
				}

				any = true;

				if (outsideOnly(part)) {
					outside = true;
					continue;
				}

				String partReason = ask(module, () -> part.probeLiveReason(minecraft));

				if (partReason == null) {
					return null;
				}

				if (reason == null) {
					reason = partReason;
				}
			}

			if (!any) {
				return ask(module, () -> module.probeLiveReason(minecraft));
			}

			return outside ? TOO_CRAMPED : reason;
		}

		return outsideOnly(module) ? TOO_CRAMPED : ask(module, () -> module.probeLiveReason(minecraft));
	}

	private static boolean outsideOnly(Module module) {
		return module.category == Category.COSMETICS || OUTSIDE_ONLY.contains(module.id);
	}

	private void hit(Channel current) {
		LivingEntity target = dummy.entity();

		if (target == null) {
			return;
		}

		if (current.dummy == DummyMode.KNOCKBACK && !dummy.canFly()) {
			// No room to fly: the 2D preview shows instead, and a comet without a flight would look broken
			return;
		}

		Module module = current.module;
		hits++;
		lastHitTick = ticks;
		boolean crit = hits % 3 == 0;
		boolean fourth = hits % 4 == 0;

		switch (current.dummy) {
			case HITS -> {
				dummy.hit(crit);
				call(module, () -> module.onHit(target, crit));
			}
			case DAMAGE -> {
				dummy.hit(crit);
				call(module, () -> {
					module.onHit(target, crit);
					module.onHealthChange(target, crit ? DAMAGE * 1.5f : DAMAGE, true, crit);
				});
			}
			case KILLS -> {
				dummy.hit(crit);
				call(module, () -> module.onHit(target, crit));

				if (fourth) {
					call(module, () -> module.onKill(target));
					dummy.kill();
				}
			}
			case TOTEMS -> {
				dummy.hit(crit);
				call(module, () -> module.onHit(target, crit));

				if (fourth) {
					int pops = ++totemPops;
					dummy.totemPopped();
					call(module, () -> module.onTotemPop(target, pops));
				}
			}
			case TARGET -> {
				CombatEvents.probeHit(target, crit, false);
				dummy.hit(crit);
				call(module, () -> module.onHit(target, crit));
			}
			case KNOCKBACK -> {
				// Gold and blue in turn; the comet reads the sprint state in onHit, before the dummy takes off
				boolean sprint = sprintNext;
				sprintNext = !sprintNext;
				CombatEvents.probeHit(target, false, sprint);
				dummy.hit(false);
				call(module, () -> module.onHit(target, false));
				dummy.knock(sprint);
			}
			case NONE -> {
			}
		}
	}

	/** Runs module code for the probe at the quieter volume; a failure is logged once and skipped. */
	private void call(Module module, Runnable action) {
		Fx.volumeScale = PROBE_VOLUME;

		try {
			action.run();
		} catch (RuntimeException e) {
			if (failed.add(module.id)) {
				JuicedClient.LOGGER.warn("Juiced: Weltprobe try-out of {} failed", module.id, e);
			}
		} finally {
			Fx.volumeScale = 1f;
		}
	}

	private @Nullable String ask(Module module, Supplier<@Nullable String> question) {
		try {
			return question.get();
		} catch (RuntimeException e) {
			if (failed.add(module.id)) {
				JuicedClient.LOGGER.warn("Juiced: Weltprobe could not ask {} whether it shows", module.id, e);
			}

			return null;
		}
	}

	/** Caption for channels that only have the 2D preview, for when the module gives none itself; null if not listed. */
	private static @Nullable String keepCaption(Module module) {
		return switch (module.id) {
			case "theme" -> "Färbt Menü und Effekte";
			case "performance" -> "Wirkt auf alle Effekte";
			case "explosions" -> "Wirkt bei Explosionen";
			case "smooth_ui" -> "Wirkt in Hotbar & Inventar";
			default -> null;
		};
	}

	/** Channels whose effect is a camera move get it from the menu camera while they run. */
	private void updateMotion(Module module) {
		MenuCamera.Motion motion = MenuCamera.Motion.NONE;
		float rate = 1f;
		float distance = Float.NaN;

		if (running(module)) {
			if (module.id.equals("zen_mode")) {
				BoolSetting orbit = setting(module, "orbit", BoolSetting.class);
				SliderSetting speed = setting(module, "speed", SliderSetting.class);

				// Zen-Modus without "Kamera kreist langsam" doesn't move the camera either
				if (orbit == null || orbit.on()) {
					motion = MenuCamera.Motion.ORBIT;
					rate = speed != null ? speed.f() : 1f;
				}
			} else if (module.id.equals("freelook")) {
				SliderSetting reach = setting(module, "distance", SliderSetting.class);
				motion = MenuCamera.Motion.SWEEP;
				distance = reach != null ? reach.f() : Float.NaN;
			}
		}

		camera.motion(motion, rate, distance);
	}

	private static <S extends Setting<?>> @Nullable S setting(Module module, String id, Class<S> type) {
		for (Setting<?> setting : module.settings) {
			if (setting.id.equals(id) && type.isInstance(setting)) {
				return type.cast(setting);
			}
		}

		return null;
	}

	/** Switching a card on keeps the look the try-out showed. */
	private void keepShown(ModuleGroup group) {
		if (group.isChoice()) {
			ChoiceSetting kind = group.kindSetting();

			if (kind != null && focus >= 0 && focus < kind.options.length) {
				kind.set(focus);
			}

			return;
		}

		List<BoolSetting> toggles = group.partToggles();

		if (toggles.isEmpty()) {
			return;
		}

		if (focus >= 0 && focus < toggles.size()) {
			toggles.get(focus).set(true);
		} else if (toggles.stream().noneMatch(BoolSetting::on)) {
			// Like switching it on outside the menu: a bundle with every part off starts with the first one
			toggles.get(0).set(true);
		}
	}

	/** The saved choice of a card (style or part switches) as one number, to notice when it changes. */
	private static long selection(Channel channel) {
		ModuleGroup group = channel.group();

		if (group == null) {
			return 0;
		}

		if (group.isChoice()) {
			ChoiceSetting kind = group.kindSetting();
			return kind != null ? kind.index() : 0;
		}

		long bits = 0;
		List<BoolSetting> toggles = group.partToggles();

		for (int i = 0; i < toggles.size() && i < 63; i++) {
			if (toggles.get(i).on()) {
				bits |= 1L << i;
			}
		}

		return bits;
	}
}
