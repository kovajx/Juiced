package dev.juiced.gui.weltprobe;

/** How the straw dummy takes part while a channel is tuned. */
public enum DummyMode {
	/** No dummy. */
	NONE,
	/** Hit every 1.1 s, every 3rd hit a crit: onHit(dummy, crit). */
	HITS,
	/** Like HITS, plus onHealthChange(dummy, damage, true, crit) for damage numbers. */
	DAMAGE,
	/** Like HITS, every 4th hit is lethal: onKill(dummy), then the dummy pops and grows back. */
	KILLS,
	/** Like HITS, every 4th hit pops a totem: onTotemPop(dummy, pops). */
	TOTEMS,
	/** Like HITS, with the dummy being the target (CombatEvents.probeHit) so the marker sits on it. */
	TARGET,
	/** Like TARGET, and the dummy flies back from each hit (alternating sprint and normal hits) and slides home. */
	KNOCKBACK
}
