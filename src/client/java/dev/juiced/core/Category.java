package dev.juiced.core;

public enum Category {
	COMBAT("Combat", "⚔"),
	VISUALS("Visuals", "✦"),
	TWEAKS("Tweaks", "⚡"),
	COSMETICS("Cosmetics", "♛"),
	HUD("HUD", "▣"),
	SYSTEM("System", "⚙");

	public final String label;
	public final String icon;

	Category(String label, String icon) {
		this.label = label;
		this.icon = icon;
	}
}
