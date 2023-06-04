import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatClientHandlerTest {
    private static final int PORT = 4444;
    private ServerSocket serverSocket;
    private ExecutorService executorService;

    @BeforeEach
    public void setUp() throws IOException {
        executorService = Executors.newFixedThreadPool(2);
        serverSocket = new ServerSocket(PORT);
    }

    @Test
    public void testChatClientHandler() throws Exception {
        executorService.submit(() -> {
            try (Socket clientSocket = new Socket("localhost", PORT)) {
                ChatClientHandler handler = new ChatClientHandler(clientSocket, null); // ChatServer instance set as null for simplicity
                handler.setClientState(Message.STATE_IDLE);

                assertEquals(Message.STATE_IDLE, handler.getClientState());

                Message originalMessage = new Message(1, Message.SEND_MESSAGE, 0, "Test message");
                handler.sendMessage(originalMessage);

                Message receivedMessage = handler.receiveMessage();
                assertEquals(originalMessage.getPayload(), receivedMessage.getPayload());

                handler.closeConnection();
                assertTrue(clientSocket.isClosed());

            } catch (Exception e) {
                fail("Exception should not have been thrown.");
            }
        });

        try (Socket socket = serverSocket.accept()) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String receivedSerializedMsg = in.readLine();
            Message receivedMsg = Message.deserialize(receivedSerializedMsg);
            receivedMsg.decryptPayload();

            out.println(receivedSerializedMsg); // Echoing back the message
        } catch (Exception e) {
            fail("Exception should not have been thrown.");
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        serverSocket.close();
        executorService.shutdown();
    }
}
