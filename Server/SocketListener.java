package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;

public class SocketListener implements Runnable {
	ServerSocket server;
	ExecutorService executor;
//	USANDO EXECUTOR NON SERVE PIU' QUESTO ARRAYLIST
//  ArrayList<Thread> children = new ArrayList<>();


	public SocketListener(ServerSocket server, ExecutorService executor) {
		this.server = server;
		this.executor = executor;
	}

	@Override
	public void run() {
		try {
			//without this line, the server will wait indefinitely for a client to connect,
			//and even if the main thread is interrupted, the server will not stop
			//until a client connects

			this.server.setSoTimeout(8000);
			while (!Thread.interrupted()) {
				try {
					System.out.println("Waiting for a new client...");
					/*
					 * Questa istruzione è bloccante, a prescindere da Thread.interrupt(). Occorre
					 * quindi controllare, una volta accettata la connessione, che il server non sia
					 * stato interrotto.
					 *
					 * In caso venga raggiunto il timeout, viene sollevata una
					 * SocketTimeoutException, dopo la quale potremo ricontrollare lo stato del
					 * Thread nella condizione del while().
					 */
					Socket s = server.accept();
					if (!Thread.interrupted()) {
						System.out.println("Client connected");

						/* crea un nuovo thread per lo specifico socket */
//                        Thread handlerThread = new Thread(new ClientHandler(s));
//                        handlerThread.start();
//                        this.children.add(handlerThread);

//                      APPROCCIO EXECUTOR
						executor.submit(new ClientHandler(s));

						/*
						 * una volta creato e avviato il thread, torna in ascolto per il prossimo client
						 */
					} else {
						s.close();
						break;
					}
				} catch (SocketTimeoutException e) {
					/* in caso di timeout procediamo semplicemente con l'esecuzione */
					System.out.println("Timeout, continuing...");
				} catch (IOException e) {
					/*
					 * s.close() potrebbe sollevare un'eccezione; in questo caso non vogliamo finire
					 * nel "catch" esterno, perché non abbiamo ancora chiamato this.server.close()
					 */
					break;
				}
			}
			this.server.close();
		} catch (IOException e) {
			System.err.println("SocketListener: IOException caught: " + e);
			e.printStackTrace();
		}

//		USANDO EXECUTOR TERMINIAMO I THREAD DIRETTAMENTE CON SHUTDOWNNOW
		System.out.println("Interrupting children...");
		executor.shutdownNow();

//		for (Thread child : this.children) {
//			System.out.println("Interrupting " + child + "...");
//			/*
//			 * child.interrupt() non è bloccante; una volta inviato il segnale
//			 * di interruzione proseguiamo con l'esecuzione, senza aspettare che "child"
//			 * termini
//			 */
//			child.interrupt();
//		}

	}

}