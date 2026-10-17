package dev.juiced.gui.weltprobe;

import org.jspecify.annotations.Nullable;

/**
 * One search result: a channel, or one part of a bundle ("Low Fire" in Klare Sicht) or one entry of a
 * choice card ("Kirschblüten" in Atmosphäre).
 *
 * @param focus  part index (bundle) or entry index (choice card), -1 for the whole channel
 * @param label  what the card headline shows ("Low Fire", or the channel name)
 * @param partOf the channel name when this is a part or entry ("Klare Sicht"), else null
 */
public record SearchHit(Channel channel, int focus, String label, @Nullable String partOf) {
	public boolean isPart() {
		return focus >= 0;
	}
}
