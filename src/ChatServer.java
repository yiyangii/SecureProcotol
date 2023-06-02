import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ChatServer {
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, ChatClientHandler> activeSessions;
    private Map<String, List<ChatClientHandler>> groups;

    private Map<String, Long> lastHeartbeat;
    private enum State {
        WAITING_FOR_CONNECTION,
        AUTHENTICATING,
        AUTHENTICATED,
        IDLE,
        SENDING_MESSAGE,
        CREATING_GROUP,
        DISCONNECTED
    }

    private enum Event {
        CONNECT_REQUEST,
        CONNECT_ACK,
        AUTH_REQUEST,
        AUTH_ACK,
        AUTH_NACK,
        SEND_MESSAGE,
        RECEIVE_MESSAGE,
        CREATE_GROUP,
        GROUP_ACK,
        GROUP_NACK,
        DISCONNECT,
        DISCONNECT_ACK
    }

    private State currentState;
    public ChatServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        activeSessions = new ConcurrentHashMap<>();
        groups = new HashMap<>();
        lastHeartbeat = new ConcurrentHashMap<>();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkHeartbeat, 0, 1, TimeUnit.MINUTES);
        currentState = State.WAITING_FOR_CONNECTION;

    }



    public void start() throws IOException {
        System.out.println("Waiting for clients...");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected!");

            ChatClientHandler handler = new ChatClientHandler(clientSocket, this);
            new Thread(() -> {
                try {
                    String inputLine;
                    while ((inputLine = handler.receiveMessage().serialize()) != null) {
                        Message msg = Message.deserialize(inputLine);
                        System.out.println("Received: " + msg.toString());
                        handleMessage(handler, msg);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    public synchronized void handleMessage(ChatClientHandler handler, Message msg) throws Exception {
        System.out.println(msg.getPayload());
        switch (msg.getType()) {
            case Message.CONNECT:
                handleConnectRequest(handler, msg.getPayload());
                break;
            case Message.DISCONNECT:
                handleDisconnect(handler);
                break;
            case Message.SEND_MESSAGE:
                handleSendMessage(handler, msg.getPayload());
                break;
            case Message.CREATE_GROUP:
                handleCreateGroup(handler, msg.getPayload());
                break;
            case Message.ADD_MEMBER:
                handleAddMember(handler, msg.getPayload());
                break;
            case Message.REMOVE_MEMBER:
                handleRemoveMember(handler, msg.getPayload());
                break;
            case Message.HEARTBEAT:
                handleHeartbeat(handler, msg.getPayload());
                break;
            default:
                throw new IllegalArgumentException("Unknown message type: " + msg.getType());
        }
    }

    private void handleConnectRequest(ChatClientHandler handler, String payload) throws Exception {
        String[] credentials = payload.split("\\|", -1);

        if (credentials.length != 2) {
            System.out.println("Invalid payload format.");
            return;
        }

        String username = credentials[0];
        String password = credentials[1];

        // Read the username and password from the file
        try (BufferedReader br = new BufferedReader(new FileReader("user.txt"))) {
            String line;
            boolean isAuthenticated = false;

            while ((line = br.readLine()) != null) {
                String[] userCredentials = line.split("\\|", -1);

                if (userCredentials.length != 2) {
                    continue;
                }
                String fileUsername = userCredentials[0];
                String filePassword = userCredentials[1];

                if (username.equals(fileUsername) && password.equals(filePassword)) {
                    isAuthenticated = true;
                    activeSessions.put(username, handler);
                    break;
                }
            }

            if (isAuthenticated) {
                System.out.println("[Log][User " + username + " authenticated successfully.]");

                // Create a new Message object to inform the client about successful authentication
                Message authSuccessMsg = new Message(1, Message.AUTH_ACK, 0, "Authentication successful");

                authSuccessMsg.setType(Message.AUTH_ACK);
                authSuccessMsg.setPayload("[Log][Authentication successful for user: " + username + "]");

                // Send the message to the client
                handler.sendMessage(authSuccessMsg);
            } else {
                System.out.println("[Log][Authentication failed for user " + username + "]");

                // Create a new Message object to inform the client about failed authentication
                Message authFailureMsg = new Message(1, Message.AUTH_NACK, 0, "Authentication failure");
                authFailureMsg.setType(Message.AUTH_NACK);
                authFailureMsg.setPayload("[Log][Authentication failed for user: " + username + "]");

                // Send the message to the client
                handler.sendMessage(authFailureMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDisconnect(ChatClientHandler handler) throws Exception {
        String username = getUsername(handler);
        if (username != null) {
            activeSessions.remove(username);
            lastHeartbeat.remove(username);
            System.out.println("User " + username + " has disconnected.");

            // Send a disconnect acknowledgement to the client
            Message disconnectAckMsg = new Message(1, Message.DISCONNECT, 0, "Disconnect successful");
            handler.sendMessage(disconnectAckMsg);
        } else {
            System.out.println("No active session found for the client.");
        }
    }

    private void handleSendMessage(ChatClientHandler handler, String payload) throws Exception {
        String[] parts = payload.split("\\|", -1);
        String sender = parts[0];
        String messageType = parts[1];
        String receiver = parts[2];
        String message = parts[3];

        switch (messageType) {
            case "private":
                ChatClientHandler recipientHandler = activeSessions.get(receiver);
                if (recipientHandler != null) {
                    Message newMsg = new Message(1, Message.RECEIVE_MESSAGE, 0, sender + "|" + message);
                    recipientHandler.sendMessage(newMsg);
                    System.out.println("Message sent to " + receiver + ": " + message);
                }
                break;
            case "group":
                List<ChatClientHandler> groupMembers = groups.get(receiver);
                if (groupMembers != null) {
                    for (ChatClientHandler memberHandler : groupMembers) {
                        Message newMsg = new Message(1, Message.RECEIVE_MESSAGE, 0, sender + "|" + message);
                        memberHandler.sendMessage(newMsg);
                        System.out.println("Message sent to group " + receiver + ": " + message);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown message type: " + messageType);
        }
    }

    private void handleCreateGroup(ChatClientHandler handler, String payload) throws Exception {
        String[] createParts = payload.split("\\|", -1);
        String creator = createParts[0];
        String groupName = createParts[1];

        if (!groups.containsKey(groupName)) {
            List<ChatClientHandler> groupMembers = new ArrayList<>();
            groupMembers.add(handler);
            groups.put(groupName, groupMembers);

            System.out.println("Group " + groupName + " created by " + creator);
            // Send a group acknowledgement to the client
            Message groupAckMsg = new Message(1, Message.GROUP_ACK, 0, "Group created");
            handler.sendMessage(groupAckMsg);
        } else {
            System.out.println("Group " + groupName + " already exists");
            // Send a group rejection to the client
            Message groupNackMsg = new Message(1, Message.GROUP_NACK, 0, "Group already exists");
            handler.sendMessage(groupNackMsg);
        }
    }

    private void handleHeartbeat(ChatClientHandler handler, String payload) {
        String username = getUsername(handler);
        if (username != null) {
            System.out.println("Received heartbeat from: " + username);
            // Update the last received heartbeat time for the user.
            lastHeartbeat.put(username, System.currentTimeMillis());
        } else {
            System.out.println("No active session found for the client.");
        }
    }

    private void checkHeartbeat() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastHeartbeat.entrySet()) {
            // If the user hasn't sent a heartbeat in the last 2 minutes, disconnect them.
            if (currentTime - entry.getValue() > 2 * 60 * 1000) {
                String username = entry.getKey();
                ChatClientHandler handler = activeSessions.get(username);
                if (handler != null) {
                    System.out.println("Closing connection with user " + username);
                    handler.closeConnection();
                    activeSessions.remove(username);
                    lastHeartbeat.remove(username);
                }
            }
        }
    }

    private void handleAddMember(ChatClientHandler handler, String payload) throws Exception {
        String[] addParts = payload.split("\\|", -1);
        String groupName = addParts[1];
        String member = addParts[2];

        List<ChatClientHandler> groupMembers = groups.get(groupName);
        if (groupMembers != null && activeSessions.containsKey(member)) {
            groupMembers.add(activeSessions.get(member));
            System.out.println("[Add " + member + " to " + groupName + "]");
        }
    }

    private void handleRemoveMember(ChatClientHandler handler, String payload) throws Exception {
        String[] removeParts = payload.split("\\|", -1);
        String groupName = removeParts[1];
        String member = removeParts[2];

        List<ChatClientHandler> groupMembers = groups.get(groupName);
        if (groupMembers != null) {
            groupMembers.remove(activeSessions.get(member));
            System.out.println("[Remove " + member + " from " + groupName + "]");
        }
    }

    private String getUsername(ChatClientHandler handler) {
        for (Map.Entry<String, ChatClientHandler> entry : activeSessions.entrySet()) {
            if (entry.getValue().equals(handler)) {
                return entry.getKey();
            }
        }
        return null;
    }



    public void stop() throws IOException {
        serverSocket.close();
    }
}
