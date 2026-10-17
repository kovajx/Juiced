package dev.juiced.gui.weltprobe;

/** The little channel logo in the top-right corner. */
public enum ProbeStatus {
	/** Switched off, running as a try-out right now. */
	PROBE,
	/** Switched on. */
	AN,
	/** Switched off and not running (turned off in the menu; runs again after tuning away and back). */
	AUS,
	/** Can't be shown live here; the 2D preview runs instead. */
	VORSCHAU,
	/** The current server doesn't allow it. */
	GESPERRT
}
