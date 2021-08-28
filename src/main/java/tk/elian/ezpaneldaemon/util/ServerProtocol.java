package tk.elian.ezpaneldaemon.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tk.elian.ezpaneldaemon.object.ServerProtocolStatus;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ServerProtocol {

	final static ExecutorService service = Executors.newSingleThreadExecutor();

	public static ServerProtocolStatus fetchStatus(String address, int port) {
		final Future<ServerProtocolStatus> future = service.submit(() -> getServerProtocolStatus(address, port));

		try {
			return future.get(1, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			return null;
		}
	}

	// https://stackoverflow.com/questions/30768091/java-sending-handshake-packets-to-minecraft-server
	private static ServerProtocolStatus getServerProtocolStatus(String address, int port) throws IOException {
		InetSocketAddress host = new InetSocketAddress(address, port);
		Socket socket = new Socket();
		socket.connect(host, 1000);
		DataOutputStream output = new DataOutputStream(socket.getOutputStream());
		DataInputStream input = new DataInputStream(socket.getInputStream());

		byte[] handshakeMessage = createHandshakeMessage(address, port);

		// C->S : Handshake State=1
		// send packet length and packet
		writeVarInt(output, handshakeMessage.length);
		output.write(handshakeMessage);

		// C->S : Request
		output.writeByte(0x01); //size is only 1
		output.writeByte(0x00); //packet id for ping


		// S->C : Response
		int size = readVarInt(input);
		int packetId = readVarInt(input);

		if (packetId == -1) {
			throw new IOException("Premature end of stream.");
		}

		if (packetId != 0x00) { //we want a status response
			throw new IOException("Invalid packetID");
		}
		int length = readVarInt(input); //length of json string

		if (length == -1) {
			throw new IOException("Premature end of stream.");
		}

		if (length == 0) {
			throw new IOException("Invalid string length.");
		}

		byte[] in = new byte[length];
		input.readFully(in);  //read json string
		String json = new String(in);


		// C->S : Ping
		long now = System.currentTimeMillis();
		output.writeByte(0x09); //size of packet
		output.writeByte(0x01); //0x01 for ping
		output.writeLong(now); //time!?

		// S->C : Pong
		readVarInt(input);
		packetId = readVarInt(input);
		if (packetId == -1) {
			throw new IOException("Premature end of stream.");
		}

		if (packetId != 0x01) {
			throw new IOException("Invalid packetID");
		}
//			long pingtime = input.readLong(); //read response

		JsonObject object = JsonParser.parseString(json).getAsJsonObject();

		String version = object.get("version").getAsJsonObject().get("name").getAsString();
		String motd = object.get("description").getAsJsonObject().get("text").getAsString();
		String htmlMOTD = MOTDToHTML.convertToHtml(motd);

		JsonObject playersObject = object.get("players").getAsJsonObject();

		int onlinePlayers = playersObject.get("online").getAsInt();
		int maxPlayers = playersObject.get("max").getAsInt();

		List<String> playerNames = new ArrayList<>();

		if (playersObject.has("sample")) {
			JsonArray array = playersObject.getAsJsonArray("sample");
			for (JsonElement e : array) {
				playerNames.add(e.getAsJsonObject().get("name").getAsString());
			}
		}

		return new ServerProtocolStatus(version, htmlMOTD, onlinePlayers, maxPlayers, playerNames);
	}

	public static byte[] createHandshakeMessage(String host, int port) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		DataOutputStream handshake = new DataOutputStream(buffer);
		handshake.writeByte(0x00); //packet id for handshake
		writeVarInt(handshake, 4); //protocol version
		writeString(handshake, host, StandardCharsets.UTF_8);
		handshake.writeShort(port); //port
		writeVarInt(handshake, 1); //state (1 for handshake)

		return buffer.toByteArray();
	}

	public static void writeString(DataOutputStream out, String string, Charset charset) throws IOException {
		byte[] bytes = string.getBytes(charset);
		writeVarInt(out, bytes.length);
		out.write(bytes);
	}

	public static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
		while (true) {
			if ((paramInt & 0xFFFFFF80) == 0) {
				out.writeByte(paramInt);
				return;
			}

			out.writeByte(paramInt & 0x7F | 0x80);
			paramInt >>>= 7;
		}
	}

	public static int readVarInt(DataInputStream in) throws IOException {
		int i = 0;
		int j = 0;
		while (true) {
			int k = in.readByte();
			i |= (k & 0x7F) << j++ * 7;
			if (j > 5) throw new RuntimeException("VarInt too big");
			if ((k & 0x80) != 128) break;
		}
		return i;
	}
}
