package Server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;


public class Server {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java publishSubscribe.Server <port>");
			return;
		}

		int port = Integer.parseInt(args[0]);
		Scanner userInput = new Scanner(System.in);

		try {
			ServerSocket server = new ServerSocket(port);
			/*
			 * deleghiamo a un altro thread la gestione di tutte le connessioni; nel thread
			 * principale ascoltiamo solo l'input da tastiera dell'utente (in caso voglia
			 * chiudere il programma)
			 */

			Thread serverThread = new Thread(new SocketListener(server));
			serverThread.start();


			boolean closed = true;
			while (closed) {
				String command = userInput.nextLine();
				String[] parts = command.split(" ");

				switch (parts[0]) {
					case "show":
						System.out.println("show");

						break;
					case "inspect":
						System.out.println("inspect");
						if (parts.length > 1) {
							String key = parts[1];
							System.out.println("key: " + key);

							//Proverò a usare serverThread.wait() per bloccare il thread principale
							//serverThread.wait();
						}

						break;
					case "quit":
						System.out.println("quit");
						closed = false;
						break;

					default:
						System.out.println("unknown command");
				}
			}

			try {
				serverThread.interrupt();
				/* attendi la terminazione del thread */
				serverThread.join();
			} catch (InterruptedException e) {
				/*
				 * se qualcuno interrompe questo thread nel frattempo, terminiamo
				 */
				return;
			}
			System.out.println("Main thread terminated.");
		} catch (IOException e) {
			System.err.println("IOException caught: " + e);
			e.printStackTrace();
		} finally {
			userInput.close();
		}
	}
}








/*
public class Server {
	private final ServerSocket serverSocket;

	public Server(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	public void startServer() {
		try {
			while (!serverSocket.isClosed()) {
				Socket socket = serverSocket.accept();
				System.out.println("NEW CLIENT CONNECTED.");
				ClientHandler clientHandler = new ClientHandler(socket);

				Thread thread = new Thread(clientHandler);
				thread.start();
			}
		} catch (IOException e) {
			closeServerSocket();
		}
	}

	public void closeServerSocket() {
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		ServerSocket serverSocket = new ServerSocket(1234);
		Server server = new Server(serverSocket);
		server.startServer();
	}
}*/