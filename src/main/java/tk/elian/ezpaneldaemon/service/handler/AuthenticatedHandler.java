package tk.elian.ezpaneldaemon.service.handler;

import com.sun.net.httpserver.HttpExchange;
import tk.elian.ezpaneldaemon.object.User;

import java.io.IOException;

public interface AuthenticatedHandler {

	void handle(HttpExchange httpExchange, User authenticatedUser) throws IOException;
}
