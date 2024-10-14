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
	private static String userRole;
	private static String topic;
	private volatile boolean running = true;

	public Client(Socket socket, String[] userRoleAndTopic) {
		try {
			this.socket = socket;
//			userRole = userRoleAndTopic[0];
//			topic = userRoleAndTopic[1];
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			closeEverything(socket, in, out);
		}
	}

	public void sendMessage() {
		out.println(userRole + " " + topic);

		new Thread(() -> {
			Scanner scanner = new Scanner(System.in);
			while (isPublisher() && running && socket.isConnected()) {
				try {
					String body = scanner.nextLine();
					out.println(body);
				} catch (Exception e) {
					break;
				}
			}
		}).start();
	}

	public void receiveMessage() {
		new Thread(() -> {
			String messageFromServer;
			while (running && socket.isConnected()) {
				try {
					messageFromServer = in.readLine();
					if (messageFromServer != null) {
						System.out.println(messageFromServer);
					} else {
						System.out.println("Server has closed the connection.");
						running = false;
						closeEverything(socket, in, out);
					}
				} catch (IOException e) {
					running = false;
					closeEverything(socket, in, out);
					break;
				}
			}
		}).start();
	}

	private void closeEverything(Socket socket, BufferedReader in, PrintWriter out) {
		running = false;
		try {
			System.out.println("Connection closed. Exiting...");
			if (socket != null && !socket.isClosed()) socket.close();
			if (in != null) in.close();
			if (out != null) out.close();
			System.in.close(); // Close System.in to interrupt scanner.nextLine()
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isPublisher() {
		return userRole.equalsIgnoreCase("publish");
	}
	public static boolean isSubscriber() {
		return userRole.equalsIgnoreCase("subscribe");
	}

	public static void main(String[] args) {
		try {
			Scanner scanner = new Scanner(System.in);
//			publish or subscribe, topic: e.g. publish test
			String[] userRoleAndTopic;
			do {
				System.out.print("Enter 'publish' or 'subscribe' followed by a topic: ");
				userRoleAndTopic = scanner.nextLine().split(" ");
				userRole = userRoleAndTopic[0];
				StringBuilder topic = new StringBuilder();
				for (int i = 1; i < userRoleAndTopic.length; i++) {
					topic.append(userRoleAndTopic[i]).append(" ");
				}
				Client.topic = topic
						.toString()
						.replace(" ", "_")
						.substring(0, topic.length() - 1);
			} while (!isPublisher() && !isSubscriber());

			Socket socket = new Socket(args[0], Integer.parseInt(args[1]));
			Client client = new Client(socket, userRoleAndTopic);

//            Receive messages from separate thread, so that it doesn't block the main thread (to send messages)
			client.receiveMessage();
			client.sendMessage();
		} catch (IOException e) {
			System.out.println("Unable to connect to the server.");
		}
	}
}