package tk.elian.ezpaneldaemon.ftp;

import com.google.gson.JsonObject;
import com.guichaguri.minimalftp.FTPConnection;
import com.guichaguri.minimalftp.FTPServer;
import com.guichaguri.minimalftp.api.IFileSystem;
import com.guichaguri.minimalftp.api.IUserAuthenticator;
import com.guichaguri.minimalftp.impl.NativeFileSystem;
import tk.elian.ezpaneldaemon.ServerInstance;
import tk.elian.ezpaneldaemon.User;
import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.util.SSLUtil;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

public class MinecraftFtpServer {

	private static FTPServer ftpServer;

	public static void startFtpServer(int port, MySQLDatabase database, boolean sslEnabled, JsonObject ssl) {
		stopFtpServer();

		UserAuthenticator auth = new UserAuthenticator(database);
		ftpServer = new FTPServer(auth);

		if (sslEnabled) {
			String dir = ssl.get("dir").getAsString(),
					pass = ssl.get("pass").getAsString(),
					country = ssl.get("country").getAsString(),
					state = ssl.get("state").getAsString(),
					locality = ssl.get("locality").getAsString(),
					organization = ssl.get("organization").getAsString(),
					email = ssl.get("email").getAsString();
			SSLContext sslContext = SSLUtil.createContext(dir, pass, country, state, locality, organization, email);

			if (sslContext != null) {
				ftpServer.setSSLContext(sslContext);
			}
		}

		new Thread(() -> {
			try {
				ftpServer.listenSync(port);
			} catch (IOException e) {
				System.out.println("FAILED TO START FTP SERVER - CHECK THAT YOU ARE RUNNING AS ROOT");
				e.printStackTrace();
			}
		}).start();
	}

	public static void stopFtpServer() {
		try {
			if (ftpServer != null)
				ftpServer.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("FAILED TO STOP FTP SERVER");
		}
	}

	private record UserAuthenticator(MySQLDatabase database) implements IUserAuthenticator {
		@Override
		public boolean needsUsername(FTPConnection ftpConnection) {
			return true;
		}

		@Override
		public boolean needsPassword(FTPConnection ftpConnection, String s, InetAddress inetAddress) {
			return true;
		}

		@Override
		public IFileSystem<File> authenticate(FTPConnection ftpConnection, InetAddress address, String username,
		                                      String password) throws AuthException {
			if (username == null || password == null) {
				throw new AuthException();
			}

			String[] split = username.split("\\.");

			// invalid username
			if (split.length < 2 || !split[split.length - 1].matches("[0-9]+")) {
				throw new AuthException();
			}

			// in case username has periods in it
			int lastPeriod = username.lastIndexOf('.');
			String userString = username.substring(0, lastPeriod);

			// could not authenticate
			if (!database.authenticateUser(split[0], password)) {
				throw new AuthException();
			}

			int serverId = Integer.parseInt(split[split.length - 1]);
			ServerInstance server = database.getServer(serverId);
			User user = database.getUser(userString);

			if (server == null || user == null) {
				throw new AuthException();
			}

			// does not have edit access to server
			if (!user.hasServerFTPAccess(server)) {
				throw new AuthException();
			}

			File rootDirectory = new File(server.getServerPath());
			return new NativeFileSystem(rootDirectory);
		}
	}
}
