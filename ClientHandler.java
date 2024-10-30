import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The ClientHandler class manages communication with a connected client.
 * It processes client commands, broadcasts messages, and maintains client state.
 */
public class ClientHandler implements Runnable {
	public static ConcurrentHashMap<Integer, ClientHandler> clientHandlers = new ConcurrentHashMap<>(); // userID : ClientHandler
	public static ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>> topics = new ConcurrentHashMap<>(); // topic : all-messages-of-topic
	public final HashMap<String, ArrayList<Message>> publisherMessages = new HashMap<>(); // topic : messages-sent-by-client
	private final Server server;
	private final Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private Boolean isPublisher = null;
	private String topic = null;
	private boolean clientRunning = true; // Originally was volatile, however realized it is unnecessary
	private static final AtomicInteger clientCounter = new AtomicInteger(0); // Unique ID for each client
	private final int userID;

	/**
	 * Constructs a ClientHandler for the given client socket and server.
	 *
	 * @param socket the client's socket connection
	 * @param server the server instance
	 */
	public ClientHandler(Socket socket, Server server) {
		this.server = server;
		this.socket = socket;
		this.userID = clientCounter.getAndIncrement();
		clientHandlers.put(this.userID, this); // Important: Add immediately so both registered and unregistered are handled
	}

	/**
	 * The main run method for the ClientHandler thread.
	 * Listens for messages from the client and processes them.
	 */
	@Override
	public void run() {
		try {
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);

			String messageFromClient;
			while (clientRunning) { // Main loop for handling client messages
				try {
					messageFromClient = in.readLine(); // Blocking call
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

	/**
	 * Processes a command or message received from the client.
	 *
	 * @param message the message or command from the client
	 */
	private void processCommand(String message) {
		// "\\s" → any whitespace character (space, tab, newline). "+" → "one or more" of these characters.
		String[] tokens = message.trim().split("\\s+");
		String command = tokens[0].toLowerCase();

		// Non-default commands are specific functions that the client requests from the server
		// Default command is the client sending a message
		switch (command) {
			case "show" -> sendTopicList();
			case "listall" -> listAllTopicMessages();
			case "list" -> listPublisherMessages();
			case "quit" -> interruptThread();
			case "publish", "subscribe" -> handleRegistration(tokens);
			default -> broadcastMessage(message);
		}
	}

	/**
	 * "show": Sends the list of existing topics to the client.
	 */
	private void sendTopicList() {
		StringBuilder topicsList = new StringBuilder();
		topicsList.append("--- SHOW: EXISTING TOPICS ---\n");
		for (String topic : topics.keySet()) {
			topicsList.append("> ").append(topic).append("\n");
		}
		// Send the topics list to the client
		out.println(topics.isEmpty() ? "> No topics available.\n" : topicsList);

	}

	/**
	 * "list": Lists the messages sent by this client in the current topic.
	 * Only available to publishers.
	 */
	private void listPublisherMessages() {
		if (isPublisher == null || !isPublisher) {
			out.println("> You need to register as a publisher first.\n");
			return;
		}

		if (publisherMessages.get(topic).isEmpty()) {
			out.println("> You have not sent any messages in '" + topic + "'.\n");
			return;
		}

		// Important: Use StringBuilder to build the message and print it all at once, avoiding interleaving
		StringBuilder messageOutput = new StringBuilder();
		synchronized (publisherMessages) {
			messageOutput.append("--- LIST: YOU SENT ").append(publisherMessages.get(topic).size()).append(" MESSAGES IN '").append(topic).append("' ---\n\n");
			for (Message msg : publisherMessages.get(topic)) messageOutput.append(msg.toString()).append("\n");
			messageOutput.append("--- LIST: END OF MESSAGES YOU SENT ---\n");
			out.println(messageOutput);
		}
	}

	/**
	 * "listall": Lists all messages in the current topic.
	 */
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

		// Important: Create a snapshot of the messages to ensure consistency
		ArrayList<Message> snapshot;
		synchronized (messages) {
			snapshot = new ArrayList<>(messages);
		}

		// Important: Use StringBuilder to build the message and print it all at once, avoiding interleaving
		StringBuilder messageOutput = new StringBuilder();
		messageOutput.append("--- LISTALL: ").append(snapshot.size()).append(" MESSAGES IN '").append(topic).append("' ---\n\n");
		for (Message msg : snapshot) messageOutput.append(msg.toString()).append("\n");
		messageOutput.append("--- LISTALL: END OF MESSAGES IN '").append(topic).append("' ---\n");
		out.println(messageOutput);
	}

	/**
	 * Handles registration commands from the client.
	 * Registers the client as a publisher or subscriber to a topic.
	 *
	 * @param tokens the command tokens containing the role and topic
	 */
	private void handleRegistration(String[] tokens) {
		String role = tokens[0].toLowerCase();
		topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length)); // "example topic" -> "example_topic"
		isPublisher = role.equals("publish"); // Important: Determine if the client is a publisher or subscriber

		System.out.println("> Client (ID " + userID + ") registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.");
		out.println(
				"--- REGISTRATION SUCCESSFUL ---\n" +
				"> Registered as '" + (isPublisher ? "publisher" : "subscriber") + "' on topic '" + topic + "'.\n" +
				"> Enter 'help' for a list of available commands.\n"
		);

		topics.putIfAbsent(topic, new ConcurrentLinkedQueue<>()); // Ensure topic is added to topics map
		publisherMessages.putIfAbsent(topic, new ArrayList<>()); // Ensure topic is added to client-specific map
		if (server.isInspectingTopic(topic)) setIsServerInspecting(true); // Important: If server inspecting topic, notify client
	}

	/**
	 * "send": Broadcasts a message to all clients subscribed to the same topic.
	 * Stores the message in the client's own message list.
	 *
	 * @param messageBody the body of the message to broadcast
	 */
	private void broadcastMessage(String messageBody) {
		Message message = new Message(topic, messageBody);
		topics.computeIfAbsent(topic, msgs -> new ConcurrentLinkedQueue<>()).offer(message); // Noticed NullPointerException without this
		publisherMessages.computeIfAbsent(topic, msgs -> new ArrayList<>()).add(message); // Important: Store the message in the client's own list
		clientHandlers.values().stream()
				.filter(ch -> topic.equals(ch.topic))
				.forEach(ch -> ch.out.println((ch != this ? "> MESSAGE RECEIVED:\n" : "> MESSAGE SENT:\n") + message));
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
							"> Use of " + commands + " will be queued and executed when the server ends Inspect mode.\n"
				);
			} else out.println("--- SERVER INSPECT ENDED FOR '" + topic + "' ---\n" +
							"> Server has exited Inspect mode for topic '" + topic + "'.\n" +
							"> Any backlogged commands will now be executed.\n"
			);
			out.println("IS_SERVER_INSPECTING " + isInspecting);
		} catch (Exception e) {
			System.out.println("> Error in setIsServerInspecting(): " + e.getMessage());
		}
	}

	/**
	 * Interrupts the client handler thread and closes resources.
	 */
	public void interruptThread() {
		clientRunning = false;
		if (!server.isRunning()) out.println("> Server initiated shutdown...");
		else {
			String role = isPublisher == null ? "Unregistered user" : isPublisher ? "Publisher" : "Subscriber";
			String topic = getTopic() == null ? "" : " in topic '" + this.topic + "'";
			System.out.println("> Client " + userID + " requested to disconnect: " + role + topic + ".");
		}
		closeEverything(socket, in, out);
	}

	/**
	 * Interrupts the client handler thread and closes resources.
	 * Overloaded method to handle client kicks.
	 * @param isKicked true if the client was kicked from the server
	 */
	public void interruptThread(boolean isKicked) {
		clientRunning = false;
		if (isKicked) {
			out.println("> You have been kicked from the server.");
			System.out.println("> Client " + userID + " has been kicked from the server.");
		}
		closeEverything(socket, in, out);
	}

	/**
	 * Closes the socket, input, and output streams, and removes the client handler.
	 *
	 * @param socket the client's socket
	 * @param in     the input stream from the client
	 * @param out    the output stream to the client
	 */
	private void closeEverything(Socket socket, BufferedReader in, PrintWriter out) {
		clientHandlers.remove(userID);
		System.out.println("> Client " + userID + " disconnected. Clients currently connected: " + clientHandlers.size() + ".");
		try {
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
		} catch (IOException e) {
			System.out.println("> Error closing socket: " + e.getMessage());
		}
	}

	public String getTopic() {
		return topic;
	}

	public int getUserID() {
		return userID;
	}

	public String getRole() {return (isPublisher == null) ?  "Null" :  isPublisher ? "Publisher" : "Subscriber";}

	public int numberOfMessages() {return  (publisherMessages.get(topic) == null) ? 0 : publisherMessages.get(topic).size();}

	public int numberOfMessagesInTopic() {return  (topics.get(topic) == null) ? 0 : topics.get(topic).size();}

	public int numberOfPublishers(String topic) {
		int count = 0;
		for (ClientHandler ch : clientHandlers.values()) {
			if (ch.getRole().equals("Publisher") && ch.getTopic().equals(topic)) count++;
		}
		return count;
	}
}