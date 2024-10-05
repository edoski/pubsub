import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
	private Socket socket;
	private BufferedReader in;
	private BufferedWriter out;
	private String username;

	public Client(Socket socket, String username) {
		try {
			this.username = username;
			this.socket = socket;
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			closeEverything(socket, in, out);
		}
	}

	public void sendMessage() {
		try {
			out.write(username);
			out.newLine();
			out.flush();

			Scanner scanner = new Scanner(System.in);
			while (socket.isConnected()) {
				String message = scanner.nextLine();
				out.write(username + ": " + message);
				out.newLine();
				out.flush();
			}
		} catch (IOException e) {
			closeEverything(socket, in, out);
		}
	}

	public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
		try {
			if (socket != null) {
				socket.close();
			}
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}