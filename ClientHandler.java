import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientHandler implements Runnable {
	public static ConcurrentLinkedQueue<ClientHandler> clientHandlers = new ConcurrentLinkedQueue<>();
	public static ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>> topics = new ConcurrentHashMap<>();
	private final Server server;
	private final Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private Boolean isPublisher = null;
	private String topic = null;
	private volatile boolean running = true;
	private final ArrayList<Message> clientMessages = new ArrayList<>(); // Messages sent by this client

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

			this.socket.setSoTimeout(500); // 500ms timeout for operations

			// Main loop for handling client messages
			String messageFromClient;
			while (running && !socket.isClosed()) {
				try {
					messageFromClient = in.readLine();  // Blocking call
					if (messageFromClient == null) break; // Client disconnected
					processCommand(messageFromClient);
				} catch (SocketTimeoutException e) {
					if (!server.isRunning()) break;
				} catch (IOException e) {
					break;
				}
			}
		} catch (IOException e) {
			System.out.println("> IOException in ClientHandler run(): " + e.getMessage());
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
			case "list":
				listPublisherMessages();
				break;
			case "quit":
				interruptThread();
				break;
			case "publish":
			case "subscribe":
				handleRegistration(tokens);
				break;
			default:
				broadcastMessage(message);
				break;
		}
	}

	private void sendTopicList() {
		StringBuilder topicsList = new StringBuilder();
		topicsList.append("--- SHOW: EXISTING TOPICS ---\n");
		for (String topic : topics.keySet()) {
			topicsList.append("> ").append(topic).append("\n");
		}
		// Send the topics list to the client
		out.println(topics.isEmpty() ? "> No topics available.\n" : topicsList);
	}

	private void listPublisherMessages() {
		if (isPublisher == null || !isPublisher) {
			out.println("> You need to register as a publisher first.\n");
			return;
		}

		if (clientMessages.isEmpty()) {
			out.println("> You have not sent any messages in '" + topic + "'.\n");
			return;
		}

		// Important: Use StringBuilder to build the message and print it all at once, avoiding interleaving
		StringBuilder messageList = new StringBuilder();
		synchronized (clientMessages) {
			messageList.append("--- LIST: YOU SENT ").append(clientMessages.size()).append(" MESSAGES IN '").append(topic).append("' ---\n\n");
			for (Message msg : clientMessages) messageList.append(msg.toString()).append("\n");
			messageList.append("--- LIST: END OF MESSAGES YOU SENT ---\n");
			out.println(messageList);
		}
	}

	private void listAllTopicMessages() {
		if (isPublisher == null) {
			out.println("> You need to subscribe/publish to a topic first.\n");
			return;
		}

		ConcurrentLinkedQueue<Message> messages = topics.get(topic);
		if (messages == null || messages.isEmpty()) {
			out.println("> No messages available for topic '" + topic + "'.\n");
			return;
		}

		// Important: Use StringBuilder to build the message and print it all at once, avoiding interleaving
		StringBuilder messageList = new StringBuilder();
		synchronized (messages) {
			messageList.append("--- LISTALL: ").append(messages.size()).append(" MESSAGES IN '").append(topic).append("' ---\n\n");
			for (Message msg : messages) messageList.append(msg.toString()).append("\n");
			messageList.append("--- LISTALL: END OF MESSAGES IN '").append(topic).append("' ---\n");
			out.println(messageList);
		}
	}

	private void handleRegistration(String[] tokens) {
		String role = tokens[0].toLowerCase();
		topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length)); // "example topic" -> "example_topic"
		isPublisher = role.equals("publish"); // Important: Determine if the client is a publisher or subscriber

		System.out.println("> Client registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.");
		out.println(
				"--- REGISTRATION SUCCESSFUL ---\n" +
				"> Registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.\n" +
				"> Enter 'help' for a list of available commands.\n"
		);

		// Ensure the topic is added to the topics map
		topics.putIfAbsent(topic, new ConcurrentLinkedQueue<>());
		// Check if the server is inspecting this topic and notify the client
		if (server.isInspectingTopic(topic)) setIsServerInspecting(true);
	}

	public void broadcastMessage(String messageBody) {
		Message message = new Message(topic, messageBody);
		topics.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()); // Noticed NullPointerException otherwise
		topics.get(topic).offer(message);
		clientMessages.add(message); // Important: Store the message in the client's own list
		for (ClientHandler clientHandler : clientHandlers) {
			if (clientHandler.topic != null && clientHandler.topic.equals(this.topic)) {
				clientHandler.out.println((clientHandler != this ? "> MESSAGE RECEIVED:\n" : "> MESSAGE SENT:\n") + message);
			}
		}
	}

	public void broadcastMessageFromServer(String message) {
		out.println(message);
	}

	public void setIsServerInspecting(boolean isInspecting) {
		try {
			if (isInspecting) {
				String commands = isPublisher ? "'send', 'list', 'listall'" : "'listall'";
				out.println("--- SERVER INSPECT STARTED FOR '" + topic + "' ---\n" +
							"> Regular functionality has been temporarily suspended. See 'help' for a list of available commands.\n" +
							"> You can still use " + commands + ", but will be queued and executed when the server ends Inspect mode.\n");
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

	public void interruptThread() {
		running = false;
		if (!server.isRunning()) out.println("> Server initiated shutdown...");
		else {
			String role = isPublisher == null ? "Unregistered user" : isPublisher ? "Publisher" : "Subscriber";
			String topic = getTopic() == null ? "" : " in topic '" + this.topic + "'";
			System.out.println("> Client requested to disconnect: " + role + topic + ".");
		}
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