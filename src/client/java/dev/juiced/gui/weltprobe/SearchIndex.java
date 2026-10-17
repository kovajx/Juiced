package dev.juiced.gui.weltprobe;

import java.util.List;

/**
 * Type-to-search over module names, bundle part names ("Low Fire") and choice entries ("Kirschblüten"), with
 * German-friendly matching (case-insensitive, umlauts also match ae/oe/ue/ss). Ranking: name prefix, then part or
 * entry prefix, then word prefix anywhere, then contains; ties keep dial order. One hit per channel and focus.
 */
public final class SearchIndex {
	private SearchIndex() {
	}

	public static List<SearchHit> search(String query) {
		return List.of(); // TODO(logic)
	}
}
