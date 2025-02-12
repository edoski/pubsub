# pubsub: OS Laboratory Project

## Project Overview

pubsub is a client-server application developed in Java that enables multiple clients to connect to a server and communicate through topic-based messaging. The project leverages **socket programming** and **multithreading** to ensure concurrent message handling.

## Features

- **Topic-based messaging**: Clients can register to topics as either publishers or subscribers.
- **Multithreaded architecture**: The server can handle multiple clients concurrently.
- **Thread synchronization**: Utilizes synchronized blocks and concurrent collections to prevent race conditions.
- **Backlog System**: Stores client commands during server inspection mode and executes them once the mode ends.
- **Extended Server Commands**:
  - `kick <clientID>`: Removes a client from the server.
  - `export <clientID|topicID>`: Saves messages to a file.
  - `users`: Lists all connected users.
  - `clear`: Deletes all messages in an inspected topic.

## Technologies Used

- **Java (Sockets, Threads, Concurrency API)**
- **ConcurrentHashMap, ConcurrentLinkedQueue**
- **ExecutorService for thread management**

## Usage

### Prerequisites

- Java JDK 8+

### Compilation

```sh
javac Server.java Client.java ClientHandler.java Message.java
```

### Running the Server

```sh
java Server <portNumber>
```

- `<portNumber>` should be in the range **1024-65535**.

### Running a Client

```sh
java Client <serverIP> <portNumber>
```

- Replace `<serverIP>` with `localhost` for local testing or the actual server IP.

## Client Commands

- `help`: Displays available commands
- `publish <topic>`: Registers as a publisher for a topic
- `subscribe <topic>`: Registers as a subscriber
- `send <message>`: Sends a message to the topic
- `list`: Displays messages sent by the publisher
- `listall`: Displays all messages in the topic
- `quit`: Disconnects from the server

## Server Commands

- `show`: Lists all topics
- `inspect <topic>`: Starts inspecting a topic
- `end`: Ends topic inspection
- `listall`: Displays all messages in a topic (during inspect mode)
- `delete <messageID>`: Removes a specific message (during inspect mode)
- `kick <clientID>`: Disconnects a client
- `clear`: Clears all messages from an inspected topic
- `export <clientID|topicID>`: Saves messages to a log file
- `users`: Displays all connected clients

## Contributors

- **Edoardo Galli** (0001049383) - [edoardo.galli3@studio.unibo.it](mailto\:edoardo.galli3@studio.unibo.it)
- **Jean Baptiste Dindane** (0001100694)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

For more details on the architecture and implementation, refer to `LABSO_DOCUMENTAZIONE.pdf` in the `docs/` directory.
