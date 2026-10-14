package dev.juiced.core;

import java.util.ArrayList;
import java.util.List;

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

	public boolean isEnabled() {
		return enabled || alwaysOn();
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;

		if (enabled) {
			onEnable();
		} else {
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
	 * Small looping animation on the module's card in the menu.
	 * Coordinates are in the card's own space; (x, y, w, h) is the preview area.
	 */
	public void drawPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float time) {
		Previews.genericIcon(graphics, x, y, w, h, time, category.icon);
	}
}
