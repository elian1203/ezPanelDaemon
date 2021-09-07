package tk.elian.ezpaneldaemon.service;

import tk.elian.ezpaneldaemon.database.MySQLDatabase;
import tk.elian.ezpaneldaemon.object.ServerInstance;
import tk.elian.ezpaneldaemon.object.Task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TaskService implements Runnable {

	private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final MySQLDatabase database;

	public TaskService(MySQLDatabase database) {
		this.database = database;
	}

	@Override
	public void run() {
		// current time
		LocalDateTime currentTime = LocalDateTime.now();

		database.getTasks().forEach(task -> {
			String days = task.days();
			if (days.indexOf(getCurrentDay()) == -1) {
				// does not run today
				return;
			}

			if (timeMatches(task, currentTime)) {
				// run command on server
				ServerInstance server = database.getServer(task.serverId());

				String command = task.command();

				// panel command
				if (command.startsWith("\\")) {
					command = command.substring(1).toLowerCase();
					switch (command) {
						case "start" -> server.start();
						case "stop" -> server.stop();
						case "restart" -> server.restart();
						case "kill" -> server.kill();
					}
				} else if (server.isRunning()) {
					server.sendCommand(command);
				}

				database.setTaskLastRun(task.taskId(), currentTime.format(format));
			}
		});
	}

	private char getCurrentDay() {
		switch (LocalDateTime.now().getDayOfWeek()) {
			case MONDAY -> {
				return '2';
			}
			case TUESDAY -> {
				return '3';
			}
			case WEDNESDAY -> {
				return '4';
			}
			case THURSDAY -> {
				return '5';
			}
			case FRIDAY -> {
				return '6';
			}
			case SATURDAY -> {
				return '7';
			}
			// sunday
			default -> {
				return '1';
			}
		}
	}

	private boolean timeMatches(Task task, LocalDateTime currentTime) {
		LocalDateTime nextRunTime = getNextRunTime(task);

		long differenceInSeconds = Duration.between(currentTime.withSecond(0), nextRunTime).getSeconds();
		return differenceInSeconds <= 0;
	}

	public static LocalDateTime getNextRunTime(Task task) {
		String repeat = task.repeatIncrement(), lastRun = task.lastRun();
		if (repeat != null && !repeat.isBlank() && lastRun != null) {
			LocalDateTime lastRunTime = LocalDateTime.parse(lastRun, format);

			int increment = Integer.parseInt(repeat.substring(0, repeat.length() - 1));
			char incrementType = repeat.charAt(repeat.length() - 1);

			return switch (incrementType) {
				case 'd' -> lastRunTime.plus(increment, ChronoUnit.DAYS);
				case 'h' -> lastRunTime.plus(increment, ChronoUnit.HOURS);
				default -> lastRunTime.plus(increment, ChronoUnit.MINUTES); // minutes
			};
		} else {
			LocalDateTime now = LocalDateTime.now();

			String[] split = task.time().split(":");
			// mal-formatted time, should never happen
			if (split.length == 1)
				return now.plus(1, ChronoUnit.DAYS);

			int hour = Integer.parseInt(split[0]);
			int minute = Integer.parseInt(split[1]);

			if (now.getHour() <= hour && now.getMinute() <= minute) {
				return now.withHour(hour).withMinute(minute).withSecond(0);
			} else {
				return now.plusDays(1).withMinute(hour).withMinute(minute).withSecond(0);
			}
		}
	}
}
