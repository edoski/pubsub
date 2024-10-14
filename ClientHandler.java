import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
	public static CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<Message>> messages = new ConcurrentHashMap<>();
	private Server server;
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private String userRole;
	private String topic;
	private boolean running = true;
	private static final int SOCKET_TIMEOUT = 500; // 500 ms

	public ClientHandler(Socket socket, Server server) {
		try {
			this.server = server;
			this.socket = socket;
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);

			System.out.println("Waiting for client to send role and topic...");

			String line = in.readLine();
			if (line == null) {
				System.out.println("Client disconnected before sending role and topic.");
				closeEverything(socket, in, out);
				return;
			}

			String[] userRoleAndTopic = line.split(" ");
			if (userRoleAndTopic.length < 2) {
				System.out.println("Invalid role and topic received from client.");
				closeEverything(socket, in, out);
				return;
			}

			this.userRole = userRoleAndTopic[0];
			this.topic = userRoleAndTopic[1];

			clientHandlers.add(this);
			System.out.println("Client registered with role: " + userRole + ", topic: " + topic);

			// Set the socket timeout after receiving the initial data
			this.socket.setSoTimeout(SOCKET_TIMEOUT);
		} catch (IOException e) {
			System.out.println("IOException in ClientHandler constructor: " + e.getMessage());
			closeEverything(socket, in, out);
		} catch (Exception e) {
			System.out.println("Exception in ClientHandler constructor: " + e.getMessage());
			closeEverything(socket, in, out);
		}
	}

	@Override
	public void run() {
		String messageFromClient;

		while (running && !socket.isClosed()) {
			try {
				messageFromClient = in.readLine();  // Blocking call
				if (messageFromClient != null) {
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
		closeEverything(socket, in, out);
	}

	public void interruptThread() {
		running = false;
		closeEverything(socket, in, out);
	}

	public void broadcastMessage(String message) {
		for (ClientHandler clientHandler : clientHandlers) {
			if (clientHandler.topic.equals(this.topic)) {
				clientHandler.out.println(message);
			}
		}
	}

	public void closeEverything(Socket socket, BufferedReader in, PrintWriter out) {
		running = false;
		clientHandlers.remove(this);
		System.out.println("Client handler removed. Current handlers: " + clientHandlers.size());
		try {
			if (in != null) in.close();
			if (out != null) out.close();
			if (socket != null && !socket.isClosed()) socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}