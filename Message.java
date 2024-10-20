import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class Message {
	private static final AtomicInteger messageCounter = new AtomicInteger(0); // Unique ID for each message
	private final int uuid;
	private final String message;
	private final String topic;
	private final Date timestamp = new Date();
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss");

	public Message(String topic, String message) {
		this.uuid = messageCounter.getAndIncrement();
		this.topic = topic;
		this.message = message.replaceAll("(.{80})", "$1\n"); // Wrap lines at 80 characters
	}

	@Override
	public String toString() {
		return    "--------------------------------------------------------------------------------\n"
				+ dateFormat.format(timestamp) + "\n"
				+ "[ID " + uuid + " | TOPIC '"  + topic + "']\n"
				+ "BODY: " + message + "\n"
				+ "--------------------------------------------------------------------------------\n";
	}

	public int getId() {
		return uuid;
	}
}