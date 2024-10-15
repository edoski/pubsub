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
	private Boolean isPublisher = null;
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
			case "show":
				sendTopicList();
				break;
			case "listall":
				listAllTopicMessages();
				break;
			case "list":
				// todo
			case "quit":
				System.out.println("> Client requested to disconnect.");
				interruptThread();
				break;
			case "publish":
			case "subscribe":
				handleRegistration(tokens);
				break;
			default:
				if (isPublisher == null) {
					out.println("> Please register first using '[publish | subscribe] <topic>'.");
				} else if (isPublisher) {
					// Publisher can send messages
					Message newMessage = new Message(topic, message);
					broadcastMessage(newMessage.toString());
					topics.putIfAbsent(topic, new CopyOnWriteArrayList<>());
					topics.get(topic).add(newMessage);
				} else {
					out.println("> As a subscriber, you cannot send messages.");
				}
				break;
		}
	}

	private void listAllTopicMessages() {
		if (isPublisher == null) {
			out.println("> You need to subscribe/publish to a topic first.");
			return;
		}

		if (topic != null) {
			CopyOnWriteArrayList<Message> messages = topics.get(topic);

			if (messages == null || messages.isEmpty()) {
				out.println("> No messages available for topic '" + topic + "'.");
				return;
			}

			out.println("--- " + messages.size() + " MESSAGES IN TOPIC '" + topic + "' ---\n");
			for (Message m : messages) {
				out.println(m);
			}
			out.println("--- END OF MESSAGES IN TOPIC '" + topic + "' ---");
		}
	}

	private void handleRegistration(String[] tokens) {
		if (tokens.length >= 2) {
			String role = tokens[0].toLowerCase();
			// Combine tokens to form the topic name in case it contains spaces
			topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length));

			if (role.equals("publish")) {
				isPublisher = true;
			} else if (role.equals("subscribe")) {
				isPublisher = false;
			} else {
				out.println("> Invalid role. Use 'publish' or 'subscribe'.");
				return;
			}

			System.out.println("> Client registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.");
			out.println(
					"> Registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.\n" +
							"> Type 'help' for a list of available commands."
			);

			// Ensure the topic is added to the topics map
			topics.putIfAbsent(topic, new CopyOnWriteArrayList<>());
		} else {
			out.println("> Usage: " + tokens[0] + " <topic_name>");
		}
	}

	private void sendTopicList() {
		// Build the list of topics
		StringBuilder topicsList = new StringBuilder();
		if (topics.isEmpty()) {
			topicsList.append("> No topics available.");
		} else {
			topicsList.append("--- START OF TOPICS ---");
			for (String topic : topics.keySet()) {
				topicsList.append("\n- ").append(topic);
			}
			topicsList.append("\n--- END OF TOPICS ---\n");
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