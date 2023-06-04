import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLOutput;
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


    //private int currentState;
    public ChatServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        activeSessions = new ConcurrentHashMap<>();
        groups = new HashMap<>();
        lastHeartbeat = new ConcurrentHashMap<>();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkHeartbeat, 0, 1, TimeUnit.MINUTES);
        //currentState = Message.STATE_WAITING_FOR_CONNECTION;

    }



    public void start() throws IOException {
        System.out.println("[LOG]:Waiting for clients...");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("[LOG]:Client connected!");

            ChatClientHandler handler = new ChatClientHandler(clientSocket, this);
            new Thread(() -> {
                try {
                    String inputLine;
                    while ((inputLine = handler.receiveMessage().serialize()) != null) {
                        Message msg = Message.deserialize(inputLine);
                        System.out.println("[Received MESSAGE]: " + msg.toString());
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
        if (msg.getVersion() != handler.getClientState() && msg.getType() != Message.HEARTBEAT) {
            System.out.println("[LOG][Version mismatch: Message version is " + msg.getVersion()
                    + ", but current server state is " + handler.getClientState() + "]");

            // Send an error message to the client
            Message errorMsg = new Message(handler.getClientState(), Message.ERROR_VERSION_MISMATCH, 0, "Version mismatch: Message version is " + msg.getVersion()
                    + ", but current server state is " + handler.getClientState());
            handler.sendMessage(errorMsg);

            // Send a close connection message to the client
            Message closeMsg = new Message(handler.getClientState(), Message.CLOSE_CONNECTION, 0, "Closing connection due to version mismatch");
            handler.sendMessage(closeMsg);

            // Close the connection
            handler.closeConnection();

            // Remove this session
            String username = getUsername(handler);
            if (username != null) {
                activeSessions.remove(username);
                lastHeartbeat.remove(username);
                System.out.println("[LOG][Closed connection with user " + username + " due to version mismatch.]");
            }

            return;
        }

        switch (msg.getType()) {
            case Message.CONNECT_REQUEST:
                handleConnectRequest(handler, msg.getPayload());
                break;
            case Message.AUTH_REQUEST:
                handleAuthRequest(handler,msg.getPayload());
                break;
            case Message.AUTH_SUCCESS:
                handleAUTHSUCCESSRequest(handler,msg.getPayload());
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
            case Message.CHANGE_STATE:
                handleChangeStateRequest(handler,msg.getPayload());
                break;

            default:
                throw new IllegalArgumentException("Unknown message type: " + msg.getType());
        }
    }

    private void handleChangeStateRequest(ChatClientHandler handler, String payload){
        handler.setClientState(Integer.valueOf(payload));
        System.out.println(handler.getClientState());
        System.out.println("[LOG][STATE change to" + Integer.valueOf(payload) +"]");

    }
    private void handleAuthRequest(ChatClientHandler handler, String payload){
        String[] credentials = payload.split("\\|", -1);

        if (credentials.length != 2) {
            System.out.println("[LOG]:Invalid payload format.");
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

                handler.setClientState(Message.STATE_AUTHENTICATED);
                System.out.println("[Log][User " + username + " authenticated successfully.STATE change to STATE_AUTHENTICATED]");

                // Create a new Message object to inform the client about successful authentication
                Message authSuccessMsg = new Message(Message.STATE_AUTHENTICATED, Message.AUTH_ACK, 0, "Authentication successful");

                authSuccessMsg.setType(Message.AUTH_ACK);
                authSuccessMsg.setPayload("[Log][Authentication successful for user: " + username + "]");

                // Send the message to the client
                handler.sendMessage(authSuccessMsg);

            } else {
                handler.setClientState(Message.STATE_WAITING_FOR_CONNECTION);
                //currentState = Message.STATE_WAITING_FOR_CONNECTION;
                System.out.println("[Log][Authentication failed for user " + username + "]");

                // Create a new Message object to inform the client about failed authentication
                Message authFailureMsg = new Message(Message.AUTH_FAILURE, Message.AUTH_NACK, 0, "Authentication failure");
                authFailureMsg.setType(Message.AUTH_NACK);
                authFailureMsg.setPayload("[Log][Authentication failed for user: " + username + "]");

                // Send the message to the client
                handler.sendMessage(authFailureMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void handleConnectRequest(ChatClientHandler handler, String payload) throws Exception {
        handler.setClientState(Message.STATE_AUTHENTICATING);

        //currentState = Message.STATE_AUTHENTICATING;
        Message connectAckMsg = new Message(Message.STATE_AUTHENTICATING, Message.CONNECT_ACK,0, "Connection request accepted");
        System.out.println("[LOG][STATE_AUTHENTICATING]");
        handler.sendMessage(connectAckMsg);

    }

    private void handleAUTHSUCCESSRequest(ChatClientHandler handler, String payload) throws Exception {
        handler.setClientState(Message.STATE_IDLE);

        //currentState = Message.STATE_IDLE;
        System.out.println("[LOG][STATE CHANGE TO IDLE]");


    }

    private void handleDisconnect(ChatClientHandler handler) throws Exception {
        System.out.println("[LOG][STATE_WAITING_FOR_CONNECTION]");

        handler.setClientState(Message.STATE_WAITING_FOR_CONNECTION);
        //currentState = Message.STATE_WAITING_FOR_CONNECTION;

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

        handler.setClientState(Message.STATE_IDLE);
        System.out.println("[LOG][STATE TO IDLE]");

        //currentState = Message.STATE_IDLE;

        switch (messageType) {
            case "private":
                ChatClientHandler recipientHandler = activeSessions.get(receiver);
                if (recipientHandler != null) {
                    Message newMsg = new Message(1, Message.RECEIVE_MESSAGE, 0, sender + "|" + message);
                    recipientHandler.sendMessage(newMsg);
                    System.out.println("[LOG]: Message sent to " + receiver + ": " + message);
                }
                break;
            case "group":
                List<ChatClientHandler> groupMembers = groups.get(receiver);
                if (groupMembers != null) {
                    for (ChatClientHandler memberHandler : groupMembers) {
                        Message newMsg = new Message(1, Message.RECEIVE_MESSAGE, 0, sender + "|" + message);
                        memberHandler.sendMessage(newMsg);
                        System.out.println("[LOG]: Message sent to group " + receiver + ": " + message);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("[LOG]: Unknown message type: " + messageType);
        }
    }

    private void handleCreateGroup(ChatClientHandler handler, String payload) throws Exception {
        System.out.println("[LOG][STATE TO IDLE]");
        handler.setClientState(Message.STATE_IDLE);

        //currentState = Message.STATE_IDLE;


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


        //currentState = Message.STATE_IDLE;
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
        System.out.println("[LOG][STATE TO IDLE]");
        handler.setClientState(Message.STATE_IDLE);

        //currentState = Message.STATE_IDLE;
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
        handler.setClientState(Message.STATE_IDLE);

        //currentState = Message.STATE_IDLE;

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
