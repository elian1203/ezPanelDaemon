package tk.elian.ezpaneldaemon;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.codehaus.plexus.util.Base64;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.gson.ServerInstanceJsonMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Scanner;

public class DaemonWebServer {

	private final Gson gson;

	private Config config;
	private MySQLDatabase database;
	private HttpServer server;

	public DaemonWebServer(Config config, MySQLDatabase database) {
		this.gson =
				new GsonBuilder().registerTypeAdapter(ServerInstance.class, new ServerInstanceJsonMapper()).create();
		this.config = config;
		this.database = database;
	}

	public void acceptConnections() {
		createServer();
		registerContexts();
		server.start();
	}

	public void denyConnections() {
		server.stop(1);
		server = null;
	}

	private void createServer() {
		try {
			server = HttpServer.create(new InetSocketAddress(12521), 0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void registerContexts() {
		server.createContext("/servers", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			List<ServerInstance> servers = database.getServers();
			JsonArray array = new JsonArray();

			for (ServerInstance serverInstance : servers) {
				JsonElement json = gson.toJsonTree(serverInstance, ServerInstance.class);
				array.add(json);
			}

			String response = gson.toJson(array);
			responseAndClose(httpExchange, response);
		});
		server.createContext("/servers/start", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				minecraft.start();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/stop", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				minecraft.stop();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/restart", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				minecraft.restart();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/kill", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				minecraft.kill();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/sendCommand", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				String command = getInputLine(httpExchange);
				minecraft.sendCommand(command);
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/details", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				String response = gson.toJson(minecraft);
				responseAndClose(httpExchange, response);
			}
		});
		server.createContext("/servers/icon", httpExchange -> {
			int serverId = getServerId(httpExchange);

			if (serverId == -1) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			}

			String serversDirectory = config.getConfig().get("serverDirectory").getAsString();
			String iconPath = serversDirectory + "/" + serverId + "/server-icon.png";

			File file = new File(iconPath);

			if (!file.exists()) {
				httpExchange.sendResponseHeaders(200, 0);
			} else {
				byte[] bytes = Files.readAllBytes(file.toPath());
				httpExchange.sendResponseHeaders(200, bytes.length);

				OutputStream out = httpExchange.getResponseBody();
				out.write(bytes);
				out.flush();
			}

			httpExchange.close();
		});
		server.createContext("/servers/create", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			String input = getInputLine(httpExchange);

			try {
				JsonObject json = JsonParser.parseString(input).getAsJsonObject();

				String javaPath = json.get("javaPath").getAsString();
				String serverJar = json.get("serverJar").getAsString();
				String jarPathRelativeTo = json.get("jarPathRelativeTo").getAsString();
				int maximumMemory = json.get("maximumMemory").getAsInt();
				boolean autoStart = json.get("autoStart").getAsBoolean();
			} catch (Exception e) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			}
		});
		server.createContext("/users/login", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
			}

			responseAndClose(httpExchange, "");
		});
	}

	private boolean authVerify(HttpExchange exchange) {
		if (!exchange.getRequestHeaders().containsKey("Authorization"))
			return false;

		if (exchange.getRequestHeaders().get("Authorization").isEmpty())
			return false;

		String auth = exchange.getRequestHeaders().get("Authorization").get(0);
		String base64 = auth.split(" ")[1];
		String decoded = new String(Base64.decodeBase64(base64.getBytes(StandardCharsets.UTF_8)));
		String[] split = decoded.split(":");

		String user = split[0], pass = split[1];
		return database.authenticateUser(user, pass);
	}

	private int getServerId(HttpExchange exchange) {
		String path = exchange.getRequestURI().getPath();
		String[] split = path.split("/");
		String id = split[split.length - 1];

		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private ServerInstance getMinecraftServer(HttpExchange exchange) {
		int serverId = getServerId(exchange);

		if (serverId == -1)
			return null;
		else
			return ServerInstance.getServerInstance(serverId, database, config);
	}

	private void responseAndClose(HttpExchange httpExchange, String response) throws IOException {
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		httpExchange.sendResponseHeaders(200, bytes.length);

		OutputStream output = httpExchange.getResponseBody();
		output.write(bytes);
		output.flush();

		httpExchange.close();
	}

	private String getInputLine(HttpExchange exchange) {
		InputStream inputStream = exchange.getRequestBody();
		Scanner scanner = new Scanner(inputStream);
		String inputLine = scanner.nextLine();
		scanner.close();

		return inputLine;
	}
}
