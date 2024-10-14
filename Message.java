import java.text.SimpleDateFormat;
import java.util.Date;

public class Message {
	private final int uuid;
	private final String message;
	private final String topic;
	private final Date timestamp = new Date();
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss");

	public Message(int uuid, String topic, String message) {
		this.uuid = uuid;
		this.topic = topic;
		this.message = message.replaceAll("(.{80})", "$1\n"); // Wrap lines at 80 characters
		timestamp.setTime(timestamp.getTime());
	}

	@Override
	public String toString() {
		return    "--------------------------------------------------------------------------------\n"
				+ dateFormat.format(timestamp) + "\n"
				+ "[ID " + uuid + "] "  + topic + "\n"
				+ "BODY: " + message + "\n"
				+ "--------------------------------------------------------------------------------\n";
	}
}