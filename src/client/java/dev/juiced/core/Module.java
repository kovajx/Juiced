package dev.juiced.core;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.core.setting.Setting;
import dev.juiced.render.Geo;

/**
 * One feature that can be switched on and off in the menu.
 * Every hook does nothing by default, and none of them run while the module is off,
 * so a disabled module costs nothing.
 */
public abstract class Module {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting<?>> settings = new ArrayList<>();
	private final boolean defaultEnabled;
	private boolean enabled;
	/** Running for a moment in the menu (Weltprobe) without being switched on. Never saved. */
	private boolean probing;

	protected Module(String id, String name, Category category, boolean defaultEnabled, String description) {
		this.id = id;
		this.name = name;
		this.category = category;
		this.description = description;
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** True while the module runs: switched on, or tried out in the menu. Everything that draws or reacts asks this. */
	public boolean isEnabled() {
		return enabled || alwaysOn() || probing;
	}

	/** The state the player chose, as saved and shown as AN/AUS. A try-out in the menu doesn't count. */
	public boolean isSwitchedOn() {
		return enabled || alwaysOn();
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		boolean wasRunning = isEnabled();
		this.enabled = enabled;
		runningChanged(wasRunning);
	}

	/** True while the menu tries the module out. */
	public boolean isProbing() {
		return probing;
	}

	/**
	 * Lets the menu run the module for a moment without switching it on: it draws and reacts like an enabled module,
	 * but stays off in the config. Turning the probe off clears its effects unless the module is switched on.
	 */
	public void setProbe(boolean probe) {
		if (probing == probe) {
			return;
		}

		boolean wasRunning = isEnabled();
		probing = probe;
		runningChanged(wasRunning);
	}

	private void runningChanged(boolean wasRunning) {
		boolean running = isEnabled();

		if (running && !wasRunning) {
			onEnable();
		} else if (!running && wasRunning) {
			onDisable();
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void resetToDefaults() {
		setEnabled(defaultEnabled);
		settings.forEach(Setting::reset);
	}

	/** System modules (theme, performance) can't be switched off. */
	public boolean alwaysOn() {
		return false;
	}

	protected void onEnable() {
	}

	/** Clear any running effects here. */
	protected void onDisable() {
	}

	/** Leaving a world: drop running effects while the module stays on. By default the same as {@link #onDisable()}. */
	public void clearEffects() {
		onDisable();
	}

	/** Every client tick (20 per second). */
	public void tick(Minecraft minecraft) {
	}

	/** Once per frame: add 3D geometry for this frame. */
	public void renderWorld(Geo geo) {
	}

	/** Once per frame: draw on the HUD. */
	public void renderHud(GuiGraphicsExtractor graphics, Hud hud) {
	}

	/** You landed a hit. */
	public void onHit(LivingEntity target, boolean crit) {
	}

	/** Something you hit recently died. */
	public void onKill(LivingEntity target) {
	}

	/**
	 * Health (plus absorption) of a nearby entity changed. {@code amount} is positive for damage
	 * and negative for healing. {@code byMe} is true when your hit caused it.
	 */
	public void onHealthChange(LivingEntity entity, float amount, boolean byMe, boolean crit) {
	}

	/** You died. */
	public void onDeath() {
	}

	/** A living entity used a totem of undying. */
	public void onTotemPop(LivingEntity entity, int pops) {
	}

	/** You (or a nearby player, if a module asks for it) jumped. */
	public void onJump(Entity entity) {
	}

	/** You landed after falling or jumping. */
	public void onLand(Entity entity, float fallHeight) {
	}

	/**
	 * Called every client tick while the menu shows this module live and it is running. Most effects show by
	 * themselves; modules that need movement, jumps or other triggers fake a little of it here, because the
	 * player stands still while the menu is open.
	 */
	public void probeTick(Minecraft minecraft, int ticks) {
	}

	/**
	 * Why the menu can't show this module live right now, as a short German caption (about 26 characters at most),
	 * or null if it can. The menu then shows {@link #drawPreview} instead.
	 */
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return null;
	}

	/** True if the current server doesn't allow this module. */
	public boolean blockedHere() {
		return ServerBlockList.blocks(this);
	}

	/**
	 * Small looping animation on the module's card in the menu.
	 * Coordinates are in the card's own space; (x, y, w, h) is the preview area.
	 */
	public void drawPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float time) {
		Previews.genericIcon(graphics, x, y, w, h, time, category.icon);
	}
}
