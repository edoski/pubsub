import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private String userRole = null;
	private String topic = null;
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
		System.out.println("> Connected to the server. Type 'help' for a list of available commands.");
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
					case "quit":
						System.out.println("> DISCONNECTING...");
						out.println("quit");
						closeEverything();
						break;
					case "publish":
					case "subscribe":
						handleRegistration(inputLine);
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
		if (userRole == null) {
			System.out.println("> You need to register as a publisher first.");
		} else if (isPublisher()) {
			if (tokens.length < 2) {
				System.out.println("> Usage: send <message>");
			} else {
				String message = String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
				out.println(message);
			}
		} else {
			System.out.println("> You are registered as a subscriber. You cannot send messages.");
		}
	}

	private void handleRegistration(String inputLine) {
		if (userRole == null && topic == null) {
			// Send registration command to the server
			out.println(inputLine);
			// Update local variables after registration
			String[] tokens = inputLine.trim().split("\\s+");
			if (tokens.length >= 2) {
				userRole = tokens[0].toLowerCase();
				topic = String.join("_", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
			}
		} else {
			System.out.println("> You have already registered as '" + userRole + "' for topic '" + topic + "'.");
		}
	}

	private void showHelp() {
		System.out.println("--- AVAILABLE COMMANDS ---");
		if (userRole == null) {
			System.out.println("- [publish | subscribe] <topic>: Register as a publisher (read & write) or subscriber (read-only) for the specified topic");
		} else if (isPublisher()) {
			System.out.println("- send <message>: Send a message to the server");
		}
		System.out.println("- show: Show available topics");
		System.out.println("- quit: Disconnect from the server");
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
		System.out.println("--- CLIENT SHUTDOWN ---");
		running = false;
		try {
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
		} catch (IOException e) {
			System.out.println("> Error closing resources: " + e.getMessage());
		}
	}

	public boolean isPublisher() {
		return userRole != null && userRole.equalsIgnoreCase("publish");
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