package dev.juiced.gui.weltprobe;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Type-to-search state of the menu: the query, its hits and which hit is selected. After Enter the query is
 * "done": it stays visible, and the next letter starts a new one instead of extending it.
 */
final class Search {
	private static final int MAX_LENGTH = 24;

	private String query = "";
	private boolean done;
	private List<SearchHit> hits = List.of();
	private int selected;
	private boolean[] matches = new boolean[0];

	boolean active() {
		return !query.isEmpty();
	}

	String query() {
		return query;
	}

	boolean done() {
		return done;
	}

	List<SearchHit> hits() {
		return hits;
	}

	@Nullable SearchHit current() {
		return hits.isEmpty() ? null : hits.get(selected);
	}

	/** True if the channel at this dial position is among the hits. */
	boolean matches(int index) {
		return index >= 0 && index < matches.length && matches[index];
	}

	/** Adds typed text, or starts over when the last query was done. */
	void type(String text) {
		if (done) {
			query = "";
			done = false;
		}

		if (query.length() + text.length() <= MAX_LENGTH) {
			query += text;
			run();
		}
	}

	/** Deletes the last character; editing a done query makes it live again. */
	void backspace() {
		if (!query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			done = false;
			run();
		}
	}

	void clear() {
		query = "";
		done = false;
		hits = List.of();
		selected = 0;
		matches = new boolean[0];
	}

	void finish() {
		done = true;
	}

	/** Selects the previous or next hit. Returns false at either end. */
	boolean step(int direction) {
		int next = Math.max(0, Math.min(hits.size() - 1, selected + direction));

		if (next == selected || hits.isEmpty()) {
			return false;
		}

		selected = next;
		return true;
	}

	private void run() {
		hits = query.isBlank() ? List.of() : SearchIndex.search(query);
		selected = 0;
		matches = new boolean[Channels.all().size()];

		for (SearchHit hit : hits) {
			int index = hit.channel().index;

			if (index >= 0 && index < matches.length) {
				matches[index] = true;
			}
		}
	}
}
