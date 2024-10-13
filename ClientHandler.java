
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
	private String clientRole;
	private String topic;
	private boolean running = true;
	private static final int SOCKET_TIMEOUT = 500; // 500 ms

	public ClientHandler(Socket socket, Server server) {
		try {
			this.server = server;
			this.socket = socket;
			this.socket.setSoTimeout(SOCKET_TIMEOUT);
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);

			String[] userRoleAndTopic = in.readLine().split(" ");
			this.clientRole = userRoleAndTopic[0];
			this.topic = userRoleAndTopic[1];

			clientHandlers.add(this);
		} catch (IOException e) {
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
//					todo: replace "test" with topic variable
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
		try {
			if (in != null) in.close();
			if (out != null) out.close();
			if (socket != null && !socket.isClosed()) socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}