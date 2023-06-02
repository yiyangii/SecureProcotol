import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String serverAddress;
    private int serverPort;
    private String username;

    private ScheduledExecutorService scheduler;

    private long lastInputTime;


    public ChatClient(String serverAddress, int serverPort, String username) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
    }

    public void startConnection() throws IOException {
        socket = new Socket(serverAddress, serverPort);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void sendMessage(Message message) throws Exception {
        message.encryptPayload();
        out.println(message.serialize());
        out.flush();
    }

    public Message receiveMessage() throws IOException, Exception {
        String inputLine = in.readLine();
        Message msg = Message.deserialize(inputLine);
        msg.decryptPayload();
        return msg;
    }

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
        Message disconnectMessage = new Message(1, Message.DISCONNECT, 0, username);
        sendMessage(disconnectMessage);
    }

    public void startHeartbeat() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            // Only send the heartbeat if the user provided some input in the last 10 seconds
            if (System.currentTimeMillis() - lastInputTime < 10000) {
                Message heartbeatMsg = new Message(1, Message.HEARTBEAT, 0, username);
                try {
                    sendMessage(heartbeatMsg);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public void stopHeartbeat() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void userInputReceived() {
        lastInputTime = System.currentTimeMillis();
    }


    public void sendMessageToUser(String receiver, String message) throws Exception {
        Message newMsg = new Message(1, Message.SEND_MESSAGE, 0, username + "|private|" + receiver + "|" + message);
        sendMessage(newMsg);
    }

    public void sendMessageToGroup(String groupName, String message) throws Exception {
        Message newMsg = new Message(1, Message.SEND_MESSAGE, 0, username + "|group|" + groupName + "|" + message);
        sendMessage(newMsg);
    }

    public void createGroup(String groupName) throws Exception {
        Message newMsg = new Message(1, Message.CREATE_GROUP, 0, username + "|" + groupName);
        sendMessage(newMsg);
    }

    public void addMemberToGroup(String groupName, String member) throws Exception {
        Message newMsg = new Message(1, Message.ADD_MEMBER, 0, username + "|" + groupName + "|" + member);
        sendMessage(newMsg);
    }

    public void removeMemberFromGroup(String groupName, String member) throws Exception {
        Message newMsg = new Message(1, Message.REMOVE_MEMBER, 0, username + "|" + groupName + "|" + member);
        sendMessage(newMsg);
    }
    public void connect(String username, String password) throws Exception {
        Message connectMessage = new Message(1, Message.CONNECT, 0, username + "|" + password);
        sendMessage(connectMessage);
    }

}
