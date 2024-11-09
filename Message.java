import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Message class represents a message sent in a topic.
 * It contains a unique ID, the topic, the message content, and a timestamp.
 */
public class Message {
	private static final AtomicInteger messageCounter = new AtomicInteger(0); // Unique ID for each message
	private final int messageID;
	private final int userID;
	private final String message;
	private final String topic;
	private final Date timestamp = new Date();
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss");

	/**
	 * Constructs a Message with the specified topic and content.
	 *
	 * @param topic   the topic of the message
	 * @param message the content of the message
	 */
	public Message(int userID, String topic, String message) {
		this.messageID = messageCounter.getAndIncrement();
		this.userID = userID;
		this.topic = topic;
		this.message = message.replaceAll("(.{80})", "$1\n"); // Wrap lines at 80 characters
	}

	/**
	 * Returns a string representation of the message, including the timestamp, ID, topic, and content.
	 *
	 * @return a formatted string representing the message
	 */
	@Override
	public String toString() {
		return "--------------------------------------------------------------------------------\n"
		    + "USER-" + userID + " @ " + dateFormat.format(timestamp) + "\n"
		    + "[ID " + messageID + " | TOPIC '" + topic + "']\n"
		    + "BODY: " + message + "\n"
		    + "--------------------------------------------------------------------------------\n";
	}

	public int getId() { return messageID; }

	public String getTopic() { return topic; }

	public int getUserID() { return userID; }
}
