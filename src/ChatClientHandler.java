import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/*
 * The ChatClientHandler is responsible for managing and handling communications with a connected client.
 * It is capable of receiving messages, sending messages, and maintaining the state of the client.
 * Each instance of ChatClientHandler is associated with a single client connection and is created by the server when a client connects.
 *
 * Here's a breakdown of its main responsibilities:
 *
 * 1. Reading incoming messages: The `receiveMessage()` method is used to read the incoming data from a client.
 *    It uses a BufferedReader to read the data from the client's socket input stream.
 *    The incoming data is expected to be serialized and encrypted. So the received message is first deserialized and then decrypted.
 *
 * 2. Sending outgoing messages: The `sendMessage()` method is used to send a message to the client.
 *    Before sending, the message is encrypted and serialized.
 *
 * 3. Managing the client state: The `getClientState()` and `setClientState()` methods are used to get and set the state of the client.
 *    The state represents the current phase of communication or action the client is in, like waiting for connection, authenticating, etc.
 *
 * 4. Closing the connection: When the communication with the client is no longer needed, the `closeConnection()` method is called.
 *    It closes the input and output streams as well as the client socket.
 */
public class ChatClientHandler {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private int clientState;

    public ChatClientHandler(Socket clientSocket, ChatServer chatServer) throws IOException {
        this.clientSocket = clientSocket;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.clientState = Message.STATE_WAITING_FOR_CONNECTION;
    }


    public int getClientState() {
        return this.clientState;
    }


    public void setClientState(int state) {
        this.clientState = state;
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
