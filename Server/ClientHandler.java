package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable {
	public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();



	private Socket socket;
	private BufferedReader in;
	private BufferedWriter out;
	private String clientUsername;

	public ClientHandler(Socket socket) {
		try {
			this.socket = socket;
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			this.clientUsername = in.readLine();
			clientHandlers.add(this);
			broadcastMessage("SERVER: " + clientUsername + " HAS ENTERED THE CHAT!");
		} catch (IOException e) {
			closeEverything(socket, in, out);
		}
	}

	@Override
	public void run() {
		String clientMessage;

		while (socket.isConnected()) {
			try {
				clientMessage = in.readLine();
				broadcastMessage(clientMessage);
			} catch (IOException e) {
				closeEverything(socket, in, out);
				break;
			}
		}
	}

	public void broadcastMessage(String message) {
		for (ClientHandler clientHandler : clientHandlers) {
			try {
				if (!clientHandler.clientUsername.equals(clientUsername)) {
					clientHandler.out.write(message);
					clientHandler.out.newLine();
					clientHandler.out.flush();
				}
			} catch (IOException e) {
				closeEverything(socket, in, out);
			}
		}
	}

	public void removeClientHandler() {
		clientHandlers.remove(this);
		broadcastMessage("SERVER: " + clientUsername + " HAS LEFT THE CHAT.");
	}

	public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
		removeClientHandler();
		try {
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
			if (socket != null) {
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}