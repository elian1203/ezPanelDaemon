package tk.elian.ezpaneldaemon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {

	private JsonObject config;

	Config() {
		createConfigFile();
		loadConfig();
	}

	private void createConfigFile() {
		try {
			String jarPath = getClass()
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()
					.getPath();

			File jarFile = new File(jarPath);
			File configFile = new File(jarFile.getParent(), "config.json");

			if (!configFile.exists()) {
				configFile.createNewFile();

				InputStream configTemplate = getClass().getClassLoader().getResourceAsStream("config.json");
				Files.copy(configTemplate, Path.of(configFile.getAbsolutePath()));
			}
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	private void loadConfig() {
		try {
			String jarPath = getClass()
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()
					.getPath();

			File jarFile = new File(jarPath);
			File configFile = new File(jarFile.getParent(), "config.json");

			Reader configReader = new FileReader(configFile);

			config = (JsonObject) JsonParser.parseReader(configReader);
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public JsonObject getConfig() {
		return config;
	}
}
