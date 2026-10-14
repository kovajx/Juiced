package dev.juiced.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** Per-frame context for HUD drawing. */
public final class Hud {
	public final Minecraft minecraft;
	public final Font font;
	public final int width;
	public final int height;
	public final float partialTick;

	public Hud(Minecraft minecraft, int width, int height, float partialTick) {
		this.minecraft = minecraft;
		this.font = minecraft.font;
		this.width = width;
		this.height = height;
		this.partialTick = partialTick;
	}

	public int centerX() {
		return width / 2;
	}

	public int centerY() {
		return height / 2;
	}
}
