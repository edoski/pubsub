import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

// todo: add documentation above each method (same style as algo project)

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
						System.out.println("--- AVAILABLE COMMANDS ---");
						if (userRole == null) {
							System.out.println("- [publish | subscribe] <topic>: Register as a publisher (read & write) / subscriber (read-only) for the specified topic");
						} else if (isPublisher()) {
							System.out.println("- send <message>: Send a message to the server");
						}
						System.out.println("- show: Show available topics");
						System.out.println("- quit: Disconnect from the server");
						break;
					case "send":
						if (userRole == null) {
							System.out.println("> You need to register as a publisher first.");
						} else if (isPublisher()) {
							if (tokens.length < 2) {
								System.out.println("> Usage: send <message>");
							} else {
								StringBuilder message = new StringBuilder();
								for (int i = 1; i < tokens.length; i++) {
									message.append(tokens[i]).append(" ");
								}
								out.println(message);
							}
						} else {
							System.out.println("> You are registered as a subscriber. You cannot send messages.");
						}
						break;
					case "show":
						if (ClientHandler.messages.isEmpty()) {
							System.out.println("> No topics available.");
						} else {
							System.out.println("--- TOPICS ---");
							for (String topic : ClientHandler.messages.keySet()) {
								System.out.println(topic);
							}
						}
						break;
					case "quit":
						System.out.println("> DISCONNECTING...");
						// Send a special "QUIT" message to the server, signaling the client's intention to disconnect
						out.println("QUIT");
						closeEverything();
						break;
					case "publish":
					case "subscribe":
						if (userRole == null && topic == null) {
							if (tokens.length >= 2) {
								userRole = command;

//								TODO: handle case where topic is not a single word
								topic = tokens[1];

								// Send userRole and topic to the server
								out.println(userRole + " " + topic);

								System.out.println("> Successfully registered with role '" + userRole + "' on topic '" + topic + "'.");

								// For publishers, prompt that they can start sending messages
								if (isPublisher()) {
									System.out.println("> You can start sending messages. Type 'help' for a list of available commands or 'quit' to exit.\n");
								}
							} else {
								System.out.println("> Usage: " + command + " <topic_name>");
							}
						} else {
							System.out.println("> You have already registered as '" + userRole + "' for topic '" + topic + "'.");
						}
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
			// Important: Close the socket first to prevent the client from sending more messages
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