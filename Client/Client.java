package Client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;


public class Client{
	public static void main(String args []){

		if (args.length < 2) {
			System.err.println("Usage: java publishSubscribe.Server <port>");
			return;
		}

		String host = args[0];
		int port = Integer.parseInt(args[1]);

		try {
			Socket s = new Socket(host, port);
			System.out.println("Connected to server");

			System.out.println("Usage: extract <key> / add <key> <value>");

			/*
			 * Delega la gestione di input/output a due thread separati, uno per inviare
			 * messaggi e uno per leggerli
			 *
			Thread sender = new Thread(new Sender(s));
			Thread receiver = new Thread(new Receiver(s, sender));

			sender.start();
			receiver.start();

			*/





		} catch (IOException e) {
			System.err.println("IOException caught: " + e);
			e.printStackTrace();
		}


	}//main


}//end class Client