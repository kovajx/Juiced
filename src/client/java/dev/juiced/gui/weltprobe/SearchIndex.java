package dev.juiced.gui.weltprobe;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.juiced.core.ModuleGroup;

/**
 * Type-to-search over module names, bundle part names ("Low Fire") and choice entries ("Kirschblüten"), with
 * German-friendly matching (case-insensitive, umlauts also match ae/oe/ue/ss). Ranking: name prefix, then part or
 * entry prefix, then word prefix anywhere, then contains; ties keep dial order. One hit per channel and focus.
 */
public final class SearchIndex {
	private static final int NAME_PREFIX = 0;
	private static final int PART_PREFIX = 1;
	private static final int WORD_PREFIX = 2;
	private static final int CONTAINS = 3;

	private static @Nullable List<Entry> index;

	private SearchIndex() {
	}

	public static List<SearchHit> search(String query) {
		String wanted = Key.of(query, false).text;

		if (wanted.isEmpty()) {
			return List.of();
		}

		// Best rank per channel and focus; the index order already is dial order, names before parts
		Map<Long, Scored> best = new HashMap<>();
		List<Entry> entries = index();

		for (int order = 0; order < entries.size(); order++) {
			Entry entry = entries.get(order);
			int rank = entry.rank(wanted);

			if (rank < 0) {
				continue;
			}

			long key = (long) entry.channel.index << 16 | (entry.focus + 1);
			Scored known = best.get(key);

			if (known == null || rank < known.rank) {
				best.put(key, new Scored(entry, rank, order));
			}
		}

		List<Scored> sorted = new ArrayList<>(best.values());
		sorted.sort(Comparator.comparingInt((Scored s) -> s.rank).thenComparingInt(s -> s.order));
		List<SearchHit> hits = new ArrayList<>(sorted.size());

		for (Scored scored : sorted) {
			Entry entry = scored.entry;
			hits.add(new SearchHit(entry.channel, entry.focus, entry.label, entry.focus >= 0 ? entry.channel.name() : null));
		}

		return hits;
	}

	private static List<Entry> index() {
		if (index == null) {
			List<Entry> entries = new ArrayList<>();

			for (Channel channel : Channels.all()) {
				entries.add(new Entry(channel, -1, channel.name()));
				ModuleGroup group = channel.group();

				if (group == null) {
					continue;
				}

				if (group.isChoice()) {
					for (int i = 0; i < group.entryCount(); i++) {
						entries.add(new Entry(channel, i, group.entryLabel(i)));
					}
				} else {
					for (int p = 0; p < group.partToggles().size(); p++) {
						entries.add(new Entry(channel, p, group.partLabel(p)));
					}
				}
			}

			index = entries;
		}

		return index;
	}

	private record Scored(Entry entry, int rank, int order) {
	}

	/** One searchable label: a channel name (focus -1), a bundle part or a choice entry. */
	private static final class Entry {
		final Channel channel;
		final int focus;
		final String label;
		/** The label folded with umlauts as plain vowels ("Rückstoß" = "ruckstoss") and spelled out ("rueckstoss"). */
		final Key plain;
		final Key spelled;

		Entry(Channel channel, int focus, String label) {
			this.channel = channel;
			this.focus = focus;
			this.label = label;
			this.plain = Key.of(label, false);
			this.spelled = Key.of(label, true);
		}

		/** Rank of this label for the folded query, or -1 if it doesn't match. */
		int rank(String query) {
			int rank = rank(plain, query);
			int other = rank(spelled, query);

			if (rank < 0 || (other >= 0 && other < rank)) {
				rank = other;
			}

			return rank;
		}

		private int rank(Key key, String query) {
			int at = key.text.indexOf(query);

			if (at < 0) {
				return -1;
			}

			if (at == 0) {
				return focus < 0 ? NAME_PREFIX : PART_PREFIX;
			}

			for (; at >= 0; at = key.text.indexOf(query, at + 1)) {
				if (key.wordStart[at]) {
					return WORD_PREFIX;
				}
			}

			return CONTAINS;
		}
	}

	/** A label reduced to lower-case letters and digits, remembering where its words start. */
	private static final class Key {
		final String text;
		final boolean[] wordStart;

		private Key(String text, boolean[] wordStart) {
			this.text = text;
			this.wordStart = wordStart;
		}

		static Key of(String label, boolean spellUmlauts) {
			StringBuilder text = new StringBuilder(label.length() + 4);
			List<Boolean> starts = new ArrayList<>(label.length() + 4);
			boolean afterGap = true;
			boolean afterLower = false;

			for (int i = 0; i < label.length(); i++) {
				char c = label.charAt(i);
				String folded = fold(c, spellUmlauts);

				if (folded.isEmpty()) {
					afterGap = true;
					afterLower = false;
					continue;
				}

				// A new word after a gap, or a capital inside a word ("TargetHud")
				boolean start = afterGap || (afterLower && Character.isUpperCase(c));

				for (int k = 0; k < folded.length(); k++) {
					text.append(folded.charAt(k));
					starts.add(start && k == 0);
				}

				afterGap = false;
				afterLower = Character.isLowerCase(c);
			}

			boolean[] wordStart = new boolean[starts.size()];

			for (int i = 0; i < wordStart.length; i++) {
				wordStart[i] = starts.get(i);
			}

			return new Key(text.toString(), wordStart);
		}

		/** One character in its searchable form: lower case, no accents, umlauts plain or spelled out; "" for gaps. */
		private static String fold(char c, boolean spellUmlauts) {
			char lower = Character.toLowerCase(c);

			switch (lower) {
				case 'ä':
					return spellUmlauts ? "ae" : "a";
				case 'ö':
					return spellUmlauts ? "oe" : "o";
				case 'ü':
					return spellUmlauts ? "ue" : "u";
				case 'ß':
					return "ss";
				default:
					break;
			}

			if (lower < 128) {
				return Character.isLetterOrDigit(lower) ? String.valueOf(lower) : "";
			}

			// Other accented letters ("é") match their plain letter
			String stripped = Normalizer.normalize(String.valueOf(lower), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
			StringBuilder out = new StringBuilder(stripped.length());

			for (int i = 0; i < stripped.length(); i++) {
				char s = stripped.charAt(i);

				if (Character.isLetterOrDigit(s)) {
					out.append(Character.toLowerCase(s));
				}
			}

			return out.toString().toLowerCase(Locale.ROOT);
		}
	}
}
