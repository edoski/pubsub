import java.text.SimpleDateFormat;
import java.util.Date;

public class Message {
	private final int uuid;
	private final String message;
	private final String topic;
	private final Date timestamp = new Date();
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss");

	public Message(String topic, String message) {
		this.uuid = Server.getNextMessageId(); // Message class in charge of fetching the next ID
		this.topic = topic;
		this.message = message.replaceAll("(.{80})", "$1\n"); // Wrap lines at 80 characters
		timestamp.setTime(timestamp.getTime());
	}

	public int getId() {
		return uuid;
	}

	@Override
	public String toString() {
		return    "--------------------------------------------------------------------------------\n"
				+ dateFormat.format(timestamp) + "\n"
				+ "[ID " + uuid + " | TOPIC '"  + topic + "']\n"
				+ "BODY: " + message + "\n"
				+ "--------------------------------------------------------------------------------\n";
	}
}