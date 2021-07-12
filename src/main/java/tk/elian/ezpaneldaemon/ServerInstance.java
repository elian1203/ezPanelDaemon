package tk.elian.ezpaneldaemon;

import com.google.gson.JsonObject;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.database.ServerDatabaseDetails;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ServerInstance {

	private static Map<Integer, ServerInstance> runningServers = new HashMap<>();

	private JsonObject config;

	private final int serverId;
	private final ServerDatabaseDetails databaseDetails;

	private Process process;

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
		return databaseDetails.name;
	}

	public int getPort() {
		return databaseDetails.port;
	}

	public String getDateCreated() {
		return databaseDetails.dateCreated;
	}

	public String getJarPath() {
		return databaseDetails.jarPath;
	}

	public String getJarPathRelativeTo() {
		return databaseDetails.jarPathRelativeTo;
	}

	public int getMaximumMemory() {
		return databaseDetails.maximumMemory;
	}

	public boolean isAutoStart() {
		return databaseDetails.autoStart;
	}

	public boolean isRunning() {
		return process != null && process.isAlive();
	}

	public void start() {
		if (process == null || !process.isAlive()) {
			String java = config.get("javaPath").getAsString();
			String serversDirectory = config.get("serverDirectory").getAsString();
			String serverJar = config.get("defaultJar").getAsString();
			int maximumMemory = config.get("defaultMaximumMemory").getAsInt();

			String serverPath = serversDirectory + "/" + serverId;

			String statement = String.format("%s -Xmx%dM -jar %s/%s nogui", java, maximumMemory,
					serverPath, serverJar);

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

			runningServers.put(serverId, this);
		}
	}

	public void stop() {
		if (isRunning()) {
			sendCommand("stop");
			runningServers.remove(serverId);
		}
	}

	public void restart() {
		stop();
		start();
	}

	public void kill() {
		process.destroy();
	}

	public String[] getConsoleLogs() {
		return consoleLogs;
	}

	public void sendCommand(String command) {
		OutputStream output = process.getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output));

		try {
			writer.write(command + "\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public double getMemoryUsage() {
		if (!isRunning())
			return -1;

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

			return -1;
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
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
			while (process.isAlive()) {
				line = reader.readLine();
				if (line != null) {
					if (lineCount < 500) {
						consoleLogs[lineCount++] = line;
					} else {
						shiftArrayLeft();
						consoleLogs[lineCount - 1] = line;
					}
				}
			}
		} catch (IOException ignored) {
		}
	}

	private void shiftArrayLeft() {
		if (lineCount - 1 >= 0) System.arraycopy(consoleLogs, 1, consoleLogs, 0, lineCount - 1);
	}

	public static ServerInstance getServerInstance(int serverId, MySQLDatabase database, Config config) {
		ServerInstance serverInstance = runningServers.get(serverId);

		if (serverInstance == null) {
			ServerDatabaseDetails databaseDetails = database.getServerDetailsById(serverId);

			if (databaseDetails == null)
				return null;

			serverInstance = new ServerInstance(serverId, config, databaseDetails);
		}

		return serverInstance;
	}

	public static ServerInstance getServerInstance(int serverId, Config config,
	                                               ServerDatabaseDetails databaseDetails) {
		return runningServers.getOrDefault(serverId, new ServerInstance(serverId, config, databaseDetails));
	}
}
