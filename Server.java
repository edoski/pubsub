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
//  *** review code for any unnecessary if's/code and concurrency issues
//  *** see if can break up classes into smaller classes

// todo: secondary
//  ? give clients ability to change roles (publisher to subscriber and vice versa) after registration
//  ? connect the server to a database to store messages
//  ? make unit tests for all classes
//  ? create a Testing class that simulates a client and server interaction

public class Server {
	private final ServerSocket serverSocket;
	private final ExecutorService pool = Executors.newCachedThreadPool();
	private static boolean running = true;
	private static boolean isInspecting = false;
	private String currentInspectTopic = null;

	public Server(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	public void startServer() {
		try {
			System.out.println("--- SERVER STARTED ON PORT " + serverSocket.getLocalPort() + " ---");
			while (running && !serverSocket.isClosed()) {
				try {
					serverSocket.setSoTimeout(1000); // Set a timeout for accept() to periodically check if the server is still running
					Socket socket = serverSocket.accept();
					System.out.println("--- NEW CLIENT CONNECTED ---");
					ClientHandler clientHandler = new ClientHandler(socket, this);
					pool.execute(clientHandler);
				} catch (SocketTimeoutException e) {
					if (!running) break; // Continue to check if the server is still running
				} catch (SocketException e) {
					if (running) throw new RuntimeException(e);
					else break; // Server socket has been closed, exit the loop
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			pool.shutdown();
		}
	}

	private void listenForCommands() {
		Scanner scanner = new Scanner(System.in);
		while (running) {
			String commandLine = scanner.nextLine();
			String[] tokens = commandLine.trim().split("\\s+");
			String command = tokens[0].toLowerCase();

			switch (command) {
				case "listall":
					listAllMessagesInTopic();
					break;
				case "delete":
					deleteMessage(tokens);
					break;
				case "end":
					endInspectMode();
					break;
				case "inspect":
					startInspectMode(tokens);
					break;
				case "quit":
					shutdownServer();
					break;
				case "show":
					showTopics();
					break;
				case "help":
					showHelp();
					break;
				case "":
					break;
				default:
					System.out.println("> Unknown command. Enter 'help' to see the list of available commands.\n");
			}
		}
	}

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
		System.out.println(
				"--- INSPECT MODE STARTED ---\n" +
				"> Begun inspecting topic '" + topic + "'. Enter 'help' for a list of available commands.\n"
		);

		// Notify clients that the server is inspecting the topic
		for (ClientHandler clientHandler : ClientHandler.clientHandlers) {
			if (topic.equals(clientHandler.getTopic())) {
				clientHandler.setIsServerInspecting(true);
			}
		}

	}

	private void endInspectMode() {
		if (!isInspecting) {
			System.out.println("> Command 'end' is only available in inspect mode.\n");
			return;
		}

		isInspecting = false;
		System.out.println(
				"> Exited inspect mode for topic '" + currentInspectTopic + "'.\n" +
				"--- INSPECT MODE ENDED ---\n"
		);
		// Notify clients that the server has stopped inspecting the topic
		for (ClientHandler clientHandler : ClientHandler.clientHandlers) {
			if (currentInspectTopic.equals(clientHandler.getTopic())) {
				clientHandler.setIsServerInspecting(false);
			}
		}
		currentInspectTopic = null;
	}

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
		for (String topic : ClientHandler.topics.keySet()) {
			System.out.println("> " + topic);
		}
		System.out.println();
	}

	private void showHelp() {
		System.out.println("--- HELP: AVAILABLE COMMANDS ---");
		System.out.println("> show: Show available topics");
		if (isInspecting) {
			System.out.println(
					"""
							> listall: List all messages in the topic
							> delete <messageId>: Delete a message by ID
							> end: Exit interactive mode
							
							! N.B. Commands "quit" & "inspect" are disabled in interactive mode,
							\t   client operations (send, list, listall) are suspended until the mode is exited."""
			);
		} else {
			System.out.println("> inspect <topic>: Open interactive mode to inspect a topic (list all messages, delete messages, etc.)");
			System.out.println("> quit: Disconnect from the server\n");
		}
	}

	public boolean isRunning() {
		return running;
	}

	private void shutdownServer() {
		if (isInspecting) {
			System.out.println("> Cannot shut down server while in inspect mode.\n");
			return;
		}

		running = false;
		try {
			System.out.println("> (PRE-QUIT) Connected clients: " + ClientHandler.clientHandlers.size());
			for (ClientHandler clientHandler : ClientHandler.clientHandlers) clientHandler.interruptThread();

			if (!serverSocket.isClosed()) serverSocket.close();
			pool.shutdownNow();
			System.out.println("> (POST-QUIT) Connected clients: " + ClientHandler.clientHandlers.size());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		System.out.println("--- SERVER SHUTDOWN ---");
		System.exit(0);
	}

	public synchronized boolean isInspectingTopic(String topic) {
		return isInspecting && topic.equals(currentInspectTopic);
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: java Server <port>");
			System.exit(1);
		}

		try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
			Server server = new Server(serverSocket);
			Thread serverThread = new Thread(server::startServer);
			serverThread.start();
			server.listenForCommands();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}