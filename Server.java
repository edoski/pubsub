import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


// todo: secondary
//  ? make unit tests for all classes
//  ? create a Testing class that simulates a client and server interaction

/*todo: firebase
 * IF WE DON'T DO DB WE CAN PUT THIS IN OUR DOC AS A POINT WE CONSIDERED
 * 1. add registration/login feature (username:password)
 * 2. userID becomes username
 * 3. Firebase db collections
 *      Clients {
 *          username1 (created by client at registration): {
 *              password: idem
 *              isPublisher: the role which the user was before disconnecting in previous session || null aka unregistered client if first time client
 *              messages: {
 *                  topic1: list of username1-ONLY messages for topic 1
 *                  topic2: messages list for topic 2
 *                  ...
 *                  topic_m: messages list for topic m
 *              }
 *          },
 *          username2 {}...,
 *          username2 {}...
 *      }
 * .
 *      Messages {
 *          topic1: list of ALL messages for topic 1
 *          topic2: list of ALL messages for topic 2
 *          topic_n: list of ALL messages for topic n
 *      }
 */

/**
 * The Server class manages client connections, handles server commands,
 * and maintains the server's operational state.
 * It listens for client connections and allows server operators to execute commands.
 */
public class Server {
	private final ServerSocket serverSocket;
	private final ExecutorService pool = Executors.newCachedThreadPool();
	private static boolean serverRunning = true;
	private static boolean isInspecting = false;
	private String currentInspectTopic = null;

	public Server(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	/**
	 * Starts the server to accept client connections and handle them using ClientHandler.
	 * It listens for incoming connections and executes each ClientHandler in a thread pool.
	 */
	private void startServer() {
		try {
			System.out.println("--- SERVER STARTED ON PORT " + serverSocket.getLocalPort() + " ---");
			while (serverRunning) {
				try {
					Socket socket = serverSocket.accept();
					System.out.println("--- NEW CLIENT CONNECTED ---");
					ClientHandler clientHandler = new ClientHandler(socket, this);
					pool.execute(clientHandler);
				} catch (SocketTimeoutException | SocketException e) {
					if (!serverRunning) break;
				}
			}
		} catch (IOException e) {
			System.out.println("> Error starting server: " + e.getMessage());
		} finally {
			pool.shutdown();
		}
	}

	/**
	 * Listens for server commands from the console input.
	 * Allows the server operator to execute commands like inspect, listall, delete, etc.
	 */
	private void listenForCommands() {
		Scanner scanner = new Scanner(System.in);
		while (serverRunning) {
			String commandLine = scanner.nextLine().trim();
			// If we try to process and empty command, continue skips the rest of the loop and goes to the next iteration
			if (commandLine.isEmpty()) continue;
			String[] tokens = commandLine.split("\\s+");
			String command = tokens[0].toLowerCase();
			switch (command) {
				case "quit" -> shutdownServer();
				case "inspect" -> startInspectMode(tokens);
				case "listall" -> listAllMessagesInTopic();
				case "delete" -> deleteMessage(tokens);
				case "end" -> endInspectMode();
				case "show" -> showTopics();
				case "help" -> showHelp();
				case "kick" -> kickClient(tokens);
				case "clear" -> clearTopic();
				case "export" -> export(tokens);
				case "users" -> showAllUsersInformation();
				case "user" -> showUserInformation(tokens);
				default -> System.out.println("> Unknown command. Enter 'help' to see the list of available commands.\n");
			}
		}
	}

	private void showUserInformation(String[] tokens) {
		if (tokens.length < 2) {
			System.out.println("> Usage: user <userID>\n");
			return;
		}

		if (!tokens[1].matches("\\d+")) {
			System.out.println("> Invalid user ID.  Enter 'users' to see the list of connected users.\n");
			return;
		}

		int userID = Integer.parseInt(tokens[1]);
		ClientHandler clientHandler = ClientHandler.clientHandlers.get(userID);
		if (clientHandler == null) {
			System.out.println("> User ID " + userID + " not found.\n");
			return;
		}

		System.out.println("--- SHOW: USER DETAILS ---");
		System.out.println("> User ID: " + clientHandler.getUserID());
		System.out.println("> Topic: " + clientHandler.getTopic());
		System.out.println("> Role: " + clientHandler.getRole());
		System.out.println("> Messages sent: " + clientHandler.numberOfMessages());
		System.out.println("--- END OF USER DETAILS ---\n");
	}

	//o show all connected users and their details current topic, current role, num. messages sent in current topic
	public void showAllUsersInformation() {
		System.out.println("--- SHOW: CONNECTED USERS ---\n");
		StringBuilder usersInformation = new StringBuilder();
		if(ClientHandler.clientHandlers.isEmpty()) System.out.println("> No users connected.\n");
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			usersInformation.append("> User ID: ").append(clientHandler.getUserID()).append(" | Topic: ").append(clientHandler.getTopic()).append("\n");
			usersInformation.append("> Role: ").append(clientHandler.getRole()).append(" | Messages sent: ").append(clientHandler.numberOfMessages()).append("\n\n");
		}
		usersInformation.append("--- END OF SHOW USERS ---\n");
		System.out.println(usersInformation);
	}

	private void export(String[] tokens) {
		if (tokens.length < 3) {
			System.out.println("> Usage: export [user <userID> | topic <topic>]\n");
			return;
		}

		String exportType = tokens[1];
		switch (exportType) {
			case "user" -> exportUser(tokens[2]); // export user <userID>
			case "topic" -> exportTopic(tokens[2]); // export topic <topic>
			default -> System.out.println("> Invalid export type. Use 'user' or 'topic'.\n");
		}
	}

	private void exportTopic(String topic) {
		if (!ClientHandler.topics.containsKey(topic)) {
			System.out.println("> Topic '" + topic + "' does not exist.\n");
			return;
		}

		ConcurrentLinkedQueue<Message> messages = ClientHandler.topics.get(topic);
		if (messages == null || messages.isEmpty()) {
			System.out.println("> No messages available for topic '" + topic + "'.\n");
			return;
		}

		// YYYYMMDD_HHMMSS
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String filename = "export_" + dateFormat.format(date) + "_topic_" + topic + ".txt";

		// Creates dir "logs/topic_exports" if it doesn't exist
		String dir = "logs/topic_exports";
		try {
			Path path = Paths.get(dir);
			Files.createDirectories(path);
			try (PrintWriter writer = new PrintWriter(dir + "/" + filename)) {
				writer.println("--- EXPORTED MESSAGES FOR TOPIC '" + topic + "' ---\n");
				for (Message msg : messages) writer.println(msg);
				System.out.println("> Messages in topic '" + topic + "' exported to '" + dir + "/" + filename + "'.\n");
			}
		} catch (IOException e) {
			System.out.println("> Error exporting messages: " + e.getMessage());
		}
	}

	private void exportUser(String userID) {
		if (!userID.matches("\\d+")) {
			System.out.println("> Invalid user ID. Enter 'users' to see the list of connected users.\n");
			return;
		}

		int id = Integer.parseInt(userID);
		ClientHandler clientHandler = ClientHandler.clientHandlers.get(id);
		if (clientHandler == null) {
			System.out.println("> User ID " + id + " not found.\n");
			return;
		}

		ArrayList<Message> messages = clientHandler.publisherMessages.get(clientHandler.getTopic());
		if (messages.isEmpty()) {
			System.out.println("> No messages available for user ID " + id + ".\n");
			return;
		}

		// YYYYMMDD_HHMMSS
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String filename = "export_" + dateFormat.format(date) + "_user_" + id + ".txt";

		// Creates dir "logs/user_exports" if it doesn't exist
		String dir = "logs/user_exports";
		try {
			Path path = Paths.get(dir);
			Files.createDirectories(path);
			try (PrintWriter writer = new PrintWriter(dir + "/" + filename)) {
				writer.println("--- EXPORTED MESSAGES FOR USER ID " + id + " ---\n");
				for (Message msg : messages) writer.println(msg);
				System.out.println("> Messages for user ID " + id + " exported to '" + dir + "/" + filename + "'.\n");
			}
		} catch (IOException e) {
			System.out.println("> Error exporting messages: " + e.getMessage());
		}
	}

	/**
	 * "clear": Clears all messages in the topic currently being inspected.
	 * Notifies clients in the topic about the deletion.
	 */
	private void clearTopic() {
		if (!isInspecting) {
			System.out.println("> Command 'clear' is only available in inspect mode.\n");
			return;
		}

		String topic = currentInspectTopic;
		ConcurrentLinkedQueue<Message> messages = ClientHandler.topics.get(topic);
		if (messages == null || messages.isEmpty()) {
			System.out.println("> No messages available for topic '" + topic + "'.\n");
			return;
		}

		Scanner scanner = new Scanner(System.in);
		System.out.print("> Are you sure you want to clear all messages in topic '" + topic + "'? (y/n): ");
		if (!scanner.nextLine().equalsIgnoreCase("y")) {
			System.out.println("> Clear operation cancelled.\n");
			return;
		}

		messages.clear();
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			if (topic.equals(clientHandler.getTopic())) {
				clientHandler.publisherMessages.get(topic).clear();
				clientHandler.broadcastMessageFromServer("> ALL MESSAGES CLEARED BY SERVER\n");
			}
		}
		System.out.println("> All messages in topic '" + topic + "' have been cleared.\n");
	}

	/**
	 * "kick": Kicks a client by their user ID.
	 * The client is disconnected from the server and their thread is interrupted.
	 *
	 * @param tokens the command tokens containing the user ID to kick
	 */
	private void kickClient(String[] tokens) {
		if (tokens.length < 2) {
			System.out.println("> Usage: kick <userID>\n");
			return;
		}

		if (!tokens[1].matches("\\d+")) {
			System.out.println("> Invalid user ID. Enter 'users' to see the list of connected users.\n");
			return;
		}

		int userID = Integer.parseInt(tokens[1]);
		ClientHandler clientHandler = ClientHandler.clientHandlers.get(userID);
		if (clientHandler == null) {
			System.out.println("> User ID " + userID + " not found.\n");
			return;
		}

		clientHandler.interruptThread(true);
	}

	/**
	 * "inspect": Starts inspect mode for the specified topic.
	 * In inspect mode, the server operator can list and delete messages in a topic.
	 *
	 * @param tokens the command tokens containing the topic name
	 */
	private void startInspectMode(String[] tokens) {
		if (isInspecting) {
			System.out.println("> Command 'inspect' is not available in inspect mode.\n");
			return;
		}

		if (tokens.length < 2) {
			System.out.println("> Usage: inspect <topic>\n");
			return;
		}

		String topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length)); // "example topic" -> "example_topic"
		if (!ClientHandler.topics.containsKey(topic)) {
			System.out.println("> Topic '" + topic + "' does not exist.\n");
			return;
		}

		isInspecting = true;
		currentInspectTopic = topic;
		System.out.println("--- INSPECT MODE STARTED ---");
		System.out.println("> Begun inspecting topic '" + topic + "'. Enter 'help' for a list of available commands.\n");

		// Notify clients that the server is inspecting the topic
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			if (topic.equals(clientHandler.getTopic())) {
				clientHandler.setIsServerInspecting(isInspecting);
			}
		}
	}

	/**
	 * "end": Ends inspect mode for the current topic.
	 * Notifies clients that the server has stopped inspecting.
	 */
	private void endInspectMode() {
		if (!isInspecting) {
			System.out.println("> Command 'end' is only available in inspect mode.\n");
			return;
		}

		isInspecting = false;
		System.out.println("> Exited inspect mode for topic '" + currentInspectTopic + "'.");
		System.out.println("--- INSPECT MODE ENDED ---\n");
		// Notify clients that the server has stopped inspecting the topic
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			if (currentInspectTopic.equals(clientHandler.getTopic())) {
				clientHandler.setIsServerInspecting(isInspecting);
			}
		}
		currentInspectTopic = null;
	}

	/**
	 * "listall": Lists all messages in the current inspect topic.
	 * Only available when in inspect mode.
	 */
	private void listAllMessagesInTopic() {
		if (!isInspecting) {
			System.out.println("> Command 'listall' is only available in inspect mode.\n");
			return;
		}

		ConcurrentLinkedQueue<Message> messages = ClientHandler.topics.get(currentInspectTopic);
		if (messages == null || messages.isEmpty()) {
			System.out.println("> No messages available for topic '" + currentInspectTopic + "'.\n");
			return;
		}
		System.out.println("--- LISTALL: " + messages.size() + " MESSAGES IN '" + currentInspectTopic + "' ---\n");
		for (Message m : messages) System.out.println(m);
		System.out.println("--- LISTALL: END OF MESSAGES IN '" + currentInspectTopic + "' ---\n");
	}

	/**
	 * "delete": Deletes a message with the specified message ID from the current inspect topic.
	 * Notifies clients in the topic about the deletion.
	 *
	 * @param tokens the command tokens containing the message ID to delete
	 */
	private void deleteMessage(String[] tokens) {
		if (!isInspecting) {
			System.out.println("> Command 'delete' is only available in inspect mode.\n");
			return;
		}

		if (tokens.length < 2 || !tokens[1].matches("\\d+")) {
			System.out.println("> Usage: delete <messageID> (see 'listall' for valid id's)\n");
			return;
		}

		int messageID = Integer.parseInt(tokens[1]);
		ConcurrentLinkedQueue<Message> messages = ClientHandler.topics.get(currentInspectTopic);
		if (messages == null) {
			System.out.println("> No messages found for topic '" + currentInspectTopic + "'.\n");
			return;
		}

		boolean removedFromTopic = messages.removeIf(msg -> msg.getId() == messageID);
		boolean removedFromClient = deleteFromClient(messageID);

		if (removedFromTopic && removedFromClient) {
			System.out.println("> (SUCCESS) Message with ID " + messageID + " deleted.\n");
			for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
				if (currentInspectTopic.equals(clientHandler.getTopic())) {
					clientHandler.broadcastMessageFromServer("> MESSAGE (ID " + messageID + ") DELETED BY SERVER");
				}
			}
		} else System.out.println("> (ERROR) Message with ID " + messageID + " not found.\n");
	}

	public boolean deleteFromClient(int msgID) {
		for (ClientHandler clientHandler: ClientHandler.clientHandlers.values()) {
			ArrayList<Message> messages = clientHandler.publisherMessages.get(currentInspectTopic);
			if (messages.removeIf(msg -> msg.getId() == msgID)) return true;
		}
		return false;
	}

	/**
	 * "show": Displays the list of existing topics.
	 * Not available during inspect mode.
	 */

	// make "show" more detailed by adding the number of connected publishers and subscribers to each topic, and also show the number of messages
	private static void showTopics() {
		if (isInspecting) {
			System.out.println("> Command 'show' is not available in inspect mode.\n");
			return;
		}

		if (ClientHandler.topics.isEmpty()) {
			System.out.println("> No topics available.\n");
			return;
		}

		System.out.println("--- SHOW: EXISTING TOPICS ---");
		ArrayList<String> topics = new ArrayList<>();
		for (String topic : ClientHandler.topics.keySet()) {
			if (topics.contains(topic)) continue;
			topics.add(topic);
			int publishers = 0;
			int subscribers = 0;
			for(ClientHandler clientHandler : ClientHandler.clientHandlers.values()){
				if(topic.equals(clientHandler.getTopic())){
					if(clientHandler.getRole().equals("Publisher"))
						publishers++;
					else
						subscribers++;
				}
			}
			System.out.println("> Topic: " + topic);
			System.out.println("> Publishers: " + publishers);
			System.out.println("> Subscribers: " + subscribers);
			System.out.println("> Messages: " + ClientHandler.topics.get(topic).size());
		}
		System.out.println("--- END OF SHOW TOPICS ---\n");

	}

	/**
	 * "help": Displays the help menu with available server commands.
	 * Shows different commands based on whether the server is in inspect mode.
	 */
	private void showHelp() {
		System.out.println("--- HELP: AVAILABLE COMMANDS ---");
		System.out.println("> kick <userID>: Kick a client by ID");
		System.out.println("> export user <userID>: Export all messages of a user to logs/user_exports");
		System.out.println("> export topic <topic>: Export all messages of a topic to logs/topic_exports");
		System.out.println("> users: Show all connected users and their details");
		System.out.println("> user <userID>: Show details of a specific user");
		if (isInspecting) {
			System.out.println("""
					> listall: List all messages in the topic
					> delete <messageId>: Delete a message by ID
					> clear: Clear all messages in the topic being inspected
					> end: Exit interactive mode
					
					! N.B. Commands 'quit' & 'inspect' are disabled in interactive mode,
					\tclient's 'send', 'list', 'listall' are suspended until the mode is exited.
					""");
		} else {
			System.out.println("> show: Show available topics");
			System.out.println("> inspect <topic>: Open interactive mode to inspect a topic (list all messages, delete messages, etc.)");
			System.out.println("> quit: Disconnect from the server\n");
		}
	}

	public boolean isRunning() {
		return serverRunning;
	}

	/**
	 * "quit": Shuts down the server gracefully.
	 * Disconnects all clients and closes the server socket.
	 */
	private void shutdownServer() {
		if (isInspecting) {
			System.out.println("> Cannot shut down server while in inspect mode.\n");
			return;
		}

		serverRunning = false;
		try {
			System.out.println("> (PRE-QUIT)  Connected clients: " + ClientHandler.clientHandlers.size());
			for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) clientHandler.interruptThread();
			if (!serverSocket.isClosed()) serverSocket.close();
			pool.shutdownNow();
			System.out.println("> (POST-QUIT) Connected clients: " + ClientHandler.clientHandlers.size());
		} catch (IOException e) {
			System.out.println("> Error shutting down server: " + e.getMessage());
		}

		System.out.println("--- SERVER SHUTDOWN ---");
		System.exit(0);
	}

	/**
	 * Checks if the server is currently inspecting the given topic.
	 *
	 * @param topic the topic to check
	 * @return true if the server is inspecting the topic, false otherwise
	 */
	public boolean isInspectingTopic(String topic) {
		return isInspecting && topic.equals(currentInspectTopic);
	}

	/**
	 * The main method to start the server.
	 * Expects a port number as an argument.
	 *
	 * @param args command-line arguments, expects one argument: the port number
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: java Server <port>");
			System.exit(1);
		}

		try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
			Server server = new Server(serverSocket);
			// When start() is called on the thread, server.startServer() is called within the thread
			Thread serverThread = new Thread(server::startServer);
			serverThread.start();
			server.listenForCommands();
		} catch (IOException e) {
			System.out.println("> Error starting server: " + e.getMessage());
		}
	}
}