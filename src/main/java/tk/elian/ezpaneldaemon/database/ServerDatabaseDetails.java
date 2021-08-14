package tk.elian.ezpaneldaemon.database;

public record ServerDatabaseDetails(String name, String dateCreated, String javaPath, String serverJar,
                                    String jarPathRelativeTo, int maximumMemory, boolean autoStart, boolean ftp,
                                    int ownerId) {
}
