package tk.elian.ezpaneldaemon.gson;

import com.google.gson.*;
import tk.elian.ezpaneldaemon.ServerInstance;
import tk.elian.ezpaneldaemon.ServerProtocolStatus;
import tk.elian.ezpaneldaemon.util.ServerProtocol;

import java.lang.reflect.Type;

public class ServerInstanceJsonMapper implements JsonSerializer<ServerInstance> {
	@Override
	public JsonElement serialize(ServerInstance serverInstance, Type type,
	                             JsonSerializationContext jsonSerializationContext) {
		JsonObject root = new JsonObject();

		root.addProperty("id", serverInstance.getServerId());
		root.addProperty("status", serverInstance.isRunning() ? "Online" : "Offline");
		root.addProperty("name", serverInstance.getName());
		root.addProperty("dateCreated", serverInstance.getDateCreated());
		root.addProperty("jarPath", serverInstance.getJarPath());
		root.addProperty("jarPathRelativeTo", serverInstance.getJarPathRelativeTo());
		root.addProperty("maximumMemory", serverInstance.getMaximumMemory());
		root.addProperty("autoStart", serverInstance.isAutoStart());

		double memory = serverInstance.getMemoryUsage();
		root.addProperty("memory", memory);

		JsonArray logs = new JsonArray();

		for (String log : serverInstance.getConsoleLogs()) {
			if (log != null)
				logs.add(log);
		}

		root.add("logs", logs);

		ServerProtocolStatus protocolStatus = serverInstance.isRunning() ? ServerProtocol.fetchStatus("127.0.0.1",
				serverInstance.getPort()) : null;

		root.addProperty("version", protocolStatus == null ? "$offline$" : protocolStatus.getVersion());
		root.addProperty("motd", protocolStatus == null ? "$offline$" : protocolStatus.getHtmlMOTD());
		root.addProperty("onlinePlayers", protocolStatus == null ? 0 : protocolStatus.getOnlinePlayers());
		root.addProperty("maxPlayers", protocolStatus == null ? 0 : protocolStatus.getMaxPlayers());

		JsonArray playerNames = new JsonArray();
		if (protocolStatus != null) {
			for (String name : protocolStatus.getPlayerNames()) {
				playerNames.add(name);
			}
		}

		root.add("playerNames", playerNames);

		return root;
	}
}
