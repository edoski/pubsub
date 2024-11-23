import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

/**
 * The Client class represents the client-side application.
 * It connects to the server, sends commands based on user input,
 * handles server responses, and manages command execution during server inspect mode.
 */
public class Client {
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private static Boolean isPublisher = null;
	private static String topic = null;
	private volatile boolean running = true;
	public static boolean isServerInspecting = false;
	private static final ArrayList<String> backlog = new ArrayList<>();
	private static final ArrayList<String> publisherOnlyCommands = new ArrayList<>(Arrays.asList("send", "list"));
	private static final ArrayList<String> disabledWhenInspecting = new ArrayList<>(Arrays.asList("send", "list", "listall"));

	/**
	 * Constructs a Client with the specified socket connection.
	 *
	 * @param socket the socket connection to the server
	 */
	public Client(Socket socket) {
		try {
			this.socket = socket;
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			closeEverything();
		}
	}

	/**
	 * Starts the client application.
	 * Handles user input and sends commands to the server.
	 */
	private void start() {
		System.out.println("--- CONNECTED TO SERVER ON PORT " + socket.getPort() + " ---\n"
		                   + "> Enter 'help' for a list of available commands.\n");
		receiveMessage(); // Start the receiveMessage thread

		// Use the main thread for input handling
		try (Scanner scanner = new Scanner(System.in)) {
			while (running) {
				processCommand(scanner.nextLine());
			}
		} catch (Exception e) {
			System.out.println("> Error reading from console: " + e.getMessage());
			closeEverything();
		}
	}

	/**
	 * Starts a new thread to listen for messages from the server.
	 * Handles server responses and updates the client's state accordingly.
	 */
	private void receiveMessage() {
		new Thread(() -> {
			while (running) {
				try {
					String messageFromServer = in.readLine();
					if (messageFromServer == null) {
						closeEverything();
					}
					handleMessageFromServer(messageFromServer);
				} catch (IOException e) {
					if (!running) {
						break;
					}
					System.out.println("> Connection lost: " + e.getMessage());
					closeEverything();
				}
			}
		}).start();
	}

	/**
	 * Processes the user's input command.
	 * Sends commands to the server or handles them locally as needed.
	 *
	 * @param inputLine the user's input command
	 */
	private void processCommand(String inputLine) {
		if (inputLine.isEmpty()) {
			return;
		}
		// Sanitize input, split by whitespace
		String[] tokens = inputLine.trim().split("\\s+");
		String command = tokens[0].toLowerCase();

		// If the server is inspecting, verify & queue the command for later execution
		if (isServerInspecting && disabledWhenInspecting.contains(command)) {
			if (!isPublisher && publisherOnlyCommands.contains(command)) {
				System.out.println("> You cannot use the command '" + command + "' as a subscriber.\n");
			} else {
				System.out.println(command.equals("listall")
				                       ? "> Command '" + inputLine + "' will execute last (to avoid inconsistencies) when Inspect mode is ended.\n"
				                       : "> Command '" + inputLine + "' has been queued and will execute when Inspect mode is ended.\n");
				backlog.add(inputLine);
			}
			return;
		}

		// Commands that use out.println() send a request to the client handler to fulfill the command
		// The rest of the commands are handled entirely or partially locally
		switch (command) {
		case "help" -> showHelp();
		case "show" -> out.println("show");
		case "send" -> handleSendCommand(tokens);
		case "list" -> out.println("list");
		case "listall" -> out.println("listall");
		case "quit" -> {
			out.println("quit");
			closeEverything();
		}
		case "publish", "subscribe" -> handleRegistration(tokens);
		default -> System.out.println("> Unknown command. Enter 'help' to see the list of available commands.\n");
		}
	}

	/**
	 * "help": Displays the help menu with available client commands.
	 * Shows different commands based on the client's role and server inspect mode.
	 */
	private void showHelp() {
		System.out.println("--- HELP: AVAILABLE COMMANDS ---");
		if (isPublisher == null) {
			System.out.println("> [publish | subscribe] <topic>: Register as publisher (read-write) or subscriber (read-only) for <topic>");
		} else {
			if (isPublisher) { // Only publishers can use these commands
				System.out.println((isServerInspecting ? "* " : "") + "> send <message>: Send a message to the server\n" +
				                   (isServerInspecting ? "* " : "") + "> list: List the messages you have sent in the topic");
			}
			// Only registered clients (both publishers & subscribers) can use this command
			System.out.println((isServerInspecting ? "* " : "") + "> listall: List all messages in the topic");
		}
		// All clients (registered & unregistered) can use these commands
		System.out.println((isServerInspecting ? "  " : "") + "> show: Show available topics");
		System.out.println((isServerInspecting ? "  " : "") + "> quit: Disconnect from the server\n");
		if (isServerInspecting) {
			System.out.println("* Commands marked with an asterisk(*) are disabled during Inspect mode.\n"
			                   + "Any usage of these commands will be queued and will execute once Inspect mode is ended.\n");
		}
	}

	/**
	 * "send": Sends a message to the server.
	 * Only available to publishers.
	 *
	 * @param tokens the user's input command tokens
	 */
	private void handleSendCommand(String[] tokens) {
		if (isPublisher == null || !isPublisher) {
			System.out.println(isPublisher == null ? "> You need to register as a publisher first.\n"
			                                       : "> You are registered as a subscriber. You cannot send messages.\n");
			return;
		}

		if (tokens.length < 2) {
			System.out.println("> Usage: send <message>\n");
			return;
		}

		// Combine tokens to form the message in case of multiple words
		String message = String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));
		out.println(message);
	}

	/**
	 * "publish" | "subscribe": Handles registration commands from the user.
	 * Registers the client as a publisher or subscriber to a topic.
	 *
	 * @param tokens the command tokens containing the role and topic
	 */
	private void handleRegistration(String[] tokens) {
		if (tokens.length < 2) {
			System.out.println("> Usage: " + tokens[0] + " <topic_name>\n");
			return;
		}

		if (isPublisher != null) { // Allow clients to change their role and topic
			String newRole = tokens[0].toLowerCase();
			String newTopic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length));

			// Check if the client is already registered as a publisher or subscriber for the same topic
			if (isPublisher == newRole.equals("publish") && topic.equals(newTopic)) {
				System.out.println("> You are already a '" + (isPublisher ? "publisher" : "subscriber") + "' for topic '" + topic + "'.\n");
				return;
			}

			try {
				Scanner scanner = new Scanner(System.in);
				System.out.print("> You are currently a '" + (isPublisher ? "publisher" : "subscriber") + "' for topic '" + topic + "'.\n"
				                 + "> Do you want to change your role and topic? (y/n): ");
				String response = scanner.nextLine().toLowerCase();
				if (!response.equalsIgnoreCase("y") && !response.equalsIgnoreCase("yes")) {
					System.out.println("> OK. Registration unchanged.\n");
					return;
				}
			} catch (Exception e) {
				System.out.println("> Error reading from console: " + e.getMessage());
				closeEverything();
			}
		}

		// Update the client's role and topic client-side
		String role = tokens[0].toLowerCase();
		isPublisher = role.equals("publish");
		topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length)); // "example topic" -> "example_topic"

		// Send registration command to the server to update the client's role and topic server-side
		// token 0 is the role, token 1 is the topic --> "publish football"
		out.println(String.join(" ", tokens));
	}

	/**
	 * Handles messages received from the server.
	 * Updates the client's inspect mode status and executes backlogged commands if necessary.
	 *
	 * @param messageFromServer the message received from the server
	 */
	private void handleMessageFromServer(String messageFromServer) {
		String[] tokens = messageFromServer.split("\\s+");

		if (tokens[0].equals("IS_SERVER_INSPECTING")) {
			isServerInspecting = Boolean.parseBoolean(tokens[1]);
			if (!isServerInspecting) {
				executeBacklogCommands();
			}
			return;
		}

		// If it's not an inspect mode start/end, the message is meant for the client
		System.out.println(messageFromServer);
	}

	/**
	 * Executes commands that were queued during server inspect mode.
	 * Commands are executed in order, except for 'list' and 'listall' commands, executed last.
	 */
	private void executeBacklogCommands() {
		if (backlog.isEmpty()) {
			return;
		}

		backlog.sort((a, b) -> { // Sort "list" and "listall" commands to be executed last to avoid interleaving
			if (a.startsWith("list") && !b.startsWith("list")) {
				return 1;
			}
			if (!a.startsWith("list") && b.startsWith("list")) {
				return -1;
			}
			return 0;
		});

		System.out.println("--- COMMANDS TO BE EXECUTED ---");
		int i = 1;
		for (String cmd : backlog) {
			System.out.println("> " + (i++) + ": " + cmd);
		}
		System.out.println();

		for (String cmd : backlog) {
			processCommand(cmd);
		}
		backlog.clear();
	}

	/**
	 * Closes the socket and input/output streams, and exits the application.
	 */
	private void closeEverything() {
		running = false;
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
			backlog.clear(); // In case the client is closed before inspect mode ends (e.g. kicked)
			System.out.println("--- CLIENT SHUTDOWN ---");
			System.exit(0);
		} catch (IOException e) {
			System.out.println("> Error closing resources: " + e.getMessage());
		}
	}

	/**
	 * The main method to start the client application.
	 * Expects a hostname and port number as arguments.
	 *
	 * @param args command-line arguments, expects two arguments: the hostname and port number
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			System.err.println("> Usage: java Client <hostname> <port>");
			return;
		}

		try {
			Socket socket = new Socket(args[0], Integer.parseInt(args[1]));
			Client client = new Client(socket);
			client.start();
		} catch (IOException e) {
			System.out.println("> Unable to connect to the server.");
		}
	}
}
