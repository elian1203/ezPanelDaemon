package tk.elian.ezpaneldaemon.util;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ServerProperties {

	public static Map<String, String> getServerProperties(String pathToProperties) {
		Map<String, String> serverProperties = new HashMap<>();

		Properties properties = loadPropertiesFile(pathToProperties);

		if (properties != null) {
			properties.stringPropertyNames().forEach(property -> serverProperties.put(property,
					properties.getProperty(property)));
		}

		return serverProperties;
	}

	public static void setServerProperty(String pathToProperties, String property, String value) {
		Properties properties = loadPropertiesFile(pathToProperties);
		File file = new File(pathToProperties);

		if (properties != null) {
			if (properties.containsKey(property) && properties.getProperty(property).equals(value))
				return;

			properties.setProperty(property, value);

			try {
				FileOutputStream fileOutput = new FileOutputStream(file);
				properties.store(fileOutput, null);
				fileOutput.flush();
			} catch (IOException ignored) {
			}
		}
		// as of now don't want to create the file if it does not already exist
//		} else {
//			try {
//				// file does not exist
//				properties = loadDefaultProperties();
//				if (properties != null) {
//					file.createNewFile();
//
//					FileOutputStream fileOutput = new FileOutputStream(file);
//					properties.store(fileOutput, "Created by ezPanel");
//
//					properties.setProperty(property, value);
//					fileOutput.flush();
//				}
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
	}

	private static Properties loadPropertiesFile(String pathToProperties) {
		try {
			Properties properties = new Properties();

			File file = new File(pathToProperties);
			FileInputStream fileInput = new FileInputStream(file);

			properties.load(fileInput);
			return properties;
		} catch (IOException e) {
			// property file not yet created
			return null;
		}
	}

	private static Properties loadDefaultProperties() {
		try {
			Properties properties = new Properties();

			InputStream input = ServerProperties.class.getClassLoader().getResourceAsStream("server.properties");
			properties.load(input);

			return properties;
		} catch (IOException e) {
			// this should never happen unless there are memory issues
			e.printStackTrace();
			return null;
		}
	}
}
