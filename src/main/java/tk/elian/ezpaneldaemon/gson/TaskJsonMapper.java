package tk.elian.ezpaneldaemon.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import tk.elian.ezpaneldaemon.object.Task;
import tk.elian.ezpaneldaemon.service.TaskService;

import java.lang.reflect.Type;
import java.time.format.DateTimeFormatter;

public class TaskJsonMapper implements JsonSerializer<Task> {

	private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	@Override
	public JsonElement serialize(Task task, Type type, JsonSerializationContext jsonSerializationContext) {
		JsonObject root = new JsonObject();

		root.addProperty("taskId", task.taskId());
		root.addProperty("serverId", task.serverId());
		root.addProperty("command", task.command());
		root.addProperty("days", task.days());
		root.addProperty("time", task.time());
		root.addProperty("repeat", task.repeatIncrement());
		root.addProperty("lastRun", task.lastRun());
		root.addProperty("nextRun", TaskService.getNextRunTime(task).format(format));

		return root;
	}
}
