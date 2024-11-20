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

	public Server(ServerSocket serverSocket) { this.serverSocket = serverSocket; }

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
					if (!serverRunning) {
						break;
					}
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
	private void processCommand() {
		try (Scanner scanner = new Scanner(System.in)) {
			while (serverRunning) {
				String commandLine = scanner.nextLine().trim();
				// Empty commands are ignored
				if (commandLine.isEmpty()) {
					continue;
				}
				// Otherwise, command line split into tokens and processed
				String[] tokens = commandLine.split("\\s+");
				String command = tokens[0].toLowerCase();

				switch (command) {
				case "show" -> showTopics();
				case "quit" -> shutdownServer();
				case "inspect" -> startInspectMode(tokens);
				case "end" -> endInspectMode();
				case "listall" -> listAllMessagesInTopic();
				case "delete" -> deleteMessage(tokens);
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
	}

	/**
	 * "show": Displays the list of existing topics.
	 * Not available during inspect mode.
	 */
	private static void showTopics() {
		if (isInspecting) {
			System.out.println("> Command 'show' is not available in inspect mode.\n");
			return;
		}

		if (ClientHandler.topics.isEmpty()) {
			System.out.println("> No existingTopics available.\n");
			return;
		}

		StringBuilder showTopicsOutput = new StringBuilder();
		showTopicsOutput.append("--- SHOW: EXISTING TOPICS ---\n");
		ArrayList<String> existingTopics = new ArrayList<>();
		for (String topic : ClientHandler.topics.keySet()) {
			if (existingTopics.contains(topic)) {
				continue;
			}
			existingTopics.add(topic);
			int publishers = 0, subscribers = 0;
			for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
				if (topic.equals(clientHandler.getTopic())) {
					boolean isPublisher = clientHandler.getRole().equals("Publisher");
					if (isPublisher) {
						publishers++;
					} else {
						subscribers++;
					}
				}
			}
			showTopicsOutput.append("\n--- TOPIC: ").append(topic).append("\n");
			showTopicsOutput.append("> PUB: ").append(publishers).append("\n");
			showTopicsOutput.append("> SUB: ").append(subscribers).append("\n");
			showTopicsOutput.append("> MSG: ").append(ClientHandler.topics.get(topic).size()).append("\n");
		}
		showTopicsOutput.append("\n--- END OF TOPIC LIST ---\n");
		System.out.println(showTopicsOutput);
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
			for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
				clientHandler.interruptThread();
			}
			if (!serverSocket.isClosed()) {
				serverSocket.close();
			}
			pool.shutdownNow();
			System.out.println("> (POST-QUIT) Connected clients: " + ClientHandler.clientHandlers.size());
		} catch (IOException e) {
			System.out.println("> Error shutting down server: " + e.getMessage());
		}

		System.out.println("--- SERVER SHUTDOWN ---");
		System.exit(0);
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
		for (Message m : messages) {
			System.out.println(m);
		}
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
		} else {
			System.out.println("> (ERROR) Message with ID " + messageID + " not found.\n");
		}
	}

	/**
	 * Deletes a message with the specified message ID from a client's message list.
	 * Helper method for the "delete" command.
	 *
	 * @param msgID the message ID to delete
	 * @return true if the message was deleted, false otherwise
	 */
	public boolean deleteFromClient(int msgID) {
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			ArrayList<Message> messages = clientHandler.publisherMessages.get(currentInspectTopic);
			if (messages.removeIf(msg -> msg.getId() == msgID)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * "help": Displays the help menu with available server commands.
	 * Shows different commands based on whether the server is in inspect mode.
	 */
	private void showHelp() {
		StringBuilder help = new StringBuilder();
		help.append("--- HELP: AVAILABLE COMMANDS ---\n");
		if (isInspecting) {
			help.append("> listall: List all messages in the topic\n");
			help.append("> delete <messageId>: Delete a message by ID\n");
			help.append("> clear: Clear all messages in the topic being inspected\n");
			help.append("> end: Exit interactive mode\n\n");
			help.append("! N.B. Commands 'quit' & 'inspect' are disabled in interactive mode,\n");
			help.append("\tclient's 'send', 'list', 'listall' are suspended until the mode is exited.\n");
		} else {
			help.append("> show: Show available topics\n");
			help.append("> inspect <topic>: Open interactive mode to inspect a topic (list all messages, delete messages, etc.)\n");
			help.append("> quit: Disconnect from the server\n");
		}
		help.append("> kick <userID>: Kick a client by ID\n");
		help.append("> export user <userID>: Export all messages of a user to logs/user_exports\n");
		help.append("> export topic <topic>: Export all messages of a topic to logs/topic_exports\n");
		help.append("> users: Show all connected users and their details\n");
		help.append("> user <userID>: Show details of a specific user\n");
		help.append("--- END OF HELP ---\n");
		System.out.println(help);
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

		try (Scanner scanner = new Scanner(System.in)) {
			System.out.print("> Are you sure you want to clear all messages in topic '" + topic + "'? (y/n): ");
			if (!scanner.nextLine().equalsIgnoreCase("y")) {
				System.out.println("> Clear operation cancelled.\n");
				return;
			}
		}
		messages.clear();
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			if (clientHandler.publisherMessages.containsKey(topic)) {
				clientHandler.publisherMessages.get(topic).clear();
				clientHandler.broadcastMessageFromServer("> ALL MESSAGES IN '" + topic + "' CLEARED BY SERVER\n");
			}
		}
		System.out.println("> All messages in topic '" + topic + "' have been cleared.\n");
	}

	/**
	 * "export": Exports messages for a user or topic to a text file.
	 * The exported file is saved in the logs/user_exports or logs/topic_exports directory,
	 * depending on the specified export type (token[1]).
	 *
	 * @param tokens the command tokens containing the export type and user ID or topic
	 */
	private void export(String[] tokens) {
		if (tokens.length < 3) {
			System.out.println("> Usage: export [user <userID> | topic <topic>]\n");
			return;
		}

		String exportType = tokens[1];
		switch (exportType) {
		case "user" -> exportUser(tokens[2]);                                                        // export user <userID>
		case "topic" -> exportTopic(String.join("_", Arrays.copyOfRange(tokens, 2, tokens.length))); // export topic <topic>
		default -> System.out.println("> Invalid export type. Use 'user' or 'topic'.\n");
		}
	}

	/**
	 * Exports all messages for a specific topic to a text file in the logs/topic_exports directory.
	 *
	 * @param topic the topic to export messages for
	 */
	private void exportTopic(String topic) {
		if (!ClientHandler.topics.containsKey(topic)) {
			System.out.println("> Topic '" + topic + "' does not exist.\n");
			return;
		}

		ConcurrentLinkedQueue<Message> messages = ClientHandler.topics.get(topic);
		if (messages.isEmpty()) {
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
				int user = -1;
				for (Message msg : messages) {
					if (!(msg.getUserID() == user)) {
						user = msg.getUserID();
						writer.println("> USER: " + user + "\n");
					}
					writer.println(msg);
				}
				System.out.println("> Messages in topic '" + topic + "' exported to '" + dir + "/" + filename + "'.\n");
			}
		} catch (IOException e) {
			System.out.println("> Error exporting messages: " + e.getMessage());
		}
	}

	/**
	 * Exports all messages for a specific user to a text file in the logs/user_exports directory.
	 *
	 * @param userID the user ID to export messages for
	 */
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

		ArrayList<Message> messages = new ArrayList<>();
		for (ArrayList<Message> msgs : clientHandler.publisherMessages.values()) {
			messages.addAll(msgs);
		}

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
				String topic = null;
				for (Message msg : messages) {
					if (!msg.getTopic().equals(topic)) {
						topic = msg.getTopic();
						writer.println("> TOPIC: " + topic + "\n");
					}
					writer.println(msg);
				}
				System.out.println("> Messages for user ID " + id + " exported to '" + dir + "/" + filename + "'.\n");
			}
		} catch (IOException e) {
			System.out.println("> Error exporting messages: " + e.getMessage());
		}
	}

	/**
	 * "users": Show all connected users and their details current topic, current role,num. messages sent in current topic
	 */
	public void showAllUsersInformation() {
		if (ClientHandler.clientHandlers.isEmpty()) {
			System.out.println("> No users connected.\n");
			return;
		}

		StringBuilder usersInformation = new StringBuilder();
		usersInformation.append("--- SHOW: ALL USERS ---\n\n");
		for (ClientHandler clientHandler : ClientHandler.clientHandlers.values()) {
			usersInformation.append(showUserInformation(clientHandler)).append("\n");
		}
		usersInformation.append("--- END OF ALL USERS ---\n");
		System.out.println(usersInformation);
	}

	/**
	 * "user": Show user information for a specific user by their user ID.
	 *
	 * @param tokens the command tokens containing the user ID
	 */
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

		String userInformation = "--- SHOW: USER ID " + clientHandler.getUserID() + " ---\n"
		                         + "> CURRENT TOPIC: " + clientHandler.getTopic() + "\n"
		                         + "> CURRENT ROLE:  " + clientHandler.getRole() + "\n"
		                         + "> MESSAGES SENT: " + clientHandler.getNumMessagesSent() + "\n"
		                         + "--- END OF USER DETAILS ---\n";
		System.out.println(userInformation);
	}

	/**
	 * Overloaded method to show user information for a specific client handler.
	 * Helper method for the "users" command.
	 *
	 * @param clientHandler the client handler to show information for
	 */
	private String showUserInformation(ClientHandler clientHandler) {
		return "--- USER ID " + clientHandler.getUserID() + " ---\n"
		    + "> CURRENT TOPIC: " + clientHandler.getTopic() + "\n"
		    + "> CURRENT ROLE:  " + clientHandler.getRole() + "\n"
		    + "> MESSAGES SENT: " + clientHandler.getNumMessagesSent() + "\n";
	}

	public boolean isRunning() { return serverRunning; }

	/**
	 * Checks if the server is currently inspecting the given topic.
	 *
	 * @param topic the topic to check
	 * @return true if the server is inspecting the topic, false otherwise
	 */
	public boolean isInspectingTopic(String topic) { return isInspecting && topic.equals(currentInspectTopic); }

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
			server.processCommand();
		} catch (IOException e) {
			System.out.println("> Error starting server: " + e.getMessage());
		}
	}
}
