package tk.elian.ezpaneldaemon.database;

import com.google.gson.JsonObject;
import tk.elian.ezpaneldaemon.Config;
import tk.elian.ezpaneldaemon.ServerInstance;
import tk.elian.ezpaneldaemon.util.Encryption;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLDatabase {

	private final Config config;
	private final JsonObject mysqlConfig;

	public MySQLDatabase(Config config) {
		this.config = config;
		mysqlConfig = config.getConfig().getAsJsonObject("mysql");
	}


	private Connection getConnection() throws SQLException {
		String connectionString = String.format("jdbc:mysql://%s/%s",
				mysqlConfig.get("host").getAsString(),
				mysqlConfig.get("database").getAsString()
		);

		String user = mysqlConfig.get("user").getAsString(),
				pass = mysqlConfig.get("pass").getAsString();

		return DriverManager.getConnection(connectionString, user, pass);
	}

	public boolean testDatabaseConnection() {
		try (Connection con = getConnection()) {
			createTables(con);
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	private void createTables(Connection con) throws SQLException {
		try {
			InputStream input = getClass().getClassLoader().getResourceAsStream("sql/init.sql");
			InputStreamReader reader = new InputStreamReader(input);

			new ScriptRunner(con, false, true).runScript(reader);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean authenticateUser(String user, String pass) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();
			ResultSet rs = statement.executeQuery("SELECT * FROM Users WHERE username = '" + user + "'");

			if (!rs.next())
				return false;

			String dbPass = rs.getString("password");
			String passwordDate = rs.getString("passwordDate");

			String encrypted = Encryption.encrypt(pass, passwordDate);
			return dbPass.equals(encrypted);
		} catch (SQLException e) {
			System.out.println("Error when authenticating user!");
			e.printStackTrace();
			return false;
		}
	}

	public List<ServerInstance> getServers() {
		List<ServerInstance> servers = new ArrayList<>();

		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();
			ResultSet rs = statement.executeQuery("SELECT * FROM Servers");

			while (rs.next()) {
				int serverId = rs.getInt("serverId");
				ServerInstance minecraft = ServerInstance.getServerInstance(serverId, this, config);
				servers.add(minecraft);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return servers;
	}

	public ServerDatabaseDetails getServerDetailsById(int serverId) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();
			ResultSet rs = statement.executeQuery("SELECT * FROM Servers where serverId = " + serverId);

			if (rs.next()) {
				String name = rs.getString("name");
				int port = rs.getInt("port");
				String dateCreated = rs.getString("dateCreated");
				String jarPath = rs.getString("jarPath");
				String jarPathRelativeTo = rs.getString("jarPathRelativeTo");
				int maximumMemory = rs.getInt("maximumMemory");
				boolean autoStart = rs.getBoolean("autoStart");
				return new ServerDatabaseDetails(name, port, dateCreated, jarPath, jarPathRelativeTo, maximumMemory,
						autoStart);
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
}
