import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


// ChatClient class for client-side operations

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String serverAddress;
    private int serverPort;
    private String username;
    private String password;
    // Executor service for scheduled tasks like heartbeat
    private ScheduledExecutorService scheduler;
    // The timestamp of the last received input
    private long lastInputTime;
    // Flag for checking if the client is authenticated
    private volatile boolean isAuthenticated = false;

    public ChatClient(String serverAddress, int serverPort, String username, String password) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;
    }
    // Method to start the connection to the server
    public void startConnection() throws Exception {
        socket = new Socket(serverAddress, serverPort);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        // Connecting with user credentials

        connect(username, password);

        // Start a new thread for receiving messages
        new Thread(() -> {
            while (true) {
                try {
                    // Receive message and handle it

                    Message msg = receiveMessage();
                    handleMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();




        long lastInputTime = System.currentTimeMillis();  // Timestamp of the last input
        boolean isHeartbeatOn = false;  // Flag indicating if heartbeat is currently on
        Scanner scanner = new Scanner(System.in);


        while (true) {
            // Print instructions to the user on how to use the client
            System.out.println(
                    "Welcome to the Chat Client! Here are the available commands:\n" +
                    " 'send': To send a message. You will be asked to enter the type of the message (private/group), the receiver of the message, and your message.\n" +
                    " 'disconnect': To disconnect from the server.\n" +
                    " 'createGroup': To create a new group. You will be asked to enter the name of the group.\n" +
                    " 'addMember': To add a member to a group. You will be asked to enter the group name and the username of the member to add.\n" +
                    " 'removeMember': To remove a member from a group. You will be asked to enter the group name and the username of the member to remove.\n" +
                    " 'exit': To quit the program.\n" +
                    " Please note: If you're inactive for more than 10 seconds, the heartbeat will stop and you'll have to enter a new command to restart it.");

            // If more than 10 seconds have passed since the last input, stop the heartbeat
            if (isHeartbeatOn && System.currentTimeMillis() - lastInputTime > 10_000) {
                stopHeartbeat();
                isHeartbeatOn = false;
            }


            System.out.println("[Enter your command: ]");
            String command = scanner.nextLine();
            userInputReceived();

            // Update the last input time
            lastInputTime = System.currentTimeMillis();

            // Start the heartbeat if it's currently off
            if (!isHeartbeatOn) {
                new Thread(this::startHeartbeat).start();
                isHeartbeatOn = true;
            }
            /* Code related to command processing, heartbeat and message handling */

            // If the user wants to disconnect
            if (command.equalsIgnoreCase("disconnect")) {
                disconnect();
                break;
            }
            // If the user wants to send a message
            else if (command.startsWith("send")) {
                Message changeStateMessage = new Message(Message.STATE_IDLE, Message.CHANGE_STATE, 0, "22");
                sendMessage(changeStateMessage);
                System.out.println("[Enter the type of the message (private/group): ]");
                String messageType = scanner.nextLine();

                System.out.println("[Enter the receiver of the message: ]");
                String receiver = scanner.nextLine();

                System.out.println("[Enter your message: ]");
                String message = scanner.nextLine();


                if (!messageType.equals("private") && !messageType.equals("group")) {
                    System.out.println("[Invalid message type. Expected: private or group]");
                } else {
                    if (messageType.equals("private")) {
                        sendMessageToUser(receiver, message);
                    } else if (messageType.equals("group")) {
                        sendMessageToGroup(receiver, message);
                    }
                }
            }
            else if (command.equalsIgnoreCase("createGroup")) {
                Message changeStateMessage = new Message(Message.STATE_IDLE, Message.CHANGE_STATE, 0, "23");
                sendMessage(changeStateMessage);
                System.out.println("[Enter the name of the group: ]");
                String groupName = scanner.nextLine();
                createGroup(groupName);
            }
            else if (command.equalsIgnoreCase("addMember")) {
                Message changeStateMessage = new Message(Message.STATE_IDLE, Message.CHANGE_STATE, 0, "23");
                sendMessage(changeStateMessage);

                System.out.println("[Enter the group name: ]");
                String groupName = scanner.nextLine();

                System.out.println("[Enter the username of the member to add: ]");
                String memberName = scanner.nextLine();

                addMemberToGroup(groupName, memberName);
            }
            else if (command.equalsIgnoreCase("removeMember")) {
                Message changeStateMessage = new Message(Message.STATE_IDLE, Message.CHANGE_STATE, 0, "23");
                sendMessage(changeStateMessage);
                System.out.println("[Enter the group name:]");
                String groupName = scanner.nextLine();

                System.out.println("[Enter the username of the member to remove: ]");
                String memberName = scanner.nextLine();

                removeMemberFromGroup(groupName, memberName);
            }
            else if (command.equalsIgnoreCase("exit")) {
                break;
            }
            // Unrecognized command
            else {
                System.out.println("Unrecognized command: " + command);
            }
        }

        // Close the connection when done
        stopConnection();

        // Close the scanner
        scanner.close();
    }
    // Method to send the message

    public void sendMessage(Message message) throws Exception {
        //System.out.println("[" + message + "]");
        message.encryptPayload();
        out.println(message.serialize());
        out.flush();
    }
    // Method to receive the message

    public Message receiveMessage() throws IOException, Exception {
        String inputLine = in.readLine();
        Message msg = Message.deserialize(inputLine);
        msg.decryptPayload();
        return msg;
    }
    // Disconnect method to send disconnect request to the server

    public void stopConnection() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() throws Exception {
        Message disconnectMessage = new Message(Message.STATE_DISCONNECTED, Message.DISCONNECT, 0, username);
        sendMessage(disconnectMessage);
    }
    // Method to start the heartbeat functionality

    public void startHeartbeat() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            // Only send the heartbeat if the user provided some input in the last 10 seconds
            if (System.currentTimeMillis() - lastInputTime < 10000) {
                Message heartbeatMsg = new Message(Message.STATE_IDLE, Message.HEARTBEAT, 0, username);
                try {
                    sendMessage(heartbeatMsg);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    // Method to stop the heartbeat functionality

    public void stopHeartbeat() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
    // Method that updates the last input time when a user input is received

    public void userInputReceived() {
        lastInputTime = System.currentTimeMillis();
    }
    // Method to send a message to a user

    public void sendMessageToUser(String receiver, String message) throws Exception {
        Message newMsg = new Message(Message.STATE_SENDING_MESSAGE, Message.SEND_MESSAGE, 0, username + "|private|" + receiver + "|" + message);
        sendMessage(newMsg);
    }
    // Method to send a message to a group

    public void sendMessageToGroup(String groupName, String message) throws Exception {
        Message newMsg = new Message(Message.STATE_SENDING_MESSAGE, Message.SEND_MESSAGE, 0, username + "|group|" + groupName + "|" + message);
        sendMessage(newMsg);
    }
    // Method to create a new group

    public void createGroup(String groupName) throws Exception {
        Message newMsg = new Message(Message.STATE_CREATING_GROUP, Message.CREATE_GROUP, 0, username + "|" + groupName);
        sendMessage(newMsg);
    }
    // Method to add a member to a group

    public void addMemberToGroup(String groupName, String member) throws Exception {
        Message newMsg = new Message(Message.STATE_CREATING_GROUP, Message.ADD_MEMBER, 0, username + "|" + groupName + "|" + member);
        sendMessage(newMsg);
    }
    // Method to remove a member from a group

    public void removeMemberFromGroup(String groupName, String member) throws Exception {
        Message newMsg = new Message(Message.STATE_CREATING_GROUP, Message.REMOVE_MEMBER, 0, username + "|" + groupName + "|" + member);
        sendMessage(newMsg);
    }
    // Method to send connection request to the server

    public void connect(String username, String password) throws Exception {
        Message connectMessage = new Message(Message.STATE_WAITING_FOR_CONNECTION, Message.CONNECT_REQUEST, 0, username + "|" + password);
        //System.out.println("[LOG][Send CONNECT_REQUEST to Server|STATE_WAITING_FOR_CONNECTION]");
        sendMessage(connectMessage);
    }


    // Method to handle incoming messages based on their type

    private void handleMessage(Message message) throws Exception {
        int messageType = message.getType();
        String payload = message.getPayload();


        switch (messageType) {
            case Message.CONNECT_ACK:
                handleConnectAck();
                break;
            case Message.AUTH_NACK:
                handleAuthNack();
                break;
            case Message.AUTH_ACK:
                handleAuthAck();
                break;
            case Message.RECEIVE_MESSAGE:
                String[] parts = payload.split("\\|", -1);
                System.out.println("[Receive Message]: " + parts[1]);
                break;
            default:
                System.out.println("[LOG][Unhandled message type: " + messageType + "]");
                break;
        }
    }
    // Method to handle connection acknowledgment

    private void handleConnectAck() throws Exception {
        Message authRequestMsg = new Message(Message.STATE_AUTHENTICATING, Message.AUTH_REQUEST, 0,username + "|" + password);
        //System.out.println("[LOG][AUTH_REQUEST SENT TO SERVER]");
        sendMessage(authRequestMsg);
    }
    // Method to handle authentication failure

    private void handleAuthNack() throws Exception {
        String failureMessage = "[LOG][Authentication failed. Disconnecting]";
        System.out.println(failureMessage);
        throw new Exception(failureMessage);
    }
    // Method to handle authentication success

    private void handleAuthAck() throws Exception {
        //System.out.println("[LOG][Authentication successfully]");
        Message idleMsg = new Message(Message.STATE_AUTHENTICATED, Message.AUTH_SUCCESS, 0,"START SENDING");
        sendMessage(idleMsg);

    }






}
