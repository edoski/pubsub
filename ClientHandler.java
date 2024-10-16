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

	public ClientHandler(Socket socket, Server server) {
		this.server = server;
		this.socket = socket;
		clientHandlers.add(this); // Important: Add immediately so both registered and unregistered are handled
	}

	@Override
	public void run() {
		try {
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);

			// Set socket timeout for operations
			this.socket.setSoTimeout(500); // 500 ms

			// Main loop for handling client messages
			String messageFromClient;
			while (running && !socket.isClosed()) {
				try {
					messageFromClient = in.readLine();  // Blocking call
					if (messageFromClient == null) break; // Client disconnected
					processCommand(messageFromClient);
				} catch (SocketTimeoutException e) {
					if (!server.isRunning()) {
						break;
					}
				} catch (IOException e) {
					break;
				}
			}
		} catch (IOException e) {
			System.out.println("> IOException in ClientHandler: " + e.getMessage());
		}
	}

	private void processCommand(String message) {
		String[] tokens = message.trim().split("\\s+");
		String command = tokens[0].toLowerCase();

		// Non-default commands are specific functions that the client requests from the server
		// Default command is the client sending a message
		switch (command) {
			case "show":
				sendTopicList();
				break;
			case "listall":
				listAllTopicMessages();
				break;
			case "quit":
				String role = isPublisher == null ? "Unregistered user" : isPublisher ? "Publisher" : "Subscriber";
				System.out.println("> Client requested to disconnect: " + role + (topic == null ? "" : " in '" + topic + "'") + ".");
				interruptThread();
				break;
			case "publish":
			case "subscribe":
				handleRegistration(tokens);
				break;
			default:
				broadcastMessage(new Message(topic, message));
				break;
		}
	}

	private void sendTopicList() {
		StringBuilder topicsList = new StringBuilder();
		topicsList.append("--- EXISTING TOPICS ---\n");
		for (String topic : topics.keySet()) {
			topicsList.append("> ").append(topic).append("\n");
		}
		// Send the topics list to the client
		out.println(topics.isEmpty() ? "> No topics available.\n" : topicsList);
	}

	private void listAllTopicMessages() {
		if (isPublisher == null) {
			out.println("> You need to subscribe/publish to a topic first.\n");
			return;
		}

		CopyOnWriteArrayList<Message> messages = topics.get(topic);
		if (messages == null || messages.isEmpty()) {
			out.println("> No messages available for topic '" + topic + "'.\n");
			return;
		}

		out.println("--- " + messages.size() + " MESSAGES IN '" + topic + "' ---\n");
		for (Message m : messages) out.println(m);
		out.println("--- END OF MESSAGES IN '" + topic + "' ---\n");
	}

	private void handleRegistration(String[] tokens) {
		String role = tokens[0].toLowerCase();
		topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length)); // "example topic" -> "example_topic"
		isPublisher = role.equals("publish");

		System.out.println("> Client registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.");
		out.println(
				"> Registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.\n" +
				"> Enter 'help' for a list of available commands.\n"
		);

		// Ensure the topic is added to the topics map
		topics.putIfAbsent(topic, new CopyOnWriteArrayList<>());

		// Check if the server is inspecting this topic and notify the client
		if (server.isInspectingTopic(topic)) {
			setIsServerInspecting(true);
		}
	}

	public void broadcastMessage(Message message) {
		topics.putIfAbsent(topic, new CopyOnWriteArrayList<>());
		topics.get(topic).add(message);

		for (ClientHandler clientHandler : clientHandlers) {
			if (clientHandler.topic.equals(this.topic)) {
				clientHandler.out.println((clientHandler != this ? "> MESSAGE RECEIVED:\n" : "> MESSAGE SENT:\n") + message.toString());
			}
		}
	}

	public void setIsServerInspecting(boolean isInspecting) {
		try {
			if (isInspecting) {
				String commands = isPublisher ? "send, list, listall" : "listall";
				out.println("--- SERVER INSPECT STARTED FOR '" + topic + "' ---\n" +
						"> Regular functionality has been temporarily suspended. See 'help' for a list of available commands.\n" +
						"> You can still use '" + commands + "', but they will be executed when the server ends Inspect mode.\n");
			} else {
				out.println("--- SERVER INSPECT ENDED ---\n" +
						"> Server has exited Inspect mode for topic '" + topic + "'.\n" +
						"> Any backlogged commands will now be executed.\n"
				);
			}
			out.println("IS_SERVER_INSPECTING " + isInspecting);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendShutdownMessage() {
		if (out != null) {
			out.println("> Server initiated shutdown...");
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

	public String getTopic() {
		return topic;
	}
}