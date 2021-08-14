package tk.elian.ezpaneldaemon;

import com.google.gson.JsonObject;
import sun.misc.Signal;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.ftp.MinecraftFtpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

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
		JsonObject ssl = ftpConfig.getAsJsonObject("ssl");
		boolean sslEnabled = ssl.get("enabled").getAsBoolean();

		if (enabled) {
			MinecraftFtpServer.startFtpServer(port, database, sslEnabled, ssl);
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

	public static int copy(InputStream input, OutputStream output)
			throws IOException {
		byte[] buffer = new byte[1024 * 4];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}

		if (count > Integer.MAX_VALUE) {
			return -1;
		}
		return (int) count;
	}
}
