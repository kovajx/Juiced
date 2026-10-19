package dev.juiced.core;

public enum Category {
	COMBAT("Combat", "Kampf", "⚔", 0xFF5A5F),
	VISUALS("Visuals", "Optik", "✦", 0xFFC23D),
	TWEAKS("Tweaks", "Tweaks", "⚡", 0x7EE06A),
	COSMETICS("Cosmetics", "Kosmetik", "♛", 0xFF7AC6),
	HUD("HUD", "HUD", "▣", 0x4FC3F7),
	SYSTEM("System", "System", "⚙", 0xB39DFF);

	public final String label;
	/** German name, used by the Weltprobe menu. */
	public final String de;
	public final String icon;
	/** The category's own colour (0xRRGGBB), used by the Weltprobe menu. */
	public final int color;

	Category(String label, String de, String icon, int color) {
		this.label = label;
		this.de = de;
		this.icon = icon;
		this.color = color;
	}
}
