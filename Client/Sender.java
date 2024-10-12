package Client;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Sender implements Runnable {

	Socket socket;

	public Sender(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
		     Scanner userInput = new Scanner(System.in)) {
			while (true) {
				String request = userInput.nextLine();

				if (Thread.interrupted()) {
					out.println("quit");
					break;
				}
				out.println(request);
				if (request.equals("quit")) {
					break;
				}
			}
			System.out.println("Sender closed.");
		} catch (IOException e) {
			System.err.println("IOException caught: " + e);
			e.printStackTrace();
		}
	}


//	@Override
//	public void run() {
//		try (Scanner userInput = new Scanner(System.in)) {
//			PrintWriter to = new PrintWriter(this.s.getOutputStream(), true);
//			while (true) {
//				String request = userInput.nextLine();
//
//				/*
//				 * se il thread è stato interrotto mentre leggevamo l'input da tastiera, inviamo
//				 * "quit" al server e usciamo
//				 */
//				if (Thread.interrupted()) {
//					to.println("quit");
//					break;
//				}
//				/* in caso contrario proseguiamo e analizziamo l'input inserito */
//				to.println(request);
//				if (request.equals("quit")) {
//					break;
//				}
//			}
//			System.out.println("Sender closed.");
//		} catch (IOException e) {
//			System.err.println("IOException caught: " + e);
//			e.printStackTrace();
//		}
//	}

}