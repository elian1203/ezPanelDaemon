package tk.elian.ezpaneldaemon.database;

public class ServerDatabaseDetails {
	public String name;
	public int port;
	public String dateCreated, jarPath, jarPathRelativeTo;
	public int maximumMemory;
	public boolean autoStart;

	public ServerDatabaseDetails(String name, int port, String dateCreated, String jarPath, String jarPathRelativeTo,
	                             int maximumMemory, boolean autoStart) {
		this.name = name;
		this.port = port;
		this.dateCreated = dateCreated;
		this.jarPath = jarPath;
		this.jarPathRelativeTo = jarPathRelativeTo;
		this.maximumMemory = maximumMemory;
		this.autoStart = autoStart;
	}
}
