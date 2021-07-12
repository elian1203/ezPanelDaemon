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

				copy(configTemplate, newConfigFile);
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

	private int copy(InputStream input, OutputStream output)
			throws IOException {
		byte[] buffer = new byte[1024 * 4];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}

		if (count > Integer.MAX_VALUE) {
			return -1;
		}
		return (int) count;
	}

	public JsonObject getConfig() {
		return config;
	}
}
