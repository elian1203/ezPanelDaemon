package tk.elian.ezpaneldaemon.object.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import tk.elian.ezpaneldaemon.object.User;

import java.io.IOException;

public interface AuthenticatedJsonHandler {

	void handle(HttpExchange httpExchange, User authenticatedUser, JsonObject json) throws IOException;
}
