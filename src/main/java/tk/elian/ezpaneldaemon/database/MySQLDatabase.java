package tk.elian.ezpaneldaemon.database;

import com.google.gson.JsonObject;
import tk.elian.ezpaneldaemon.Config;
import tk.elian.ezpaneldaemon.ServerInstance;
import tk.elian.ezpaneldaemon.User;
import tk.elian.ezpaneldaemon.util.Encryption;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
		Statement statement = con.createStatement();

		ResultSet rs = statement.executeQuery("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				  AND table_name = "Users\"""");
		rs.next();
		boolean usersExists = rs.getBoolean(1);

		rs = statement.executeQuery("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				  AND table_name = "Servers\"""");
		rs.next();
		boolean serversExists = rs.getBoolean(1);

		rs = statement.executeQuery("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				  AND table_name = "GlobalProperties\"""");
		rs.next();
		boolean propertiesExists = rs.getBoolean(1);

		rs = statement.executeQuery("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				  AND table_name = "Tasks\"""");
		rs.next();
		boolean tasksExists = rs.getBoolean(1);

		if (!usersExists) {
			statement.execute("""
					CREATE TABLE Users
					(
					    userId       int PRIMARY KEY,
					    username     varchar(255)  NOT NULL,
					    email        varchar(255) NULL,
					    password     varchar(1000) NOT NULL,
					    passwordDate varchar(10)   NOT NULL,
					    permissions  varchar(1000) NULL
					);""");
		}

		rs = statement.executeQuery("SELECT COUNT(*) FROM Users");
		rs.next();
		if (rs.getInt(1) == 0) {
			statement.execute("""
					INSERT INTO Users(userId, username, email, password, passwordDate, permissions)
									VALUES (1, 'admin', 'admin@local', 'qEUqmzQpHhxmV34hIwaLTA==', '2000-12-03', '*');""");
		}

		if (!serversExists) {
			statement.execute("""
					CREATE TABLE Servers
					(
						serverId          int PRIMARY KEY,
					    name              varchar(255)  NOT NULL,
					    dateCreated       varchar(10)   NOT NULL,
					    javaPath          varchar(1000) NOT NULL,
					    serverJar         varchar(1000) NOT NULL,
					    jarPathRelativeTo varchar(50)  NOT NULL,
					    maximumMemory     int NULL,
					    autoStart         BOOLEAN DEFAULT true,
					    ftp               BOOLEAN DEFAULT true,
						ownerId           int NULL
					);""");
//			statement.execute("""
//					INSERT INTO Servers(serverId, name, dateCreated, javaPath, serverJar, jarPathRelativeTo,
//					maximumMemory)
//					VALUES (1, 'Default Server', CURRENT_DATE, '/bin/java', 'paper-1.17.1-165.jar', 'Server Jar
//					Folder', 2048);""");
//			getServers().get(0).createServerFiles();
		}

		if (!propertiesExists) {
			statement.execute("""
					CREATE TABLE GlobalProperties
					(
					    property varchar(1000),
					    value    varchar(1000)
					);""");
		}

		if (!tasksExists) {
			statement.execute("""
					CREATE TABLE Tasks(
					    taskId int PRIMARY KEY AUTO_INCREMENT,
					    serverId int,
					    command varchar(1000),
					    days varchar(7),
					    time varchar(10)
					);""");
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

	public ServerInstance getServer(int serverId) {
		return getServers().stream().filter(serverInstance -> serverInstance.getServerId() == serverId).findFirst().orElse(null);
	}

	public int getMaxServerId() {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();
			ResultSet rs = statement.executeQuery("SELECT MAX(serverId) FROM Servers");

			rs.next();
			int serverId = rs.getInt(1);

			if (rs.wasNull())
				serverId = 0;

			return serverId;
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}
	}

	public int getMaxUserId() {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();
			ResultSet rs = statement.executeQuery("SELECT MAX(userId) FROM Users");

			rs.next();
			int userId = rs.getInt(1);

			if (rs.wasNull())
				userId = 0;

			return userId;
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}
	}

	public ServerDatabaseDetails getServerDetailsById(int serverId) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();
			ResultSet rs = statement.executeQuery("SELECT * FROM Servers where serverId = " + serverId);

			if (rs.next()) {
				String name = rs.getString("name");
				String dateCreated = rs.getString("dateCreated");
				String javaPath = rs.getString("javaPath");
				String serverJar = rs.getString("serverJar");
				String jarPathRelativeTo = rs.getString("jarPathRelativeTo");
				int maximumMemory = rs.getInt("maximumMemory");
				boolean autoStart = rs.getBoolean("autoStart");
				boolean ftp = rs.getBoolean("ftp");
				int ownerId = rs.getInt("ownerId");

				return new ServerDatabaseDetails(name, dateCreated, javaPath, serverJar, jarPathRelativeTo,
						maximumMemory, autoStart, ftp, ownerId);
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public int createServer(String name, String javaPath, String serverJar, String jarPathRelativeTo,
	                        int maximumMemory, boolean autoStart, int ownerId) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();

			int newServerId = getMaxServerId() + 1;
			String sql = String.format("""
							INSERT INTO Servers(serverId, name, dateCreated, javaPath, serverJar, jarPathRelativeTo,
								maximumMemory, autostart, ownerId)
							VALUES (%d, '%s', CURRENT_DATE, '%s', '%s', '%s', %d, %b, %s);""",
					newServerId, name, javaPath, serverJar, jarPathRelativeTo, maximumMemory, autoStart,
					ownerId == -1 ?
							"null" : Integer.toString(ownerId));
			statement.execute(sql);
			return newServerId;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	public void updateServer(int serverId, String name, String javaPath, String serverJar, String jarPathRelativeTo,
	                         int maximumMemory, boolean autoStart, int ownerId) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();

			String sql = String.format("""
							UPDATE Servers
							SET
							    name = '%s',
							    javaPath = '%s',
							    serverJar = '%s',
							    jarPathRelativeTo = '%s',
							    maximumMemory = %d,
							    autoStart = %b,
							    ownerId = %d
							WHERE
							    serverId = %d""",
					name, javaPath, serverJar, jarPathRelativeTo, maximumMemory, autoStart, ownerId, serverId);
			statement.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setServerJar(int serverId, String serverJar) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();

			String sql = String.format("""
							UPDATE Servers
							SET
							    serverJar = '%s'
							WHERE
							    serverId = %d""",
					serverJar, serverId);
			statement.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void deleteServer(int serverId) {
		try (Connection con = getConnection()) {
			Statement statement = con.createStatement();

			String sql = String.format("""
					DELETE FROM Servers
					WHERE serverId = %d""", serverId);
			statement.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public List<User> getUsers() {
		List<User> users = new ArrayList<>();

		try (Connection con = getConnection()) {
			ResultSet rs = con.createStatement().executeQuery("SELECT * FROM Users");

			while (rs.next()) {
				int userId = rs.getInt("userId");
				String username = rs.getString("username");
				String email = rs.getString("email");
				String permissions = rs.getString("permissions");

				users.add(new User(userId, username, email, permissions));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return users;
	}

	public User getUser(String username) {
		return getUsers().stream().filter(user -> user.username().equalsIgnoreCase(username)).findFirst().orElse(null);
	}

	public boolean createUser(String username, String email, String password, String permissions) {
		String passwordDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String passwordEncrypted = Encryption.encrypt(password, passwordDate);

		try (Connection con = getConnection()) {
			int newUserId = getMaxUserId() + 1;
			String sql = String.format("""
							INSERT INTO Users(userId, username, email, password, passwordDate, permissions)
							VALUES (%d, '%s', '%s', '%s', '%s', '%s')""", newUserId, username, email,
					passwordEncrypted, passwordDate, permissions);
			con.createStatement().execute(sql);
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	public void setUserPassword(int userId, String password) {
		String passwordDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String passwordEncrypted = Encryption.encrypt(password, passwordDate);

		try (Connection con = getConnection()) {
			String sql = String.format("""
					UPDATE Users
					SET
						password = '%s', passwordDate = '%s'
					WHERE
						userId = %d""", passwordEncrypted, passwordDate, userId);
			con.createStatement().execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setUserEmail(int userId, String email) {
		try (Connection con = getConnection()) {
			String sql = String.format("""
					UPDATE Users
					SET
						email = '%s'
					WHERE
						userId = %d""", email, userId);
			con.createStatement().execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setUserPermissions(int userId, String permissions) {
		try (Connection con = getConnection()) {
			String sql = String.format("""
					UPDATE Users
					SET
						permissions = '%s'
					WHERE
						userId = %d""", permissions, userId);
			con.createStatement().execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void deleteUser(int userId) {
		try (Connection con = getConnection()) {
			String sql = String.format("""
					DELETE FROM Users
					WHERE
						userId = %d""", userId);
			con.createStatement().execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
