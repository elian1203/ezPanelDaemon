package tk.elian.ezpaneldaemon.service;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.codehaus.plexus.util.Base64;
import tk.elian.ezpaneldaemon.EzPanelDaemon;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.gson.ServerInstanceJsonMapper;
import tk.elian.ezpaneldaemon.gson.TaskJsonMapper;
import tk.elian.ezpaneldaemon.object.ServerInstance;
import tk.elian.ezpaneldaemon.object.Setting;
import tk.elian.ezpaneldaemon.object.Task;
import tk.elian.ezpaneldaemon.object.User;
import tk.elian.ezpaneldaemon.service.handler.AuthenticatedHandler;
import tk.elian.ezpaneldaemon.service.handler.AuthenticatedJsonHandler;
import tk.elian.ezpaneldaemon.service.handler.AuthenticatedServerHandler;
import tk.elian.ezpaneldaemon.util.ServerProperties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class WebService {

	private final Gson gson;

	private final MySQLDatabase database;
	private HttpServer server;

	public WebService(MySQLDatabase database) {
		this.gson =
				new GsonBuilder()
						.registerTypeAdapter(ServerInstance.class, new ServerInstanceJsonMapper())
						.registerTypeAdapter(Task.class, new TaskJsonMapper())
						.create();
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
			int port = Integer.parseInt(database.getSetting("webServerPort"));
			server = HttpServer.create(new InetSocketAddress(port), 0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void registerContexts() {
		createAuthenticatedContext(server, "/servers", (httpExchange, authenticatedUser) -> {
			List<ServerInstance> servers = database.getServers();
			JsonArray array = new JsonArray();

			for (ServerInstance serverInstance : servers) {
				if (authenticatedUser.hasServerViewAccess(serverInstance)) {
					JsonElement json = gson.toJsonTree(serverInstance, ServerInstance.class);
					array.add(json);
				}
			}

			String response = gson.toJson(array);
			respondAndClose(httpExchange, 200, response);
		});
		createAuthenticatedContext(server, "/servers/editable", (httpExchange, authenticatedUser) -> {
			ServerInstance inputServer = getMinecraftServer(httpExchange);

			if (inputServer == null) {
				List<ServerInstance> servers = database.getServers();
				JsonArray array = new JsonArray();

				for (ServerInstance serverInstance : servers) {
					if (authenticatedUser.hasServerViewAccess(serverInstance)
							&& authenticatedUser.hasServerEditAccess(serverInstance)) {
						JsonElement json = gson.toJsonTree(serverInstance, ServerInstance.class);
						array.add(json);
					}
				}

				String response = gson.toJson(array);
				respondAndClose(httpExchange, 200, response);
			} else {
				if (!authenticatedUser.hasServerEditAccess(inputServer)) {
					respondAndClose(httpExchange, 403, "");
				} else {
					String response = gson.toJson(inputServer);
					respondAndClose(httpExchange, 200, response);
				}
			}
		});
		createAuthenticatedServerContext(server, "/servers/start", (httpExchange, authenticatedUser, minecraft) -> {
			if (!authenticatedUser.hasServerCommandAccess(minecraft)) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received start command");
			minecraft.start();
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedServerContext(server, "/servers/stop", (httpExchange, authenticatedUser, minecraft) -> {
			if (!authenticatedUser.hasServerCommandAccess(minecraft)) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received stop command");
			minecraft.stop();
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedServerContext(server, "/servers/restart", (httpExchange, authenticatedUser, minecraft) -> {
			if (!authenticatedUser.hasServerCommandAccess(minecraft)) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received restart command");
			minecraft.restart();
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedServerContext(server, "/servers/kill", (httpExchange, authenticatedUser, minecraft) -> {
			if (!authenticatedUser.hasServerCommandAccess(minecraft)) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received kill command");
			minecraft.kill();
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedServerContext(server, "/servers/sendCommand",
				(httpExchange, authenticatedUser, minecraft) -> {
					if (!authenticatedUser.hasServerCommandAccess(minecraft)) {
						respondAndClose(httpExchange, 403, "");
						return;
					}

					String command = getInputLine(httpExchange);
					minecraft.sendCommand(command, authenticatedUser.userId());
					respondAndClose(httpExchange, 200, "");
				});
		createAuthenticatedServerContext(server, "/servers/details", (httpExchange, authenticatedUser, minecraft) -> {
			if (!authenticatedUser.hasServerViewAccess(minecraft)) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			JsonObject obj = gson.toJsonTree(minecraft).getAsJsonObject();

			if (!authenticatedUser.hasServerConsoleAccess(minecraft)) {
				obj.remove("logs");
			}

			String response = gson.toJson(obj);
			respondAndClose(httpExchange, 200, response);
		});
		server.createContext("/servers/icon", httpExchange -> {
			int serverId = getEndingId(httpExchange);

			if (serverId == -1) {
				respondAndClose(httpExchange, 400, "");
				return;
			}

			String serverDirectory = database.getSetting("serverDirectory");
			String iconPath = serverDirectory + "/" + serverId + "/server-icon.png";

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
		createAuthenticatedJsonContext(server, "/servers/create", (httpExchange, authenticatedUser, json) -> {
			if (!authenticatedUser.hasCreateServerAccess()) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			String name = json.get("name").getAsString();
			String javaPath = json.get("javaPath").getAsString();
			String serverJar = json.get("serverJar").getAsString();
			String jarPathRelativeTo = json.get("jarPathRelativeTo").getAsString();
			int maximumMemory = json.get("maximumMemory").getAsInt();
			boolean autoStart = json.get("autoStart").getAsBoolean();
			int ownerId = json.has("owner") ? json.get("owner").getAsInt() : -1;

			int serverId = database.createServer(name, javaPath, serverJar, jarPathRelativeTo, maximumMemory,
					autoStart, ownerId);

			if (serverId == -1) {
				respondAndClose(httpExchange, 500, "");
			} else {
				ServerInstance serverInstance = ServerInstance.getServerInstance(serverId, database);

				if (serverInstance != null) {
					serverInstance.createServerFiles();
				}

				respondAndClose(httpExchange, 200, "");
			}
		});
		createAuthenticatedJsonContext(server, "/servers/update", (httpExchange, authenticatedUser, json) -> {
			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				respondAndClose(httpExchange, 500, "");
				return;
			}

			if (!authenticatedUser.hasServerEditAccess(minecraft)) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			String name = json.get("name").getAsString();
			String javaPath = json.get("javaPath").getAsString();
			String serverJar = json.get("serverJar").getAsString();
			String jarPathRelativeTo = json.get("jarPathRelativeTo").getAsString();
			int maximumMemory = json.get("maximumMemory").getAsInt();
			boolean autoStart = json.get("autoStart").getAsBoolean();
			boolean ftp = json.get("ftp").getAsBoolean();
			int ownerId = json.has("owner") ? json.get("owner").getAsInt() : -1;

			String[] oldJarSplit = minecraft.getServerJar().split("-");
			String[] newJarSplit = serverJar.split("-");

			// don't update if saving settings. must hit jar update to update same jar version
			if (oldJarSplit.length == 3 && newJarSplit.length == 3 && oldJarSplit[0].equals(newJarSplit[0]) &&
					oldJarSplit[1].equals(newJarSplit[1])) {
				serverJar = minecraft.getServerJar();
			}

			database.updateServer(minecraft.getServerId(), name, javaPath, serverJar, jarPathRelativeTo,
					maximumMemory, autoStart, ftp, ownerId);
			minecraft.setDatabaseDetails(database.getServerDetailsById(minecraft.getServerId()));

			respondAndClose(httpExchange, 200, "");
			minecraft.createServerFiles();
		});
		createAuthenticatedServerContext(server, "/servers/updateJar",
				(httpExchange, authenticatedUser, minecraft) -> {
					if (!authenticatedUser.hasServerEditAccess(minecraft)) {
						respondAndClose(httpExchange, 403, "");
						return;
					}

					respondAndClose(httpExchange, 200, "");
					minecraft.updateJar(database);
				});
		createAuthenticatedServerContext(server, "/servers/delete", (httpExchange, authenticatedUser, minecraft) -> {
			if (!authenticatedUser.hasDeleteServerAccess()) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			database.deleteServer(minecraft.getServerId());
			ServerInstance.removeCachedServer(minecraft.getServerId());

			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedContext(server, "/servers/create/config", (httpExchange, authenticatedUser) -> {
			JsonObject responseObject = new JsonObject();
			responseObject.add("javaVersions", gson.toJsonTree(EzPanelDaemon.getJavaVersions()));

			List<User> users = database.getUsers();
			responseObject.add("users", gson.toJsonTree(users));

			List<ServerInstance> servers = database.getServers();
			responseObject.add("servers", gson.toJsonTree(servers));

			String response = gson.toJson(responseObject);
			respondAndClose(httpExchange, 200, response);
		});
		createAuthenticatedContext(server, "/servers/javaVersions", (httpExchange, authenticatedUser) -> {
			String response = gson.toJson(EzPanelDaemon.getJavaVersions());
			respondAndClose(httpExchange, 200, response);
		});
		createAuthenticatedContext(server, "/servers/ftpport", (httpExchange, authenticatedUser) -> {
			boolean enabled = Boolean.parseBoolean(database.getSetting("ftpEnabled"));
			int port = Integer.parseInt(database.getSetting("ftpPort"));

			String response = Integer.toString(enabled ? port : -1);
			respondAndClose(httpExchange, 200, response);
		});
		createAuthenticatedContext(server, "/users", (httpExchange, authenticatedUser) -> {
			List<User> users = database.getUsers();

			String response = gson.toJson(users);
			respondAndClose(httpExchange, response);
		});
		createAuthenticatedContext(server, "/users/login", (httpExchange, authenticatedUser) -> {
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedContext(server, "/users/self", (httpExchange, authenticatedUser) -> {
			respondAndClose(httpExchange, 200, gson.toJson(authenticatedUser));
		});
		createAuthenticatedJsonContext(server, "/users/create", (httpExchange, authenticatedUser, json) -> {
			if (!authenticatedUser.hasCreateUserAccess()) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			try {
				String username = json.get("username").getAsString();
				String email = json.get("email").getAsString();
				String password = json.get("password").getAsString();
				String permissions = json.get("permissions").getAsString();

				if (database.getUser(username) != null) {
					// user already exists
					respondAndClose(httpExchange, 400, "");
					return;
				}

				boolean success = database.createUser(username, email, password, permissions);

				if (success) {
					respondAndClose(httpExchange, 200, "");
				} else {
					respondAndClose(httpExchange, 500, "");
				}
			} catch (Exception ignored) {
			}
		});
		createAuthenticatedJsonContext(server, "/users/set/pass", (httpExchange, authenticatedUser, json) -> {
			int userId = json.get("userId").getAsInt();
			String password = json.get("password").getAsString();

			if (!authenticatedUser.hasModifyUserAccess() && authenticatedUser.userId() != userId) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			database.setUserPassword(userId, password);
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedJsonContext(server, "/users/set/email", (httpExchange, authenticatedUser, json) -> {
			int userId = json.get("userId").getAsInt();
			String email = json.get("email").getAsString();

			if (!authenticatedUser.hasModifyUserAccess() && authenticatedUser.userId() != userId) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			database.setUserEmail(userId, email);
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedJsonContext(server, "/users/set/permissions", (httpExchange, authenticatedUser, json) -> {
			int userId = json.get("userId").getAsInt();
			String permissions = json.get("permissions").getAsString();

			if (!authenticatedUser.hasModifyUserAccess() || authenticatedUser.userId() == userId) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			database.setUserPermissions(userId, permissions);
			respondAndClose(httpExchange, 200, "");
		});
		createAuthenticatedContext(server, "/users/delete", (httpExchange, authenticatedUser) -> {
			int userId = getEndingId(httpExchange);

			if (!authenticatedUser.hasModifyUserAccess() || authenticatedUser.userId() == userId) {
				respondAndClose(httpExchange, 403, "");
				return;
			}

			database.deleteUser(userId);
			respondAndClose(httpExchange, 200, "");
		});
		server.createContext("/settings", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			if (!authenticatedUser.hasGlobalSettingsAccess()) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			List<Setting> settings = database.getAllSettings();
			settings.sort(Comparator.comparing(Setting::order));

			String response = gson.toJson(settings);
			respondAndClose(httpExchange, response);
		});
		server.createContext("/settings/update", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			if (!authenticatedUser.hasGlobalSettingsAccess()) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			JsonObject obj = JsonParser.parseString(getInputLine(httpExchange)).getAsJsonObject();
			obj.entrySet().forEach(e -> {
				// start/stop ftp server if setting changed
				if (e.getKey().equals("ftpEnabled")) {
					boolean currentSetting = Boolean.parseBoolean(database.getSetting("ftpEnabled"));
					if (e.getValue().getAsBoolean() && !currentSetting) {
						EzPanelDaemon.startFtpServer(database);
					} else if (!e.getValue().getAsBoolean() && currentSetting) {
						FtpService.stopFtpServer();
					}
				}

				database.updateSetting(e.getKey(), e.getValue().getAsString());
			});

			respondAndClose(httpExchange, "");
		});
		server.createContext("/settings/tasks", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);
			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(500, 0);
				httpExchange.close();
				return;
			}

			if (!user.hasServerEditAccess(minecraft)) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			List<Task> tasks = database.getTasksForServer(minecraft.getServerId());

			String response = gson.toJson(tasks);
			respondAndClose(httpExchange, response);
		});
		server.createContext("/settings/tasks/create", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			try {
				JsonObject obj = JsonParser.parseString(getInputLine(httpExchange)).getAsJsonObject();

				int serverId = obj.get("serverId").getAsInt();
				String command = obj.get("command").getAsString();
				String days = obj.get("days").getAsString();
				String time = obj.get("time").getAsString();
				String repeat = obj.get("repeat").getAsString();

				User user = getAuthenticatedUser(httpExchange);
				ServerInstance minecraft = database.getServer(serverId);

				if (minecraft == null) {
					httpExchange.sendResponseHeaders(500, 0);
					httpExchange.close();
					return;
				}

				if (!user.hasServerEditAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				database.addTask(serverId, command, days, time, repeat);

				respondAndClose(httpExchange, "");
			} catch (Exception e) {
				httpExchange.sendResponseHeaders(500, 0);
				httpExchange.close();
				return;
			}
		});
		server.createContext("/settings/tasks/update", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			try {
				JsonObject obj = JsonParser.parseString(getInputLine(httpExchange)).getAsJsonObject();

				int taskId = obj.get("taskId").getAsInt();
				String command = obj.get("command").getAsString();
				String days = obj.get("days").getAsString();
				String time = obj.get("time").getAsString();
				String repeat = obj.get("repeat").getAsString();

				User user = getAuthenticatedUser(httpExchange);

				Task task = database.getTaskById(taskId);
				ServerInstance minecraft = database.getServer(task.serverId());

				if (minecraft == null) {
					httpExchange.sendResponseHeaders(500, 0);
					httpExchange.close();
					return;
				}

				if (!user.hasServerEditAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				database.updateTask(taskId, command, days, time, repeat);

				respondAndClose(httpExchange, "");
			} catch (Exception e) {
				httpExchange.sendResponseHeaders(500, 0);
				httpExchange.close();
				return;
			}
		});
		server.createContext("/settings/tasks/delete", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			int taskId = getEndingId(httpExchange);
			Task task = database.getTaskById(taskId);

			User user = getAuthenticatedUser(httpExchange);
			ServerInstance minecraft = database.getServer(task.serverId());

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(500, 0);
				httpExchange.close();
				return;
			}

			if (!user.hasServerEditAccess(minecraft)) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			database.deleteTask(taskId);
			respondAndClose(httpExchange, "");
		});
		server.createContext("/settings/properties", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			int serverId = getEndingId(httpExchange);

			User user = getAuthenticatedUser(httpExchange);
			ServerInstance minecraft = database.getServer(serverId);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(500, 0);
				httpExchange.close();
				return;
			}

			if (!user.hasServerEditAccess(minecraft)) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			String pathToProperties = minecraft.getServerPath() + "/server.properties";
			Map<String, String> properties = ServerProperties.getServerProperties(pathToProperties);

			respondAndClose(httpExchange, gson.toJson(properties));
		});
		server.createContext("/settings/properties/update", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);

			JsonObject obj = JsonParser.parseString(getInputLine(httpExchange)).getAsJsonObject();
			int serverId = obj.get("serverId").getAsInt();

			ServerInstance minecraft = database.getServer(serverId);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(500, 0);
				httpExchange.close();
				return;
			}

			if (!user.hasServerEditAccess(minecraft)) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			String pathToProperties = minecraft.getServerPath() + "/server.properties";

			obj.entrySet().forEach(e -> ServerProperties.setServerProperty(pathToProperties, e.getKey(),
					e.getValue().getAsString()));
			respondAndClose(httpExchange, "");
		});
	}

	private void createAuthenticatedContext(HttpServer server, String endPoint, AuthenticatedHandler handler) {
		server.createContext(endPoint, httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);
			handler.handle(httpExchange, user);
		});
	}

	private void createAuthenticatedServerContext(HttpServer server, String endPoint,
	                                              AuthenticatedServerHandler handler) {
		server.createContext(endPoint, httpExchange -> {
			if (!authVerify(httpExchange)) {
				respondAndClose(httpExchange, 401, "");
				return;
			}

			User user = getAuthenticatedUser(httpExchange);

			int serverId = getEndingId(httpExchange);
			ServerInstance minecraft = database.getServer(serverId);

			if (minecraft == null) {
				respondAndClose(httpExchange, 400, "");
				return;
			}

			handler.handle(httpExchange, user, minecraft);
		});
	}

	private void createAuthenticatedJsonContext(HttpServer server, String endPoint, AuthenticatedJsonHandler handler) {
		server.createContext(endPoint, httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);
			JsonElement element = JsonParser.parseString(getInputLine(httpExchange));

			if (element == null || element.isJsonNull()) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			try {
				handler.handle(httpExchange, user, element.getAsJsonObject());
			} catch (Exception e) {
				e.printStackTrace();
			}
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

	private User getAuthenticatedUser(HttpExchange httpExchange) {
		String auth = httpExchange.getRequestHeaders().get("Authorization").get(0);
		String base64 = auth.split(" ")[1];
		String decoded = new String(Base64.decodeBase64(base64.getBytes(StandardCharsets.UTF_8)));
		String[] split = decoded.split(":");

		String username = split[0];
		Optional<User> user = database.getUsers().stream().filter(u -> u.username().equals(username)).findFirst();

		return user.orElse(null);
	}

	private int getEndingId(HttpExchange exchange) {
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
		int serverId = getEndingId(exchange);

		if (serverId == -1)
			return null;
		else
			return ServerInstance.getServerInstance(serverId, database);
	}

	private void respondAndClose(HttpExchange httpExchange, int code, String response) throws IOException {
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		httpExchange.sendResponseHeaders(code, bytes.length);

		OutputStream output = httpExchange.getResponseBody();
		output.write(bytes);
		output.flush();

		httpExchange.close();
	}

	private void respondAndClose(HttpExchange httpExchange, String response) throws IOException {
		respondAndClose(httpExchange, 200, response);
	}

	private String getInputLine(HttpExchange exchange) {
		InputStream inputStream = exchange.getRequestBody();
		Scanner scanner = new Scanner(inputStream);
		String inputLine = scanner.nextLine();
		scanner.close();

		return inputLine;
	}
}
