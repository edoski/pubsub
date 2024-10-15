import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
	private final ServerSocket serverSocket;
	private final ExecutorService pool = Executors.newCachedThreadPool();
	private boolean running = true;
	public static int counter = 0; // Used to generate unique message IDs

	public Server(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	public void startServer() {
		try {
			System.out.println("--- SERVER STARTED ---");
			while (running && !serverSocket.isClosed()) {
				try {
					serverSocket.setSoTimeout(1000); // Set a timeout for accept() to periodically check if the server is still running
					Socket socket = serverSocket.accept();
					System.out.println("--- CLIENT CONNECTED ---");
					ClientHandler clientHandler = new ClientHandler(socket, this);
					pool.execute(clientHandler);
				} catch (SocketTimeoutException e) {
					// Continue to check if the server is still running
					if (!running) {
						break;
					}
				} catch (SocketException e) {
					if (running) {
						throw new RuntimeException(e);
					} else {
						// Server socket has been closed, exit the loop
						break;
					}
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
			String command = scanner.nextLine();
			switch (command) {
				case "quit":
					System.out.println("> (PRE-QUIT) Connected clients: " + ClientHandler.clientHandlers.size());
					shutdownServer();
					break;
				case "show":
					if (ClientHandler.topics.isEmpty()) {
						System.out.println("> No topics available.");
					} else {
						System.out.println("--- TOPICS ---");
						for (String topic : ClientHandler.topics.keySet()) {
							System.out.println(topic);
						}
					}
					break;
				case "inspect":
//					todo
					System.out.println("Inspecting...");
					break;
				default:
					System.out.println("> Unknown command.");
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

	private void shutdownServer() {
		running = false;
		try {
			for (ClientHandler clientHandler : ClientHandler.clientHandlers) {
				clientHandler.interruptThread();
			}

			if (!serverSocket.isClosed()) {
				serverSocket.close();
			}

			pool.shutdownNow();

			System.out.println("> (POST-QUIT) Connected clients: " + ClientHandler.clientHandlers.size());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		System.out.println("--- SERVER SHUTDOWN ---");
		System.exit(0);
	}

	//  Synchronized so that only one thread can access the counter at a time
	public static synchronized int getNextMessageId() {
		return counter++;
	}

	public static void main(String[] args) {
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