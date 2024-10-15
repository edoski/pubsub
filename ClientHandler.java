import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
	public static CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<Message>> topics = new ConcurrentHashMap<>();
	private final Server server;
	private final Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private String userRole = null;
	private String topic = null;
	private volatile boolean running = true;
	private static final int SOCKET_TIMEOUT = 500; // 500 ms

	public ClientHandler(Socket socket, Server server) {
		this.server = server;
		this.socket = socket;
		clientHandlers.add(this); // Important: Add to the list of handlers immediately
	}

	@Override
	public void run() {
		try {
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);

//			System.out.println("> Client connected.");

			// Set socket timeout for operations
			this.socket.setSoTimeout(SOCKET_TIMEOUT);

			// Main loop for handling client messages
			String messageFromClient;
			while (running && !socket.isClosed()) {
				try {
					messageFromClient = in.readLine();  // Blocking call
					if (messageFromClient != null) {
						handleClientMessage(messageFromClient.trim());
					} else {
						break; // Client disconnected
					}
				} catch (SocketTimeoutException e) {
					if (!server.isRunning()) {
						break;
					}
					// Continue loop if still running
				} catch (IOException e) {
					break;
				}
			}
		} catch (IOException e) {
			System.out.println("> IOException in ClientHandler: " + e.getMessage());
		}
	}

	private void handleClientMessage(String message) {
		String[] tokens = message.split("\\s+");
		String command = tokens[0].toLowerCase();

		switch (command) {
			case "quit":
				System.out.println("> Client requested to disconnect.");
				interruptThread();
				break;
			case "show":
				sendTopicList();
				break;
			case "publish":
			case "subscribe":
				if (userRole == null || topic == null) {
					handleRegistration(tokens);
				} else {
					out.println("> Unknown command. Please register first using 'publish <topic>' or 'subscribe <topic>'.");
				}
				break;
			default:
				if (userRole.equals("publish")) {
					Message newMessage = new Message(topic, message);
					broadcastMessage(newMessage.toString());
					topics.putIfAbsent(topic, new CopyOnWriteArrayList<>());
					topics.get(topic).add(newMessage);
				} else {
					out.println("> Unknown command. Enter 'help' for a list of available commands.");
				}
				break;
		}
	}

	private void handleRegistration(String[] tokens) {
		if (tokens.length >= 2) {
			userRole = tokens[0].toLowerCase();
			// Combine tokens to form the topic name in case it contains spaces
			topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length));

			System.out.println("> Client registered with role '" + userRole + "' on topic '" + topic + "'.");
			out.println(
					"> Registered with role '" + userRole + "' on topic '" + topic + "'.\n" +
					"> Type 'help' for a list of available commands."
			);
		} else {
			out.println("> Usage: " + tokens[0] + " <topic_name>");
		}
	}

	// todo: make it so that even if the topic has no messages, it still shows up in the list
	private void sendTopicList() {
		// Build the list of topics
		StringBuilder topicsList = new StringBuilder();
		if (topics.isEmpty()) {
			topicsList.append("> No topics available.");
		} else {
			topicsList.append("--- TOPICS ---");
			for (String topic : topics.keySet()) {
				topicsList.append("\n").append(topic);
			}
		}
		// Send the topics list to the client
		out.println(topicsList);
	}

	public void broadcastMessage(String message) {
		for (ClientHandler clientHandler : clientHandlers) {
			if (clientHandler.topic != null && clientHandler.topic.equals(this.topic)) {
				clientHandler.out.println(message);
			}
		}
	}

	public void interruptThread() {
		running = false;
		closeEverything(socket, in, out);
	}

	public void closeEverything(Socket socket, BufferedReader in, PrintWriter out) {
		clientHandlers.remove(this);
		System.out.println("> Client handler removed. Current handlers: " + clientHandlers.size());
		try {
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}