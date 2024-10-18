import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

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

	public Client(Socket socket) {
		try {
			this.socket = socket;
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			closeEverything();
		}
	}

	private void start() {
        System.out.println(
                "--- CONNECTED TO SERVER ON PORT " + socket.getPort() + " ---\n" +
                "> Enter 'help' for a list of available commands.\n"
        );
        receiveMessage(); // Start the receiveMessage thread

        try (Scanner scanner = new Scanner(System.in)) {
            while (running) { // Use the main thread for input handling
                if (!running) break;
				processCommand(scanner.nextLine());
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("> Error reading from console: " + e.getMessage());
                running = false;
                closeEverything();
            }
        }
    }

	private void receiveMessage() {
		new Thread(() -> {
			while (running && !socket.isClosed()) {
				try {
					String messageFromServer = in.readLine();
					if (messageFromServer == null) {
						closeEverything();
						break; // Server disconnected
					}
					handleMessageFromServer(messageFromServer);
				} catch (IOException e) {
					if (running) {
						System.out.println("> Connection lost: " + e.getMessage());
						running = false;
						closeEverything();
					}
					break;
				}
			}
		}).start();
	}

	// Heart of the client, processes the input commands
	// todo refactor this and ClientHandler inputs so that you send sanitized tokens[] directly instead of inputLine
	private void processCommand(String inputLine) {
		// Sanitize input, split by whitespace
		String[] tokens = inputLine.trim().split("\\s+");
		String command = tokens[0].toLowerCase();

		// If the server is inspecting, queue the command for later execution
		// todo refactor this in a cleaner way
		if (isServerInspecting && disabledWhenInspecting.contains(command)) {
			if (!isPublisher && publisherOnlyCommands.contains(command)) {
				System.out.println("> You cannot use the command '" + command + "' as a subscriber.\n");
				return;
			}
			System.out.println("> Command '" + inputLine + "' will be executed when Inspect mode is ended.\n");
			synchronized (backlog) {







				// todo test if synchronized is needed








				backlog.add(inputLine);
			}
			return;
		}

		// Commands that use out.println() send a request to the client handler to fulfill the command
		// The rest of the commands are handled entirely or partially locally
		switch (command) {
			case "":
				break;
			case "help":
				showHelp();
				break;
			case "show":
				out.println("show");
				break;
			case "send":
				handleSendCommand(tokens);
				break;
			case "list":
				out.println("list");
				break;
			case "listall":
				out.println("listall");
				break;
			case "quit":
				out.println("quit");
				closeEverything();
				break;
			case "publish":
			case "subscribe":
				handleRegistration(tokens);
				break;
			default:
				System.out.println("> Unknown command. Enter 'help' to see the list of available commands.\n");
				break;
		}
	}

	private void showHelp() {
		System.out.println("--- AVAILABLE COMMANDS ---");
		if (isPublisher == null) {
			System.out.println("> [publish | subscribe] <topic>: Register as a publisher (read & write) or subscriber (read-only) for the specified topic");
		}
		if (isPublisher != null && !isServerInspecting) {
			if (isPublisher) { // Only publishers can use these commands
				System.out.println("> send <message>: Send a message to the server");
				System.out.println("> list: List the messages you have sent in the topic");
			}
			// Only registered clients (both publishers & subscribers) can use this command
			System.out.println("> listall: List all messages in the topic");
		}
		// All clients (registered & unregistered) can use these commands
		System.out.println("> show: Show available topics");
		System.out.println("> quit: Disconnect from the server\n");
	}

	private void handleSendCommand(String[] tokens) {
		if (isPublisher == null || !isPublisher) {
			String errorMessage = isPublisher == null
					? "> You need to register as a publisher first.\n"
					: "> You are registered as a subscriber. You cannot send messages.\n";
			System.out.println(errorMessage);
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

	private void handleRegistration(String[] tokens) {
		if (isPublisher != null) {
			System.out.println(
					"> You have already registered as '" + (isPublisher ? "publisher" : "subscriber") + "' for topic '" + topic + "'.\n"
			);
			return;
		}

		if (tokens.length < 2) {
			System.out.println("> Usage: " + tokens[0] + " <topic_name>\n");
			return;
		}

		// Send registration command to the server
		out.println(String.join(" ", tokens));

		String role = tokens[0].toLowerCase();
		isPublisher = role.equals("publish");
		topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length)); // "example topic" -> "example_topic"
	}

	private void handleMessageFromServer(String messageFromServer) {
		String[] tokens = messageFromServer.split("\\s+");
		if (tokens[0].equals("IS_SERVER_INSPECTING")) {
			isServerInspecting = Boolean.parseBoolean(tokens[1]);
			if (!isServerInspecting) executeBacklogCommands();
			return;
		}

		System.out.println(messageFromServer);
	}

	private void executeBacklogCommands() {
		if (backlog.isEmpty()) return;
		synchronized (backlog) {
			for (String cmd : backlog) {




//	todo			out.println("process " + cmd);     like add in ClientHandler a "process" case that prefixes the command to the backlog execution





				processCommand(cmd);
			}
			backlog.clear();
		}
	}

	private void closeEverything() {
		running = false;
		try {
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
			System.out.println("--- CLIENT SHUTDOWN ---");
			System.exit(0);
		} catch (IOException e) {
			System.out.println("> Error closing resources: " + e.getMessage());
		}
	}

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