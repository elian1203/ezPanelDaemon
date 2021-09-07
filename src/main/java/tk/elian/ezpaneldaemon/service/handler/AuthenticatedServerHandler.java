package tk.elian.ezpaneldaemon.service.handler;

import com.sun.net.httpserver.HttpExchange;
import tk.elian.ezpaneldaemon.object.ServerInstance;
import tk.elian.ezpaneldaemon.object.User;

import java.io.IOException;

public interface AuthenticatedServerHandler {

	void handle(HttpExchange httpExchange, User authenticatedUser, ServerInstance minecraft) throws IOException;
}
