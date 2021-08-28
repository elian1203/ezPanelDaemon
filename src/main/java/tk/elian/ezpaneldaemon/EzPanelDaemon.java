package tk.elian.ezpaneldaemon;

import sun.misc.Signal;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.ftp.MinecraftFtpServer;
import tk.elian.ezpaneldaemon.object.ServerInstance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class EzPanelDaemon {

	public static void main(String[] args) {
		if (args.length < 4) {
			System.err.println("MySQL database info not provided!");
			System.err.println("Enter MySQL database info as such [database host] [database name] [database user]" +
					" [password]");
			System.err.println("Ex: java -jar EzPanelDaemon.jar 127.0.0.1 ezpanel_daemon ezpanel_user ezpanel_pass");
			return;
		}

		MySQLDatabase database = connectToDatabase(args);


		DaemonWebServer webServer = new DaemonWebServer(database);
		webServer.acceptConnections();

		startAutoStartServers(database);

		startFtpServer(database);

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

	private static MySQLDatabase connectToDatabase(String[] args) {
		String dbHost = args[0], dbName = args[1], dbUser = args[2], dbPass = args[3];

		MySQLDatabase database = new MySQLDatabase(dbHost, dbName, dbUser, dbPass);

		if (!database.testDatabaseConnection()) {
			System.out.println("Error connecting to database!");
			System.exit(1);
		}

		return database;
	}

	private static void startAutoStartServers(MySQLDatabase database) {
		database.getServers().stream().filter(ServerInstance::isAutoStart).forEach(ServerInstance::start);
	}

	public static void startFtpServer(MySQLDatabase database) {
		boolean enabled = Boolean.parseBoolean(database.getSetting("ftpEnabled"));
		int port = Integer.parseInt(database.getSetting("ftpPort"));
		int pasvMinPort = Integer.parseInt(database.getSetting("pasvPortMin"));
		int pasvMaxPort = Integer.parseInt(database.getSetting("pasvPortMax"));

		if (enabled) {
			MinecraftFtpServer.startFtpServer(port, database, pasvMinPort, pasvMaxPort, false, null);
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
