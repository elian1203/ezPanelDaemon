package tk.elian.ezpaneldaemon;

import tk.elian.ezpaneldaemon.database.MySQLDatabase;

import java.util.Scanner;

public class EzPanelDaemon {

	public static void main(String[] args) {
		Config config = new Config();

		if (args.length > 0 && args[0].equalsIgnoreCase("--create-config"))
			return;

		MySQLDatabase database = new MySQLDatabase(config);

		if (!database.testDatabaseConnection()) {
			System.out.println("Error connecting to database!");
			System.exit(1);
			return;
		}

		DaemonWebServer webServer = new DaemonWebServer(config, database);
		webServer.acceptConnections();

		startAutoStartServers(database);

		new Thread(() -> {
			Scanner scanner = new Scanner(System.in);
			while (true) {
				try {
					String input = scanner.nextLine();
					if (input.equalsIgnoreCase("stop")) {
						webServer.denyConnections();
						database.getServers().stream().filter(ServerInstance::isRunning).forEach(ServerInstance::stop);

						Thread.sleep(30000);
						System.exit(0);
						break;
					}
				} catch (Exception ignored) {
				}
			}
		}).start();
	}

	private static void startAutoStartServers(MySQLDatabase database) {
		database.getServers().stream().filter(ServerInstance::isAutoStart).forEach(ServerInstance::start);
	}
}
