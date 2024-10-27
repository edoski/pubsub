import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// todo: priority
//  ***** does Server.isInspectingTopic need to be synchronized? does ClientHandler.clientRunning need to be volatile?
//  *** see if can break up classes into smaller classes
//  ** connect the server to a database to store messages
//  * make "show" more detailed by adding the number of connected publishers and subscribers to each topic, and also show the number of messages
//  * add a "user <userID>" server command to show the user's details (current topic, current role, messages sent in current topic)
//  * add a "kick <userID>" command to disconnect a client by ID
//  * add a "clear <topic>" command to clear all messages in a topic (with confirmation)
//  * add a "export [user <userID> | topic <topic>]" command to save all messages [user sent in all topics (segment by topic) | in a topic] to file
//  * FOR ANY OF THE ABOVE: update server's showHelp() method to include new commands
//  * improve listPublisherMessages function, because if the server deletes something during inspect mode and the client
//    that send the deleted message digits list, he will get all the messages, including the deleted one

// todo: secondary
//  ? make unit tests for all classes
//  ? create a Testing class that simulates a client and server interaction

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
				case "listall" -> listAllMessagesInTopic();
				case "delete" -> deleteMessage(tokens);
				case "end" -> endInspectMode();
				case "inspect" -> startInspectMode(tokens);
				case "quit" -> shutdownServer();
				case "show" -> showTopics();
				case "help" -> showHelp();
				default -> System.out.println("> Unknown command. Enter 'help' to see the list of available commands.\n");
			}
		}
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
		for (ClientHandler clientHandler : ClientHandler.clientHandlers) {
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
		for (ClientHandler clientHandler : ClientHandler.clientHandlers) {
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
			System.out.println("> Usage: delete <messageId> (see 'listall' for valid id's)\n");
			return;
		}

		int messageId = Integer.parseInt(tokens[1]);
		ConcurrentLinkedQueue<Message> messages = ClientHandler.topics.get(currentInspectTopic);
		if (messages == null) {
			System.out.println("> No messages found for topic '" + currentInspectTopic + "'.\n");
			return;
		}

		boolean removed = messages.removeIf(m -> m.getId() == messageId);
		if (removed) {
			System.out.println("> (SUCCESS) Message with ID " + messageId + " deleted.\n");
			for (ClientHandler clientHandler : ClientHandler.clientHandlers) {
				if (currentInspectTopic.equals(clientHandler.getTopic())) {
					clientHandler.broadcastMessageFromServer("> MESSAGE (ID " + messageId + ") DELETED BY SERVER");
				}
			}
		} else System.out.println("> (ERROR) Message with ID " + messageId + " not found.\n");
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
			System.out.println("> No topics available.\n");
			return;
		}

		System.out.println("--- SHOW: EXISTING TOPICS ---");
		for (String topic : ClientHandler.topics.keySet()) System.out.println("> " + topic);
		System.out.println();
	}

	/**
	 * "help": Displays the help menu with available server commands.
	 * Shows different commands based on whether the server is in inspect mode.
	 */
	private void showHelp() {
		System.out.println("--- HELP: AVAILABLE COMMANDS ---");
		if (isInspecting) {
			System.out.println("""
					> listall: List all messages in the topic
					> delete <messageId>: Delete a message by ID
					> end: Exit interactive mode
					
					! N.B. Commands 'quit' & 'inspect' are disabled in interactive mode,
					\tclient's 'send', 'list', 'listall' are suspended until the mode is exited.""");
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
			for (ClientHandler clientHandler : ClientHandler.clientHandlers) clientHandler.interruptThread();
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
	public synchronized boolean isInspectingTopic(String topic) {
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