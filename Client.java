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
						System.out.println("- [publish | subscribe] <topic>: Register as a publisher (read & write) / subscriber (read-only) for the specified topic");
						System.out.println("- show: Show available topics");
						System.out.println("- quit: Disconnect from the server");
						break;
					case "quit":
						System.out.println("DISCONNECTING...");
						running = false;
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

								System.out.println("Successfully registered with role '" + userRole + "' on topic '" + topic + "'.");

								// For publishers, prompt that they can start sending messages
								if (isPublisher()) {
									System.out.println("You can start sending messages. Type 'help' for a list of available commands or 'quit' to exit.");
								}
							} else {
								System.out.println("Usage: " + command + " <topic_name>");
							}
						} else {
							System.out.println("You have already registered as '" + userRole + "' for topic '" + topic + "'.");
						}
						break;
					case "show":
						if (ClientHandler.messages.isEmpty()) {
							System.out.println("No topics available.");
						} else {
							System.out.println("--- TOPICS ---");
							for (String topic : ClientHandler.messages.keySet()) {
								System.out.println(topic);
							}
						}
						break;
					default:
						if (userRole == null) {
							System.out.println("Unknown command. Please register first using 'publish <topic>' or 'subscribe <topic>'.");
						} else if (isPublisher()) {
//							TODO: turn this into a case "send" above
							// Publishers can send messages
							out.println(inputLine);
							out.flush();
						} else {
							// Subscribers can only send commands
							System.out.println("Unknown command.");
						}
						break;
				}
			}
		} catch (Exception e) {
			if (running) {
				System.out.println("Error reading from console: " + e.getMessage());
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
						System.out.println("Server has closed the connection.");
						running = false;
						closeEverything();
						break; // Exit the loop
					}
				} catch (IOException e) {
					if (running) {
						System.out.println("Connection lost: " + e.getMessage());
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
			System.out.println("Connection closed. Exiting...");
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
//			Necessary, do not remove
			System.exit(0);
		} catch (IOException e) {
			System.out.println("Error closing resources: " + e.getMessage());
		}
	}

	public boolean isPublisher() {
		return userRole != null && userRole.equalsIgnoreCase("publish");
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: java Client <hostname> <port>");
			return;
		}

		try {
			Socket socket = new Socket(args[0], Integer.parseInt(args[1]));
			Client client = new Client(socket);

			client.start();
		} catch (IOException e) {
			System.out.println("Unable to connect to the server.");
		}
	}
}