package Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Receiver implements Runnable {

	Socket socket;
//	Thread sender;

	public Receiver(Socket socket) {
		this.socket = socket;
//		this.sender = sender;
	}

	@Override
	public void run() {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			String message;
			while ((message = in.readLine()) != null) {
				System.out.println("Received: " + message);
			}
		} catch (IOException e) {
			System.err.println("Receiver: IOException caught: " + e);
			e.printStackTrace();
		}
	}





//	@Override
//	public void run() {
//		try {
//			Scanner from = new Scanner(this.s.getInputStream());
//			while (true) {
//				String response = from.nextLine();
//				System.out.println("Received: " + response);
//				if (response.equals("quit")) {
//					break;
//				}
//
//			}
//		} catch (IOException e) {
//			System.err.println("IOException caught: " + e);
//			e.printStackTrace();
//		} finally {
////			this.sender.interrupt();
//			System.out.println("Receiver closed.");
//		}
//	}
}