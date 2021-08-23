package tk.elian.ezpaneldaemon;

import com.google.gson.JsonObject;
import sun.misc.Signal;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.ftp.MinecraftFtpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class EzPanelDaemon {

	public static void main(String[] args) {
		Config config = new Config();

		if (args.length > 0 && args[0].equalsIgnoreCase("--create-config"))
			return;

		File serverDirectory = new File(config.getConfig().get("serverDirectory").getAsString());
		serverDirectory.mkdirs();

		MySQLDatabase database = new MySQLDatabase(config);

		if (!database.testDatabaseConnection()) {
			System.out.println("Error connecting to database!");
			System.exit(1);
			return;
		}

		DaemonWebServer webServer = new DaemonWebServer(config, database);
		webServer.acceptConnections();

		startAutoStartServers(database);

		startFtpServer(config, database);

		new Thread(() -> {
			Scanner scanner = new Scanner(System.in);
			while (true) {
				try {
					String input = scanner.nextLine();
					if (input.equalsIgnoreCase("stop")) {
						handleShutDown(webServer, database);
						break;
					}
				} catch (Exception ignored) {
				}
			}
		}).start();

		Signal.handle(new Signal("INT"), signal -> handleShutDown(webServer, database));
	}

	private static void startAutoStartServers(MySQLDatabase database) {
		database.getServers().stream().filter(ServerInstance::isAutoStart).forEach(ServerInstance::start);
	}

	private static void startFtpServer(Config config, MySQLDatabase database) {
		JsonObject ftpConfig = config.getConfig().getAsJsonObject("ftpServer");
		boolean enabled = ftpConfig.get("enabled").getAsBoolean();
		int port = ftpConfig.get("port").getAsInt();
		int pasvMinPort = ftpConfig.get("pasvPortMin").getAsInt();
		int pasvMaxPort = ftpConfig.get("pasvPortMax").getAsInt();
		JsonObject ssl = ftpConfig.getAsJsonObject("ssl");
		boolean sslEnabled = ssl.get("enabled").getAsBoolean();

		if (enabled) {
			MinecraftFtpServer.startFtpServer(port, database, pasvMinPort, pasvMaxPort, sslEnabled, ssl);
		}
	}

	private static void handleShutDown(DaemonWebServer webServer, MySQLDatabase database) {
		try {
			System.out.println("Shutting down...");

			webServer.denyConnections();
			MinecraftFtpServer.stopFtpServer();
			database.getServers().stream().filter(ServerInstance::isRunning).forEach(ServerInstance::stop);

			Thread.sleep(10000);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static Map<String, String> getJavaVersions() {
		Map<String, String> versions = new HashMap<>();

		try {
			Process process = Runtime.getRuntime().exec("update-alternatives --display java");

			BufferedReader reader =
					new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.startsWith("/") && line.contains("/bin/java")) {
					String javaPath = line.split(" ")[0];
					String[] split = javaPath.split("/");
					for (String s : split) {
						if (s.startsWith("java-")) {
							int releaseNumber = Integer.parseInt(s.split("-")[1]);
							versions.put("Java " + releaseNumber, javaPath);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return new TreeMap<>(versions);
	}
}
