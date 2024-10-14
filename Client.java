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
				String inputLine = scanner.nextLine();
				if (!running) {
					break;
				}

				String[] tokens = inputLine.trim().split("\\s+");
				String command = tokens[0].toLowerCase();

				if (userRole == null) {
					// Handle commands before registration
					switch (command) {
						case "quit":
							System.out.println("DISCONNECTING...");
							running = false;
							closeEverything();
							break;
						case "publish":
						case "subscribe":
							if (tokens.length >= 2) {
								userRole = command;
								topic = tokens[1];

								// Send userRole and topic to the server
								out.println(userRole + " " + topic);
								out.flush();

								System.out.println("Registered with role '" + userRole + "' on topic '" + topic + "'.");

								if (isPublisher()) {
									System.out.println("You can start sending messages. Type 'quit' to exit.");
								}
							} else {
								System.out.println("Usage: " + command + " <topic_name>");
							}
							break;
						default:
							System.out.println("Unknown command. Register using '[publish | topic] <topic>' or 'quit' to exit.");
					}
				} else {
					// After registration
					if (command.equals("quit")) {
						System.out.println("DISCONNECTING...");
						running = false;
						closeEverything();
						break;
					} else if (isPublisher()) {
						// Publishers can send messages
						out.println(inputLine);
						out.flush();
					} else {
						// Subscribers can only send commands
						System.out.println("Unknown command.");
					}
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
			while (running && socket.isConnected()) {
				try {
					String messageFromServer = in.readLine();
					if (messageFromServer != null) {
						System.out.println(messageFromServer);
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