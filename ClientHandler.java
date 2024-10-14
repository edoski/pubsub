import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
	public static CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<Message>> messages = new ConcurrentHashMap<>();
	private final Server server;
	private final Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private String userRole;
	private String topic;
	private boolean running = true;
	private static final int SOCKET_TIMEOUT = 500; // 500 ms


	public ClientHandler(Socket socket, Server server) {
        this.server = server;
        this.socket = socket;
        clientHandlers.add(this); // Important: Add to the list of handlers immediately so both registered and unregistered clients are handled
    }

	@Override
	public void run() {
		try {
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);

			System.out.println("Waiting for client to send role and topic...");

			// If after 60 seconds the client has not sent role and topic, disconnect
			this.socket.setSoTimeout(60000);

			String line = in.readLine();
			if (line == null) {
				System.out.println("Client disconnected before sending role and topic.");
				interruptThread();
				return;
			}

			String[] userRoleAndTopic = line.split(" ");
			if (userRoleAndTopic.length < 2) {
				System.out.println("Invalid role and topic received from client.");
				interruptThread();
				return;
			}

			this.userRole = userRoleAndTopic[0];
			this.topic = userRoleAndTopic[1];

			System.out.println("Client registered with role '" + userRole + "' on topic '" + topic + "'.");

			// Set reasonable socket timeout for regular operations
			this.socket.setSoTimeout(SOCKET_TIMEOUT);

			// Main loop for handling client messages
			String messageFromClient;
			while (running && !socket.isClosed()) {
				try {
					messageFromClient = in.readLine();  // Blocking call
					if (messageFromClient != null) {
						// Process message
						Message message = new Message(server.getNextMessageId(), topic, messageFromClient);
						broadcastMessage(message.toString());
						messages.putIfAbsent(topic, new CopyOnWriteArrayList<>());
						messages.get(topic).add(message);
					} else {
						break; // Client disconnected
					}
				} catch (SocketTimeoutException e) {
					if (!server.isRunning()) {
						break;
					}
					// Continue loop if still running
				} catch (IOException e) {
					break;
				}
			}
		} catch (IOException e) {
			System.out.println("IOException in ClientHandler: " + e.getMessage());
		}
	}

	public void broadcastMessage(String message) {
		for (ClientHandler clientHandler : clientHandlers) {
			if (clientHandler.topic.equals(this.topic)) {
				clientHandler.out.println(message);
			}
		}
	}

	public void interruptThread() {
		running = false;
		closeEverything(socket, in, out);
	}

	public void closeEverything(Socket socket, BufferedReader in, PrintWriter out) {
		clientHandlers.remove(this);
		System.out.println("Client handler removed. Current handlers: " + clientHandlers.size());
		try {
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}