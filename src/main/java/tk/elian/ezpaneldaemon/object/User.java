package tk.elian.ezpaneldaemon.object;

public record User(int userId, String username, String email, String permissions) {

	public boolean hasGlobalSettingsAccess() {
		return permissions.equals("*");
	}

	public boolean hasCreateUserAccess() {
		return permissions.equals("*");
	}

	public boolean hasModifyUserAccess() {
		return permissions.equals("*");
	}

	public boolean hasCreateServerAccess() {
		return permissions.equals("*");
	}

	public boolean hasDeleteServerAccess() {
		return permissions.equals("*");
	}

	public boolean hasServerViewAccess(ServerInstance serverInstance) {
		int serverId = serverInstance.getServerId();
		return serverInstance.getOwnerId() == userId || permissions.equals("*")
				|| permissions.contains("server." + serverId + ".*")
				|| permissions.contains("server." + serverId + ".view");
	}

	public boolean hasServerConsoleAccess(ServerInstance serverInstance) {
		int serverId = serverInstance.getServerId();
		return serverInstance.getOwnerId() == userId || permissions.equals("*")
				|| permissions.contains("server." + serverId + ".*")
				|| permissions.contains("server." + serverId + ".console");
	}

	public boolean hasServerCommandAccess(ServerInstance serverInstance) {
		int serverId = serverInstance.getServerId();
		return serverInstance.getOwnerId() == userId || permissions.equals("*")
				|| permissions.contains("server." + serverId + ".*")
				|| permissions.contains("server." + serverId + ".command");
	}

	public boolean hasServerEditAccess(ServerInstance serverInstance) {
		int serverId = serverInstance.getServerId();
		return serverInstance.getOwnerId() == userId || permissions.equals("*")
				|| permissions.contains("server." + serverId + ".*")
				|| permissions.contains("server." + serverId + ".edit");
	}

	public boolean hasServerFTPAccess(ServerInstance serverInstance) {
		int serverId = serverInstance.getServerId();
		return serverInstance.ftpEnabled() && (serverInstance.getOwnerId() == userId || permissions.equals("*")
				|| permissions.contains("server." + serverId + ".*")
				|| permissions.contains("server." + serverId + ".ftp"));
	}
}
