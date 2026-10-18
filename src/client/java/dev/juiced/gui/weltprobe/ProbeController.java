package dev.juiced.gui.weltprobe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
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
 */
public final class ProbeController {
	/** One hit every 1.1 s. */
	private static final int HIT_TICKS = 22;
	/** The first hit after the dummy came up out of the ground. */
	private static final int FIRST_HIT_TICKS = 10;
	private static final int REASON_TICKS = 20;
	private static final float DAMAGE = 4.5f;
	/** Effects on the dummy play a little quieter than in a real fight. */
	private static final float PROBE_VOLUME = 0.6f;

	private final MenuCamera camera;
	private final StrawDummy dummy = new StrawDummy();
	/** Modules whose probe code failed once; logged once so a broken effect can't flood the log. */
	private final Set<String> failed = new HashSet<>();

	private @Nullable Channel channel;
	private int focus = -1;
	/** Switched off in the menu: the effect stays stopped until the channel is tuned again. */
	private boolean paused;
	private boolean blocked;
	/** The channel's third-person shot had no room, so the camera shows your own view. */
	private boolean cramped;
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
			aimCamera(channel, deliberate);
			return;
		}

		Channel previous = this.channel;
		Module module = channel.module;

		if (previous != null && previous.module != module) {
			release(previous.module);
		}

		CombatEvents.endProbe(dummy.entity());
		this.channel = channel;
		this.focus = focus;
		paused = false;
		ticks = 0;
		reasonTimer = 0;
		liveReason = null;
		hits = 0;
		lastHitTick = -HIT_TICKS;
		totemPops = 0;
		sprintNext = true;
		blocked = module.blockedHere();
		Shot shot = aimCamera(channel, deliberate);

		if (channel.dummy == DummyMode.NONE || blocked) {
			dummy.remove(dummy.placed());
		} else {
			if (!dummy.placed() && !dummy.hasRoom()) {
				dummy.place(camera.stageYaw(), camera.viewYaw(), shot.firstPerson);
			} else if (!dummy.placed() && dummy.hasRoom()) {
				// Growing back after a kill on the previous channel: that continues by itself
			}

			hitTimer = FIRST_HIT_TICKS;
		}

		ModuleGroup group = channel.group();

		// The focus first, so the parts switch on once, already showing the right one
		if (group != null) {
			group.setProbeFocus(focus);
		}

		if (!blocked && !module.isSwitchedOn()) {
			module.setProbe(true);
		}

		selectionAtTune = selection(channel);
	}

	public @Nullable Channel channel() {
		return channel;
	}

	public int focus() {
		return focus;
	}

	/** Stops the probe and takes the dummy away (closing, or handing over to the wardrobe or HUD editor). Leaves the camera alone. */
	public void stop() {
		Channel current = channel;

		if (current != null) {
			release(current.module);
		}

		CombatEvents.endProbe(dummy.entity());
		dummy.remove(dummy.placed());
		camera.motion(MenuCamera.Motion.NONE, 1f);
		channel = null;
		focus = -1;
		paused = false;
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
		ticks++;
		dummy.tick();

		// The drawer can block or allow the current server while the channel is tuned
		boolean nowBlocked = module.blockedHere();

		if (nowBlocked != blocked) {
			blocked = nowBlocked;
			reasonTimer = 0;

			if (blocked) {
				module.setProbe(false);
				dummy.remove(dummy.placed());
			} else if (!paused && !module.isSwitchedOn()) {
				module.setProbe(true);
			}
		}

		boolean running = module.isEnabled() && !paused;

		if (running) {
			int tick = ticks;
			call(module, () -> module.probeTick(minecraft, tick));
		}

		if (--reasonTimer <= 0) {
			reasonTimer = REASON_TICKS;

			// While paused the last answer stays, so the picture doesn't jump when you switch it off
			if (!paused) {
				liveReason = running ? ask(module, () -> module.probeLiveReason(minecraft)) : null;
			}

			// Back from another world, or the ground changed: look for a spot again
			if (current.dummy != DummyMode.NONE && !blocked && !dummy.placed() && !dummy.hasRoom()) {
				dummy.place(camera.stageYaw(), camera.viewYaw(), cramped || camera.shot().firstPerson);
			}
		}

		if (running && current.dummy != DummyMode.NONE) {
			if (hitTimer > 0) {
				hitTimer--;
			}

			if (hitTimer <= 0 && dummy.ready()) {
				hit(current);
				hitTimer = HIT_TICKS;
			}
		}

		camera.motion(running ? motionOf(module) : MenuCamera.Motion.NONE, motionRate(module));
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

		if (module.blockedHere()) {
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

		Module module = current.module;

		if (module.blockedHere()) {
			return "Auf diesem Server gesperrt";
		}

		if (cramped) {
			// Effects that are only worth seeing from outside: cosmetics, and the two camera moves
			if (current.category() == Category.COSMETICS) {
				return "Zu eng für die Kamera";
			}

			if (module.id.equals("zen_mode")) {
				return "Hier kein Platz zum Kreisen";
			}

			if (module.id.equals("freelook")) {
				return "Hier kein Platz hinter dir";
			}
		}

		if (current.dummy != DummyMode.NONE) {
			if (!dummy.hasRoom()) {
				return "Kein Platz für die Puppe";
			}

			if (current.dummy == DummyMode.KNOCKBACK && !dummy.canFly()) {
				return "Braucht freie Fläche";
			}
		}

		if (current.isHud() && current.hudModule() != null) {
			return null;
		}

		if (liveReason != null) {
			return liveReason;
		}

		return current.shot == Shot.KEEP ? keepCaption(module) : null;
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
			paused = false;

			// Keep what the try-out showed: the focused entry becomes the chosen style, the focused part gets switched on
			if (group != null) {
				keepShown(group);
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

		focus = on ? focus : -1;
		reasonTimer = 0;
		selectionAtTune = selection(current);
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
		reasonTimer = 0;

		// Show the change on the dummy soon, but don't hit faster than twice a second while a slider is dragged
		if (current.dummy != DummyMode.NONE && ticks - lastHitTick >= HIT_TICKS / 2) {
			hitTimer = Math.min(hitTimer, 3);
		}
	}

	/** True while the straw dummy stands in the world. */
	public boolean dummyPlaced() {
		return dummy.placed();
	}

	// ---- internals -----------------------------------------------------------------------------

	/** Aims the camera for a channel and returns the shot it goes to (KEEP: it stays). */
	private Shot aimCamera(Channel channel, boolean deliberate) {
		Shot shot = channel.shot;
		cramped = false;

		if (channel.isHud()) {
			// The HUD is drawn over your own view; from a first-person shot (or a 2D one) the camera stays
			shot = camera.thirdPerson() ? Shot.EGO : Shot.KEEP;
		} else if (shot != Shot.KEEP && !shot.firstPerson && !camera.fits(shot)) {
			cramped = true;
			shot = Shot.EGO;
		}

		camera.aim(shot, channel.aimYaw, channel.aimPitch, deliberate);
		return shot;
	}

	/** Ends the try-out of a module: its effects stop unless it is switched on. */
	private void release(Module module) {
		module.setProbe(false);

		if (module instanceof ModuleGroup group) {
			group.setProbeFocus(-1);
		}
	}

	private void hit(Channel current) {
		LivingEntity target = dummy.entity();

		if (target == null) {
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

	/** Caption for channels that only have the 2D preview, when the module gives none itself. */
	private static @Nullable String keepCaption(Module module) {
		return switch (module.id) {
			// A colour filter on the whole picture: live in any shot
			case "screen_effects" -> null;
			case "theme" -> "Färbt Menü und Effekte";
			case "performance" -> "Wirkt auf alle Effekte";
			case "explosions" -> "Wirkt bei Explosionen";
			case "smooth_ui" -> "Wirkt in Hotbar & Inventar";
			default -> "Wirkt beim Spielen";
		};
	}

	/** Channels whose effect is a camera move get it from the menu camera. */
	private static MenuCamera.Motion motionOf(Module module) {
		if (module.id.equals("zen_mode")) {
			BoolSetting orbit = setting(module, "orbit", BoolSetting.class);
			return orbit == null || orbit.on() ? MenuCamera.Motion.ORBIT : MenuCamera.Motion.NONE;
		}

		if (module.id.equals("freelook") && !module.blockedHere()) {
			return MenuCamera.Motion.SWEEP;
		}

		return MenuCamera.Motion.NONE;
	}

	private static float motionRate(Module module) {
		SliderSetting speed = module.id.equals("zen_mode") ? setting(module, "speed", SliderSetting.class) : null;
		return speed != null ? speed.f() : 1f;
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
