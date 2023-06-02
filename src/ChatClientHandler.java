import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClientHandler {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;

    public ChatClientHandler(Socket clientSocket, ChatServer chatServer) throws IOException {
        this.clientSocket = clientSocket;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
    }

    public Message receiveMessage() throws IOException, Exception {
        String response = in.readLine();
        Message msg = Message.deserialize(response);
        msg.decryptPayload();
        return msg;
    }

    public void sendMessage(Message msg) throws Exception {
        msg.encryptPayload();
        out.println(msg.serialize());
    }

    public void closeConnection() {
        try {
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
