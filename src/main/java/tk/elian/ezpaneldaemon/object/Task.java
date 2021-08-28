package tk.elian.ezpaneldaemon.object;

public record Task(int taskId, int serverId, String command, String days, String time) {
}
