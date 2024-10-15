import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class Client {
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private Boolean isPublisher = null;
	private String topic = null;
	public static CopyOnWriteArrayList<Message> messages = new CopyOnWriteArrayList<>();
	private volatile boolean running = true;

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
		// Start the receiveMessage thread
		receiveMessage();

		// Use the main thread for input handling
		try (Scanner scanner = new Scanner(System.in)) {
			while (running) {
				if (!running) {
					break;
				}

				String inputLine = scanner.nextLine();
				String[] tokens = inputLine.trim().split("\\s+");
				String command = tokens[0].toLowerCase();

				switch (command) {
					case "help":
						showHelp();
						break;
					case "send":
						handleSendCommand(tokens);
						break;
					case "show":
						out.println("show");
						break;
					case "listall":
						out.println("listall");
						break;
					case "list":
						out.println("list");
						break;
					case "quit":
						out.println("quit");
						closeEverything();
						break;
					case "publish":
					case "subscribe":
						handleRegistration(inputLine);
						break;
					case "":
						break;
					default:
						System.out.println("> Unknown command. Enter 'help' to see the list of available commands.");
						break;
				}
			}
		} catch (Exception e) {
			if (running) {
				System.out.println("> Error reading from console: " + e.getMessage());
				running = false;
				closeEverything();
			}
		}
	}

	private void handleSendCommand(String[] tokens) {
		if (isPublisher == null) {
			System.out.println("> You need to register as a publisher first.");
		} else if (isPublisher) {
			if (tokens.length < 2) {
				System.out.println("> Usage: send <message>");
			} else {
				String message = String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));
				messages.add(new Message(topic, message));
				out.println(message);
			}
		} else {
			System.out.println("> You are registered as a subscriber. You cannot send messages.");
		}
	}

	private void handleRegistration(String inputLine) {
		if (isPublisher == null && topic == null) {
			// Send registration command to the server
			out.println(inputLine);
			// Update local variables after registration
			String[] tokens = inputLine.trim().split("\\s+");
			if (tokens.length >= 2) {
				String role = tokens[0].toLowerCase();
				isPublisher = role.equals("publish");
				// Combine tokens to form the topic name in case it contains spaces
				topic = String.join("_", Arrays.copyOfRange(tokens, 1, tokens.length));
			}
		} else {
			System.out.println("> You have already registered as '" + (isPublisher ? "publisher" : "subscriber") + "' for topic '" + topic + "'.");
		}
	}

	private void showHelp() {
		System.out.println("--- AVAILABLE COMMANDS ---");
		if (isPublisher == null) {
			System.out.println("- [publish | subscribe] <topic>: Register as a publisher (read & write) or subscriber (read-only) for the specified topic");
		} else if (isPublisher) {
			// Only publishers can use these command
			System.out.println("- send <message>: Send a message to the server");
//			todo
			System.out.println("- list: List all messages you have sent in the topic");
		} else {
			// Only registered clients (both publishers & subscribers) can use this command
			System.out.println("- listall: List all messages in the topic");
		}
		// All clients (registered & unregistered) can use these commands
		System.out.println("- show: Show available topics");
		System.out.println("- quit: Disconnect from the server\n");
	}

	public void receiveMessage() {
		new Thread(() -> {
			while (running && !socket.isClosed()) {
				try {
					String messageFromServer = in.readLine();
					if (messageFromServer != null) {
						System.out.println(messageFromServer);
					} else {
						closeEverything();
						break; // Server disconnected
					}
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
			System.out.println("> Usage: java Client <hostname> <port>");
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