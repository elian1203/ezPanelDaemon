package tk.elian.ezpaneldaemon.gson;

import com.google.gson.*;
import tk.elian.ezpaneldaemon.ServerInstance;
import tk.elian.ezpaneldaemon.ServerProtocolStatus;
import tk.elian.ezpaneldaemon.util.MOTDToHTML;
import tk.elian.ezpaneldaemon.util.ServerProperties;
import tk.elian.ezpaneldaemon.util.ServerProtocol;

import java.lang.reflect.Type;
import java.util.Map;

public class ServerInstanceJsonMapper implements JsonSerializer<ServerInstance> {
	@Override
	public JsonElement serialize(ServerInstance serverInstance, Type type,
	                             JsonSerializationContext jsonSerializationContext) {
		JsonObject root = new JsonObject();

		root.addProperty("id", serverInstance.getServerId());
		root.addProperty("status", serverInstance.isRunning() ? "Online" : "Offline");
		root.addProperty("name", serverInstance.getName());
		root.addProperty("dateCreated", serverInstance.getDateCreated());
		root.addProperty("serverJar", serverInstance.getServerJar());
		root.addProperty("jarPathRelativeTo", serverInstance.getJarPathRelativeTo());
		root.addProperty("maximumMemory", serverInstance.getMaximumMemory());
		root.addProperty("autoStart", serverInstance.isAutoStart());

		double memory = serverInstance.getMemoryUsage();
		root.addProperty("memory", memory);

		String pathToProperties = serverInstance.getServerPath() + "/server.properties";
		Map<String, String> serverProperties = ServerProperties.getServerProperties(pathToProperties);

		String motd = MOTDToHTML.convertToHtml(serverProperties.getOrDefault("motd", "A Minecraft Server"));
		int port = Integer.parseInt(serverProperties.getOrDefault("server-port", "25565"));

		root.addProperty("motd", motd);
		root.addProperty("maxPlayers", Integer.parseInt(serverProperties.getOrDefault("max-players", "20")));
		root.addProperty("port", port);
		root.addProperty("world", serverProperties.getOrDefault("level-name", "world"));
		root.addProperty("difficulty", serverProperties.getOrDefault("difficulty", "easy"));
		root.addProperty("serverIp", serverProperties.getOrDefault("server-ip", ""));

		ServerProtocolStatus protocolStatus = serverInstance.isRunning() ? ServerProtocol.fetchStatus("127.0.0.1",
				port) : null;

		root.addProperty("version", protocolStatus == null ? "$offline$" : protocolStatus.version());
		root.addProperty("onlinePlayers", protocolStatus == null ? 0 : protocolStatus.onlinePlayers());

		JsonArray playerNames = new JsonArray();
		if (protocolStatus != null) {
			for (String name : protocolStatus.playerNames()) {
				playerNames.add(name);
			}
		}

		root.add("playerNames", playerNames);

		JsonArray logs = new JsonArray();

		for (String log : serverInstance.getConsoleLogs()) {
			if (log != null)
				logs.add(log);
		}

		root.add("logs", logs);

		return root;
	}
}
