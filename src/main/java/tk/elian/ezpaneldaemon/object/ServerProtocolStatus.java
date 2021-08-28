package tk.elian.ezpaneldaemon.object;

import java.util.List;

public record ServerProtocolStatus(String version, String htmlMOTD, int onlinePlayers,
                                   int maxPlayers, List<String> playerNames) {
}
