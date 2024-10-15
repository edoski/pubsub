import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
	public static CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<Message>> messages = new ConcurrentHashMap<>();
	private final Server server;
	private final Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private String userRole = null; // Initialize as null
	private String topic = null;    // Initialize as null
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

			System.out.println("> Client connected.");

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
		} finally {
			closeEverything(socket, in, out);
		}
	}

	private void handleClientMessage(String message) {
		if (userRole == null || topic == null) {
			// Client is not registered yet
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
					handleRegistration(tokens);
					break;
				default:
					out.println("> Unknown command. Please register first using 'publish <topic>' or 'subscribe <topic>'.");
					break;
			}
		} else {
			// Client is registered
			if (message.equalsIgnoreCase("quit")) {
				System.out.println("> Client requested to disconnect.");
				interruptThread();
			} else if (message.equalsIgnoreCase("show")) {
				sendTopicList();
			} else if (userRole.equals("publish")) {
				// Publisher can send messages
				Message newMessage = new Message(server.getNextMessageId(), topic, message);
				broadcastMessage(newMessage.toString());
				messages.putIfAbsent(topic, new CopyOnWriteArrayList<>());
				messages.get(topic).add(newMessage);
			} else {
				// Subscriber or unknown role
				out.println("> Unknown command. As a subscriber, you cannot send messages.");
			}
		}
	}

	private void handleRegistration(String[] tokens) {
		if (tokens.length >= 2) {
			userRole = tokens[0].toLowerCase();
			// Combine tokens to form the topic name in case it contains spaces
			topic = String.join("_", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));

			System.out.println("> Client registered with role '" + userRole + "' on topic '" + topic + "'.");
			out.println("> Successfully registered with role '" + userRole + "' on topic '" + topic + "'.");

			if (userRole.equals("publish")) {
				out.println("> You can start sending messages. Type 'help' for a list of available commands or 'quit' to exit.");
			}
		} else {
			out.println("> Usage: " + tokens[0] + " <topic_name>");
		}
	}

	private void sendTopicList() {
		// Build the list of topics
		StringBuilder topicsList = new StringBuilder();
		if (messages.isEmpty()) {
			topicsList.append("> No topics available.");
		} else {
			topicsList.append("--- TOPICS ---");
			for (String topic : messages.keySet()) {
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