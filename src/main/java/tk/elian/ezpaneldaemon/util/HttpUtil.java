package tk.elian.ezpaneldaemon.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;

public class HttpUtil {

	/**
	 * Simple HTTP GET request with JSON response required
	 *
	 * @param url     URL to call
	 * @return JSON response
	 * @throws IOException Error making request
	 */
	public static JsonElement httpGET(String url) throws IOException {
		URL urlObject = new URL(url);
		HttpsURLConnection con = (HttpsURLConnection) urlObject.openConnection();
		con.setRequestMethod("GET");

		BufferedInputStream input = new BufferedInputStream(con.getInputStream());

		byte[] bytes = input.readAllBytes();
		String response = new String(bytes);

		return JsonParser.parseString(response);
	}
}
