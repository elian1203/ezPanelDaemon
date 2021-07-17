package tk.elian.ezpaneldaemon;

import com.google.gson.JsonObject;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.database.ServerDatabaseDetails;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ServerInstance {

	private static final Map<Integer, ServerInstance> cachedServers = new HashMap<>();

	private final JsonObject config;

	private final int serverId;
	private final ServerDatabaseDetails databaseDetails;

	private Process process;
	private OutputStream commandOutput;

	private final String[] consoleLogs = new String[500];
	int lineCount = 0;

	private ServerInstance(int serverId, Config config, ServerDatabaseDetails databaseDetails) {
		this.serverId = serverId;
		this.config = config.getConfig();
		this.databaseDetails = databaseDetails;
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

	public String getJarPathRelativeTo() {
		return databaseDetails.jarPathRelativeTo();
	}

	public int getMaximumMemory() {
		return databaseDetails.maximumMemory();
	}

	public boolean isAutoStart() {
		return databaseDetails.autoStart();
	}

	public String getServerPath() {
		String serversDirectory = config.get("serverDirectory").getAsString();
		return serversDirectory + "/" + serverId;
	}

	public boolean isRunning() {
		return process != null && process.isAlive();
	}

	public void start() {
		if (!isRunning()) {
			String java = getJavaPath();
			String serverJar = getServerJar();
			String jarPathRelativeTo = getJarPathRelativeTo();
			String serverPath = getServerPath();

			serverJar = switch (jarPathRelativeTo) {
				case "Absolute" -> serverJar;
				case "Server Base Directory" -> serverPath + "/" + serverJar;
				default -> serverPath + "/" + serverJar;
			};

			int maximumMemory = getMaximumMemory();
			if (maximumMemory == 0)
				maximumMemory = config.get("defaultMaximumMemory").getAsInt();

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
			sendCommand("stop");
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
			start();
		}).start();
	}

	public void kill() {
		process.destroy();
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

	public double[] getMemoryAndCPUUsage() {
		return new double[]{-1, -1};
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
		} catch (IOException ignored) {
		}
	}

	public void addLog(String log) {
		if (lineCount < 500) {
			consoleLogs[lineCount++] = log;
		} else {
			shiftArrayLeft();
			consoleLogs[lineCount - 1] = log;
		}
	}

	private void shiftArrayLeft() {
		if (lineCount - 1 >= 0) System.arraycopy(consoleLogs, 1, consoleLogs, 0, lineCount - 1);
	}

	public static ServerInstance getServerInstance(int serverId, MySQLDatabase database, Config config) {
		ServerInstance serverInstance = cachedServers.get(serverId);

		if (serverInstance == null) {
			ServerDatabaseDetails databaseDetails = database.getServerDetailsById(serverId);

			if (databaseDetails == null)
				return null;

			serverInstance = new ServerInstance(serverId, config, databaseDetails);
			cachedServers.put(serverId, serverInstance);
		}

		return serverInstance;
	}

	public static ServerInstance getServerInstance(int serverId, Config config,
	                                               ServerDatabaseDetails databaseDetails) {
		ServerInstance serverInstance = cachedServers.getOrDefault(serverId, new ServerInstance(serverId, config,
				databaseDetails));

		cachedServers.put(serverId, serverInstance);
		return serverInstance;
	}
}
