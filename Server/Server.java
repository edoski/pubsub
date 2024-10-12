package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java Server <port>");
			return;
		}

		int port = Integer.parseInt(args[0]);
		ExecutorService executor = Executors.newCachedThreadPool();

		try (Scanner userInput = new Scanner(System.in)) {
			ServerSocket server = new ServerSocket(port);
			/*
			 * deleghiamo a un altro thread la gestione di tutte le connessioni; nel thread
			 * principale ascoltiamo solo l'input da tastiera dell'utente (in caso voglia
			 * chiudere il programma)
			 */

			Thread serverThread = new Thread(new SocketListener(server, executor));
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
//			Ponendolo nel finally-clause mi assicuro che l'executor termini a prescindere
			executor.shutdown();
		}
	}
}