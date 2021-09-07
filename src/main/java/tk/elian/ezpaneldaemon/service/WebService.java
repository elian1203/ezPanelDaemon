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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

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
				return;
			}

			User user = getAuthenticatedUser(httpExchange);

			List<ServerInstance> servers = database.getServers();
			JsonArray array = new JsonArray();

			for (ServerInstance serverInstance : servers) {
				if (user.hasServerViewAccess(serverInstance)) {
					JsonElement json = gson.toJsonTree(serverInstance, ServerInstance.class);
					array.add(json);
				}
			}

			String response = gson.toJson(array);
			responseAndClose(httpExchange, response);
		});
		server.createContext("/servers/editable", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);
			ServerInstance inputServer = getMinecraftServer(httpExchange);

			if (inputServer == null) {
				List<ServerInstance> servers = database.getServers();
				JsonArray array = new JsonArray();

				for (ServerInstance serverInstance : servers) {
					if (user.hasServerViewAccess(serverInstance) && user.hasServerEditAccess(serverInstance)) {
						JsonElement json = gson.toJsonTree(serverInstance, ServerInstance.class);
						array.add(json);
					}
				}

				String response = gson.toJson(array);
				responseAndClose(httpExchange, response);
			} else {
				if (!user.hasServerEditAccess(inputServer)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
				} else {
					String response = gson.toJson(inputServer);
					responseAndClose(httpExchange, response);
				}
			}
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
				User user = getAuthenticatedUser(httpExchange);

				if (!user.hasServerCommandAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received start command");
				minecraft.start();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/stop", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			ServerInstance minecraft = getMinecraftServer(httpExchange);

			if (minecraft == null) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			} else {
				User user = getAuthenticatedUser(httpExchange);

				if (!user.hasServerCommandAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received stop command");
				minecraft.stop();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/restart", httpExchange -> {
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
				User user = getAuthenticatedUser(httpExchange);

				if (!user.hasServerCommandAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received restart command");
				minecraft.restart();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/kill", httpExchange -> {
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
				User user = getAuthenticatedUser(httpExchange);

				if (!user.hasServerCommandAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				minecraft.addLog("<span class=\"fw-bold\">[ezPanel]</span> Received kill command");
				minecraft.kill();
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/sendCommand", httpExchange -> {
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
				User user = getAuthenticatedUser(httpExchange);

				if (!user.hasServerCommandAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				String command = getInputLine(httpExchange);
				minecraft.sendCommand(command);
				responseAndClose(httpExchange, "");
			}
		});
		server.createContext("/servers/details", httpExchange -> {
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
				User user = getAuthenticatedUser(httpExchange);

				if (!user.hasServerViewAccess(minecraft)) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				JsonObject obj = gson.toJsonTree(minecraft).getAsJsonObject();

				if (!user.hasServerConsoleAccess(minecraft)) {
					obj.remove("logs");
				}

				String response = gson.toJson(obj);
				responseAndClose(httpExchange, response);
			}
		});
		server.createContext("/servers/icon", httpExchange -> {
			int serverId = getEndingId(httpExchange);

			if (serverId == -1) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
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
		server.createContext("/servers/create", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);

			if (!user.hasCreateServerAccess()) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			String input = getInputLine(httpExchange);

			try {
				JsonObject json = JsonParser.parseString(input).getAsJsonObject();

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
					httpExchange.sendResponseHeaders(500, 0);
				} else {
					ServerInstance serverInstance = ServerInstance.getServerInstance(serverId, database);

					if (serverInstance != null) {
						serverInstance.createServerFiles();
					}

					httpExchange.sendResponseHeaders(200, 0);
				}

				httpExchange.close();
			} catch (Exception e) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			}
		});
		server.createContext("/servers/update", httpExchange -> {
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

			String input = getInputLine(httpExchange);

			try {
				JsonObject json = JsonParser.parseString(input).getAsJsonObject();

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

				responseAndClose(httpExchange, "");
				minecraft.createServerFiles();
			} catch (Exception e) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
			}
		});
		server.createContext("/servers/updateJar", httpExchange -> {
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

			responseAndClose(httpExchange, "");
			minecraft.updateJar(database);
		});
		server.createContext("/servers/delete", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);

			if (!user.hasDeleteServerAccess()) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			int serverId = getEndingId(httpExchange);

			if (serverId == -1) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			database.deleteServer(serverId);
			ServerInstance.removeCachedServer(serverId);

			responseAndClose(httpExchange, "");
		});
		server.createContext("/servers/create/config", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			JsonObject responseObject = new JsonObject();
			responseObject.add("javaVersions", gson.toJsonTree(EzPanelDaemon.getJavaVersions()));

			List<User> users = database.getUsers();
			responseObject.add("users", gson.toJsonTree(users));

			List<ServerInstance> servers = database.getServers();
			responseObject.add("servers", gson.toJsonTree(servers));

			String response = gson.toJson(responseObject);
			responseAndClose(httpExchange, response);
		});
		server.createContext("/server/javaVersions", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			String response = gson.toJson(EzPanelDaemon.getJavaVersions());
			responseAndClose(httpExchange, response);
		});
		server.createContext("/servers/ftpport", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}


			boolean enabled = Boolean.parseBoolean(database.getSetting("ftpEnabled"));
			int port = Integer.parseInt(database.getSetting("ftpPort"));

			String response = Integer.toString(enabled ? port : -1);
			responseAndClose(httpExchange, response);
		});
		server.createContext("/users", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			List<User> users = database.getUsers();

			String response = gson.toJson(users);
			responseAndClose(httpExchange, response);
		});
		server.createContext("/users/login", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			responseAndClose(httpExchange, "");
		});
		server.createContext("/users/self", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User user = getAuthenticatedUser(httpExchange);
			responseAndClose(httpExchange, gson.toJson(user));
		});
		server.createContext("/users/create", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			if (!authenticatedUser.hasCreateUserAccess()) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			JsonElement inputElement = JsonParser.parseString(getInputLine(httpExchange));

			if (!inputElement.isJsonObject()) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			try {
				JsonObject obj = inputElement.getAsJsonObject();

				String username = obj.get("username").getAsString();
				String email = obj.get("email").getAsString();
				String password = obj.get("password").getAsString();
				String permissions = obj.get("permissions").getAsString();

				boolean success = database.createUser(username, email, password, permissions);

				if (success) {
					responseAndClose(httpExchange, "");
				} else {
					httpExchange.sendResponseHeaders(500, 0);
					httpExchange.close();
				}
			} catch (Exception ignored) {
			}
		});
		server.createContext("/users/set/pass", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			JsonElement inputElement = JsonParser.parseString(getInputLine(httpExchange));

			if (!inputElement.isJsonObject()) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			try {
				JsonObject obj = inputElement.getAsJsonObject();

				int userId = obj.get("userId").getAsInt();
				String password = obj.get("password").getAsString();

				if (!authenticatedUser.hasModifyUserAccess() && authenticatedUser.userId() != userId) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				database.setUserPassword(userId, password);
				responseAndClose(httpExchange, "");
			} catch (Exception ignored) {
			}
		});
		server.createContext("/users/set/email", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			JsonElement inputElement = JsonParser.parseString(getInputLine(httpExchange));

			if (!inputElement.isJsonObject()) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			try {
				JsonObject obj = inputElement.getAsJsonObject();

				int userId = obj.get("userId").getAsInt();
				String email = obj.get("email").getAsString();

				if (!authenticatedUser.hasModifyUserAccess() && authenticatedUser.userId() != userId) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				database.setUserEmail(userId, email);
				responseAndClose(httpExchange, "");
			} catch (Exception ignored) {
			}
		});
		server.createContext("/users/set/permissions", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			JsonElement inputElement = JsonParser.parseString(getInputLine(httpExchange));

			if (!inputElement.isJsonObject()) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			try {
				JsonObject obj = inputElement.getAsJsonObject();

				int userId = obj.get("userId").getAsInt();
				String permissions = obj.get("permissions").getAsString();

				if (!authenticatedUser.hasModifyUserAccess() || authenticatedUser.userId() == userId) {
					httpExchange.sendResponseHeaders(403, 0);
					httpExchange.close();
					return;
				}

				database.setUserPermissions(userId, permissions);
				responseAndClose(httpExchange, "");
			} catch (Exception ignored) {
			}
		});
		server.createContext("/users/delete", httpExchange -> {
			if (!authVerify(httpExchange)) {
				httpExchange.sendResponseHeaders(401, 0);
				httpExchange.close();
				return;
			}

			User authenticatedUser = getAuthenticatedUser(httpExchange);

			int userId = getEndingId(httpExchange);

			if (userId == -1) {
				httpExchange.sendResponseHeaders(400, 0);
				httpExchange.close();
				return;
			}

			if (!authenticatedUser.hasModifyUserAccess() || authenticatedUser.userId() == userId) {
				httpExchange.sendResponseHeaders(403, 0);
				httpExchange.close();
				return;
			}

			database.deleteUser(userId);
			responseAndClose(httpExchange, "");
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
			responseAndClose(httpExchange, response);
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

			responseAndClose(httpExchange, "");
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
			responseAndClose(httpExchange, response);
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

				responseAndClose(httpExchange, "");
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

				responseAndClose(httpExchange, "");
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
