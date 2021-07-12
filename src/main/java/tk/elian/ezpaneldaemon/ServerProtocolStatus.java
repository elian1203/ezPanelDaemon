package tk.elian.ezpaneldaemon;

import java.util.List;

public class ServerProtocolStatus {

	private final String version, htmlMOTD;
	private final int onlinePlayers, maxPlayers;
	private final List<String> playerNames;

	public ServerProtocolStatus(String version, String htmlMOTD, int onlinePlayers, int maxPlayers,
	                            List<String> playerNames) {
		this.version = version;
		this.htmlMOTD = htmlMOTD;
		this.onlinePlayers = onlinePlayers;
		this.maxPlayers = maxPlayers;
		this.playerNames = playerNames;
	}

	public String getVersion() {
		return version;
	}

	public String getHtmlMOTD() {
		return htmlMOTD;
	}

	public int getOnlinePlayers() {
		return onlinePlayers;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public List<String> getPlayerNames() {
		return playerNames;
	}
}
