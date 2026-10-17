package dev.juiced.core;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import dev.juiced.core.setting.ButtonSetting;
import dev.juiced.core.setting.StringListSetting;

/**
 * A list of servers where a module stays off, because some servers don't allow it.
 * Adds two rows to the module's card: whether the current server is blocked (with a button to block
 * or allow it) and the whole list (with a button back to the defaults).
 */
public final class ServerBlockList {
	private static final Map<Module, ServerBlockList> BY_MODULE = new IdentityHashMap<>();

	private final StringListSetting blocked;
	private final @Nullable Runnable onBlock;

	private ServerBlockList(Module module, List<String> defaults, @Nullable Runnable onBlock) {
		this.onBlock = onBlock;
		BY_MODULE.put(module, this);
		blocked = module.add(new StringListSetting("blocked_servers", defaults));
		module.add(new ButtonSetting("server", this::serverText, this::serverButtonText, this::toggleCurrentServer));
		module.add(new ButtonSetting("server_list", this::listText, () -> "Standard", this::resetList));
	}

	/**
	 * Adds the block list rows to {@code module}. Call it from a field initializer, like the other settings.
	 * {@code onBlock} runs when the current server gets blocked, to stop what the module is doing right now.
	 */
	public static ServerBlockList add(Module module, List<String> defaults, @Nullable Runnable onBlock) {
		return new ServerBlockList(module, defaults, onBlock);
	}

	/** True if {@code module} has a block list and the current server is on it. */
	public static boolean blocks(Module module) {
		ServerBlockList list = BY_MODULE.get(module);
		return list != null && list.blockedHere();
	}

	/** Host of the server you're on, lower case without port; null in singleplayer. */
	private static @Nullable String currentHost() {
		ServerData server = Minecraft.getInstance().getCurrentServer();

		if (server == null || server.ip == null) {
			return null;
		}

		return normalize(server.ip);
	}

	static String normalize(String address) {
		String host = address.trim().toLowerCase(Locale.ROOT);

		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}

		// Strip a port, but leave IPv6 addresses alone
		int colon = host.lastIndexOf(':');

		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}

		return host;
	}

	/** True if {@code host} is a listed server or one of its subdomains (mc.hypixel.net matches hypixel.net). */
	static boolean matches(String host, List<String> entries) {
		for (String entry : entries) {
			if (covers(normalize(entry), host)) {
				return true;
			}
		}

		return false;
	}

	private static boolean covers(String blockedHost, String host) {
		return !blockedHost.isEmpty() && (host.equals(blockedHost) || host.endsWith("." + blockedHost));
	}

	/** True while you're on a server from the list. Always false in singleplayer. */
	public boolean blockedHere() {
		String host = currentHost();
		return host != null && matches(host, blocked.get());
	}

	private String serverText() {
		String host = currentHost();

		if (host == null) {
			return "Einzelspieler: immer erlaubt";
		}

		return host + (blockedHere() ? ": gesperrt" : ": erlaubt");
	}

	private String serverButtonText() {
		if (currentHost() == null) {
			return "—";
		}

		return blockedHere() ? "Erlauben" : "Sperren";
	}

	private void toggleCurrentServer() {
		String host = currentHost();

		if (host == null) {
			return;
		}

		List<String> list = blocked.get();

		if (blockedHere()) {
			// Remove every entry that covers this server
			list.removeIf(entry -> covers(normalize(entry), host));
		} else {
			list.add(host);

			if (onBlock != null) {
				onBlock.run();
			}
		}

		Config.save();
	}

	private String listText() {
		List<String> list = blocked.get();
		return list.isEmpty() ? "Gesperrt: keine Server" : "Gesperrt: " + String.join(", ", list);
	}

	private void resetList() {
		blocked.reset();
		Config.save();
	}
}
