package tk.elian.ezpaneldaemon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.net.URISyntaxException;

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
				OutputStream newConfigFile = new FileOutputStream(configFile);

				EzPanelDaemon.copy(configTemplate, newConfigFile);
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
