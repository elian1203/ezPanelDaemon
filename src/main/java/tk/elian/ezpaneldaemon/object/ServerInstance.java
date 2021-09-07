package tk.elian.ezpaneldaemon.object;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.database.ServerDatabaseDetails;
import tk.elian.ezpaneldaemon.util.HttpUtil;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ServerInstance {

	private static final Map<Integer, ServerInstance> cachedServers = new HashMap<>();

	private final String serverDirectory;

	private final int serverId;
	private ServerDatabaseDetails databaseDetails;

	private Process process;
	private OutputStream commandOutput;

	private final String[] consoleLogs = new String[500];
	int lineCount = 0;

	private String serverStatus = "Offline";

	private ServerInstance(int serverId, ServerDatabaseDetails databaseDetails, String serverDirectory) {
		this.serverId = serverId;
		this.databaseDetails = databaseDetails;
		this.serverDirectory = serverDirectory;
	}

	public int getServerId() {
		return serverId;
	}

	public String getName() {
		return databaseDetails.name();
	}

	public String getDateCreated() {
		return databaseDetails.dateCreated();
	}

	public String getJavaPath() {
		return databaseDetails.javaPath();
	}

	public String getServerJar() {
		return databaseDetails.serverJar();
	}

	public String getServerJarPath() {
		String serverJar = getServerJar();
		String jarPathRelativeTo = getJarPathRelativeTo();
		String serverPath = getServerPath();

		return switch (jarPathRelativeTo) {
			case "Absolute" -> serverJar;
			case "Server Jar Folder" -> serverPath + "/jar/" + serverJar;
			case "Server Base Directory" -> serverPath + "/" + serverJar;
			default -> serverPath + "/" + serverJar;
		};
	}

	public String getJarPathRelativeTo() {
		return databaseDetails.jarPathRelativeTo();
	}

	public int getMaximumMemory() {
		return databaseDetails.maximumMemory();
	}

	public boolean isAutoStart() {
		return databaseDetails.autoStart();
	}

	public boolean ftpEnabled() {
		return databaseDetails.ftp();
	}

	public int getOwnerId() {
		return databaseDetails.ownerId();
	}

	public String getServerPath() {
		return serverDirectory + "/" + serverId;
	}

	public boolean isRunning() {
		return process != null && process.isAlive();
	}

	public void start() {
		if (!isRunning()) {
			serverStatus = "Starting";

			acceptEula();

			String java = getJavaPath();
			String serverJar = getServerJarPath();
			String serverPath = getServerPath();

			File jarFile = new File(serverJar);
			if (!jarFile.getParentFile().isDirectory()) {
				jarFile.getParentFile().mkdirs();
			}

			int maximumMemory = getMaximumMemory();
			if (maximumMemory == 0)
				maximumMemory = 2048;

			String statement = String.format("%s -Xmx%dM -jar %s nogui", java, maximumMemory, serverJar);

			System.out.println(statement);
			new Thread(() -> {
				try {
					process = Runtime.getRuntime().exec(statement, null, new File(serverPath));
					new Thread(this::listenOnConsole).start();
					System.out.println("started on pid " + process.pid());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}).start();

			if (!cachedServers.containsKey(serverId))
				cachedServers.put(serverId, this);
		}
	}

	public void stop() {
		if (isRunning()) {
			serverStatus = "Stopping";
			// try to use end command if using bungee or waterfall
			String jarName = getServerJar().toLowerCase();
			if (jarName.matches(".*(bungee|waterfall).*")) {
				sendCommand("end");
			} else {
				sendCommand("stop");
			}
			commandOutput = null;
		}
	}

	public void restart() {
		stop();
		new Thread(() -> {
			while (isRunning()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignored) {
				}
			}
			addLog("<span class=\"fw-bold\">[ezPanel]</span> Starting");
			start();
		}).start();
	}

	public void kill() {
		process.destroy();
		serverStatus = "Offline";
	}

	public String[] getConsoleLogs() {
		return consoleLogs;
	}

	public void sendCommand(String command) {
		try {
			if (commandOutput == null)
				commandOutput = process.getOutputStream();

			command += "\n";
			byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
			commandOutput.write(bytes);
			commandOutput.flush();
		} catch (IOException ignored) {
		}
	}

	public double getMemoryUsage() {
		if (!isRunning())
			return 0;

		try {
			byte[] bytes = Files.readAllBytes(Path.of("/proc/" + process.pid() + "/status"));
			String status = new String(bytes);
			String[] lines = status.split("\n");

			for (String line : lines) {
				if (line.startsWith("VmRSS")) {
					line = line.replace(" ", "").replace("\t", "");
					String[] split = line.split(":");

					String memory = split[1];
					memory = memory.substring(0, memory.length() - 2);

					double memoryParsed = Double.parseDouble(memory) / 1024;
					memoryParsed = ((double) ((int) (memoryParsed * 100))) / 100; // limit to hundredths place

					return memoryParsed;
				}
			}

			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}
	}

	public String getStatus() {
		return serverStatus;
	}

	private void updateStatus(String log) {
		if (!isRunning()) {
			serverStatus = "Offline";
		}

		if (log != null) {
			// [11:01:50 INFO]: Done (5.309s)! For help, type "help"
			if (log.matches("\\[[0-9]{2}:[0-9]{2}:[0-9]{2} INFO]: Done \\([0-9]+(.[0-9]+)?s\\).*") // paper
					|| log.matches("\\[[0-9]{2}:[0-9]{2}:[0-9]{2} INFO]: Listening on /.*")) { // waterfall
				serverStatus = "Online";
			} else if (log.equals("<span class=\"fw-bold\">[ezPanel]</span> Received stop command")
					|| log.equals("<span class=\"fw-bold\">[ezPanel]</span> Received restart command")) {
				serverStatus = "Stopping";
			} else if (log.equals("<span class=\"fw-bold\">[ezPanel]</span> Starting")
					|| log.equals("<span class=\"fw-bold\">[ezPanel]</span> Received start command")) {
				serverStatus = "Starting";
			}
		}
	}

	private void listenOnConsole() {
		try {
			InputStream processInput = process.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(processInput));

			String line;
			while (isRunning()) {
				line = reader.readLine();

				if (line == null)
					break;

				addLog(line);
			}
		} catch (IOException e) {
			serverStatus = "Offline";
		}
	}

	public void addLog(String log) {
		if (lineCount < 500) {
			consoleLogs[lineCount++] = log;
		} else {
			shiftArrayLeft();
			consoleLogs[lineCount - 1] = log;
		}

		updateStatus(log);
	}

	private void shiftArrayLeft() {
		if (lineCount - 1 >= 0) System.arraycopy(consoleLogs, 1, consoleLogs, 0, lineCount - 1);
	}

	public void createServerFiles() {
		new Thread(() -> {
			File serverFolder = new File(getServerPath());
			serverFolder.mkdirs();

			if (getJarPathRelativeTo().equals("Absolute"))
				return;

			String jarName = getServerJar();
			File jarFile = new File(getServerJarPath());

			if (!jarFile.exists()) {
				String[] split = jarName.split("-");
				if (split.length != 3)
					return;

				String project = split[0];

				if (!project.equals("paper") && !project.equals("waterfall"))
					return;

				String version = split[1], build = split[2].split("\\.")[0];

				String urlString = String.format("https://papermc.io/api/v2/projects/%s/versions/%s/builds/" +
						"%s/downloads/%s", project, version, build, jarName);

				try {
					if (!jarFile.getParentFile().isDirectory()) {
						jarFile.getParentFile().mkdirs();
					}

					jarFile.createNewFile();

					URL url = new URL(urlString);
					InputStream in = url.openStream();

					Files.copy(in, Paths.get(getServerJarPath()), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void updateJar(MySQLDatabase database) {
		String currentJar = getServerJar();
		if (!currentJar.matches("(paper|waterfall)-[0-9a-zA-Z.]+-[0-9]+\\.jar"))
			return;

		String[] split = currentJar.split("-");
		String project = split[0], version = split[1];

		String url = String.format("https://papermc.io/api/v2/projects/%s/versions/%s", project, version);

		try {
			JsonObject obj = HttpUtil.httpGET(url).getAsJsonObject();

			if (obj.has("error"))
				return;

			JsonArray builds = obj.getAsJsonArray("builds");
			String build = builds.get(builds.size() - 1).getAsString();

			String jar = project + "-" + version + "-" + build + ".jar";
			database.setServerJar(getServerId(), jar);
			setDatabaseDetails(database.getServerDetailsById(serverId));

			createServerFiles();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void acceptEula() {
		File eulaFile = new File(getServerPath(), "eula.txt");

		if (eulaFile.exists()) {
			eulaFile.delete();
		}

		try {
			eulaFile.createNewFile();
			PrintWriter writer = new PrintWriter(new FileOutputStream(eulaFile));

			writer.write("eula=true\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static ServerInstance getServerInstance(int serverId, MySQLDatabase database) {
		ServerInstance serverInstance = cachedServers.get(serverId);

		if (serverInstance == null) {
			ServerDatabaseDetails databaseDetails = database.getServerDetailsById(serverId);

			if (databaseDetails == null)
				return null;

			String serverDirectory = database.getSetting("serverDirectory");
			serverInstance = new ServerInstance(serverId, databaseDetails, serverDirectory);
			cachedServers.put(serverId, serverInstance);
		}

		return serverInstance;
	}

	public void setDatabaseDetails(ServerDatabaseDetails databaseDetails) {
		this.databaseDetails = databaseDetails;
	}

	public static void removeCachedServer(int serverId) {
		cachedServers.remove(serverId);
	}
}
